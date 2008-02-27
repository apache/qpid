#ifndef QPID_AMQP_0_10_HELPERS_H
#define QPID_AMQP_0_10_HELPERS_H

/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
n * "License"); you may not use this file except in compliance
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

/** Static information about an AMQP class */
struct ClassInfo { uint8_t code; const char* name; };

/** Info about a class memeber - command, control or struct */
struct MemberInfo {
    ClassInfo* class_;          // 0 for top level struct.

    const char* name;
    uint8_t code;
    std::string fullName() const {
        return std::string(class_->name)+"."+name;
    }
};

/** Info about a struct */
struct StructInfo : public MemberInfo { uint8_t size, pack; };

// Look up info by code.
const ClassInfo&  getClassInfo(uint8_t code);
const MemberInfo& getCommandInfo(uint8_t classCode, uint8_t code);
const MemberInfo& getControlInfo(uint8_t classCode, uint8_t code);
const StructInfo& getStructInfo(uint8_t classCode, uint8_t code);

struct Command {
    virtual ~Command();
    class Visitor;
    virtual const MemberInfo& info() const = 0;
    virtual void accept(Visitor&) const = 0;
};

struct Control {
    virtual ~Control();
    class Visitor;
    virtual const MemberInfo& info() const = 0;
    virtual void accept(Visitor&) const = 0;
};

struct Struct {
    virtual ~Struct();
    virtual const StructInfo&  info() const = 0;
};

/** Base class for generated enum domains.
 * Enums map to classes for type safety and to provide separate namespaces
 * for clashing values.
 */
struct Enum {
    int value;
    Enum(int v=0) : value(v) {}
    operator int() const { return value; }
    template <class S> void serialize(S &s) { s(value); }
};

}} // namespace qpid::amqp_0_10

#endif  /*!QPID_AMQP_0_10_HELPERS_H*/
