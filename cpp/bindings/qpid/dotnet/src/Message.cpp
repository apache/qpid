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
#include <typeinfo.h>
#include <string>
#include <limits>
#include <iostream>

#include "qpid/messaging/Message.h"
#include "qpid/types/Variant.h"

#include "QpidMarshal.h"
#include "Address.h"
#include "Duration.h"
#include "Message.h"
#include "QpidTypeCheck.h"
#include "QpidException.h"
#include "TypeTranslator.h"

namespace Org {
namespace Apache {
namespace Qpid {
namespace Messaging {

    /// <summary>
    /// Message is a managed wrapper for a ::qpid::messaging::Message
    /// </summary>

    // Create empty message
    Message::Message() :
        messagep(new ::qpid::messaging::Message(QpidMarshal::ToNative("")))
    {
    }

    // Create from string
    Message::Message(System::String ^ theStr) :
        messagep(new ::qpid::messaging::Message(QpidMarshal::ToNative(theStr)))
    {
    }

    // Create from object
    Message::Message(System::Object ^ theValue) :
        messagep(new ::qpid::messaging::Message(QpidMarshal::ToNative("")))
    {
        if (QpidTypeCheck::ObjectIsMap(theValue))
        {
            // Create a mapped message using given dictionary

            // Allocate a map
            ::qpid::types::Variant::Map newMap;

            // Add the map variables to the map
            TypeTranslator::ManagedToNative((QpidMap ^)theValue, newMap);

            // Set message content type
            messagep->setContentType("ampq/map");

            // Insert the map into the message
            ::qpid::messaging::encode(newMap, *messagep, QpidMarshal::ToNative("amqp/map"));
        }
        else if (QpidTypeCheck::ObjectIsList(theValue))
        {
            // Create a list message using given list

            // Allocate a list
            ::qpid::types::Variant::List newList;

            // Add the list variables to the list
            TypeTranslator::ManagedToNative((QpidList ^)theValue, newList);

            // Set message content type
            messagep->setContentType("ampq/list");

            // Insert the list into the message
            ::qpid::messaging::encode(newList, *messagep, QpidMarshal::ToNative("amqp/list"));
        }
        else
        {
            // Create a binary string message
            messagep->setContent(QpidMarshal::ToNative(theValue->ToString()));
        }
    }

    // Create from received message
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
    // TODO: prevent copy
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


    //
    // ReplyTo
    //
    void Message::SetReplyTo(Address ^ address)
    {
        messagep->setReplyTo(*(address->NativeAddress));
    }

    Address ^ Message::GetReplyTo()
    {
        const ::qpid::messaging::Address & addrp =
            messagep->::qpid::messaging::Message::getReplyTo();

        return gcnew Address(const_cast<::qpid::messaging::Address *>(&addrp));
    }


    //
    // Subject
    //
    void Message::SetSubject(System::String ^ subject)
    {
        messagep->setSubject(QpidMarshal::ToNative(subject));
    }
    
    System::String ^ Message::GetSubject()
    {
        return gcnew String(messagep->getSubject().c_str());
    }
    

    //
    // ContentType
    //
    void Message::SetContentType(System::String ^ ct)
    {
        messagep->setContentType(QpidMarshal::ToNative(ct));
    }
    
	System::String ^ Message::GetContentType()
    {
		return gcnew String(messagep->::qpid::messaging::Message::getContentType().c_str());
    }
    
    
    //
    // MessageId
    //
    void Message::SetMessageId(System::String ^ messageId)
    {
        messagep->setMessageId(QpidMarshal::ToNative(messageId));
    }
    
    System::String ^ Message::GetMessageId()
    {
        return gcnew String(messagep->getMessageId().c_str());
    }
    
    
    //
    // UserId
    //
    void Message::SetUserId(System::String ^ uId)
    {
        messagep->setUserId(QpidMarshal::ToNative(uId));
    }
    
