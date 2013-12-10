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

#include "qpid/linearstore/BufferValue.h"

namespace qpid {
namespace linearstore {



BufferValue::BufferValue(uint32_t size, uint64_t offset)
    : data(new char[size]),
      buffer(data, size)
{
    set_data(data);
    set_size(size);
    set_flags(DB_DBT_USERMEM | DB_DBT_PARTIAL);
    set_doff(offset);
    set_dlen(size);
    set_ulen(size);
}

BufferValue::BufferValue(const qpid::broker::Persistable& p)
  : data(new char[p.encodedSize()]),
    buffer(data, p.encodedSize())
{
    p.encode(buffer);

    set_data(data);
    set_size(p.encodedSize());
}

BufferValue::~BufferValue()
{
    delete [] data;
}

}}
