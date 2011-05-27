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

#include "SqlTransaction.h"
#include "DatabaseConnection.h"

namespace qpid {
namespace store {
namespace ms_sql {

SqlTransaction::SqlTransaction(const boost::shared_ptr<DatabaseConnection>& _db)
  : db(_db), transDepth(0)
{
}

SqlTransaction::~SqlTransaction()
{
    if (transDepth > 0)
        this->abort();
}

void
SqlTransaction::begin()
{
    _bstr_t beginCmd("BEGIN TRANSACTION");
    _ConnectionPtr c = *db;
    c->Execute(beginCmd, NULL, adExecuteNoRecords);
    ++transDepth;
}

void
SqlTransaction::commit()
{
    if (transDepth > 0) {
        _bstr_t commitCmd("COMMIT TRANSACTION");
        _ConnectionPtr c = *db;
        c->Execute(commitCmd, NULL, adExecuteNoRecords);
        --transDepth;
    }
}

void
SqlTransaction::abort()
{
    if (transDepth > 0) {
        _bstr_t rollbackCmd("ROLLBACK TRANSACTION");
        _ConnectionPtr c = *db;
        c->Execute(rollbackCmd, NULL, adExecuteNoRecords);
        transDepth = 0;
    }
}

}}}  // namespace qpid::store::ms_sql
