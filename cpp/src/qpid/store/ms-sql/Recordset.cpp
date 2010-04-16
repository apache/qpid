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

#include <qpid/Exception.h>
#include <qpid/log/Statement.h>

#include "Recordset.h"
#include "BlobEncoder.h"
#include "DatabaseConnection.h"
#include "VariantHelper.h"

namespace {
inline void TESTHR(HRESULT x) {if FAILED(x) _com_issue_error(x);};
}

namespace qpid {
namespace store {
namespace ms_sql {


void
Recordset::init(DatabaseConnection* conn, const std::string& table)
{
    dbConn = conn;
    TESTHR(rs.CreateInstance(__uuidof(::Recordset)));
    tableName = table;
}

void
Recordset::openRs()
{
    // Client-side cursors needed to get access to newly added
    // identity column immediately. Recordsets need this to get the
    // persistence ID for the broker objects.
    rs->CursorLocation = adUseClient;
    _ConnectionPtr p = *dbConn;
    rs->Open(tableName.c_str(),
             _variant_t((IDispatch *)p, true), 
             adOpenStatic,
             adLockOptimistic,
             adCmdTable);
}

void
Recordset::open(DatabaseConnection* conn, const std::string& table)
{
    init(conn, table);
    openRs();
}

void
Recordset::close()
{
    if (rs && rs->State == adStateOpen)
        rs->Close();
}

void
Recordset::requery()
{
    // Restore the recordset to reflect all current records.
    rs->Filter = "";
    rs->Requery(-1);
}

void
Recordset::dump()
{
    long count = rs->RecordCount;
    QPID_LOG(notice, "DB Dump: " + tableName <<
                     ": " << count << " records");
}

}}}  // namespace qpid::store::ms_sql
