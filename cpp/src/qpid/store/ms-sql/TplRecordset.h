#ifndef QPID_STORE_MSSQL_TPLRECORDSET_H
#define QPID_STORE_MSSQL_TPLRECORDSET_H

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

#include "Recordset.h"
#include <string>
#include <set>

namespace qpid {
namespace store {
namespace ms_sql {

/**
 * @class TplRecordset
 *
 * Class for the TPL (Transaction Prepared List) records.
 */
class TplRecordset : public Recordset {
protected:

public:
    virtual void open(DatabaseConnection* conn, const std::string& table);

    void add(const std::string& xid);

    // Remove a record given its xid.
    void remove(const std::string& xid);

    // Recover prepared transaction XIDs.
    void recover(std::set<std::string>& xids);

    // Dump table contents; useful for debugging.
    void dump();
};

}}}  // namespace qpid::store::ms_sql

#endif /* QPID_STORE_MSSQL_TPLRECORDSET_H */
