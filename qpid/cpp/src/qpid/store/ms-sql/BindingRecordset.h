#ifndef QPID_STORE_MSSQL_BINDINGRECORDSET_H
#define QPID_STORE_MSSQL_BINDINGRECORDSET_H

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

#include <icrsint.h>
#include "Recordset.h"
#include <qpid/broker/RecoveryManager.h>

namespace qpid {
namespace store {
namespace ms_sql {

/**
 * @class BindingRecordset
 *
 * Class for the binding records.
 */
class BindingRecordset : public Recordset {

    class Binding : public CADORecordBinding {
        BEGIN_ADO_BINDING(Binding)
          ADO_FIXED_LENGTH_ENTRY2(1, adBigInt, exchangeId, FALSE)
          ADO_VARIABLE_LENGTH_ENTRY4(2, adVarChar, queueName, 
                                     sizeof(queueName), FALSE)
          ADO_VARIABLE_LENGTH_ENTRY4(3, adVarChar, routingKey, 
                                     sizeof(routingKey), FALSE)
        END_ADO_BINDING()

    public:
        uint64_t exchangeId;
        char queueName[256];
        char routingKey[256];
    };

public:
    // Add a new binding
    void add(uint64_t exchangeId,
             const std::string& queueName,
             const std::string& routingKey,
             const qpid::framing::FieldTable& args);

    // Remove a specific binding
    void remove(uint64_t exchangeId,
                const std::string& queueName,
                const std::string& routingKey,
                const qpid::framing::FieldTable& args);

    // Remove all bindings for the specified exchange
    void remove(uint64_t exchangeId);

    // Remove all bindings for the specified queue
    void remove(const std::string& queueName);

    // Recover bindings set using exchMap to get from Id to RecoverableExchange.
    void recover(qpid::broker::RecoveryManager& recoverer,
                 std::map<uint64_t, broker::RecoverableExchange::shared_ptr> exchMap);

    // Dump table contents; useful for debugging.
    void dump();
};

}}}  // namespace qpid::store::ms_sql

#endif /* QPID_STORE_MSSQL_BINDINGRECORDSET_H */
