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
#include <qpid/store/StorageProvider.h>

#include "MessageMapRecordset.h"
#include "BlobEncoder.h"
#include "DatabaseConnection.h"
#include "Exception.h"
#include "VariantHelper.h"

namespace {
inline void TESTHR(HRESULT x) {if FAILED(x) _com_issue_error(x);};
}

namespace qpid {
namespace store {
namespace ms_sql {

void
MessageMapRecordset::open(DatabaseConnection* conn, const std::string& table)
{
    init(conn, table);
}

void
MessageMapRecordset::add(uint64_t messageId,
                         uint64_t queueId,
                         const std::string& xid)
{
    std::ostringstream command;
    command << "INSERT INTO " << tableName
            << " (messageId, queueId";
    if (!xid.empty())
        command << ", prepareStatus, xid";
    command << ") VALUES (" << messageId << "," << queueId;
    if (!xid.empty())
        command << "," << PREPARE_ADD << ",?";
    command << ")" << std::ends;

    _CommandPtr cmd = NULL;
    _ParameterPtr xidVal = NULL;
    TESTHR(cmd.CreateInstance(__uuidof(Command)));
    _ConnectionPtr p = *dbConn;
    cmd->ActiveConnection = p;
    cmd->CommandText = command.str().c_str();
    cmd->CommandType = adCmdText;
    if (!xid.empty()) {
        TESTHR(xidVal.CreateInstance(__uuidof(Parameter)));
        xidVal->Name = "@xid";
        xidVal->Type = adVarBinary;
        xidVal->Size = xid.length();
        xidVal->Direction = adParamInput;
        xidVal->Value = BlobEncoder(xid);
        cmd->Parameters->Append(xidVal);
    }
    cmd->Execute(NULL, NULL, adCmdText | adExecuteNoRecords);
}

void
MessageMapRecordset::remove(uint64_t messageId, uint64_t queueId)
{
    std::ostringstream command;
    command << "DELETE FROM " << tableName
            << " WHERE queueId = " << queueId
            << " AND messageId = " << messageId << std::ends;
    _CommandPtr cmd = NULL;
    TESTHR(cmd.CreateInstance(__uuidof(Command)));
    _ConnectionPtr p = *dbConn;
    cmd->ActiveConnection = p;
    cmd->CommandText = command.str().c_str();
    cmd->CommandType = adCmdText;
    _variant_t deletedRecords;
    cmd->Execute(&deletedRecords, NULL, adCmdText | adExecuteNoRecords);
    if ((long)deletedRecords == 0)
        throw ms_sql::Exception("Message does not exist in queue mapping");
    // Trigger on deleting the mapping takes care of deleting orphaned
    // message record from tblMessage.
}

void
MessageMapRecordset::pendingRemove(uint64_t messageId,
                                   uint64_t queueId,
                                   const std::string& xid)
{
    // Look up the mapping for the specified message and queue. There
    // should be only one because of the uniqueness constraint in the
    // SQL table. Update it to reflect it's pending delete with
    // the specified xid.
    std::ostringstream command;
    command << "UPDATE " << tableName
            << " SET prepareStatus=" << PREPARE_REMOVE
            << " , xid=?"
            << " WHERE queueId = " << queueId
            << " AND messageId = " << messageId << std::ends;

    _CommandPtr cmd = NULL;
    _ParameterPtr xidVal = NULL;
    TESTHR(cmd.CreateInstance(__uuidof(Command)));
    TESTHR(xidVal.CreateInstance(__uuidof(Parameter)));
    _ConnectionPtr p = *dbConn;
    cmd->ActiveConnection = p;
    cmd->CommandText = command.str().c_str();
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
MessageMapRecordset::removeForQueue(uint64_t queueId)
{
    std::ostringstream command;
    command << "DELETE FROM " << tableName
            << " WHERE queueId = " << queueId << std::ends;
    _CommandPtr cmd = NULL;

    TESTHR(cmd.CreateInstance(__uuidof(Command)));
    _ConnectionPtr p = *dbConn;
    cmd->ActiveConnection = p;
    cmd->CommandText = command.str().c_str();
    cmd->CommandType = adCmdText;
    cmd->Execute(NULL, NULL, adCmdText | adExecuteNoRecords);
}

void
MessageMapRecordset::commitPrepared(const std::string& xid)
{
    // Find all the records for the specified xid. Records marked as adding
    // are now permanent so remove the xid and prepareStatus. Records marked
    // as removing are removed entirely.
    openRs();
    MessageMap m;
    IADORecordBinding *piAdoRecordBinding;
    rs->QueryInterface(__uuidof(IADORecordBinding),
                       (LPVOID *)&piAdoRecordBinding);
    piAdoRecordBinding->BindToRecordset(&m);
    for (; !rs->EndOfFile; rs->MoveNext()) {
        if (m.xidStatus != adFldOK)
            continue;
        const std::string x(m.xid, m.xidLength);
        if (x != xid)
            continue;
        if (m.prepareStatus == PREPARE_REMOVE) {
            rs->Delete(adAffectCurrent);
        }
        else {
            _variant_t dbNull;
            dbNull.ChangeType(VT_NULL);
            rs->Fields->GetItem("prepareStatus")->Value = dbNull;
            rs->Fields->GetItem("xid")->Value = dbNull;
        }
        rs->Update();
    }
    piAdoRecordBinding->Release();
}

void
MessageMapRecordset::abortPrepared(const std::string& xid)
{
    // Find all the records for the specified xid. Records marked as adding
    // need to be removed while records marked as removing are put back to
    // no xid and no prepareStatus.
    openRs();
    MessageMap m;
    IADORecordBinding *piAdoRecordBinding;
    rs->QueryInterface(__uuidof(IADORecordBinding), 
                       (LPVOID *)&piAdoRecordBinding);
    piAdoRecordBinding->BindToRecordset(&m);
    for (; !rs->EndOfFile; rs->MoveNext()) {
        if (m.xidStatus != adFldOK)
            continue;
        const std::string x(m.xid, m.xidLength);
        if (x != xid)
            continue;
        if (m.prepareStatus == PREPARE_ADD) {
            rs->Delete(adAffectCurrent);
        }
        else {
            _variant_t dbNull;
            dbNull.ChangeType(VT_NULL);
            rs->Fields->GetItem("prepareStatus")->Value = dbNull;
            rs->Fields->GetItem("xid")->Value = dbNull;
        }
        rs->Update();
    }
    piAdoRecordBinding->Release();
}

void
MessageMapRecordset::recover(MessageQueueMap& msgMap)
{
    openRs();
    if (rs->BOF && rs->EndOfFile)
        return;   // Nothing to do
    rs->MoveFirst();
    MessageMap b;
    IADORecordBinding *piAdoRecordBinding;
    rs->QueryInterface(__uuidof(IADORecordBinding), 
                       (LPVOID *)&piAdoRecordBinding);
    piAdoRecordBinding->BindToRecordset(&b);
    while (!rs->EndOfFile) {
        qpid::store::QueueEntry entry(b.queueId);
        if (b.xidStatus == adFldOK && b.xidLength > 0) {
            entry.xid.assign(b.xid, b.xidLength);
            entry.tplStatus =
                b.prepareStatus == PREPARE_ADD ? QueueEntry::ADDING
                                               : QueueEntry::REMOVING;
        }
        else {
            entry.tplStatus = QueueEntry::NONE;
        }
        msgMap[b.messageId].push_back(entry);
        rs->MoveNext();
    }

    piAdoRecordBinding->Release();
}

void
MessageMapRecordset::dump()
{
    openRs();
    Recordset::dump();
    if (rs->EndOfFile && rs->BOF)    // No records
        return;
    rs->MoveFirst();

    MessageMap m;
    IADORecordBinding *piAdoRecordBinding;
    rs->QueryInterface(__uuidof(IADORecordBinding), 
                       (LPVOID *)&piAdoRecordBinding);
    piAdoRecordBinding->BindToRecordset(&m);
   
    while (!rs->EndOfFile) {
        QPID_LOG(notice, "msg " << m.messageId << " on queue " << m.queueId);
        rs->MoveNext();
    }

    piAdoRecordBinding->Release();
}

}}}  // namespace qpid::store::ms_sql
