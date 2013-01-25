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

#ifndef QPID_LEGACYSTORE_BINDINGDBT_H
#define QPID_LEGACYSTORE_BINDINGDBT_H

#include "db-inc.h"
#include "qpid/broker/PersistableExchange.h"
#include "qpid/broker/PersistableQueue.h"
#include "qpid/framing/Buffer.h"
#include "qpid/framing/FieldTable.h"

namespace mrg{
namespace msgstore{

class BindingDbt : public Dbt
{
    char* data;
    qpid::framing::Buffer buffer;

    static uint32_t encodedSize(const qpid::broker::PersistableExchange& e,
                                const qpid::broker::PersistableQueue& q,
                                const std::string& k,
                                const qpid::framing::FieldTable& a);

public:
    BindingDbt(const qpid::broker::PersistableExchange& e,
               const qpid::broker::PersistableQueue& q,
               const std::string& k,
               const qpid::framing::FieldTable& a);

    virtual ~BindingDbt();

};

}}

#endif // ifndef QPID_LEGACYSTORE_BINDINGDBT_H
