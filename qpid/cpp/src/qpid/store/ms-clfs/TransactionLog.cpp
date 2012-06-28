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

#include <windows.h>
#include <clfsw32.h>
#include <exception>
#include <malloc.h>
#include <memory.h>
#include <qpid/framing/Buffer.h>
#include <qpid/log/Statement.h>
#include <qpid/sys/IntegerTypes.h>
#include <qpid/sys/windows/check.h>

#include "TransactionLog.h"
#include "Transaction.h"
#include "Lsn.h"

namespace qpid {
namespace store {
namespace ms_clfs {

namespace {

// Structures that hold log records. Each has a type field at the start.
enum TransactionEntryType {
    TransactionStartDtxEntry         = 1,
    TransactionStartTxEntry          = 2,
    TransactionPrepareEntry          = 3,
    TransactionCommitEntry           = 4,
    TransactionAbortEntry            = 5,
    TransactionDeleteEntry           = 6
};
// The only thing that really takes up space in transaction records is the
// xid. Max xid length is in the neighborhood of 600 bytes. Leave some room.
static const uint32_t MaxTransactionContentLength = 1024;

// Dtx-Start
struct TransactionStartDtx {
    TransactionEntryType type;
    uint32_t length;
    char content[MaxTransactionContentLength];

    TransactionStartDtx()
        : type(TransactionStartDtxEntry), length(0) {}
};
// Tx-Start
struct TransactionStartTx {
    TransactionEntryType type;

    TransactionStartTx()
        : type(TransactionStartTxEntry) {}
};
// Prepare
struct TransactionPrepare {
    TransactionEntryType type;

    TransactionPrepare()
        : type(TransactionPrepareEntry) {}
};
// Commit
struct TransactionCommit {
    TransactionEntryType type;

    TransactionCommit()
        : type(TransactionCommitEntry) {}
};
// Abort
struct TransactionAbort {
    TransactionEntryType type;

    TransactionAbort()
        : type(TransactionAbortEntry) {}
};
// Delete
struct TransactionDelete {
    TransactionEntryType type;

