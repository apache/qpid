/*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

#include <windows.h>
#include <msclr\lock.h>

#include "qpid/client/AsyncSession.h"
#include "qpid/client/SubscriptionManager.h"
#include "qpid/client/Connection.h"
#include "qpid/client/SessionImpl.h"
#include "qpid/client/SessionBase_0_10Access.h"
#include "qpid/client/Message.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/client/Future.h"

#include "AmqpConnection.h"
#include "AmqpSession.h"
#include "AmqpMessage.h"
#include "MessageBodyStream.h"
#include "InputLink.h"
#include "OutputLink.h"
#include "QpidMarshal.h"
#include "QpidException.h"

namespace Apache {
namespace Qpid {
namespace Interop {

using namespace System;
using namespace System::Runtime::InteropServices;
using namespace msclr;

using namespace qpid::client;
using namespace std;


AmqpSession::AmqpSession(AmqpConnection^ conn, qpid::client::Connection* qpidConnectionp) :
    connection(conn),
    sessionp(NULL),
    sessionImplp(NULL),
    subs_mgrp(NULL),
    openCount(0)
{
    bool success = false;

    try {
	sessionp = new qpid::client::AsyncSession;
	*sessionp = qpidConnectionp->newSession();
	subs_mgrp = new SubscriptionManager (*sessionp);
	waiters = gcnew Collections::Generic::List<CompletionWaiter^>();
	success = true;
    } finally {
        if (!success) {
 	    Cleanup();
	    // TODO: include inner exception information
	    throw gcnew QpidException ("session creation failure");
	}
    }
}


void AmqpSession::Cleanup()
{
    if (subs_mgrp != NULL) {
	subs_mgrp->stop();
	delete subs_mgrp;
	subs_mgrp = NULL;
    }

    if (localQueuep != NULL) {
        delete localQueuep;
        localQueuep = NULL;
    }

    if (sessionp != NULL) {
        sessionp->close();
	delete sessionp;
	sessionp = NULL;
	sessionImplp = NULL;
    }

    if (connectionp != NULL) {
        connectionp->close();
	delete connectionp;
	connectionp = NULL;
    }
}


// Called by the parent AmqpConnection

void AmqpSession::ConnectionClosed()
{
    lock l(waiters);
    Cleanup();
}

InputLink^ AmqpSession::CreateInputLink(System::String^ sourceQueue)
{
    return CreateInputLink(sourceQueue, true, false, nullptr, nullptr);
}

InputLink^ AmqpSession::CreateInputLink(System::String^ sourceQueue, bool exclusive, bool temporary, 
					System::String^ filterKey, System::String^ exchange)
{
    InputLink^ link = gcnew InputLink (this, sourceQueue, sessionp, subs_mgrp, exclusive, temporary, filterKey, exchange);
    {
        lock l(waiters);
	if (openCount == 0) {
	    connection->NotifyBusy();
	}
	openCount++;
    }
    return link;
}

OutputLink^ AmqpSession::CreateOutputLink(System::String^ targetQueue)
{
    OutputLink^ link = gcnew OutputLink (this, targetQueue);

    lock l(waiters);

    if (sessionImplp == NULL) {
	// not needed unless sending messages
	SessionBase_0_10Access sa(*sessionp);
	boost::shared_ptr<SessionImpl> sip = sa.get();
	sessionImplp = sip.get();
    }

    if (openCount == 0) {
	connection->NotifyBusy();
    }
    openCount++;

    return link;
}


// called whenever a child InputLink or OutputLink is closed or finalized
void AmqpSession::NotifyClosed()
{
    lock l(waiters);
    openCount--;
    if (openCount == 0) {
	connection->NotifyIdle();
    }
}    


CompletionWaiter^ AmqpSession::SendMessage (System::String^ queue, MessageBodyStream ^mbody, TimeSpan timeout, bool async, AsyncCallback^ callback, Object^ state)
{
    lock l(waiters);
    if (sessionp == NULL)
	throw gcnew ObjectDisposedException("Send");

    // create an AMQP message.transfer command to use with the partial frameset from the MessageBodyStream

    std::string exname = QpidMarshal::ToNative(queue);
    FrameSet *framesetp = (FrameSet *) mbody->GetFrameSet().ToPointer();
    uint8_t acceptMode=1;
    uint8_t acquireMode=0;
    MessageTransferBody mtcmd(ProtocolVersion(0,10), exname, acceptMode, acquireMode);
    // ask for a command completion
    mtcmd.setSync(true);

    //send it

    Future *futurep = NULL;
    try {
	futurep = new Future(sessionImplp->send(mtcmd, *framesetp));

	CompletionWaiter^ waiter = nullptr;
	if (async || (timeout != TimeSpan::MaxValue)) {
	    waiter = gcnew CompletionWaiter(this, timeout, (IntPtr) futurep, callback, state);
	    // waiter is responsible for releasing the Future native resource
	    futurep = NULL;
	    addWaiter(waiter);
	}

	l.release();

	if (waiter != nullptr)
	    return waiter;

	// synchronous send with no timeout: no need to involve the asyncHelper thread

	internalWaitForCompletion((IntPtr) futurep);
    }
    finally {
	if (futurep != NULL)
	    delete (futurep);
    }
    return nullptr;
}
    
void AmqpSession::Bind(System::String^ queue, System::String^ exchange, System::String^ filterKey)
{
    sessionp->exchangeBind(arg::queue=QpidMarshal::ToNative(queue),
			       arg::exchange=QpidMarshal::ToNative(exchange),
			       arg::bindingKey=QpidMarshal::ToNative(filterKey));    

}


void AmqpSession::internalWaitForCompletion(IntPtr fp)
{
    lock l(waiters);
    if (sessionp == NULL)
	throw gcnew ObjectDisposedException("AmqpSession");

    // increment the smart pointer count to sessionImplp to guard agains async close
    Session sessionCopy(*sessionp);

    l.release();
    // Qpid native lib call to wait for the command completion
    ((Future *)fp.ToPointer())->wait(*sessionImplp);
}

// call with lock held
void AmqpSession::addWaiter(CompletionWaiter^ waiter)
{
    waiters->Add(waiter);
    if (!helperRunning) {
	helperRunning = true;
	ThreadPool::QueueUserWorkItem(gcnew WaitCallback(this, &AmqpSession::asyncHelper));
    }
}


void AmqpSession::removeWaiter(CompletionWaiter^ waiter)
{
    // a waiter can be removed from anywhere in the list if timed out

    lock l(waiters);
    int idx = waiters->IndexOf(waiter);
    if (idx == -1) {
	// TODO: assert or log
    }
    else {
	waiters->RemoveAt(idx);
    }
}


// process CompletionWaiter list one at a time.

void AmqpSession::asyncHelper(Object ^unused)
{
    lock l(waiters);

    while (true) {
	if (waiters->Count == 0) {
	    helperRunning = false;
	    return;
	}

	CompletionWaiter^ waiter = waiters[0];
	l.release();
	// can block, but for short time
	// the waiter removes itself from the list, possibly as the timer thread on timeout
	waiter->Run();
	l.acquire();
    }
}

bool AmqpSession::MessageStop(Completion &comp, std::string &name)
{
    lock l(waiters);

    if (sessionp == NULL)
	return false;

    comp = sessionp->messageStop(name, true);
    return true;
}

void AmqpSession::AcceptAndComplete(SequenceSet& transfers)
{
    lock l(waiters);

    if (sessionp == NULL)
	throw gcnew ObjectDisposedException("Accept");

    sessionp->markCompleted(transfers, false);
    sessionp->messageAccept(transfers, false);
}


}}} // namespace Apache::Qpid::Cli
