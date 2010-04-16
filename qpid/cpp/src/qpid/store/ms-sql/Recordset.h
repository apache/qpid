#ifndef QPID_STORE_MSSQL_RECORDSET_H
#define QPID_STORE_MSSQL_RECORDSET_H

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


// Bring in ADO 2.8 (yes, I know it says "15", but that's it...)
#import "C:\Program Files\Common Files\System\ado\msado15.dll" \
        no_namespace rename("EOF", "EndOfFile")
#include <comdef.h>
#include <comutil.h>
#include <string>
#if 0
#include <utility>
#endif

namespace qpid {
namespace store {
namespace ms_sql {

class DatabaseConnection;

/**
 * @class Recordset
 *
 * Represents an ADO Recordset, abstracting out the common operations needed
 * on the common tables used that have 2 fields, persistence ID and blob.
 */
class Recordset {
protected:
    _RecordsetPtr rs;
    DatabaseConnection* dbConn;
    std::string tableName;

    void init(DatabaseConnection* conn, const std::string& table);
    void openRs();

public:
    Recordset() : rs(0), dbConn(0) {}
    virtual ~Recordset() { close(); rs = 0; dbConn = 0; }

    /**
     * Default open() reads all records into the recordset.
     */
    virtual void open(DatabaseConnection* conn, const std::string& table);
    void close();
    void requery();
    operator _RecordsetPtr () { return rs; }

    // Dump table contents; useful for debugging.
    void dump();
};

}}}  // namespace qpid::store::ms_sql

#endif /* QPID_STORE_MSSQL_RECORDSET_H */
