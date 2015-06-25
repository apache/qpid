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

#include <string>
#include <qpid/Exception.h>
#include <qpid/log/Statement.h>

#include "TplRecordset.h"
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
TplRecordset::open(DatabaseConnection* conn, const std::string& table)
{
    init(conn, table);
    // Don't actually open until we know what to do. It's far easier and more
    // efficient to simply do most of these TPL/xid ops in a single statement.
}

void
TplRecordset::add(const std::string& xid)
{
    const std::string command =
        "INSERT INTO " + tableName + " ( xid ) VALUES ( ? )";
    _CommandPtr cmd = NULL;
    _ParameterPtr xidVal = NULL;

    TESTHR(cmd.CreateInstance(__uuidof(Command)));
    TESTHR(xidVal.CreateInstance(__uuidof(Parameter)));
    _ConnectionPtr p = *dbConn;
    cmd->ActiveConnection = p;
    cmd->CommandText = command.c_str();
    cmd->CommandType = adCmdText;
    xidVal->Name = "@xid";
    xidVal->Type = adVarBinary;
    xidVal->Size = xid.length();
    xidVal->Direction = adParamInput;
    xidVal->Value = BlobEncoder(xid);
    cmd->Parameters->Append(xidVal);
    cmd->Execute(NULL, NULL, adCmdText | adExecuteNoRecords);
}

void
TplRecordset::remove(const std::string& xid)
{
    // Look up the item by its xid
    const std::string command =
        "DELETE FROM " + tableName + " WHERE xid = ?";
    _CommandPtr cmd = NULL;
    _ParameterPtr xidVal = NULL;

    TESTHR(cmd.CreateInstance(__uuidof(Command)));
    TESTHR(xidVal.CreateInstance(__uuidof(Parameter)));
    _ConnectionPtr p = *dbConn;
    cmd->ActiveConnection = p;
    cmd->CommandText = command.c_str();
    cmd->CommandType = adCmdText;
    xidVal->Name = "@xid";
    xidVal->Type = adVarBinary;
    xidVal->Size = xid.length();
    xidVal->Direction = adParamInput;
    xidVal->Value = BlobEncoder(xid);
    cmd->Parameters->Append(xidVal);
    _variant_t deletedRecords;
    cmd->Execute(&deletedRecords, NULL, adCmdText | adExecuteNoRecords);
}

void
TplRecordset::recover(std::set<std::string>& xids)
{
    openRs();
    if (rs->BOF && rs->EndOfFile)
        return;   // Nothing to do
    rs->MoveFirst();
    while (!rs->EndOfFile) {
        _variant_t wxid = rs->Fields->Item["xid"]->Value;
        char *xidBytes;
        SafeArrayAccessData(wxid.parray, (void **)&xidBytes);
        std::string xid(xidBytes, rs->Fields->Item["xid"]->ActualSize);
        xids.insert(xid);
        SafeArrayUnaccessData(wxid.parray);
        rs->MoveNext();
    }
}

void
TplRecordset::dump()
{
    Recordset::dump();
    if (rs->EndOfFile && rs->BOF)    // No records
        return;

    rs->MoveFirst();
    while (!rs->EndOfFile) {
        _bstr_t wxid = rs->Fields->Item["xid"]->Value;
        QPID_LOG(notice, "  -> " << (const char *)wxid);
        rs->MoveNext();
    }
}

}}}  // namespace qpid::store::ms_sql