    TransactionDelete()
        : type(TransactionDeleteEntry) {}
};

}   // namespace

void
TransactionLog::initialize()
{
    // Write something to occupy the first record, preventing a real
    // transaction from being lsn/id 0. Delete of a non-existant id is easily
    // tossed during recovery if no other transactions have caused the tail
    // to be moved up past this dummy record by then.
    deleteTransaction(0);
}

uint32_t
TransactionLog::marshallingBufferSize()
{
    size_t biggestNeed = sizeof(TransactionStartDtx);
    uint32_t defSize = static_cast<uint32_t>(biggestNeed);
    uint32_t minSize = Log::marshallingBufferSize();
    if (defSize <= minSize)
        return minSize;
    // Round up to multiple of minSize
    return (defSize + minSize) / minSize * minSize;
}

// Get a new Transaction
boost::shared_ptr<Transaction>
TransactionLog::begin()
{
    TransactionStartTx entry;
    CLFS_LSN location;
    uint64_t id;
    uint32_t entryLength = static_cast<uint32_t>(sizeof(entry));
    location = write(&entry, entryLength);
    try {
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(idsLock);
        id = lsnToId(location);
        std::auto_ptr<Transaction> t(new Transaction(id, shared_from_this()));
        boost::shared_ptr<Transaction> t2(t);
        boost::weak_ptr<Transaction> weak_t2(t2);
        {
            qpid::sys::ScopedLock<qpid::sys::Mutex> l(idsLock);
            validIds[id] = weak_t2;
        }
        return t2;
    }
    catch(...) {
        deleteTransaction(id);
        throw;
    }
}

// Get a new TPCTransaction
boost::shared_ptr<TPCTransaction>
TransactionLog::begin(const std::string& xid)
{
    TransactionStartDtx entry;
    CLFS_LSN location;
    uint64_t id;
    uint32_t entryLength = static_cast<uint32_t>(sizeof(entry));
    entry.length = static_cast<uint32_t>(xid.length());
    memcpy_s(entry.content, sizeof(entry.content),
             xid.c_str(), xid.length());
    entryLength -= (sizeof(entry.content) - entry.length);
    location = write(&entry, entryLength);
    try {
        id = lsnToId(location);
        std::auto_ptr<TPCTransaction> t(new TPCTransaction(id,
                                                           shared_from_this(),
                                                           xid));
        boost::shared_ptr<TPCTransaction> t2(t);
        boost::weak_ptr<Transaction> weak_t2(t2);
        {
            qpid::sys::ScopedLock<qpid::sys::Mutex> l(idsLock);
            validIds[id] = weak_t2;
        }
        return t2;
    }
    catch(...) {
        deleteTransaction(id);
        throw;
    }
}

void
TransactionLog::recordPrepare(uint64_t transId)
{
    TransactionPrepare entry;
    CLFS_LSN transLsn = idToLsn(transId);
    write(&entry, sizeof(entry), &transLsn);
}

void
TransactionLog::recordCommit(uint64_t transId)
{
    TransactionCommit entry;
    CLFS_LSN transLsn = idToLsn(transId);
    write(&entry, sizeof(entry), &transLsn);
    {
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(idsLock);
        validIds[transId].reset();
    }
}

void
TransactionLog::recordAbort(uint64_t transId)
{
    TransactionAbort entry;
    CLFS_LSN transLsn = idToLsn(transId);
    write(&entry, sizeof(entry), &transLsn);
    {
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(idsLock);
        validIds[transId].reset();
    }
}

void
TransactionLog::deleteTransaction(uint64_t transId)
{
    uint64_t newFirstId = 0;
    {
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(idsLock);
        validIds.erase(transId);
        // May have deleted the first entry; if so the log can release that.
        // If this deletion results in an empty list of transactions,
        // move the tail up to this transaction's LSN. This may result in
        // one or more transactions being stranded in the log until there's
        // more activity. If a restart happens while these unneeded log
        // records are there, the presence of the TransactionDelete
        // entry will cause them to be ignored anyway.
        if (validIds.empty())
            newFirstId = transId;
        else if (validIds.begin()->first > transId)
            newFirstId = validIds.begin()->first;
    }
    TransactionDelete deleteEntry;
    CLFS_LSN transLsn = idToLsn(transId);
    write(&deleteEntry, sizeof(deleteEntry), &transLsn);
    if (newFirstId != 0)
        moveTail(idToLsn(newFirstId));
}

void
TransactionLog::collectPreparedXids(std::map<std::string, TPCTransaction::shared_ptr>& preparedMap)
{
    // Go through all the known transactions; if the transaction is still
    // valid (open or prepared) it will have weak_ptr to the Transaction.
    // If it can be downcast and has a state of TRANS_PREPARED, add to the map.
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(idsLock);
    std::map<uint64_t, boost::weak_ptr<Transaction> >::const_iterator i;
    for (i = validIds.begin(); i != validIds.end(); ++i) {
        Transaction::shared_ptr t = i->second.lock();
        if (t.get() == 0)
            continue;
        TPCTransaction::shared_ptr tpct(boost::dynamic_pointer_cast<TPCTransaction>(t));
        if (tpct.get() == 0)
            continue;
        if (tpct->state == Transaction::TRANS_PREPARED)
            preparedMap[tpct->getXid()] = tpct;
    }
}

void
TransactionLog::recover(std::map<uint64_t, Transaction::shared_ptr>& transMap)
{
    QPID_LOG(debug, "Recovering transaction log");

    // Note that there may be transaction refs in the log which are deleted,
    // so be sure to only add transactions at Start records, and ignore those
    // that don't have an existing message record.
    // Get the base LSN - that's how to say "start reading at the beginning"
    CLFS_INFORMATION info;
    ULONG infoLength = sizeof (info);
    BOOL ok = ::GetLogFileInformation(handle, &info, &infoLength);
    QPID_WINDOWS_CHECK_NOT(ok, 0);

    // Pointers for the various record types that can be assigned in the
    // reading loop below.
    TransactionStartDtx *startDtxEntry;
    TransactionStartTx *startTxEntry;

    PVOID recordPointer;
    ULONG recordLength;
    CLFS_RECORD_TYPE recordType = ClfsDataRecord;
    CLFS_LSN transLsn, current, undoNext;
    PVOID readContext;
    uint64_t transId;
    // Note 'current' in case it's needed below; ReadNextLogRecord returns it
    // via a parameter.
    current = info.BaseLsn;
    ok = ::ReadLogRecord(marshal,
                         &info.BaseLsn,
                         ClfsContextForward,
                         &recordPointer,
                         &recordLength,
                         &recordType,
                         &undoNext,
                         &transLsn,
                         &readContext,
                         0);

    std::auto_ptr<Transaction> tPtr;
    std::auto_ptr<TPCTransaction> tpcPtr;
    while (ok) {
        std::string xid;

        // All the record types this class writes have a TransactionEntryType
        // in the beginning. Based on that, do what's needed.
        TransactionEntryType *t =
            reinterpret_cast<TransactionEntryType *>(recordPointer);
        switch(*t) {
        case TransactionStartDtxEntry:
            startDtxEntry =
                reinterpret_cast<TransactionStartDtx *>(recordPointer);
            transId = lsnToId(current);
            QPID_LOG(debug, "Dtx start, id " << transId);
            xid.assign(startDtxEntry->content, startDtxEntry->length);
            tpcPtr.reset(new TPCTransaction(transId, shared_from_this(), xid));
            transMap[transId] = boost::shared_ptr<TPCTransaction>(tpcPtr);
            break;
        case TransactionStartTxEntry:
            startTxEntry =
                reinterpret_cast<TransactionStartTx *>(recordPointer);
            transId = lsnToId(current);
            QPID_LOG(debug, "Tx start, id " << transId);
            tPtr.reset(new Transaction(transId, shared_from_this()));
            transMap[transId] = boost::shared_ptr<Transaction>(tPtr);
            break;
        case TransactionPrepareEntry:
            transId = lsnToId(transLsn);
            QPID_LOG(debug, "Dtx prepare, id " << transId);
            if (transMap.find(transId) == transMap.end()) {
                QPID_LOG(debug,
                         "Dtx " << transId << " doesn't exist; discarded");
            }
            else {
                transMap[transId]->state = Transaction::TRANS_PREPARED;
            }
            break;
        case TransactionCommitEntry:
            transId = lsnToId(transLsn);
            QPID_LOG(debug, "Txn commit, id " << transId);
            if (transMap.find(transId) == transMap.end()) {
                QPID_LOG(debug,
                         "Txn " << transId << " doesn't exist; discarded");
            }
            else {
                transMap[transId]->state = Transaction::TRANS_COMMITTED;
            }
            break;
        case TransactionAbortEntry:
            transId = lsnToId(transLsn);
            QPID_LOG(debug, "Txn abort, id " << transId);
            if (transMap.find(transId) == transMap.end()) {
                QPID_LOG(debug,
                         "Txn " << transId << " doesn't exist; discarded");
            }
            else {
                transMap[transId]->state = Transaction::TRANS_ABORTED;
            }
            break;
        case TransactionDeleteEntry:
            transId = lsnToId(transLsn);
            QPID_LOG(debug, "Txn delete, id " << transId);
            if (transMap.find(transId) == transMap.end()) {
                QPID_LOG(debug,
                         "Txn " << transId << " doesn't exist; discarded");
            }
            else {
                transMap[transId]->state = Transaction::TRANS_DELETED;
                transMap.erase(transId);
            }
            break;
        default:
            throw std::runtime_error("Bad transaction log entry type");
        }

        recordType = ClfsDataRecord;
        ok = ::ReadNextLogRecord(readContext,
                                 &recordPointer,
                                 &recordLength,
                                 &recordType,
                                 0,             // No userLsn
                                 &undoNext,
                                 &transLsn,
                                 &current,
                                 0);
    }
    DWORD status = ::GetLastError();
    ::TerminateReadLog(readContext);
    if (status != ERROR_HANDLE_EOF)  // No more records
        throw QPID_WINDOWS_ERROR(status);

    QPID_LOG(debug, "Transaction log recovered");

    // At this point we have a list of all the not-deleted transactions that
    // were in existence when the broker last ran. All transactions of both
    // Dtx and Tx types that haven't prepared or committed will be aborted.
    // This will give the proper background against which to decide each
    // message's disposition when recovering messages that were involved in
    // transactions.
    // In addition to recovering and aborting transactions, rebuild the
    // validIds map now that we know which ids are really valid.
    std::map<uint64_t, Transaction::shared_ptr>::const_iterator i;
    for (i = transMap.begin(); i != transMap.end(); ++i) {
        switch(i->second->state) {
        case Transaction::TRANS_OPEN:
            QPID_LOG(debug, "Txn " << i->first << " was open; aborted");
            i->second->state = Transaction::TRANS_ABORTED;
            break;
        case Transaction::TRANS_ABORTED:
            QPID_LOG(debug, "Txn " << i->first << " was aborted");
            break;
        case Transaction::TRANS_COMMITTED:
            QPID_LOG(debug, "Txn " << i->first << " was committed");
            break;
        case Transaction::TRANS_PREPARED:
            QPID_LOG(debug, "Txn " << i->first << " was prepared");
            break;
        case Transaction::TRANS_DELETED:
            QPID_LOG(error,
                     "Txn " << i->first << " was deleted; shouldn't be here");
            break;
        }
        boost::weak_ptr<Transaction> weak_txn(i->second);
        validIds[i->first] = weak_txn;
    }
}

}}}  // namespace qpid::store::ms_clfs
