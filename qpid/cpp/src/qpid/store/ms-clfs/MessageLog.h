#ifndef QPID_STORE_MSCLFS_MESSAGELOG_H
#define QPID_STORE_MSCLFS_MESSAGELOG_H

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

#include <boost/intrusive_ptr.hpp>
#include <qpid/broker/PersistableMessage.h>
#include <qpid/broker/RecoveryManager.h>
#include <qpid/sys/IntegerTypes.h>
#include <qpid/store/StorageProvider.h>

#include "Log.h"

namespace qpid {
namespace store {
namespace ms_clfs {

/**
 * @class MessageLog
 *
 * Represents a CLFS-housed message log.
 */
class MessageLog : public Log {

protected:
    // Message log needs to have a no-op first record written in the log
    // to ensure that no real message gets an ID 0.
    virtual void initialize();

public:
    // Inherited and reimplemented from Log. Figure the minimum marshalling
    // buffer size needed for the records this class writes.
    virtual uint32_t marshallingBufferSize();

    // Add the specified message to the log; Return the persistence Id.
    uint64_t add(const boost::intrusive_ptr<qpid::broker::PersistableMessage>& msg);

    // Write a Delete entry for messageId. If newFirstId is not 0, it is now
    // the earliest valid message in the log, so move the tail up to it.
    void deleteMessage(uint64_t messageId, uint64_t newFirstId);

    // Load part or all of a message's content from previously stored
    // log record(s).
    void loadContent(uint64_t messageId,
                     std::string& data,
                     uint64_t offset,
                     uint32_t length);

    // Enqueue and dequeue operations track messages' transit across
    // queues; each operation may be associated with a transaction. If
    // the transactionId is 0 the operation is not associated with a
    // transaction.
    void recordEnqueue (uint64_t messageId,
                        uint64_t queueId,
                        uint64_t transactionId);
    void recordDequeue (uint64_t messageId,
                        uint64_t queueId,
                        uint64_t transactionId);

    // Recover the messages and their queueing records from the log.
    // @param recoverer  Recovery manager used to recreate broker objects from
    //                   encoded framing buffers recovered from the log.
    // @param messageMap This method fills in the map of id -> ptr of
    //                   recovered messages.
    // @param messageOps This method fills in the map of msg id ->
    //                   vector of operations involving the message that were
    //                   recovered from the log. It is the caller's
    //                   responsibility to sort the operations out and
    //                   ascertain which operations should be acted on. The
    //                   order of operations in the vector is as they were
    //                   read in order from the log.
    typedef enum { RECOVERED_ENQUEUE = 1, RECOVERED_DEQUEUE } RecoveredOpType;
    struct RecoveredMsgOp {
        RecoveredOpType op;
        uint64_t queueId;
        uint64_t txnId;

        RecoveredMsgOp(RecoveredOpType o, const uint64_t& q, const uint64_t& t)
            : op(o), queueId(q), txnId(t) {}
    };
    void recover(qpid::broker::RecoveryManager& recoverer,
                 qpid::store::MessageMap& messageMap,
                 std::map<uint64_t, std::vector<RecoveredMsgOp> >& messageOps);
};

}}}  // namespace qpid::store::ms_clfs

#endif /* QPID_STORE_MSCLFS_MESSAGELOG_H */
