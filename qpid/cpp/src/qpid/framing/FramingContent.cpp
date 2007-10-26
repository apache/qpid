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
#include "Buffer.h"
#include "FramingContent.h"
#include "qpid/Exception.h"
#include "qpid/framing/reply_exceptions.h"

namespace qpid {
namespace framing {

Content::Content() : discriminator(0) {}

Content::Content(uint8_t _discriminator, const string& _value): discriminator(_discriminator), value(_value) {
    validate();
}

void Content::validate() {
    if (discriminator == REFERENCE) {
        if(value.empty()) {
            throw InvalidArgumentException(
                QPID_MSG("Reference cannot be empty"));
        }
    }else if (discriminator != INLINE) {
        throw SyntaxErrorException(
            QPID_MSG("Invalid discriminator: " << discriminator));
    }
}

Content::~Content() {}
  
void Content::encode(Buffer& buffer) const {
    buffer.putOctet(discriminator);
    buffer.putLongString(value);
}

void Content::decode(Buffer& buffer) {
    discriminator = buffer.getOctet();
    buffer.getLongString(value);
    validate();
}

size_t Content::size() const {
    return 1/*discriminator*/ + 4/*for recording size of long string*/ + value.size();
}

std::ostream& operator<<(std::ostream& out, const Content& content) {
    if (content.discriminator == REFERENCE) {
        out << "{REF:" << content.value << "}";
    } else if (content.discriminator == INLINE) {
        out << "{INLINE:" << content.value.size() << " bytes}";
    }
    return out;
}

}} // namespace framing::qpid
