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

#ifndef QPID_LINEARSTORE_BUFFERVALUE_H
#define QPID_LINEARSTORE_BUFFERVALUE_H

#include "db-inc.h"
#include "qpid/broker/Persistable.h"
#include "qpid/framing/Buffer.h"

namespace qpid{
namespace linearstore{

class BufferValue : public Dbt
{
    char* data;

public:
    qpid::framing::Buffer buffer;

    BufferValue(uint32_t size, uint64_t offset);
    BufferValue(const qpid::broker::Persistable& p);
    virtual ~BufferValue();
};

}}

#endif // ifndef QPID_LINEARSTORE_BUFFERVALUE_H
