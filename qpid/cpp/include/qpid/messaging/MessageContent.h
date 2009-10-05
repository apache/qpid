#ifndef QPID_MESSAGING_MESSAGECONTENT_H
#define QPID_MESSAGING_MESSAGECONTENT_H

/*
 *
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
 *
 */

#include "qpid/messaging/Variant.h"
#include <string>
#include "qpid/client/ClientImportExport.h"

namespace qpid {
namespace messaging {

/**
 *
 */
class MessageContent
{
  public:
    QPID_CLIENT_EXTERN virtual ~MessageContent() {}

    virtual const std::string& asString() const = 0;
    virtual std::string& asString() = 0;

    virtual const char* asChars() const = 0;
    virtual size_t size() const = 0;

    virtual const Variant::Map& asMap() const = 0;
    virtual Variant::Map& asMap() = 0;
    virtual bool isMap() const = 0;

    virtual const Variant::List& asList() const = 0;
    virtual Variant::List& asList() = 0;
    virtual bool isList() const = 0;

    virtual void clear() = 0;

    virtual Variant& operator[](const std::string&) = 0;


    virtual std::ostream& print(std::ostream& out) const = 0;


    //operator<< for variety of types... (is this a good idea?)
    virtual MessageContent& operator<<(const std::string&) = 0;
    virtual MessageContent& operator<<(const char*) = 0;
    virtual MessageContent& operator<<(bool) = 0;
    virtual MessageContent& operator<<(int8_t) = 0;
    virtual MessageContent& operator<<(int16_t) = 0;
    virtual MessageContent& operator<<(int32_t) = 0;
    virtual MessageContent& operator<<(int64_t) = 0;
    virtual MessageContent& operator<<(uint8_t) = 0;
    virtual MessageContent& operator<<(uint16_t) = 0;
    virtual MessageContent& operator<<(uint32_t) = 0;
    virtual MessageContent& operator<<(uint64_t) = 0;
    virtual MessageContent& operator<<(double) = 0;
    virtual MessageContent& operator<<(float) = 0;

    //assignment from string, map and list
    virtual MessageContent& operator=(const std::string&) = 0;
    virtual MessageContent& operator=(const char*) = 0;
    virtual MessageContent& operator=(const Variant::Map&) = 0;
    virtual MessageContent& operator=(const Variant::List&) = 0;
    
  private:
};

QPID_CLIENT_EXTERN std::ostream& operator<<(std::ostream& out, const MessageContent& content);

}} // namespace qpid::messaging

#endif  /*!QPID_MESSAGING_MESSAGECONTENT_H*/
