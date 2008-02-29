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
#include "qpid/amqp_0_10/built_in_types.h"
#include <string>

namespace qpid {
namespace amqp_0_10 {

// Look up names by code
const char* getClassName(uint8_t code);
const char* getCommandName(uint8_t classCode, uint8_t code);
const char* getControlName(uint8_t classCode, uint8_t code);
const char* getStructName(uint8_t classCode, uint8_t code);

struct Command {
    static const int SEGMENT_TYPE=COMMAND;
    virtual ~Command();
    struct Visitor;
    virtual void accept(Visitor&) const = 0;
};

struct Control {
    static const int SEGMENT_TYPE=CONTROL;
    virtual ~Control();
    struct Visitor;
    virtual void accept(Visitor&) const = 0;
};

// Struct 
struct Struct {
    virtual ~Struct();
    struct Visitor;
    virtual void accept(Visitor&) const = 0;
};

template <class SizeType, bool Coded, uint8_t Code, uint8_t Pack>
struct SerializableStruct : public Struct {
    static const uint8_t SIZE=sizeof(SizeType);
    static const bool CODED=Coded;
    static const uint8_t CODE=Code;
    static const uint8_t PACK;
    // TODO aconway 2008-02-29: handle common encoding/decoding/size
    // for structs. Support for packing.
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
