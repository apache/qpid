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

namespace org {
namespace apache {
namespace qpid {
namespace messaging {

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

    public:
        // Create empty message
        Message();

        // Create from String
        Message(System::String ^ string);

        // Create from object
        Message(System::Object ^ obj);

        // TODO: Create from bytes
        // Message(System::Byte [] ^ bytes);

        // Create from received message
        Message(::qpid::messaging::Message * msgp);

        ~Message();
        !Message();

        // Copy constructor
        Message(const Message % rhs);

        // The kept object in the Messaging C++ DLL
        ::qpid::messaging::Message * messagep;

        void setReplyTo(Address ^ address);
        Address ^ getReplyTo();

        void setSubject(System::String ^ subject);
        System::String ^ getSubject();

        void setContentType(System::String ^ ct);
        System::String ^ getContentType();
        
        void setMessageId(System::String ^ mId);
        System::String ^ getMessageId();
        
        void setUserId(System::String ^ uId);
        System::String ^ getUserId();
        
        void setCorrelationId(System::String ^ cId);
        System::String ^ getCorrelationId();

        void setPriority(unsigned char priority);
        unsigned char getPriority();

        void setTtl(Duration ^ ttl);
        Duration ^ getTtl();

        void setDurable(bool durable);
        bool getDurable();

        bool getRedelivered();
        void setRedelivered(bool redelivered);

        System::Collections::Generic::Dictionary<
            System::String^, System::Object^> ^ getProperties();

        void setContent(System::String ^ content);

        //TODO:: void setContent(Bytes{} bytes, offset, length);

        // get content as string
        System::String ^ getContent();

        // get content as dictionary
        void getContent(System::Collections::Generic::Dictionary<
                            System::String^, 
                            System::Object^> ^ dict);

        // get content as map
        void getContent(System::Collections::Generic::List<
                            System::Object^> ^);

        // get content as bytes
        void getRaw(cli::array<System::Byte> ^ arr);

        System::UInt64 getContentSize();

        //TODO: EncodingException

        // Note: encode/decode functions are in TypeTranslator
    };
}}}}
