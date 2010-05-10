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

#include "qpid/messaging/Message.h"

#include "QpidMarshal.h"
#include "Message.h"

namespace org {
namespace apache {
namespace qpid {
namespace messaging {

    /// <summary>
    /// Message is a managed wrapper for a ::qpid::messaging::Message
    /// </summary>

    // This constructor is used to create a message from bytes to put into the message
    Message::Message(System::String ^ bytes) :
        messagep(new ::qpid::messaging::Message(QpidMarshal::ToNative(bytes)))
    {
    }

    // This constructor creates a message from a native received message
    Message::Message(::qpid::messaging::Message * msgp) :
        messagep(msgp)
    {
    }

    // Destructor
    Message::~Message()
    {
        Cleanup();
    }


    // Finalizer
    Message::!Message()
    {
        Cleanup();
    }

    // Copy constructor
    Message::Message(const Message % rhs)
    {
        messagep      = rhs.messagep;
    }

    // Destroys kept object
    // TODO: add lock
    void Message::Cleanup()
    {
        if (NULL != messagep)
        {
            delete messagep;
            messagep = NULL;
        }
    }

    //void Message::setReplyTo(System::String ^ address)
    //{
    //    messagep->setReplyTo(QpidMarshal::ToNative(address));
    //}

    //System::String ^ Message::getReplyTo()
    //{
    //    return gcnew String(messagep->getReplyTo().c_str());
    //}


    void Message::setSubject(System::String ^ subject)
    {
        messagep->setSubject(QpidMarshal::ToNative(subject));
    }
    
    System::String ^ Message::getSubject()
    {
        return gcnew String(messagep->getSubject().c_str());
    }
    

    void Message::setContentType(System::String ^ ct)
    {
        messagep->setContentType(QpidMarshal::ToNative(ct));
    }
    
    System::String ^ Message::getContentType()
    {
        return gcnew String(messagep->getContentType().c_str());
    }
    
    
    void Message::setMessageId(System::String ^ mId)
    {
        messagep->setMessageId(QpidMarshal::ToNative(mId));
    }
    
    System::String ^ Message::getMessageId()
    {
        return gcnew String(messagep->getMessageId().c_str());
    }
    
    
    void Message::setUserId(System::String ^ uId)
    {
        messagep->setUserId(QpidMarshal::ToNative(uId));
    }
    
    System::String ^ Message::getUserId()
    {
        return gcnew String(messagep->getUserId().c_str());
    }
    
    
    void Message::setCorrelationId(System::String ^ cId)
    {
        messagep->setCorrelationId(QpidMarshal::ToNative(cId));
    }
    
    System::String ^ Message::getCorrelationId()
    {
        return gcnew String(messagep->getCorrelationId().c_str());
    }
    

    void Message::setPriority(unsigned char priority)
    {
        messagep->setPriority(priority);
    }
    
    unsigned char Message::getPriority()
    {
        return messagep->getPriority();
    }
    

    //void setTtl(Duration ttl);
    //Duration getTtl();

    void Message::setDurable(bool durable)
    {
        messagep->setDurable(durable);
    }
    
    bool Message::getDurable()
    {
        return messagep->getDurable();
    }


    bool Message::getRedelivered()
    {
        return messagep->getRedelivered();
    }

    void Message::setRedelivered(bool redelivered)
    {
        messagep->setRedelivered(redelivered);
    }


    //System::String ^ Message::getProperties()
    //{
    //    pqid::types::Variant::Map * mapp = new
    //    return gcnew String(messagep->getReplyTo().c_str());
    //}


    void Message::setContent(System::String ^ content)
    {
        messagep->setContent(QpidMarshal::ToNative(content));
    }


    System::String ^ Message::getContent()
    {
        return gcnew String(messagep->getContent().c_str());
    }

}}}}
