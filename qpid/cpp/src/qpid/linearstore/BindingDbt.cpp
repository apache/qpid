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

#include "qpid/linearstore/BindingDbt.h"

namespace qpid {
namespace linearstore {

BindingDbt::BindingDbt(const qpid::broker::PersistableExchange& e, const qpid::broker::PersistableQueue& q, const std::string& k, const qpid::framing::FieldTable& a)
  : data(new char[encodedSize(e, q, k, a)]),
    buffer(data, encodedSize(e, q, k, a))
{
    buffer.putLongLong(q.getPersistenceId());
    buffer.putShortString(q.getName());
    buffer.putShortString(k);
    buffer.put(a);

    set_data(data);
    set_size(encodedSize(e, q, k, a));
}

BindingDbt::~BindingDbt()
{
  delete [] data;
}

uint32_t BindingDbt::encodedSize(const qpid::broker::PersistableExchange& /*not used*/, const qpid::broker::PersistableQueue& q, const std::string& k, const qpid::framing::FieldTable& a)
{
    return 8 /*queue id*/ + q.getName().size() + 1 + k.size() + 1 + a.encodedSize();
}

}}
