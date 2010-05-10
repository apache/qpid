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

#include "qpid/messaging/Receiver.h"
#include "qpid/messaging/Session.h"
#include "qpid/messaging/Message.h"

#include "Receiver.h"
#include "Session.h"
#include "Message.h"
#include "Duration.h"

namespace org {
namespace apache {
namespace qpid {
namespace messaging {

    /// <summary>
    /// Receiver is a managed wrapper for a ::qpid::messaging::Receiver
    /// </summary>

    Receiver::Receiver(::qpid::messaging::Receiver * r,
                       Session ^ sessRef) :
        receiverp(r),
        parentSession(sessRef)
    {
    }


    // Destructor
    Receiver::~Receiver()
    {
        Cleanup();
    }


    // Finalizer
    Receiver::!Receiver()
    {
        Cleanup();
    }


    // Copy constructor
    Receiver::Receiver(const Receiver ^ rhs)
    {
        receiverp     = rhs->receiverp;
        parentSession = rhs->parentSession;
    }


    // Destroys kept object
    // TODO: add lock
    void Receiver::Cleanup()
    {
        if (NULL != receiverp)
        {
            delete receiverp;
            receiverp = NULL;
        }
    }

    bool Receiver::get(Message ^ mmsgp)
    {
        return receiverp->Receiver::get(*((*mmsgp).messagep));
    }

    bool Receiver::get(Message ^ mmsgp, Duration ^ durationp)
    {
        return receiverp->Receiver::get(*((*mmsgp).messagep),
                                        *((*durationp).durationp));
    }

    Message ^ Receiver::get(Duration ^ durationp)
    {
        // allocate a message
        ::qpid::messaging::Message * msgp = new ::qpid::messaging::Message;

        // get the message
        *msgp = receiverp->::qpid::messaging::Receiver::get(*((*durationp).durationp));

        // create new managed message with received message embedded in it
        Message ^ newMessage = gcnew Message(msgp);

        return newMessage;
    }

    bool Receiver::fetch(Message ^ mmsgp)
    {
        return receiverp->::qpid::messaging::Receiver::fetch(*((*mmsgp).messagep));
    }

    bool Receiver::fetch(Message ^ mmsgp, Duration ^ durationp)
    {
        return receiverp->::qpid::messaging::Receiver::fetch(*((*mmsgp).messagep),
                                          *((*durationp).durationp));
    }
    
    Message ^ Receiver::fetch(Duration ^ durationp)
    {
        // allocate a message
        ::qpid::messaging::Message * msgp = new ::qpid::messaging::Message;

        // get the message
        *msgp = receiverp->::qpid::messaging::Receiver::fetch(*((*durationp).durationp));

        // create new managed message with received message embedded in it
        Message ^ newMessage = gcnew Message(msgp);

        return newMessage;
    }

    System::UInt32 Receiver::getCapacity()
    {
        return receiverp->getCapacity();
    }

    System::UInt32 Receiver::getAvailable()
    {
        return receiverp->getAvailable();
    }

    System::UInt32 Receiver::getUnsettled()
    {
        return receiverp->getUnsettled();
    }

    void Receiver::close()
    {
        receiverp->close();
    }

    System::String ^ Receiver::getName()
    {
        return gcnew System::String(receiverp->getName().c_str());
    }

    Session ^ Receiver::getSession()
    {
        return parentSession;
    }
}}}}
