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
#include <string>
#include <limits>

#include "qpid/messaging/Sender.h"
#include "qpid/messaging/Session.h"
#include "qpid/messaging/Message.h"

#include "Sender.h"
#include "Session.h"
#include "Message.h"

namespace org {
namespace apache {
namespace qpid {
namespace messaging {

    /// <summary>
    /// Sender a managed wrapper for a ::qpid::messaging::Sender 
    /// </summary>

    Sender::Sender(::qpid::messaging::Sender * s,
                     Session ^ sessRef) :
        senderp(s),
        parentSession(sessRef)
    {
    }


    // Destructor
    Sender::~Sender()
    {
        Cleanup();
    }


    // Finalizer
    Sender::!Sender()
    {
        Cleanup();
    }

    // Copy constructor
    Sender::Sender(const Sender % rhs)
    {
        senderp       = rhs.senderp;
        parentSession = rhs.parentSession;
    }

    // Destroys kept object
    // TODO: add lock
    void Sender::Cleanup()
    {
        if (NULL != senderp)
        {
            delete senderp;
            senderp = NULL;
        }
    }

    void Sender::send(Message ^ mmsgp)
    {
        senderp->::qpid::messaging::Sender::send(*((*mmsgp).messagep));
    }

    void Sender::send(Message ^ mmsgp, bool sync)
    {
        senderp->::qpid::messaging::Sender::send(*((*mmsgp).messagep), sync);
    }

    void Sender::setCapacity(System::UInt32 capacity)
    {
        senderp->setCapacity(capacity);
    }

    System::UInt32 Sender::getCapacity()
    {
        return senderp->getCapacity();
    }

    System::UInt32 Sender::getUnsettled()
    {
        return senderp->getUnsettled();
    }

    System::UInt32 Sender::getAvailable()
    {
        return senderp->getAvailable();
    }

    System::String ^ Sender::getName()
    {
        return gcnew System::String(senderp->getName().c_str());
    }

    Session ^ Sender::getSession()
    {
        return parentSession;
    }
}}}}
