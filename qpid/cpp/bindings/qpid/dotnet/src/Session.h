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

#include <windows.h>
#include <msclr\lock.h>
#include <oletx2xa.h>
#include <string>
#include <limits>

#include "qpid/messaging/Session.h"
#include "qpid/messaging/Connection.h"
#include "qpid/messaging/Duration.h"
#include "qpid/messaging/Receiver.h"
#include "qpid/messaging/Sender.h"

namespace qpid {
namespace messaging {
    // Dummy class to satisfy linker
    class SessionImpl {};
}}

namespace org {
namespace apache {
namespace qpid {
namespace messaging {

    /// <summary>
    /// Session is a managed wrapper for a ::qpid::messaging::Session
    /// </summary>

    ref class Connection;
    ref class Duration;
    ref class Receiver;
    ref class Sender;

    public ref class Session
    {
    private:
        // The kept object in the Messaging C++ DLL
        ::qpid::messaging::Session * sessionp;

        // The connection that created this session
        Connection ^ parentConnectionp;

        // Kept object deletion code
        void Cleanup();

    public:
        Session(::qpid::messaging::Session * sessionp,
            Connection ^ connRef);
        ~Session();
        !Session();
        Session(const Session % rhs);

        void close();
        void commit();
        void rollback();
        void acknowledge();
        void acknowledge(bool sync);
        //void reject(Message);
        //void release(Message);
        void sync();
        void sync(bool block);
        System::UInt32 getReceivable();
        System::UInt32 getUnsettledAcks();
        //bool nextReceiver(Receiver);
        //bool nextReceiver(Receiver, Duration timeout);
        //Receiver nextReceiver(Duration timeout);
        //bool nextReceiver()
        Sender     ^ createSender  (System::String ^ address);
        Receiver   ^ createReceiver(System::String ^ address);
        Connection ^ getConnection();
        bool hasError();
        void checkError();
    };
}}}}
