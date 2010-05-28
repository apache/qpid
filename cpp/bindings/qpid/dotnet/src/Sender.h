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

#include "qpid/messaging/Sender.h"
#include "qpid/messaging/Session.h"
#include "qpid/messaging/Message.h"

namespace qpid {
namespace messaging {
    // Dummy class to satisfy linker
    class SenderImpl {};
}}

namespace org {
namespace apache {
namespace qpid {
namespace messaging {

    /// <summary>
    /// Sender is a managed wrapper for a ::qpid::messaging::Sender 
    /// </summary>

    ref class Session;
    ref class Message;

    public ref class Sender
    {
    private:
        // The kept object in the Messaging C++ DLL
        ::qpid::messaging::Sender * senderp;

        // The session that created this Sender
        Session ^ parentSession;

        // Kept object deletion code
        void Cleanup();

    public:
        Sender(::qpid::messaging::Sender * s,
            Session ^ sessRef);
        ~Sender();
        !Sender();
        Sender(const Sender % rhs);

        // send(message)
        void send(Message ^ mmsgp);
        void send(Message ^ mmsgp, bool sync);

        void close();

        property System::UInt32 Capacity
        {
            System::UInt32 get () { return senderp->getCapacity(); }
            void set (System::UInt32 capacity) { senderp->setCapacity(capacity); }
        }

        property System::UInt32 Unsettled
        {
            System::UInt32 get () { return senderp->getUnsettled(); }
        }

        property System::UInt32 Available
        {
            System::UInt32 get () { return senderp->getAvailable(); }
        }

        property System::String ^ Name
        {
            System::String ^ get ()
            {
                return gcnew System::String(senderp->getName().c_str());
            }
        }

        Session ^ getSession();
    };
}}}}
