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

#include "qpid/amqp_0_10/ApplyCommand.h"
#include "qpid/amqp_0_10/ApplyControl.h"
// FIXME aconway 2008-03-04:  #include "qpid/amqp_0_10/ApplyStruct.h"
#include "qpid/amqp_0_10/apply.h"

namespace qpid {
namespace amqp_0_10 {
// Functors for getting static values from a visitable base type.

#define QPID_STATIC_VALUE_GETTER(NAME, TYPE, VALUE) \
    struct NAME : public ConstApplyFunctor<TYPE> {  \
        template <class T> TYPE operator()(const T&) const { return T::VALUE; }\
    }

QPID_STATIC_VALUE_GETTER(GetCode, uint8_t, CODE);
QPID_STATIC_VALUE_GETTER(GetSize, uint8_t, SIZE);
QPID_STATIC_VALUE_GETTER(GetPack, uint8_t, PACK);
QPID_STATIC_VALUE_GETTER(GetClassCode, uint8_t, CLASS_CODE);
QPID_STATIC_VALUE_GETTER(GetName, const char*, NAME);
QPID_STATIC_VALUE_GETTER(GetClassName, const char*, CLASS_NAME);


uint8_t Command::getCode() const { return apply(GetCode(), *this); }
uint8_t Command::getClassCode() const { return apply(GetClassCode(), *this); }
const char* Command::getName() const { return apply(GetName(), *this); }
const char* Command::getClassName() const { return apply(GetClassName(), *this); }

uint8_t Control::getCode() const { return apply(GetCode(), *this); }
uint8_t Control::getClassCode() const { return apply(GetClassCode(), *this); }
const char* Control::getName() const { return apply(GetName(), *this); }
const char* Control::getClassName() const { return apply(GetClassName(), *this); }

// FIXME aconway 2008-03-04: Struct visitors
// uint8_t Struct::getCode() const { return apply(GetCode(), *this); }
// uint8_t Struct::getPack() const { return apply(GetPack(), *this); }
// uint8_t Struct::getSize() const { return apply(GetSize(), *this); }
// uint8_t Struct::getClassCode() const { return apply(GetClassCode(), *this); }
uint8_t Struct::getCode() const { assert(0); return 0; }
uint8_t Struct::getPack() const { assert(0); return 0; }
uint8_t Struct::getSize() const { assert(0); return 0; }
uint8_t Struct::getClassCode() const { assert(0); return 0; }

}} // namespace qpid::amqp_0_10

