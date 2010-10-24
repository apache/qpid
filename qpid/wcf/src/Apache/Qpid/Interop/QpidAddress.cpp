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


/*
 * This program parses strings of the form "node/subject;{options}" as
 * used in the Qpid messaging API.  It provides basic wiring
 * capabilities to create/delete temporary queues (to topic
 * subsciptions) and unbound "point and shoot" queues.
 */


#include <windows.h>
#include <msclr\lock.h>
#include <oletx2xa.h>

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
#include "QpidAddress.h"

namespace Apache {
namespace Qpid {
namespace Interop {

using namespace System;
using namespace System::Runtime::InteropServices;
using namespace msclr;

using namespace qpid::client;
using namespace std;

QpidAddress::QpidAddress(String^ s, bool isInput) {
    address = s;
    nodeName = s;
    isInputChannel = isInput;
    isQueue = true;

    if (address->StartsWith("//")) {
	// special case old style address to default exchange, 
	// no options, output only
	if ((s->IndexOf(';') != -1) || isInputChannel)
		throw gcnew ArgumentException("Invalid 0-10 address: " + address);
	nodeName = nodeName->Substring(2);
	return;
    }

    String^ options = nullptr;
    int pos = s->IndexOf(';');
    if (pos != -1) {
	options = s->Substring(pos + 1);
	nodeName = s->Substring(0, pos);

	if (options->Length > 0) {
	    if (!options->StartsWith("{") || !options->EndsWith("}"))
		throw gcnew ArgumentException("Invalid address: " + address);
	    options = options->Substring(1, options->Length - 2);
	    array<String^>^ subOpts = options->Split(String(",: ").ToCharArray(), StringSplitOptions::RemoveEmptyEntries);

	    if ((subOpts->Length % 2) != 0)
		throw gcnew ArgumentException("Bad address (options): " + address);

	    for (int i=0; i < subOpts->Length; i += 2) {
		String^ opt = subOpts[i];
		String^ optArg = subOpts[i+1];
		if (opt->Equals("create")) {
		    creating = PolicyApplies(optArg);
		}
		else if (opt->Equals("delete")) {
		    deleting = PolicyApplies(optArg);
		}
		else if (opt->Equals("mode")) {
		    if (optArg->Equals("browse")) {
			browsing = isInputChannel;
		    }
		    else if (!optArg->Equals("consume")) {
			throw gcnew ArgumentException("Invalid browsing option: " + optArg);
		    }
		}
		else if (opt->Equals("assert") || opt->Equals("node")) {
		    throw gcnew ArgumentException("Unsupported address option: " + opt);
		}
		else {
		    throw gcnew ArgumentException("Bad address option: " + opt);
		}
	    }
	}
	else
	    options = nullptr;
    }

    pos = nodeName->IndexOf('/');
    if (pos != -1) {
	subject = nodeName->Substring(pos + 1);
	if (String::IsNullOrEmpty(subject))
	    subject = nullptr;
	nodeName = nodeName->Substring(0, pos);
    }
}


QpidAddress^ QpidAddress::CreateAddress(String^ s, bool isInput) {
    QpidAddress^ addr = gcnew QpidAddress(s, isInput);
    return addr;
}


void QpidAddress::ResolveLink(AmqpSession^ amqpSession) {

    AsyncSession* asyncSessionp = (AsyncSession *) amqpSession->BorrowNativeSession().ToPointer();
    if (asyncSessionp == NULL)
	throw gcnew ObjectDisposedException("session");

    deleteName = nullptr;
    isQueue = true;

    try {
	Session session = sync(*asyncSessionp);
	std::string n_name = QpidMarshal::ToNative(nodeName);
        ExchangeBoundResult result = session.exchangeBound(arg::exchange=n_name, arg::queue=n_name);

	bool queueFound = !result.getQueueNotFound();
	bool exchangeFound = !result.getExchangeNotFound();

	if (isInputChannel) {

	    if (queueFound) {
		linkName = nodeName;
		if (deleting)
		    deleteName = nodeName;
	    }
	    else if (exchangeFound) {
		isQueue = false;
		String^ tmpkey = nullptr;
		String^ tmpname = nodeName + "_" + Guid::NewGuid().ToString();
		bool haveSubject = !String::IsNullOrEmpty(subject);
		FieldTable bindArgs;

		std::string exchangeType = session.exchangeQuery(n_name).getType();
		if (exchangeType == "topic") {
		    tmpkey = haveSubject ? subject : "#";
		}
		else if (exchangeType == "fanout") {
		    tmpkey = tmpname;
		}
		else if (exchangeType == "headers") {
		    tmpkey = haveSubject ? subject : "match-all";
		    if (haveSubject)
			bindArgs.setString("qpid.subject", QpidMarshal::ToNative(subject));
		    bindArgs.setString("x-match", "all");
		}
		else if (exchangeType == "xml") {
		    tmpkey = haveSubject ? subject : "";
		    if (haveSubject) {
			String^ v = "declare variable $qpid.subject external; $qpid.subject = '" +
			    subject + "'";
			bindArgs.setString("xquery", QpidMarshal::ToNative(v));
		    }
		    else
			bindArgs.setString("xquery", "true()");
		}
		else {
		    tmpkey = haveSubject ? subject : "";
		}

		std::string qn = QpidMarshal::ToNative(tmpname);
		session.queueDeclare(arg::queue=qn, arg::autoDelete=true, arg::exclusive=true);
		bool success = false;
		try {
		    session.exchangeBind(arg::exchange=n_name, arg::queue=qn, 
					 arg::bindingKey=QpidMarshal::ToNative(tmpkey),
					 arg::arguments=bindArgs);
		    bindKey = tmpkey; // remember for later cleanup
		    success = true;
		}
		finally {
		    if (!success)
			session.queueDelete(arg::queue=qn);
		}
		linkName = tmpname;
		deleteName = tmpname;
		deleting = true;
	    }
	    else if (creating) {
		// only create "point and shoot" queues for now
		session.queueDeclare(arg::queue=QpidMarshal::ToNative(nodeName));
		// leave unbound

		linkName = nodeName;

		if (deleting)
		    deleteName = nodeName;
	    }
	    else {
		throw gcnew ArgumentException("AMQP broker node not found: " + nodeName);
	    }
	}
	else {
	    // Output channel

	    bool oldStyleUri = address->StartsWith("//");

	    if (queueFound) {
		linkName = "";	// default exchange for point and shoot
		routingKey = nodeName;
		if (deleting)
		    deleteName = nodeName;
	    }
	    else if (exchangeFound && !oldStyleUri) {
		isQueue = false;
		linkName = nodeName;
		routingKey = subject;
	    }
	    else if (creating) {
		// only create "point and shoot" queues for now
		session.queueDeclare(arg::queue=QpidMarshal::ToNative(nodeName));
		// leave unbound
		linkName = "";
		routingKey = nodeName;
		if (deleting)
		    deleteName = nodeName;
	    }
	    else {
		throw gcnew ArgumentException("AMQP broker node not found: " + nodeName);
	    }
	}
    }
    finally {
	amqpSession->ReturnNativeSession();
    }
}

void QpidAddress::CleanupLink(AmqpSession^ amqpSession) {
    if (deleteName == nullptr)
	return;

    AsyncSession* asyncSessionp = (AsyncSession *) amqpSession->BorrowNativeSession().ToPointer();
    if (asyncSessionp == NULL) {
	// TODO: log it: can't undo tear down actions
	return;
    }

    try {
	Session session = sync(*asyncSessionp);
	std::string q = QpidMarshal::ToNative(deleteName);
	if (isInputChannel && !isQueue) {
	    // undo the temp wiring to the topic
	    session.exchangeUnbind(arg::exchange=QpidMarshal::ToNative(nodeName), arg::queue=q,
				   arg::bindingKey=QpidMarshal::ToNative(bindKey));
	}
	session.queueDelete(q);
    }
    catch (Exception^ e) {
	// TODO: log it
    }
    finally {
	amqpSession->ReturnNativeSession();
    }
}

bool QpidAddress::PolicyApplies(String^ mode) {
    if (mode->Equals("always"))
	return true;
    if (mode->Equals("sender"))
	return !isInputChannel;
    if (mode->Equals("receiver"))
	return isInputChannel;
    if (mode->Equals("never"))
	return false;

    throw gcnew ArgumentException(String::Format("Bad address option {0} for {1}", mode, address));
}

}}} // namespace Apache::Qpid::Interop
