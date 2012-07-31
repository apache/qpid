/*
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
 */

#ifndef qpid_broker_AsyncStore_h_
#define qpid_broker_AsyncStore_h_

#include "qpid/types/Variant.h" // qpid::types::Variant::Map

#include <boost/shared_ptr.hpp>
#include <stdint.h> // uint64_t
#include <string>

namespace qpid {
namespace broker {

// This handle carries async op results
class AsyncResultHandle;

// Broker to subclass as a pollable queue
class AsyncResultQueue {
public:
    virtual ~AsyncResultQueue() {}
    virtual void submit(boost::shared_ptr<AsyncResultHandle>) = 0;
};

// Subclass this for specific contexts
class BrokerAsyncContext {
public:
    virtual ~BrokerAsyncContext() {}
    virtual AsyncResultQueue* getAsyncResultQueue() const = 0;
    virtual void invokeCallback(const AsyncResultHandle* const) const = 0;
    void setOpStr(const char* opStr) { m_opStr = opStr; }
    const char* getOpStr() const { return m_opStr; }
private:
    const char* m_opStr;
};

class DataSource {
public:
    virtual ~DataSource() {}
    virtual uint64_t getSize() = 0;
    virtual void write(char* target) = 0;
};

// Opaque async handles used for carrying persistence state.

class ConfigHandle;
class EnqueueHandle;
class EventHandle;
class MessageHandle;
class QueueHandle;
class TxnHandle;

class QueueAsyncContext;
class TpcTxnAsyncContext;
class TxnAsyncContext;
class TxnBuffer;

class AsyncTransactionalStore {
public:
    virtual ~AsyncTransactionalStore() {}

    virtual TxnHandle createTxnHandle() = 0;
    virtual TxnHandle createTxnHandle(TxnBuffer* tb) = 0;
    virtual TxnHandle createTxnHandle(const std::string& xid,
                                      const bool tpcFlag) = 0;
    virtual TxnHandle createTxnHandle(const std::string& xid,
                                      const bool tpcFlag,
                                      TxnBuffer* tb) = 0;

    virtual void submitPrepare(TxnHandle&,
                               boost::shared_ptr<TpcTxnAsyncContext>) = 0; // Distributed txns only
    virtual void submitCommit(TxnHandle&,
                              boost::shared_ptr<TxnAsyncContext>) = 0;
    virtual void submitAbort(TxnHandle&,
                             boost::shared_ptr<TxnAsyncContext>) = 0;
    void testOp() const {}
};

// Subclassed by store:
class AsyncStore {
public:
    virtual ~AsyncStore() {}

    // --- Factory methods for creating handles ---

    virtual ConfigHandle createConfigHandle() = 0;
    virtual EnqueueHandle createEnqueueHandle(MessageHandle&,
                                              QueueHandle&) = 0;
    virtual EventHandle createEventHandle(QueueHandle&,
                                          const std::string& key=std::string()) = 0;
    virtual MessageHandle createMessageHandle(const DataSource* const) = 0;
    virtual QueueHandle createQueueHandle(const std::string& name,
                                          const qpid::types::Variant::Map& opts) = 0;


    // --- Store async interface ---

    // TODO: Switch from BrokerAsyncContext (parent class) to ConfigAsyncContext
    // when theses features (and async context classes) are developed.
    virtual void submitCreate(ConfigHandle&,
                              const DataSource* const,
                              boost::shared_ptr<BrokerAsyncContext>) = 0;
    virtual void submitDestroy(ConfigHandle&,
                               boost::shared_ptr<BrokerAsyncContext>) = 0;

    virtual void submitCreate(QueueHandle&,
                              const DataSource* const,
                              boost::shared_ptr<QueueAsyncContext>) = 0;
    virtual void submitDestroy(QueueHandle&,
                               boost::shared_ptr<QueueAsyncContext>) = 0;
    virtual void submitFlush(QueueHandle&,
                             boost::shared_ptr<QueueAsyncContext>) = 0;

    // TODO: Switch from BrokerAsyncContext (parent class) to EventAsyncContext
    // when theses features (and async context classes) are developed.
    virtual void submitCreate(EventHandle&,
                              const DataSource* const,
                              TxnHandle&,
                              boost::shared_ptr<BrokerAsyncContext>) = 0;
    virtual void submitDestroy(EventHandle&,
                               TxnHandle&,
                               boost::shared_ptr<BrokerAsyncContext>) = 0;

    virtual void submitEnqueue(EnqueueHandle&,
                               TxnHandle&,
                               boost::shared_ptr<QueueAsyncContext>) = 0;
    virtual void submitDequeue(EnqueueHandle&,
                               TxnHandle&,
                               boost::shared_ptr<QueueAsyncContext>) = 0;

    // Legacy - Restore FTD message, is NOT async!
    virtual int loadContent(MessageHandle&,
                            QueueHandle&,
                            char* data,
                            uint64_t offset,
                            const uint64_t length) = 0;
};


}} // namespace qpid::broker

#endif // qpid_broker_AsyncStore_h_
