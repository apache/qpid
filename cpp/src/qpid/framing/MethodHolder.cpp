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

#include "MethodHolder.h"
#include "amqp_types.h"
#include "qpid/framing/Buffer.h"
#include "qpid/framing/variant.h"

using namespace boost;

namespace qpid {
namespace framing {

struct SetVariantVisitor : public NoBlankVisitor<> {
    QPID_USING_NOBLANK();
    MethodId id;
    SetVariantVisitor(MethodId m) : id(m) {}
    template <class T> void operator()(T& t) const { setVariant(t, id); }
};

inline void setVariant(MethodVariant& var, ClassId c, MethodId m) {
    setVariant(var,c);
    boost::apply_visitor(SetVariantVisitor(m), var);
}

void MethodHolder::setMethod(ClassId c, MethodId m) {
    setVariant(method, c, m);
}

MethodHolder::MethodHolder(ClassId c, MethodId m) {
    setMethod(c,m);
}

struct GetClassId : public NoBlankVisitor<ClassId> {
    QPID_USING_NOBLANK(ClassId);
    template <class T> ClassId operator()(const T&) const {
        return T::CLASS_ID;
    }
};

struct GetMethodId : public NoBlankVisitor<MethodId> {
    QPID_USING_NOBLANK(ClassId);
    template <class T> MethodId operator()(const T&) const {
        return T::METHOD_ID;
    }
};

void MethodHolder::encode(Buffer& b) const {
    const AMQMethodBody* body = getMethod();
    b.putShort(body->amqpClassId());
    b.putShort(body->amqpMethodId());
    body->encodeContent(b);
}

void MethodHolder::decode(Buffer& b) {
    ClassId classId = b.getShort();
    ClassId methodId = b.getShort();
    setVariant(method, classId, methodId);
    getMethod()->decodeContent(b);
}

uint32_t  MethodHolder::size() const {
    return sizeof(ClassId)+sizeof(MethodId)+getMethod()->size();
}


AMQMethodBody* MethodHolder::getMethod() {
    return applyApplyVisitor(AddressVisitor<AMQMethodBody*>(), method);
}

const AMQMethodBody* MethodHolder::getMethod() const {
    return const_cast<MethodHolder*>(this)->getMethod();
}

}} // namespace qpid::framing
