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

#include "qpid/messaging/Receiver.h"
#include "qpid/messaging/Session.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/Duration.h"

namespace qpid {
namespace messaging {
    // Dummy class to satisfy linker
    class ReceiverImpl {};
}}

namespace org {
namespace apache {
namespace qpid {
namespace messaging {

    /// <summary>
    /// Mreceiver is a managed wrapper for a ::qpid::messaging::Receiver
    /// </summary>

    ref class Session;
    ref class Message;
    ref class Duration;

    public ref class Receiver
    {
    private:
        // The kept object in the Messaging C++ DLL
        ::qpid::messaging::Receiver * receiverp;

        // The session that created this Receiver
        Session ^ parentSession;

        // Kept object lifetime flag
        bool disposed;

        // Kept object deletion code
        void Cleanup();

    public:
        Receiver(::qpid::messaging::Receiver * r,
            Session ^ sessRef);
        ~Receiver();
        !Receiver();
        Receiver(const Receiver ^ rhs);

        bool get(Message ^ mmsgp);
        bool get(Message ^ mmsgp, Duration ^ durationp);
        Message ^ get(Duration ^ durationp);

        bool fetch(Message ^ mmsgp);
        bool fetch(Message ^ mmsgp, Duration ^ durationp);
        Message ^ fetch(Duration ^ durationp);

        System::UInt32 getCapacity();
        System::UInt32 getAvailable();
        System::UInt32 getUnsettled();
        void close();
        System::String ^ getName();
        Session ^ getSession();
    };
}}}}
