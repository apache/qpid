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
#include "qpid/amqp/MessageId.h"
#include <assert.h>
#include <boost/lexical_cast.hpp>

namespace qpid {
namespace amqp {

MessageId::MessageId() : type(NONE)
{
}
void MessageId::assign(std::string& s) const
{
    switch (type) {
      case NONE:
        s = std::string();
        break;
      case BYTES:
        if (value.bytes) s.assign(value.bytes.data, value.bytes.size);
        break;
      case UUID:
        s = qpid::types::Uuid(value.bytes).str();
        break;
      case ULONG:
        s = boost::lexical_cast<std::string>(value.ulong);
        break;
    }
}

MessageId::operator bool() const
{
    return type!=NONE;
}

std::string MessageId::str() const
{
    std::string s;
    assign(s);
    return s;
}

void MessageId::set(qpid::amqp::CharSequence bytes, qpid::types::VariantType t)
{
    switch (t) {
      case qpid::types::VAR_STRING:
        type = BYTES;
        break;
      case qpid::types::VAR_UUID:
        type = UUID;
        assert(bytes.size == 16);
        break;
      default:
        assert(false);
    }
    value.bytes = bytes;
}
void MessageId::set(uint64_t ulong)
{
    type = ULONG;
    value.ulong = ulong;
}

}} // namespace qpid::amqp
