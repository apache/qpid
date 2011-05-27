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
#include <boost/foreach.hpp>

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
    MessageInfo::Location loc(queueId, t, MessageInfo::TRANSACTION_ENQUEUE);
    {
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(p->whereLock);
        p->where.push_back(loc);
        uint64_t transactionId = 0;
        if (t.get() != 0) {
            transactionId = t->getId();
            t->enroll(msgId);
        }
        try {
            log.recordEnqueue(msgId, queueId, transactionId);
        }
        catch (...) {
            // Undo the record-keeping if the log wasn't written correctly.
            if (transactionId != 0)
                t->unenroll(msgId);
            p->where.pop_back();
            throw;
        }
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
    {
        // Locate the 'where' entry for the specified queue. Once this operation
        // is recorded in the log, update the 'where' entry to reflect it.
        // Note that an existing entry in 'where' that refers to a transaction
        // is not eligible for this operation.
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(p->whereLock);
        std::list<MessageInfo::Location>::iterator i;
        for (i = p->where.begin(); i != p->where.end(); ++i) {
            if (i->queueId == queueId && i->transaction.get() == 0)
                break;
        }
        if (i == p->where.end())
            THROW_STORE_EXCEPTION("Message not on queue");
        uint64_t transactionId = 0;
        if (t.get() != 0) {
            transactionId = t->getId();
            t->enroll(msgId);
        }
        try {
            log.recordDequeue(msgId, queueId, transactionId);
        }
        catch (...) {
            // Undo the record-keeping if the log wasn't written correctly.
            if (transactionId != 0)
                t->unenroll(msgId);
            throw;
        }
        // Ok, logged successfully. If this is a transactional op, note
        // the transaction. If non-transactional, remove the 'where' entry.
        if (transactionId != 0) {
            i->transaction = t;
            i->disposition = MessageInfo::TRANSACTION_DEQUEUE;
        }
        else {
            p->where.erase(i);
            // If the message doesn't exist on any other queues, remove it.
            if (p->where.empty())
                remove(msgId);
        }
    }
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
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(p->whereLock);
        std::list<MessageInfo::Location>::iterator i;
        for (i = p->where.begin(); i != p->where.end(); ++i) {
            if (i->transaction != t)
                continue;
            // Transactional dequeues can now remove the item from the
            // where list; enqueues just clear the transaction reference.
            if (i->disposition == MessageInfo::TRANSACTION_DEQUEUE)
                i = p->where.erase(i);
            else
                i->transaction.reset();
        }
    }
    // If committing results in this message having no further enqueue
    // references, delete it. If the delete fails, swallow the exception
    // and let recovery take care of removing it later.
    if (p->where.empty()) {
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
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(p->whereLock);
        std::list<MessageInfo::Location>::iterator i = p->where.begin();
        while (i != p->where.end()) {
            if (i->transaction != t) {
                ++i;
                continue;
            }
            // Aborted transactional dequeues result in the message remaining
            // enqueued like before the operation; enqueues clear the
            // message from the where list - like the enqueue never happened.
            if (i->disposition == MessageInfo::TRANSACTION_ENQUEUE)
                i = p->where.erase(i);
            else {
                i->transaction.reset();
                ++i;
            }
        }
    }
    // If aborting results in this message having no further enqueue
    // references, delete it. If the delete fails, swallow the exception
    // and let recovery take care of removing it later.
    if (p->where.empty()) {
        try {
            remove(msgId);
        }
        catch(...) {}
    }
}

// Load part or all of a message's content from previously stored
// log record(s).
void
Messages::loadContent(uint64_t msgId,
                      std::string& data,
                      uint64_t offset,
                      uint32_t length)
{
    log.loadContent(msgId, data, offset, length);
}

