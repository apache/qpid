#ifndef QPID_AMQP_0_10_COMPLEX_TYPES_H
#define QPID_AMQP_0_10_COMPLEX_TYPES_H

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

namespace qpid {
namespace amqp_0_10 {

// Base classes for complex types.

template <class V, class CV, class H> struct Visitable {
    typedef V  Visitor;
    typedef CV ConstVisitor;
    typedef H  Holder;

    virtual ~Visitable() {}
    virtual void accept(Visitor&) = 0;
    virtual void accept(ConstVisitor&) const = 0;
};

struct CommandVisitor;
struct ConstCommandVisitor;
struct CommandHolder;
struct Command
    : public Visitable<CommandVisitor, ConstCommandVisitor, CommandHolder>
{
    uint8_t getCode() const;
    uint8_t getClassCode() const;
    const char* getName() const;
    const char* getClassName() const;
};

struct ControlVisitor;
struct ConstControlVisitor;
struct ControlHolder;
struct Control
    : public Visitable<ControlVisitor, ConstControlVisitor, ControlHolder>
{
    uint8_t getCode() const;
    uint8_t getClassCode() const;
    const char* getName() const;
    const char* getClassName() const;
};

struct StructVisitor;
struct ConstStructVisitor;
struct StructHolder;
struct Struct
    : public Visitable<StructVisitor, ConstStructVisitor, StructHolder>
{
    uint8_t getCode() const;
    uint8_t getPack() const;
    uint8_t getSize() const;
    uint8_t getClassCode() const;
};


}} // namespace qpid::amqp_0_10

#endif  /*!QPID_AMQP_0_10_COMPLEX_TYPES_H*/
