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
        // Kept object deletion code
        void Cleanup();

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

        // Create from received message
        Message(::qpid::messaging::Message * msgp);

        ~Message();
        !Message();

        // Copy constructor
        Message(const Message % rhs);

        property ::qpid::messaging::Message * NativeMessage
        {
            ::qpid::messaging::Message * get () { return messagep; }
        }

        void SetReplyTo(Address ^ address);
        Address ^ GetReplyTo();

        void SetSubject(System::String ^ subject);
        System::String ^ GetSubject();

        void SetContentType(System::String ^ ct);
        System::String ^ GetContentType();
        
        void SetMessageId(System::String ^ messageId);
        System::String ^ GetMessageId();
        
        void SetUserId(System::String ^ uId);
        System::String ^ GetUserId();
        
        void SetCorrelationId(System::String ^ correlationId);
        System::String ^ GetCorrelationId();

        void SetPriority(unsigned char priority);
        unsigned char GetPriority();

        void SetTtl(Duration ^ ttl);
        Duration ^ GetTtl();

        void SetDurable(bool durable);
        bool GetDurable();

        bool GetRedelivered();
        void SetRedelivered(bool redelivered);

        System::Collections::Generic::Dictionary<
            System::String^, System::Object^> ^ GetProperties();

		void SetProperty(System::String ^ name, System::Object ^ value);

		void SetProperties(System::Collections::Generic::Dictionary<
            System::String^, System::Object^> ^ properties);

        void SetContent(System::String ^ content);

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
        void GetRaw(cli::array<System::Byte> ^ arr);

        System::UInt64 GetContentSize();

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
