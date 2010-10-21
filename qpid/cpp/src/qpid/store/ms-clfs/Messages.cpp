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

#include <qpid/log/Statement.h>

#include "Messages.h"
#include "Lsn.h"
#include "qpid/store/StoreException.h"

namespace qpid {
namespace store {
namespace ms_clfs {

void
Messages::openLog(const std::string& path, const Log::TuningParameters& params)
{
    log.open (path, params);
}

void
Messages::add(const boost::intrusive_ptr<qpid::broker::PersistableMessage>& msg)
{
    uint64_t id = log.add(msg);
    msg->setPersistenceId(id);
    std::auto_ptr<MessageInfo> autom(new MessageInfo);
    MessageInfo::shared_ptr m(autom);
    std::pair<uint64_t, MessageInfo::shared_ptr> p(id, m);
    {
        qpid::sys::ScopedWlock<qpid::sys::RWlock> l(lock);
        messages.insert(p);
        // If there's only this one message there, move the tail to it.
        // This prevents the log from continually growing when messages
        // are added and removed one at a time.
        if (messages.size() == 1) {
            CLFS_LSN newTail = idToLsn(id);
            log.moveTail(newTail);
        }
    }
}

void
Messages::enqueue(uint64_t msgId, uint64_t queueId, Transaction::shared_ptr& t)
{
    MessageInfo::shared_ptr p;
    {
        qpid::sys::ScopedRlock<qpid::sys::RWlock> l(lock);
        MessageMap::const_iterator i = messages.find(msgId);
        if (i == messages.end())
            THROW_STORE_EXCEPTION("Message does not exist");
        p = i->second;
    }
    // If transacted, it still needs to be counted as enqueued to ensure it
    // is not deleted. Remember the transacted operation so it can be properly
    // resolved later.
    ::InterlockedIncrement(&p->enqueuedCount);
    uint64_t transactionId = 0;
    if (t.get() != 0)
        transactionId = t->getId();
    if (transactionId != 0) {
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(p->transOpsLock);
        p->transOps[t].push_back(MessageInfo::TRANSACTION_ENQUEUE);
        t->enroll(msgId);
    }
    try {
        log.recordEnqueue(msgId, queueId, transactionId);
    }
    catch (...) {
        // Undo the record-keeping if the log wasn't written correctly.
        ::InterlockedDecrement(&p->enqueuedCount);
        if (transactionId != 0) {
            t->unenroll(msgId);
            qpid::sys::ScopedLock<qpid::sys::Mutex> l(p->transOpsLock);
            std::vector<MessageInfo::TransType> &oplist = p->transOps[t];
            std::vector<MessageInfo::TransType>::iterator i;
            for (i = oplist.begin(); i < oplist.end(); ++i) {
                if (*i == MessageInfo::TRANSACTION_ENQUEUE) {
                    oplist.erase(i);
                    break;
                }
            }
        }
        throw;
    }
}

void
Messages::dequeue(uint64_t msgId, uint64_t queueId, Transaction::shared_ptr& t)
{
    MessageInfo::shared_ptr p;
    {
        qpid::sys::ScopedRlock<qpid::sys::RWlock> l(lock);
        MessageMap::const_iterator i = messages.find(msgId);
        if (i == messages.end())
            THROW_STORE_EXCEPTION("Message does not exist");
        p = i->second;
    }
    // Remember the transacted operation so it can be properly resolved later.
    uint64_t transactionId = 0;
    if (t.get() != 0)
        transactionId = t->getId();
    if (transactionId != 0) {
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(p->transOpsLock);
        p->transOps[t].push_back(MessageInfo::TRANSACTION_DEQUEUE);
        t->enroll(msgId);
    }
    try {
        log.recordDequeue(msgId, queueId, transactionId);
    }
    catch(...) {
        if (transactionId != 0) {
            t->unenroll(msgId);
            qpid::sys::ScopedLock<qpid::sys::Mutex> l(p->transOpsLock);
            std::vector<MessageInfo::TransType> &oplist = p->transOps[t];
            std::vector<MessageInfo::TransType>::iterator i;
            for (i = oplist.begin(); i < oplist.end(); ++i) {
                if (*i == MessageInfo::TRANSACTION_DEQUEUE) {
                    oplist.erase(i);
                    break;
                }
            }
        }
        throw;
    }

    // If transacted, leave the reference until the transaction commits.
    if (transactionId == 0)
        if (::InterlockedDecrement(&p->enqueuedCount) == 0)
            remove(msgId);
}

// Commit a previous provisional enqueue or dequeue of a particular message
// actions under a specified transaction. If this results in the message's
// being removed from all queues, it is deleted.
void
Messages::commit(uint64_t msgId, Transaction::shared_ptr& t)
{
    MessageInfo::shared_ptr p;
    {
        qpid::sys::ScopedRlock<qpid::sys::RWlock> l(lock);
        MessageMap::const_iterator i = messages.find(msgId);
        if (i == messages.end())
            THROW_STORE_EXCEPTION("Message does not exist");
        p = i->second;
    }
    {
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(p->transOpsLock);
        std::vector<MessageInfo::TransType> &oplist = p->transOps[t];
        std::vector<MessageInfo::TransType>::iterator i;
        for (i = oplist.begin(); i < oplist.end(); ++i) {
            // Transactional dequeues left the ref count alone until commit
            // while transaction enqueues already incremented it.
            if (*i == MessageInfo::TRANSACTION_DEQUEUE)
                ::InterlockedDecrement(&p->enqueuedCount);
        }
        // Remember, last deref of Transaction::shared_ptr deletes Transaction.
        p->transOps.erase(t);
    }
    // If committing results in this message having no further enqueue
    // references, delete it. If the delete fails, swallow the exception
    // and let recovery take care of removing it later.
    if (::InterlockedCompareExchange(&p->enqueuedCount, 0, 0) == 0) {
        try {
            remove(msgId);
        }
        catch(...) {}
    }
}

// Abort a previous provisional enqueue or dequeue of a particular message
// actions under a specified transaction. If this results in the message's
// being removed from all queues, it is deleted.
void
Messages::abort(uint64_t msgId, Transaction::shared_ptr& t)
{
    MessageInfo::shared_ptr p;
    {
        qpid::sys::ScopedRlock<qpid::sys::RWlock> l(lock);
        MessageMap::const_iterator i = messages.find(msgId);
        if (i == messages.end())
            THROW_STORE_EXCEPTION("Message does not exist");
        p = i->second;
    }
    {
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(p->transOpsLock);
        std::vector<MessageInfo::TransType> &oplist = p->transOps[t];
        std::vector<MessageInfo::TransType>::iterator i;
        for (i = oplist.begin(); i < oplist.end(); ++i) {
            // Transactional enqueues incremented the ref count when seen;
            // while transaction dequeues left it alone.
            if (*i == MessageInfo::TRANSACTION_ENQUEUE)
                ::InterlockedDecrement(&p->enqueuedCount);
        }
        // Remember, last deref of Transaction::shared_ptr deletes Transaction.
        p->transOps.erase(t);
    }
    // If committing results in this message having no further enqueue
    // references, delete it. If the delete fails, swallow the exception
    // and let recovery take care of removing it later.
    if (::InterlockedCompareExchange(&p->enqueuedCount, 0, 0) == 0) {
        try {
            remove(msgId);
        }
        catch(...) {}
    }
}

// Recover the current set of messages and where they're queued from
// the log.
void
Messages::recover(qpid::broker::RecoveryManager& recoverer,
                  qpid::store::MessageMap& messageMap,
                  qpid::store::MessageQueueMap& messageQueueMap,
                  const std::map<uint64_t, Transaction::shared_ptr>& transMap)
{
    std::map<uint64_t, std::vector<MessageLog::RecoveredMsgOp> > messageOps;
    log.recover(recoverer, messageMap, messageOps);
    // Now read through the messageOps replaying the operations with the
    // knowledge of which transactions committed, aborted, etc. A transaction
    // should not be deleted until there are no messages referencing it so
    // a message operation with a transaction id not found in transMap is
    // a serious problem.
    QPID_LOG(debug, "Beginning CLFS-recovered message operation replay");
    // Keep track of any messages that are recovered from the log but don't
    // have any place to be. This can happen, for example, if the broker
    // crashes while logging a message deletion. After all the recovery is
    // done, delete all the homeless messages.
    std::vector<uint64_t> homeless;
    std::map<uint64_t, std::vector<MessageLog::RecoveredMsgOp> >::const_iterator msg;
    for (msg = messageOps.begin(); msg != messageOps.end(); ++msg) {
        uint64_t msgId = msg->first;
        const std::vector<MessageLog::RecoveredMsgOp>& ops = msg->second;
        QPID_LOG(debug, "Message " << msgId << "; " << ops.size() << " ops");
        MessageInfo::shared_ptr m(new MessageInfo);
        std::vector<QueueEntry>& entries = messageQueueMap[msgId];
        std::vector<MessageLog::RecoveredMsgOp>::const_iterator op;
        for (op = ops.begin(); op != ops.end(); ++op) {
            QueueEntry entry(op->queueId);
            std::string dir =
                op->op == MessageLog::RECOVERED_ENQUEUE ? "enqueue"
                                                        : "dequeue";
            if (op->txnId != 0) {
                // Be sure to enroll this message in the transaction even if
                // it has committed or aborted. This ensures that the
                // transaction isn't removed from the log while finalizing the
                // recovery. If it were to be removed and the broker failed
                // again before removing this message during normal operation,
                // it couldn't be recovered again.
                //
                // Recall what is being reconstructed; 2 things:
                //   1. This class's 'messages' list which only keeps track
                //      of how many queues reference each message (though NOT
                //      which queues) and the transactions each message is
                //      enrolled in. For this, aborted transactions cause the
                //      result of the operation to be ignored, but the
                //      message does need to be enrolled in the transaction
                //      to properly maintain the transaction references until
                //      the message is deleted.
                //   2. The StorageProvider's MessageQueueMap, which DOES
                //      have an entry for each queue each message is on and
                //      its TPL status and associated xid.
                const Transaction::shared_ptr &t =
                    transMap.find(op->txnId)->second;
                // Adds t to map, ensuring a reference to Transaction, even if
                // no ops are added to the TransType vector.
                std::vector<MessageInfo::TransType>& tOps = m->transOps[t];
                // Prepared transactions cause the operation to be
                // provisionally acted on, and the message to be enrolled in
                // the transaction for when it commits/aborts. This is
                // noted in the QueueEntry for the StorageProvider's map.
                if (t->getState() == Transaction::TRANS_PREPARED) {
                    QPID_LOG(debug, dir << " for queue " << op->queueId <<
                                    ", prepared txn " << op->txnId);
                    TPCTransaction::shared_ptr tpct(boost::dynamic_pointer_cast<TPCTransaction>(t));
                    if (tpct.get() == 0)
                        THROW_STORE_EXCEPTION("Invalid transaction state");
                    t->enroll(msgId);
                    entry.xid = tpct->getXid();
                    if (op->op == MessageLog::RECOVERED_ENQUEUE) {
                        tOps.push_back(MessageInfo::TRANSACTION_ENQUEUE);
                        entry.tplStatus = QueueEntry::ADDING;
                    }
                    else {
                        tOps.push_back(MessageInfo::TRANSACTION_DEQUEUE);
                        entry.tplStatus = QueueEntry::REMOVING;
                    }
                }
                else if (t->getState() != Transaction::TRANS_COMMITTED) {
                    QPID_LOG(debug, dir << " for queue " << op->queueId <<
                                    ", txn " << op->txnId << ", rolling back");
                    continue;
                }
            }
            // Here for non-transactional and prepared transactional operations
            // to set up the messageQueueMap entries. Note that at this point
            // a committed transactional operation looks like a
            // non-transactional one as far as the QueueEntry is
            // concerned - just do it. If this is an entry enqueuing a
            // message, just add it to the entries list. If it's a dequeue
            // operation, locate the matching entry for the queue and delete
            // it if the current op is non-transactional; if it's a prepared
            // transaction then replace the existing entry with the current
            // one that notes the message is enqueued but being removed under
            // a prepared transaciton.
            QPID_LOG(debug, dir + " at queue " << entry.queueId);
            if (op->op == MessageLog::RECOVERED_ENQUEUE) {
                entries.push_back(entry);
            }
            else {
                std::vector<QueueEntry>::iterator i = entries.begin();
                while (i != entries.end()) {
                    if (i->queueId == entry.queueId) {
                        *i = entry;
                        break;
                    }
                    ++i;
                }
            }
        }
        // Now that all the queue entries have been set correctly, the
        // enqueuedCount that MessageInfo keeps track of is simply the
        // number of queue map entries. If there are none, add this
        // message to the homeless list to be deleted from the log after
        // the recovery is done.
        if ((m->enqueuedCount = entries.size()) == 0) {
            homeless.push_back(msgId);
            messageMap.erase(msgId);
            messageQueueMap.erase(msgId);
        }
        std::pair<uint64_t, MessageInfo::shared_ptr> p(msgId, m);
        messages.insert(p);
    }
    QPID_LOG(debug, "Message log recovery done.");
    // Done! Ok, go back and delete all the homeless messages.
    for (std::vector<uint64_t>::iterator i = homeless.begin();
         i != homeless.end();
         ++i) {
        QPID_LOG(debug, "Deleting homeless message " << *i);
        remove(*i);
    }
}

// Remove a specified message from those controlled by this object.
void
Messages::remove(uint64_t messageId)
{
    uint64_t newFirstId = 0;
    {
        qpid::sys::ScopedWlock<qpid::sys::RWlock> l(lock);
        messages.erase(messageId);
        // May have deleted the first entry; if so the log can release that.
        // If this message being deleted results in an empty list of
        // messages, move the tail up to this message's LSN. This may
        // result in one or more messages being stranded in the log
        // until there's more activity. If a restart happens while these
        // unneeded log records are there, the presence of the MessageDelete
        // entry will cause the message(s) to be ignored anyway.
        if (messages.empty())
            newFirstId = messageId;
        else if (messages.begin()->first > messageId)
            newFirstId = messages.begin()->first;
    }
    log.deleteMessage(messageId, newFirstId);
}

}}}  // namespace qpid::store::ms_clfs
