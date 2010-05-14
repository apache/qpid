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

typedef System::Collections::Generic::Dictionary<
            System::String^, 
            System::Object^> 
                VMap;

typedef System::Collections::Generic::List<
            System::Object^> 
                VList;

    /// <summary>
    /// Message is a managed wrapper for a ::qpid::messaging::Message
    /// </summary>

    public ref class Message
    {

    private:
        // Kept object deletion code
        void Cleanup();

        bool objIsMap (System::Object ^ op)
        { 
            return (*op).GetType() == pVMapType;
        }

        bool objIsList(System::Object ^ op)
        { 
            return (*op).GetType() == pVListType;
        }

        // The given object is a Dictionary.
        // Add its elements to the qpid map.
        void Encode(::qpid::types::Variant::Map & theMapp,
                    VMap ^ theObjp);

        // The given object is a List.
        // Add its elements to the qpid list.
        void Encode(::qpid::types::Variant::List & theListp,
                    VList ^ theObjp);

        // Returns a variant representing simple native type object.
        // Not to be called for Map/List objects.
        void EncodeObject(System::Object ^ theObjp,
                          ::qpid::types::Variant & targetp);


        void Decode(VMap ^ dict, ::qpid::types::Variant::Map & map);

        void Decode(VList ^ vList, ::qpid::types::Variant::List & qpidList);


        // map and list for type comparison
        VMap  aVMap;
        VList aVList;
        System::Type ^ pVMapType;
        System::Type ^ pVListType;

    public:
        // Create from String
        Message(System::String ^ bytes);

        // Create from object
        Message(System::Object ^ obj);

        // Create reference copy
        Message(::qpid::messaging::Message * msgp);

        ~Message();
        !Message();

        // Copy constructor
        Message(const Message % rhs);

        // The kept object in the Messaging C++ DLL
        ::qpid::messaging::Message * messagep;

        //void setReplyTo(System::String ^ address);
        //System::String ^ getReplyTo();

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

        //void setTtl(Duration ttl);
        //Duration getTtl();

        void setDurable(bool durable);
        bool getDurable();

        bool getRedelivered();
        void setRedelivered(bool redelivered);

        //System::String ^ getProperties();

        void setContent(System::String ^ content);

        System::String ^ getContent();

        void getContent(System::Collections::Generic::Dictionary<
                            System::String^, 
                            System::Object^> ^ dict);

        void getContent(System::Collections::Generic::List<
                            System::Object^> ^);
    };
}}}}