    System::String ^ Message::GetUserId()
    {
        return gcnew String(messagep->getUserId().c_str());
    }
    
    
    //
    // CorrelationId
    //
    void Message::SetCorrelationId(System::String ^ correlationId)
    {
        messagep->setCorrelationId(QpidMarshal::ToNative(correlationId));
    }
    
    System::String ^ Message::GetCorrelationId()
    {
        return gcnew String(messagep->getCorrelationId().c_str());
    }
    

    //
    // Priority
    //
    void Message::SetPriority(unsigned char priority)
    {
        messagep->setPriority(priority);
    }
    
    unsigned char Message::GetPriority()
    {
        return messagep->getPriority();
    }
    

    //
    // Ttl
    //
    void Message::SetTtl(Duration ^ ttl)
    {
        ::qpid::messaging::Duration dur(ttl->Milliseconds);

        messagep->setTtl(dur);
    }
    
    Duration ^ Message::GetTtl()
    {
        Duration ^ dur = gcnew Duration(messagep->getTtl().getMilliseconds());

        return dur;
    }

    void Message::SetDurable(bool durable)
    {
        messagep->setDurable(durable);
    }
    
    bool Message::GetDurable()
    {
        return messagep->getDurable();
    }


    bool Message::GetRedelivered()
    {
        return messagep->getRedelivered();
    }

    void Message::SetRedelivered(bool redelivered)
    {
        messagep->setRedelivered(redelivered);
    }


    System::Collections::Generic::Dictionary<
            System::String^, System::Object^> ^ Message::GetProperties()
    {
        ::qpid::types::Variant::Map map;

        map = messagep->getProperties();

        System::Collections::Generic::Dictionary<
            System::String^, System::Object^> ^ dict =
            gcnew System::Collections::Generic::Dictionary<
                      System::String^, System::Object^> ;

        TypeTranslator::NativeToManaged(map, dict);

        return dict;
    }


    void Message::SetContent(System::String ^ content)
    {
        messagep->setContent(QpidMarshal::ToNative(content));
    }


    System::String ^ Message::GetContent()
    {
        return gcnew String(messagep->getContent().c_str());
    }


    //
    // User wants to extract a Dictionary from the message
    //
    void Message::GetContent(System::Collections::Generic::Dictionary<
                                System::String^, 
                                System::Object^> ^ dict)
    {
        // Extract the message map from the message
        ::qpid::types::Variant::Map map;
        
        ::qpid::messaging::decode(*messagep, map, QpidMarshal::ToNative("amqp/map"));

        TypeTranslator::NativeToManaged(map, dict);
    }


    //
    // User wants to extract a list from the message
    //
    void Message::GetContent(System::Collections::ObjectModel::Collection<
                        System::Object^> ^ list)
    {
        // allocate a native messaging::List
        ::qpid::types::Variant::List nativeList;
        
        // Extract the list from the message in native format
        ::qpid::messaging::decode(*messagep, nativeList, QpidMarshal::ToNative("amqp/list"));

        // translate native list into user's managed list
        TypeTranslator::NativeToManaged(nativeList, list);
    }

    //
    // User wants content as bytes.
    // result array must be correct size already
    //
    void Message::GetRaw(array<System::Byte> ^ arr)
    {
        System::UInt32 size = messagep->getContentSize();
     
        if (0 == size)
            throw gcnew QpidException("Message::GetRaw - message size is zero");

        if (arr->Length != size)
            throw gcnew QpidException("Message::GetRaw - receive buffer is too small");

        const char * ptr = messagep->getContentPtr();

        // TODO: System::Runtime::InteropServices::Marshal::Copy(ptr, arr, 0, size);

        for (UInt32 i = 0; i < size; i++)
        {
            arr[i] = ptr[i];
        }
    }


    System::UInt64 Message::GetContentSize()
    {
        return messagep->getContentSize();
    }
}}}}
