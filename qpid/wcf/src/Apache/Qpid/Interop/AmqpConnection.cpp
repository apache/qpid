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
#include <oletx2xa.h>

#include "qpid/client/AsyncSession.h"
#include "qpid/client/SubscriptionManager.h"
#include "qpid/client/Connection.h"
#include "qpid/client/Message.h"
#include "qpid/client/MessageListener.h"
#include "qpid/framing/FrameSet.h"

#include "AmqpConnection.h"
#include "AmqpSession.h"
#include "QpidMarshal.h"
#include "QpidException.h"
#include "DtxResourceManager.h"
#include "XaTransaction.h"

namespace Apache {
namespace Qpid {
namespace Interop {

using namespace System;
using namespace System::Runtime::InteropServices;
using namespace msclr;

using namespace qpid::client;
using namespace std;


// Note on locks: Use thisLock for fast counting and idle/busy
// notifications.  Use the "sessions" list to serialize session
// creation/reaping and overall tear down.


AmqpConnection::AmqpConnection(String^ server, int port) :
    connectionp(NULL),
    busyCount(0),
    disposed(false)
{
    initialize (server, port, false, false, nullptr, nullptr);
}

AmqpConnection::AmqpConnection(System::String^ server, int port, bool ssl, bool saslPlain, System::String^ username, System::String^ password) :
    connectionp(NULL),
    busyCount(0),
    disposed(false)
{
    initialize (server, port, ssl, saslPlain, username, password);
}

void AmqpConnection::initialize(System::String^ server, int port, bool ssl, bool saslPlain, System::String^ username, System::String^ password)
{
    if (server == nullptr)
	throw gcnew ArgumentNullException("AMQP server");
    if (saslPlain) {
	if (username == nullptr)
	    throw gcnew ArgumentNullException("username");
	if (username == nullptr)
	    throw gcnew ArgumentNullException("password");
    }

    bool success = false;
    System::Exception^ openException = nullptr;
    sessions = gcnew Collections::Generic::List<AmqpSession^>();
    thisLock = gcnew Object();

    try {
        connectionp = new Connection;

	if (ssl || saslPlain) {
	    ConnectionSettings proposedSettings;
	    proposedSettings.host = QpidMarshal::ToNative(server);
	    proposedSettings.port = port;
	    if (ssl)
		proposedSettings.protocol = "ssl";

	    if (saslPlain) {
		proposedSettings.username = QpidMarshal::ToNative(username);
		proposedSettings.password = QpidMarshal::ToNative(password);
		proposedSettings.mechanism = "PLAIN";
	    }

	    connectionp->open (proposedSettings);
	}
	else {
	    connectionp->open (QpidMarshal::ToNative(server), port);
	}

	// TODO: registerFailureCallback for failover
	success = true;
	const ConnectionSettings& settings = connectionp->getNegotiatedSettings();
	this->maxFrameSize = settings.maxFrameSize;
	this->host = server;
	this->port = port;
	this->ssl = ssl;
	this->saslPlain = saslPlain;
	this->username = username;
	this->password = password;
	this->isOpen = true;
    } catch (const qpid::Exception& error) {
        String^ errmsg = gcnew String(error.what());
	openException = gcnew QpidException(errmsg);
    } finally {
        if (!success) {
 	    Cleanup();
	    if (openException == nullptr) {
	        openException = gcnew QpidException ("unknown connection failure");
	    }
	    throw openException;
	}
    }
}

AmqpConnection^ AmqpConnection::Clone() {
    if (disposed)
	throw gcnew ObjectDisposedException("AmqpConnection.Clone");
    return gcnew AmqpConnection (this->host, this->port, this->ssl, this->saslPlain, this->username, this->password);
}

void AmqpConnection::Cleanup()
{
    {
        lock l(sessions);
	if (disposed)
	    return;
	disposed = true;
    }

    try {
        // let the child sessions clean up

        for each(AmqpSession^ s in sessions) {
	    s->ConnectionClosed();
	}
    }
    finally
    {
	if (connectionp != NULL) {
	    isOpen = false;
	    connectionp->close();
	    delete connectionp;
	    connectionp = NULL;
	}
    }
}

AmqpConnection::~AmqpConnection()
{
    Cleanup();
}

AmqpConnection::!AmqpConnection()
{
    Cleanup();
}

void AmqpConnection::Close()
{
    // Simulate Dispose()...
    Cleanup();
    GC::SuppressFinalize(this);
}

AmqpSession^ AmqpConnection::CreateSession()
{
    lock l(sessions);
    if (disposed) {
	throw gcnew ObjectDisposedException("AmqpConnection");
    }
    AmqpSession^ session = gcnew AmqpSession(this, connectionp);
    sessions->Add(session);
    return session;
}

// called whenever a child session becomes newly busy (a first reader or writer since last idle)

void AmqpConnection::NotifyBusy()
{
    bool changed = false;
    {
        lock l(thisLock);
	if (busyCount++ == 0)
	    changed = true;
    }
}

// called whenever a child session becomes newly idle (a last reader or writer has closed)
// The connection is idle when none of its child sessions are busy

void AmqpConnection::NotifyIdle()
{
    bool connectionIdle = false;
    {
        lock l(thisLock);
	if (--busyCount == 0)
	    connectionIdle = true;
    }
    if (connectionIdle) {
        OnConnectionIdle(this, System::EventArgs::Empty);
    }
}

void HexAppend(StringBuilder^ sb, String^ s) {
    if (s->Length > 0) {
	array<unsigned char>^ bytes = Encoding::UTF8->GetBytes(s);
	for each (unsigned char b in bytes) {
	    sb->Append(String::Format("{0:x2}", b));
	}
    }
    sb->Append(".");
}


// Note: any change to this format has to be reflected in the DTC plugin's xa_open()
// for now: "QPIDdsnV2.port.host.instance_id.SSL_tf.SASL_mech.username.password"
// This extended info is needed so that the DTC can make a separate connection to the broker
// for recovery.

String^ AmqpConnection::DataSourceName::get() {
    if (dataSourceName == nullptr) {
	StringBuilder^ sb = gcnew StringBuilder();
	sb->Append("QPIDdsnV2.");

	sb->Append(this->port);
	sb->Append(".");

	HexAppend(sb, this->host);

	sb->Append(System::Diagnostics::Process::GetCurrentProcess()->Id);
	sb->Append("-");
	sb->Append(AppDomain::CurrentDomain->Id);
	sb->Append(".");

	if (this->ssl)
	    sb->Append("T");
	else
	    sb->Append("F");
	sb->Append(".");

	if (this->saslPlain) {
	    sb->Append("P.");
	    HexAppend(sb, this->username);
	    HexAppend(sb, this->password);
	}
	else {
	    // SASL anonymous
	    sb->Append("A.");
	}

	dataSourceName = sb->ToString();
    }
    return dataSourceName;
}


}}} // namespace Apache::Qpid::Interop
