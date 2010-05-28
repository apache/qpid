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
#include "qpid/messaging/exceptions.h"

#include "Receiver.h"
#include "Session.h"
#include "Message.h"
#include "Duration.h"
#include "QpidException.h"

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

    //
    // get(message)
    //
    bool Receiver::get(Message ^ mmsgp)
    {
        return get(mmsgp, DurationConstants::FORVER);
    }

    bool Receiver::get(Message ^ mmsgp, Duration ^ durationp)
    {
        ::qpid::messaging::Duration dur((*durationp).Milliseconds);

        return receiverp->Receiver::get(*(mmsgp->messagep), dur);
    }

    //
    // message = get()
    //
    Message ^ Receiver::get()
    {
        return get(DurationConstants::FORVER);
    }


    Message ^ Receiver::get(Duration ^ durationp)
    {
        System::Exception          ^ newException = nullptr;
        ::qpid::messaging::Message * msgp         = NULL;
        Message                    ^ newMessage   = nullptr;

        try
        {
            // allocate a message
            msgp = new ::qpid::messaging::Message;

            // translate the duration
            ::qpid::messaging::Duration dur((*durationp).Milliseconds);

            // get the message
            *msgp = receiverp->::qpid::messaging::Receiver::get(dur);

            // create new managed message with received message embedded in it
            newMessage = gcnew Message(msgp);
        } 
        catch (const ::qpid::types::Exception & error) 
        {
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        }
        catch (const std::exception & error) 
        {
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        } 
        catch ( ... )
        {
            newException = gcnew QpidException("Receiver:get unknown error");
        }
        finally
        {
            // Clean up and throw on caught exceptions
            if (newException != nullptr)
            {
                if (msgp != NULL)
                {
                    delete msgp;
                }

                throw newException;
            }
        }

        return newMessage;
    }

    //
    // fetch(message)
    //
    bool Receiver::fetch(Message ^ mmsgp)
    {
        return fetch(mmsgp, DurationConstants::FORVER);
    }

    bool Receiver::fetch(Message ^ mmsgp, Duration ^ durationp)
    {
        ::qpid::messaging::Duration dur((*durationp).Milliseconds);

        return receiverp->::qpid::messaging::Receiver::fetch(*((*mmsgp).messagep), dur);
    }
    

    //
    // message = fetch()
    //

    Message ^ Receiver::fetch()
    {
        return fetch(DurationConstants::FORVER);
    }

    Message ^ Receiver::fetch(Duration ^ durationp)
    {
        System::Exception          ^ newException = nullptr;
        ::qpid::messaging::Message * msgp         = NULL;
         Message                   ^ newMessage   = nullptr;

        try
        {
            // allocate a message
            ::qpid::messaging::Message * msgp = new ::qpid::messaging::Message;

            // translate the duration
            ::qpid::messaging::Duration dur((*durationp).Milliseconds);

            // get the message
            *msgp = receiverp->::qpid::messaging::Receiver::fetch(dur);

            // create new managed message with received message embedded in it
            newMessage = gcnew Message(msgp);
        } 
        catch (const ::qpid::types::Exception & error) 
        {
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        }
        catch (const std::exception & error) 
        {
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        } 
        catch ( ... )
        {
            newException = gcnew QpidException("Receiver:fetch unknown error");

        }
        finally
        {
            // Clean up and throw on caught exceptions
            if (newException != nullptr)
            {
                if (msgp != NULL)
                {
                    delete msgp;
                }

                throw newException;
            }
        }

        return newMessage;
    }

    void Receiver::setCapacity(System::UInt32 capacity)
    {
        receiverp->setCapacity(capacity);
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
