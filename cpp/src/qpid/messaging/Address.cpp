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
#include "qpid/messaging/Address.h"

namespace qpid {
namespace messaging {

Address::Address() {}
Address::Address(const std::string& address) : value(address) {}
Address::Address(const std::string& address, const std::string& t) : value(address), type(t) {}
Address::operator const std::string&() const { return value; }
const std::string& Address::toStr() const { return value; }
Address::operator bool() const { return !value.empty(); }
bool Address::operator !() const { return value.empty(); }

const std::string TYPE_SEPARATOR(":");

std::ostream& operator<<(std::ostream& out, const Address& address)
{
    if (!address.type.empty()) {
        out << address.type;
        out << TYPE_SEPARATOR;
    }
    out << address.value;
    return out;
}

}} // namespace qpid::messaging
