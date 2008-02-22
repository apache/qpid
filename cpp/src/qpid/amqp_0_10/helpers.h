#ifndef QPID_AMQP_0_10_HELPERS_H
#define QPID_AMQP_0_10_HELPERS_H

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
#include <string>

namespace qpid {

namespace amqp_0_10 {

struct ClassAttributes { uint8_t code; const char* name };

struct MemberAttributes {
    ClassAttributes class_;
    const char* name
    uint8_t code;

    std::string fullName() const {
        return std::string(class_.name)+"."+name;
    }
};

struct StructAttributes : public MemberAttributes { uint8_t size, pack; };

static const ClassAttributes getClass(uint8_t code);
static const MemberAttributes getCommand(uint8_t classCode, uint8_t code);
static const MemberAttributes getControl(uint8_t classCode, uint8_t code);

struct Command : public Member {
    class Visitor;
    virtual const MemberAttributes& attributes() const = 0;
    virtual void accept(Visitor&) const = 0;
};

struct Control : public Member {
    class Visitor;
    struct Attributes { uint8_t classCode, code };

    virtual const MemberAttributes& attributes() const = 0;
    virtual void accept(Visitor&) const = 0;
};


struct Struct : public Member {
    virtual const StructAttributes&  attributes() const = 0;
};


}} // namespace qpid::amqp_0_10

#endif  /*!QPID_AMQP_0_10_HELPERS_H*/
