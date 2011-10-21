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

#include "qpid/messaging/Message.h"

#include "QpidMarshal.h"
#include "Address.h"
#include "Duration.h"
#include "TypeTranslator.h"

namespace Org {
namespace Apache {
namespace Qpid {
namespace Messaging {

    ref class Address;
    ref class Duration;

    /// <summary>
    /// Message is a managed wrapper for a ::qpid::messaging::Message
    /// </summary>

    public ref class Message
    {

    private:
        // The kept object in the Messaging C++ DLL
        ::qpid::messaging::Message * messagep;

    public:
        // Create empty message
        Message();

        // Create from String
        Message(System::String ^ theStr);

        // Create from object
        Message(System::Object ^ theValue);

        // Create from byte array
		Message(array<System::Byte> ^ bytes);

        // Create from byte array slice
		Message(array<System::Byte> ^ bytes, int offset, int size);

        ~Message();
        !Message();

        // Copy constructor
        Message(const Message ^ message);
        Message(const Message % message);

	    // unmanaged clone
        Message(const ::qpid::messaging::Message & msgp);

        // assignment operator
        Message % operator=(const Message % rhs)
        {
            if (this == %rhs)
            {
                // Self assignment, do nothing
            }
            else
            {
                if (NULL != messagep)
                    delete messagep;
                messagep = new ::qpid::messaging::Message(
                    *(const_cast<Message %>(rhs).NativeMessage) );
            }
            return *this;
        }

        //
        // NativeMessage
        //
        property ::qpid::messaging::Message * NativeMessage
        {
            ::qpid::messaging::Message * get () { return messagep; }
        }

        //
        // ReplyTo
        //
        property Address ^ ReplyTo 
        {
            void set (Address ^ address)
            {
                 messagep->setReplyTo(*(address->NativeAddress));
            }

            Address ^ get () 
            {
                const ::qpid::messaging::Address & addrp =
                    messagep->::qpid::messaging::Message::getReplyTo();

                return gcnew Address(addrp);
            }
        }

        //
        // Subject
        //
        property System::String ^ Subject
        {
            void set (System::String ^ subject)
            {
                messagep->setSubject(QpidMarshal::ToNative(subject));
            }
            
            
            System::String ^ get ()
            {
                return gcnew String(messagep->getSubject().c_str());
            }
        }


        //
        // ContentType
        //
        property System::String ^ ContentType
        {
            void set (System::String ^ ct)
            {
                messagep->setContentType(QpidMarshal::ToNative(ct));
            }
            
	        System::String ^ get ()
            {
		        return gcnew String(messagep->::qpid::messaging::Message::getContentType().c_str());
            }
        }
    

        //
        // MessageId
        //
        property System::String ^ MessageId
        {
            void set (System::String ^ messageId)
            {
                messagep->setMessageId(QpidMarshal::ToNative(messageId));
            }

            System::String ^ get ()
            {
                return gcnew String(messagep->getMessageId().c_str());
            }
        }

        
        //
        // UserId
        //
        property System::String ^ UserId
        {
            void set (System::String ^ uId)
            {
                messagep->setUserId(QpidMarshal::ToNative(uId));
            }
            
            System::String ^ get ()
            {
                return gcnew String(messagep->getUserId().c_str());
            }
        }

            
        //
        // CorrelationId
        //
        property System::String ^ CorrelationId
        {
            void set (System::String ^ correlationId)
            {
                messagep->setCorrelationId(QpidMarshal::ToNative(correlationId));
            }
            
            System::String ^ get ()
            {
                return gcnew String(messagep->getCorrelationId().c_str());
            }
        }


        //
        // Priority
        //
        property unsigned char Priority
        {
            void set (unsigned char priority)
            {
                messagep->setPriority(priority);
            }
            
            unsigned char get ()
            {
                return messagep->getPriority();
            }
        }   


        //
        // Ttl
        //
        property Duration ^ Ttl
        {
            void set (Duration ^ ttl)
            {
                ::qpid::messaging::Duration dur(ttl->Milliseconds);

                messagep->setTtl(dur);
            }
            
            Duration ^ get ()
            {
                Duration ^ dur = gcnew Duration(messagep->getTtl().getMilliseconds());

                return dur;
            }
        }

        //
        // Durable
        //
        property bool Durable
        {
            void set (bool durable)
            {
                messagep->setDurable(durable);
            }
            
            bool get ()
            {
                return messagep->getDurable();
            }
        }

        //
        // Redelivered
        //
        property bool Redelivered
        {
            bool get ()
            {
                return messagep->getRedelivered();
            }

            void set (bool redelivered)
            {
                messagep->setRedelivered(redelivered);
            }
        }

        //
        // Property
        //
        void Message::SetProperty(System::String ^ name, System::Object ^ value);

        //
        // Properties
        //
        property System::Collections::Generic::Dictionary<
                    System::String^, System::Object^> ^ Properties
        {
            System::Collections::Generic::Dictionary<
                    System::String^, System::Object^> ^ get ()
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


	        void set (System::Collections::Generic::Dictionary<
                    System::String^, System::Object^> ^ properties)
	        {
		        for each (System::Collections::Generic::KeyValuePair
			             <System::String^, System::Object^> kvp in properties)
                {
			        SetProperty(kvp.Key, kvp.Value);
		        }
	        }
        }


        void SetContent(System::String ^ content);

        void SetContent(cli::array<System::Byte> ^ bytes);

        void SetContent(cli::array<System::Byte> ^ bytes, int offset, int size);

        //TODO:: void setContent(Bytes{} bytes, offset, length);

        // get content as string
        System::String ^ GetContent();

        // get content as dictionary
        void GetContent(System::Collections::Generic::Dictionary<
                            System::String^, 
                            System::Object^> ^ dict);

        // get content as map
        void GetContent(System::Collections::ObjectModel::Collection<
                            System::Object^> ^);

        // get content as bytes
        void GetContent(cli::array<System::Byte> ^ arr);

        //
        // ContentSize
        //
        property System::UInt64 ContentSize
        {
            System::UInt64 get ()
            {
                return messagep->getContentSize();
            }
        }


		// A message has been returned to managed code through GetContent().
		// Display the content of that System::Object as a string.
		System::String ^ AsString(System::Object ^ obj);

		System::String ^ MapAsString(System::Collections::Generic::Dictionary<
						System::String^, System::Object^> ^ dict);
		
		System::String ^ ListAsString(System::Collections::ObjectModel::Collection<
			            System::Object^> ^ list);

        //TODO: EncodingException

        // Note: encode/decode functions are in TypeTranslator
    };
}}}}