// Recover the current set of messages and where they're queued from
// the log.
void
Messages::recover(qpid::broker::RecoveryManager& recoverer,
                  const std::set<uint64_t> &validQueues,
                  const std::map<uint64_t, Transaction::shared_ptr>& transMap,
                  qpid::store::MessageMap& messageMap,
                  qpid::store::MessageQueueMap& messageQueueMap)
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
        QPID_LOG(debug, "Message " << msgId << "; " << ops.size() << " op(s)");
        MessageInfo::shared_ptr m(new MessageInfo);
        std::vector<QueueEntry>& entries = messageQueueMap[msgId];
        std::vector<MessageLog::RecoveredMsgOp>::const_iterator op;
        for (op = ops.begin(); op != ops.end(); ++op) {
            QueueEntry entry(op->queueId);
            MessageInfo::Location loc(op->queueId);
            std::string dir =
                op->op == MessageLog::RECOVERED_ENQUEUE ? "enqueue"
                                                        : "dequeue";
            if (validQueues.find(op->queueId) == validQueues.end()) {
                QPID_LOG(info,
                         "Message " << msgId << dir << " on non-existant queue "
                         << op->queueId << "; dropped");
                continue;
            }
            if (op->txnId != 0) {
                // Be sure to enroll this message in the transaction even if
                // it has committed or aborted. This ensures that the
                // transaction isn't removed from the log while finalizing the
                // recovery. If it were to be removed and the broker failed
                // again before removing this message during normal operation,
                // it couldn't be recovered again.
                //
                // Recall what is being reconstructed; 2 things:
                //   1. This class's 'messages' list which keeps track
                //      of the queues each message is on and the transactions
                //      each message is enrolled in. For this, aborted
                //      transactions cause the result of the operation to be
                //      ignored, but the message does need to be enrolled in
                //      the transaction to properly maintain the transaction
                //      references until the message is deleted.
                //   2. The StorageProvider's MessageQueueMap, which also
                //      has an entry for each queue each message is on and
                //      its TPL status and associated xid.
                const Transaction::shared_ptr &t =
                    transMap.find(op->txnId)->second;
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
                    loc.transaction = t;
                    if (op->op == MessageLog::RECOVERED_ENQUEUE) {
                        entry.tplStatus = QueueEntry::ADDING;
                        loc.disposition = MessageInfo::TRANSACTION_ENQUEUE;
                    }
                    else {
                        entry.tplStatus = QueueEntry::REMOVING;
                        loc.disposition = MessageInfo::TRANSACTION_DEQUEUE;
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
            // a prepared transaction.
            QPID_LOG(debug, dir + " at queue " << entry.queueId);
            if (op->op == MessageLog::RECOVERED_ENQUEUE) {
                entries.push_back(entry);
                m->where.push_back(loc);
            }
            else {
                std::vector<QueueEntry>::iterator i = entries.begin();
                while (i != entries.end()) {
                    if (i->queueId == entry.queueId) {
                        if (entry.tplStatus != QueueEntry::NONE)
                            *i = entry;
                        else
                            entries.erase(i);
                        break;
                    }
                    ++i;
                }
                std::list<MessageInfo::Location>::iterator w = m->where.begin();
                while (w != m->where.end()) {
                    if (w->queueId == loc.queueId) {
                        if (loc.transaction.get() != 0) {
                            *w = loc;
                            ++w;
                        }
                        else {
                            w = m->where.erase(w);
                        }
                    }
                }
            }
        }
        // Now that all the queue entries have been set correctly, see if
        // there are any entries; they may have all been removed during
        // recovery. If there are none, add this message to the homeless
        // list to be deleted from the log after the recovery is done.
        if (m->where.size() == 0) {
            homeless.push_back(msgId);
            messageMap.erase(msgId);
            messageQueueMap.erase(msgId);
        }
        else {
            std::pair<uint64_t, MessageInfo::shared_ptr> p(msgId, m);
            messages.insert(p);
        }
    }

    QPID_LOG(debug, "Message log recovery done.");
    // Done! Ok, go back and delete all the homeless messages.
    BOOST_FOREACH(uint64_t msg, homeless) {
        QPID_LOG(debug, "Deleting homeless message " << msg);
        remove(msg);
    }
}

// Expunge is called when a queue is deleted. All references to that
// queue must be expunged from all messages. 'Dequeue' log records are
// written for each queue entry removed, but any errors are swallowed.
// On recovery there's a list of valid queues passed in. The deleted
// queue will not be on that list so if any references to it are
// recovered they'll get weeded out then.
void
Messages::expunge(uint64_t queueId)
{
    std::vector<uint64_t> toBeDeleted;   // Messages to be deleted later.

    {
        // Lock everybody out since all messages are possibly in play.
        // There also may be other threads already working on particular
        // messages so individual message mutex still must be acquired.
        qpid::sys::ScopedWlock<qpid::sys::RWlock> l(lock);
        MessageMap::iterator m;
        for (m = messages.begin(); m != messages.end(); ++m) {
            MessageInfo::shared_ptr p = m->second;
            {
                qpid::sys::ScopedLock<qpid::sys::Mutex> ml(p->whereLock);
                std::list<MessageInfo::Location>::iterator i = p->where.begin();
                while (i != p->where.end()) {
                    if (i->queueId != queueId) {
                        ++i;
                        continue;
                    }
                    // If this entry is involved in a transaction, unenroll it.
                    // Then remove the entry.
                    if (i->transaction.get() != 0)
                        i->transaction->unenroll(m->first);
                    i = p->where.erase(i);
                    try {
                        log.recordDequeue(m->first, queueId, 0);
                    }
                    catch(...) {
                    }
                }
                if (p->where.size() == 0)
                    toBeDeleted.push_back(m->first);
            }
        }
    }
    // Swallow any exceptions during this; don't care. Recover it later
    // if needed.
    try {
        BOOST_FOREACH(uint64_t msg, toBeDeleted)
            remove(msg);
    }
    catch(...) {
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
