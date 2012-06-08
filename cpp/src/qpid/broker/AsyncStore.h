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

// TODO: See if we can replace this with a forward declaration, but current definition of qpid::types::Variant::Map
// does not allow it. Using a local map<std::string, Variant> definition also precludes forward declaration.
#include "qpid/types/Variant.h" // qpid::types::Variant::Map

#include <boost/shared_ptr.hpp>
#include <stdint.h>
#include <string>

namespace qpid {
namespace broker {

// This handle carries async op results
class AsyncResultHandle;

// Broker to subclass as a pollable queue
class AsyncResultQueue {
public:
    virtual ~AsyncResultQueue();
    // TODO: Remove boost::shared_ptr<> from this interface
    virtual void submit(boost::shared_ptr<AsyncResultHandle>) = 0;
};

// Subclass this for specific contexts
class BrokerAsyncContext {
public:
    virtual ~BrokerAsyncContext();
    virtual AsyncResultQueue* getAsyncResultQueue() const = 0;
    virtual void invokeCallback(const AsyncResultHandle* const) const = 0;
};

class DataSource {
public:
    virtual ~DataSource();
    virtual uint64_t getSize() = 0;
    virtual void write(char* target) = 0;
};

// Callback invoked by AsyncResultQueue to pass back async results
typedef void (*AsyncResultCallback)(const AsyncResultHandle* const);

class ConfigHandle;
class EnqueueHandle;
class EventHandle;
class MessageHandle;
class QueueHandle;
class TxnHandle;


// Subclassed by store:
class AsyncStore {
public:
    virtual ~AsyncStore();

    // --- Factory methods for creating handles ---

    virtual ConfigHandle createConfigHandle() = 0;
    virtual EnqueueHandle createEnqueueHandle(MessageHandle&, QueueHandle&) = 0;
    virtual EventHandle createEventHandle(QueueHandle&, const std::string& key=std::string()) = 0;
    virtual MessageHandle createMessageHandle(const DataSource* const) = 0;
    virtual QueueHandle createQueueHandle(const std::string& name, const qpid::types::Variant::Map& opts) = 0;
    virtual TxnHandle createTxnHandle(const std::string& xid=std::string()) = 0; // Distr. txns must supply xid


    // --- Store async interface ---

    // TODO: Remove boost::shared_ptr<> from this interface
    virtual void submitPrepare(TxnHandle&, boost::shared_ptr<BrokerAsyncContext>) = 0; // Distributed txns only
    virtual void submitCommit(TxnHandle&, boost::shared_ptr<BrokerAsyncContext>) = 0;
    virtual void submitAbort(TxnHandle&, boost::shared_ptr<BrokerAsyncContext>) = 0;

    virtual void submitCreate(ConfigHandle&, const DataSource* const, boost::shared_ptr<BrokerAsyncContext>) = 0;
    virtual void submitDestroy(ConfigHandle&, boost::shared_ptr<BrokerAsyncContext>) = 0;

    virtual void submitCreate(QueueHandle&, const DataSource* const, boost::shared_ptr<BrokerAsyncContext>) = 0;
    virtual void submitDestroy(QueueHandle&, boost::shared_ptr<BrokerAsyncContext>) = 0;
    virtual void submitFlush(QueueHandle&, boost::shared_ptr<BrokerAsyncContext>) = 0;

    virtual void submitCreate(EventHandle&, const DataSource* const, boost::shared_ptr<BrokerAsyncContext>) = 0;
    virtual void submitCreate(EventHandle&, const DataSource* const, TxnHandle&, boost::shared_ptr<BrokerAsyncContext>) = 0;
    virtual void submitDestroy(EventHandle&, boost::shared_ptr<BrokerAsyncContext>) = 0;
    virtual void submitDestroy(EventHandle&, TxnHandle&, boost::shared_ptr<BrokerAsyncContext>) = 0;

    virtual void submitEnqueue(EnqueueHandle&, TxnHandle&, boost::shared_ptr<BrokerAsyncContext>) = 0;
    virtual void submitDequeue(EnqueueHandle&, TxnHandle&, boost::shared_ptr<BrokerAsyncContext>) = 0;

    // Legacy - Restore FTD message, is NOT async!
    virtual int loadContent(MessageHandle&, QueueHandle&, char* data, uint64_t offset, const uint64_t length) = 0;
};


}} // namespace qpid::broker

#endif // qpid_broker_AsyncStore_h_
