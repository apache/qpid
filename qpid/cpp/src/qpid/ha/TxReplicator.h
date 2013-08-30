#ifndef QPID_HA_TRANSACTIONREPLICATOR_H
#define QPID_HA_TRANSACTIONREPLICATOR_H

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

#include "QueueReplicator.h"
#include "Event.h"
#include "qpid/broker/DeliveryRecord.h"
#include "qpid/broker/TransactionalStore.h"
#include "qpid/sys/Mutex.h"

namespace qpid {

namespace broker {
class TxBuffer;
class TxAccept;
class DtxBuffer;
class Broker;
class MessageStore;
}

namespace ha {
class BrokerReplicator;

/**
 * Exchange created on a backup broker to replicate a transaction on the primary.
 *
 * Subscribes to a tx-queue like a normal queue but puts replicated messages and
 * transaction events into a local TxBuffer.
 *
 * THREAD SAFE: Called in different connection threads.
 */
class TxReplicator : public QueueReplicator {
  public:
    typedef boost::shared_ptr<broker::Queue> QueuePtr;
    typedef boost::shared_ptr<broker::Link> LinkPtr;

    static bool isTxQueue(const std::string& queue);
    static std::string getTxId(const std::string& queue);

    TxReplicator(HaBroker&, const QueuePtr& txQueue, const LinkPtr& link);
    ~TxReplicator();

    std::string getType() const;

    // QueueReplicator overrides
    void destroy();

  protected:

    void deliver(const broker::Message&);

  private:

    typedef void (TxReplicator::*DispatchFunction)(
        const std::string&, sys::Mutex::ScopedLock&);
    typedef qpid::sys::unordered_map<std::string, DispatchFunction> DispatchMap;
    typedef qpid::sys::unordered_map<std::string, ReplicationIdSet> DequeueMap;

    void sendMessage(const broker::Message&, sys::Mutex::ScopedLock&);
    void enqueue(const std::string& data, sys::Mutex::ScopedLock&);
    void dequeue(const std::string& data, sys::Mutex::ScopedLock&);
    void prepare(const std::string& data, sys::Mutex::ScopedLock&);
    void commit(const std::string& data, sys::Mutex::ScopedLock&);
    void rollback(const std::string& data, sys::Mutex::ScopedLock&);
    void members(const std::string& data, sys::Mutex::ScopedLock&);
    void end(sys::Mutex::ScopedLock&);

    std::string logPrefix;
    TxEnqueueEvent enq;         // Enqueue data for next deliver.
    boost::shared_ptr<broker::TxBuffer> txBuffer;
    broker::MessageStore* store;
    std::auto_ptr<broker::TransactionContext> context;
    framing::ChannelId channel; // Channel to send prepare-complete.
    bool complete;

    // Class to process dequeues and create DeliveryRecords to populate a
    // TxAccept.
    class DequeueState {
      public:
        DequeueState(broker::QueueRegistry& qr) : queues(qr) {}
        void add(const TxDequeueEvent&);
        boost::shared_ptr<broker::TxAccept> makeAccept();

      private:
        // Delivery record IDs are command IDs from the session.
        // On a backup we will just fake these Ids.
        typedef framing::SequenceNumber Id;
        typedef framing::SequenceSet IdSet;
        typedef qpid::sys::unordered_map<std::string, ReplicationIdSet> EventMap;

        bool addRecord(const broker::Message& m,
                       const boost::shared_ptr<broker::Queue>&,
                       const ReplicationIdSet& );
        void addRecords(const DequeueMap::value_type& entry);

        broker::QueueRegistry& queues;
        EventMap events;
        broker::DeliveryRecords records;
        broker::QueueCursor cursor;
        framing::SequenceNumber nextId;
        IdSet recordIds;
    };
    DequeueState dequeueState;
};


}} // namespace qpid::ha

#endif  /*!QPID_HA_TRANSACTIONREPLICATOR_H*/
