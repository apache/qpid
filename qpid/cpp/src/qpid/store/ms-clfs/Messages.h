#ifndef QPID_STORE_MSCLFS_MESSAGES_H
#define QPID_STORE_MSCLFS_MESSAGES_H

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
#include <map>
#include <vector>
#include <boost/intrusive_ptr.hpp>
#include <qpid/broker/PersistableMessage.h>
#include <qpid/sys/Mutex.h>

#include "MessageLog.h"
#include "Transaction.h"

namespace qpid {
namespace store {
namespace ms_clfs {

class Messages {

    struct MessageInfo {
        // How many queues this message is on, whether actually (non-transacted)
        // or provisionally (included in a non-yet-committed transaction).
        volatile LONG enqueuedCount;

        // Keep a list of transactional operations this message is
        // referenced in. When the transaction changes/finalizes these all
        // need to be acted on.
        typedef enum { TRANSACTION_ENQUEUE, TRANSACTION_DEQUEUE } TransType;
        std::map<Transaction::shared_ptr, std::vector<TransType> > transOps;
        qpid::sys::Mutex transOpsLock;

        typedef boost::shared_ptr<MessageInfo> shared_ptr;

        MessageInfo() : enqueuedCount(0) {}
    };

    qpid::sys::RWlock lock;
    typedef std::map<uint64_t, MessageInfo::shared_ptr> MessageMap;
    MessageMap messages;
    MessageLog log;

    // Remove a specified message from those controlled by this object.
    void remove(uint64_t messageId);

public:
    void openLog(const std::string& path, const Log::TuningParameters& params);

    // Add the specified message to the log and list of known messages.
    // Upon successful return the message's persistenceId is set.
    void add(const boost::intrusive_ptr<qpid::broker::PersistableMessage>& msg);

    // Add the specified queue to the message's list of places it is
    // enqueued.
    void enqueue(uint64_t msgId, uint64_t queueId, Transaction::shared_ptr& t);

    // Remove the specified queue from the message's list of places it is
    // enqueued. If there are no other queues holding the message, it is
    // deleted.
    void dequeue(uint64_t msgId, uint64_t queueId, Transaction::shared_ptr& t);

    // Commit a previous provisional enqueue or dequeue of a particular message
    // actions under a specified transaction. If this results in the message's
    // being removed from all queues, it is deleted.
    void commit(uint64_t msgId, Transaction::shared_ptr& transaction);

    // Abort a previous provisional enqueue or dequeue of a particular message
    // actions under a specified transaction. If this results in the message's
    // being removed from all queues, it is deleted.
    void abort(uint64_t msgId, Transaction::shared_ptr& transaction);

    // Load part or all of a message's content from previously stored
    // log record(s).
    void loadContent(uint64_t msgId,
                     std::string& data,
                     uint64_t offset,
                     uint32_t length);

    // Recover the current set of messages and where they're queued from
    // the log.
    void recover(qpid::broker::RecoveryManager& recoverer,
                 qpid::store::MessageMap& messageMap,
                 qpid::store::MessageQueueMap& messageQueueMap,
                 const std::map<uint64_t, Transaction::shared_ptr>& transMap);
};

}}}  // namespace qpid::store::ms_clfs

#endif /* QPID_STORE_MSCLFS_MESSAGES_H */
