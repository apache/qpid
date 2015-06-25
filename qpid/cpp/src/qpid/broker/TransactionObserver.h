#ifndef QPID_BROKER_TRANSACTIONOBSERVER_H
#define QPID_BROKER_TRANSACTIONOBSERVER_H

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


#include "DeliveryRecord.h"
#include <boost/shared_ptr.hpp>

namespace qpid {

namespace framing {
class SequenceSet;
}

namespace broker {
class Queue;
class Message;
class TxBuffer;
class DtxBuffer;

/**
 * Interface for intercepting events in a transaction.
 */
class TransactionObserver {
  public:
    typedef boost::shared_ptr<Queue> QueuePtr;
    typedef framing::SequenceNumber SequenceNumber;

    virtual ~TransactionObserver() {}

    /** Message enqueued in the transaction. */
    virtual void enqueue(const QueuePtr&, const Message&) = 0;

    /**
     * Message is dequeued in the transaction (it was accepted by a consumer.)
     *@param queuePosition: Sequence number of message on queue.
     *@param replicationId: Replication sequence number, may be different.
     */
    virtual void dequeue(const QueuePtr& queue,
                         SequenceNumber queueSeq,
                         SequenceNumber replicationSeq) = 0;

    virtual bool prepare() = 0;
    virtual void commit() = 0;
    virtual void rollback() = 0;
};

/**
 * No-op TransactionObserver.
 */
class NullTransactionObserver : public TransactionObserver {
  public:
    void enqueue(const QueuePtr&, const Message&) {}
    void dequeue(const QueuePtr&, SequenceNumber, SequenceNumber) {}
    bool prepare() { return true; }
    void commit() {}
    void rollback() {}
};

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_TRANSACTIONOBSERVER_H*/
