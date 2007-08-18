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
#include "qpid/framing/AMQMethodBody.h"
#include "qpid/framing/Buffer.h"

// Note: MethodHolder::construct is and operator= are code-generated
// in file MethodHolder_construct.cpp.

using namespace boost;

namespace qpid {
namespace framing {

AMQMethodBody* MethodHolder::get() {
    return static_cast<AMQMethodBody*>(blob.get());
}

const AMQMethodBody* MethodHolder::get() const {
    return const_cast<MethodHolder*>(this)->get();
}

void MethodHolder::encode(Buffer& b) const {
    const AMQMethodBody* body = get();
    b.putShort(body->amqpClassId());
    b.putShort(body->amqpMethodId());
    body->encode(b);
}

void MethodHolder::decode(Buffer& b) {
    ClassId c=b.getShort();
    MethodId m=b.getShort();
    construct(c,m);
    get()->decode(b);
}

uint32_t  MethodHolder::size() const {
    return sizeof(ClassId)+sizeof(MethodId)+get()->size();
}

std::ostream& operator<<(std::ostream& out, const MethodHolder& h) {
    h.get()->print(out);
    return out;
}

}} // namespace qpid::framing
