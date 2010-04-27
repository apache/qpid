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

#pragma once

namespace Apache {
namespace Qpid {
namespace Interop {

using namespace System;
using namespace std;
using namespace qpid::client;

ref class AmqpSession;
ref class DtxResourceManager;

public delegate void ConnectionIdleEventHandler(Object^ sender, EventArgs^ eventArgs);

public ref class AmqpConnection
{
private:
    Connection* connectionp;
    bool disposed;
    Collections::Generic::List<AmqpSession^>^ sessions;
    bool isOpen;
    int busyCount;
    int maxFrameSize;
    DtxResourceManager^ dtxResourceManager;
    // unique string used for distributed transactions
    String^ dataSourceName;
    Object ^thisLock;

    // properties needed to allow DTC to do transactions (see DataSourceName
    String^ host;
    int port;
    bool ssl;
    bool saslPlain;
    String^ username;
    String^ password;

    void Cleanup();
    void initialize (System::String^ server, int port, bool ssl, bool saslPlain, System::String^ username, System::String^ password);

 internal:
    void NotifyBusy();
    void NotifyIdle();
    AmqpConnection^ Clone();

    property int MaxFrameSize {
	int get () { return maxFrameSize; }
    }

    property DtxResourceManager^ CachedResourceManager {
	DtxResourceManager^ get () { return dtxResourceManager; }
	void set (DtxResourceManager^ value) { dtxResourceManager = value; }
    }

    property String^ DataSourceName {
	String^ get();
    }

public:  
    AmqpConnection(System::String^ server, int port);
    AmqpConnection(System::String^ server, int port, bool ssl, bool saslPlain, System::String^ username, System::String^ password);
    ~AmqpConnection();
    !AmqpConnection();
    void Close();
    AmqpSession^ CreateSession();
    event ConnectionIdleEventHandler^ OnConnectionIdle;

    property bool IsOpen {
	bool get() { return isOpen; }
    };

    property bool IsIdle {
	bool get() { return (busyCount == 0); }
    }
};


}}} // namespace Apache::Qpid::Interop
