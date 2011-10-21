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

namespace Org {
namespace Apache {
namespace Qpid {
namespace Messaging {

    /// <summary>
    /// Mreceiver is a managed wrapper for a ::qpid::messaging::Receiver
    /// </summary>

    ref class Session;
    ref class Message;
    ref class Duration;

    public ref class Receiver
    {
    private:
        // The session that created this Receiver
        Session ^ parentSession;

        // The kept object in the Messaging C++ DLL
        ::qpid::messaging::Receiver * receiverp;

    public:

        // unmanaged clone
        Receiver(const ::qpid::messaging::Receiver & r,
            Session ^ sessRef);

        // copy constructor
        Receiver(const Receiver ^ receiver);

        // unmanaged clone
        // undefined

        ~Receiver();
        !Receiver();

        // assignment operator
        Receiver % operator=(const Receiver % rhs)
        {
            if (this == %rhs)
            {
                // Self assignment, do nothing
            }
            else
            {
                if (NULL != receiverp)
                    delete receiverp;
                receiverp = new ::qpid::messaging::Receiver(
                    *(const_cast<Receiver %>(rhs).NativeReceiver));
                parentSession = rhs.parentSession;
            }
            return *this;
        }

        property ::qpid::messaging::Receiver * NativeReceiver
        {
            ::qpid::messaging::Receiver * get () { return receiverp; }
        }

        // Get(message)
        bool Get(Message ^% mmsgp);
        bool Get(Message ^% mmsgp, Duration ^ durationp);

        // message = Get()
        Message ^ Get();
        Message ^ Get(Duration ^ durationp);

        // Fetch(message)
        bool Fetch(Message ^% mmsgp);
        bool Fetch(Message ^% mmsgp, Duration ^ duration);

        // message = Fetch()
        Message ^ Fetch();
        Message ^ Fetch(Duration ^ durationp);

        //
        // Capacity
        //
        property System::UInt32 Capacity
        {
            void set (System::UInt32 capacity)
            {
                receiverp->setCapacity(capacity);
            }

            System::UInt32 get ()
            {
                return receiverp->getCapacity();
            }
        }

        //
        // Available
        //
        property System::UInt32 Available
        {
            System::UInt32 get ()
            {
                return receiverp->getAvailable();
            }
        }

        //
        // Unsettled
        //
        property System::UInt32 Unsettled
        {
            System::UInt32 get ()
            {
                return receiverp->getUnsettled();
            }
        }

        void Close();
        
        //
        // IsClosed
        //
        property System::Boolean IsClosed
        {
            System::Boolean get ()
            {
                return receiverp->isClosed();
            }
        }

        //
        // Name
        //
        property System::String ^ Name
        {
            System::String ^ get ()
            {
                return gcnew System::String(receiverp->getName().c_str());
            }
        }

        //
        // Session
        //
        property Org::Apache::Qpid::Messaging::Session ^ Session
        {
            Org::Apache::Qpid::Messaging::Session ^ get ()
            {
                return parentSession;
            }
        }
    };
}}}}
