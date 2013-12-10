#ifndef QPID_MESSAGING_ADDRESSIMPL_H
#define QPID_MESSAGING_ADDRESSIMPL_H

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
#include "qpid/types/Variant.h"
namespace qpid {
namespace messaging {

class AddressImpl
{
  public:
    std::string name;
    std::string subject;
    qpid::types::Variant::Map options;
    bool temporary;

    AddressImpl() : temporary(false) {}
    AddressImpl(const std::string& n, const std::string& s, const qpid::types::Variant::Map& o) :
        name(n), subject(s), options(o), temporary(false) {}
    static void setTemporary(Address& a, bool value) { a.impl->temporary = value; }
    static bool isTemporary(const Address& a) { return a.impl->temporary; }
};
}} // namespace qpid::messaging

#endif  /*!QPID_MESSAGING_ADDRESSIMPL_H*/
