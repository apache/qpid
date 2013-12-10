#ifndef QPID_STORE_MSSQL_MESSAGEMAPRECORDSET_H
#define QPID_STORE_MSSQL_MESSAGEMAPRECORDSET_H

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
 * @class MessageMapRecordset
 *
 * Class for the message map (message -> queue) records.
 */
class MessageMapRecordset : public Recordset {

    // These values are defined in a constraint on the tblMessageMap table.
    // the prepareStatus column can only be null, 1, or 2.
    enum { PREPARE_ADD=1, PREPARE_REMOVE=2 };

    class MessageMap : public CADORecordBinding {
        BEGIN_ADO_BINDING(MessageMap)
          ADO_FIXED_LENGTH_ENTRY2(1, adBigInt, messageId, FALSE)
          ADO_FIXED_LENGTH_ENTRY2(2, adBigInt, queueId, FALSE)
          ADO_FIXED_LENGTH_ENTRY2(3, adTinyInt, prepareStatus, FALSE)
          ADO_VARIABLE_LENGTH_ENTRY(4, adVarBinary, xid, sizeof(xid),
                                    xidStatus, xidLength, FALSE)
        END_ADO_BINDING()

    public:
        uint64_t messageId;
        uint64_t queueId;
        uint8_t  prepareStatus;
        char     xid[512];
        int      xidStatus;
        uint32_t xidLength;
    };

    void selectOnXid(const std::string& xid);

public:
    virtual void open(DatabaseConnection* conn, const std::string& table);

    // Add a new mapping
    void add(uint64_t messageId,
             uint64_t queueId,
             const std::string& xid = "");

    // Remove a specific mapping.
    void remove(uint64_t messageId, uint64_t queueId);

    // Mark the indicated message->queue entry pending removal. The entry
    // for the mapping is updated to indicate pending removal with the
    // specified xid.
    void pendingRemove(uint64_t messageId,
                       uint64_t queueId,
                       const std::string& xid);

    // Remove mappings for all messages on a specified queue.
    void removeForQueue(uint64_t queueId);

    // Commit records recorded as prepared.
    void commitPrepared(const std::string& xid);

    // Abort prepared changes.
    void abortPrepared(const std::string& xid);

    // Recover the mappings of message ID -> vector<queue ID>.
    void recover(MessageQueueMap& msgMap);

    // Dump table contents; useful for debugging.
    void dump();
};

}}}  // namespace qpid::store::ms_sql

#endif /* QPID_STORE_MSSQL_MESSAGEMAPRECORDSET_H */
