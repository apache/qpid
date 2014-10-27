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
#include "Descriptor.h"

namespace qpid {
namespace amqp {
Descriptor::Descriptor(uint64_t code) : type(NUMERIC) { value.code = code; }
Descriptor::Descriptor(const CharSequence& symbol) : type(SYMBOLIC) { value.symbol = symbol; }
bool Descriptor::match(const std::string& symbol, uint64_t code) const
{
    switch (type) {
      case SYMBOLIC:
        return symbol.compare(0, symbol.size(), value.symbol.data, value.symbol.size) == 0;
      case NUMERIC:
        return code == value.code;
    }
    return false;
}

size_t Descriptor::getSize() const
{
    size_t size = 1/*descriptor indicator*/ + 1/*type code*/;
    switch (type) {
        case Descriptor::NUMERIC:
          if (value.code > 0)  size += value.code < 256 ? 1/*encode as byte*/ : 8/*encode as long*/;
          //else value will be indicated through ULONG_ZERO typecode
        break;
        case Descriptor::SYMBOLIC:
          size += value.symbol.size < 256? 1/*size field is a byte*/ : 4/*size field is an int*/;
          size += value.symbol.size;
          break;
    }
    return size;
}

Descriptor* Descriptor::nest(const Descriptor& d)
{
    nested = boost::shared_ptr<Descriptor>(new Descriptor(0));
    *nested = d;
    return nested.get();
}

std::ostream& operator<<(std::ostream& os, const Descriptor& d)
{
    switch (d.type) {
      case Descriptor::SYMBOLIC:
        if (d.value.symbol.data && d.value.symbol.size) os << std::string(d.value.symbol.data, d.value.symbol.size);
        else os << "null";
        break;
      case Descriptor::NUMERIC:
        os << "0x" << std::hex << d.value.code;
        break;
    }
    if (d.nested.get()) {
        os << " ->(" << *d.nested << ")";
    }
    return os;
}
}} // namespace qpid::amqp
