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

/**
 * \file AsyncStoreImpl.h
 */

#ifndef qpid_asyncStore_AsyncStoreImpl_h_
#define qpid_asyncStore_AsyncStoreImpl_h_

#include "AsyncStoreOptions.h"
#include "RunState.h"
#include "OperationQueue.h"

#include "qpid/asyncStore/jrnl2/RecordIdCounter.h"
#include "qpid/broker/AsyncStore.h"
#include "qpid/sys/Poller.h"

namespace qpid {

namespace broker {
class Broker;
} // namespace qpid::broker

namespace asyncStore {

class AsyncStoreImpl: public qpid::broker::AsyncStore {
public:
    AsyncStoreImpl(boost::shared_ptr<qpid::sys::Poller> poller,
                   const AsyncStoreOptions& opts);
    virtual ~AsyncStoreImpl();
    void initialize();
    uint64_t getNextRid();

    // Management

    void initManagement(qpid::broker::Broker* broker);

    // AsyncStore interface

    qpid::broker::TxnHandle createTxnHandle(const std::string& xid=std::string());
    qpid::broker::ConfigHandle createConfigHandle();
    qpid::broker::QueueHandle createQueueHandle(const std::string& name,
                                                const qpid::types::Variant::Map& opts);
    qpid::broker::EventHandle createEventHandle(qpid::broker::QueueHandle& queueHandle,
                                                const std::string& key=std::string());
    qpid::broker::MessageHandle createMessageHandle(const qpid::broker::DataSource* dataSrc);
    qpid::broker::EnqueueHandle createEnqueueHandle(qpid::broker::MessageHandle& msgHandle,
                                                    qpid::broker::QueueHandle& queueHandle);

    void submitPrepare(qpid::broker::TxnHandle& txnHandle,
                       qpid::broker::ResultCallback resultCb,
                       qpid::broker::BrokerContext* brokerCtxt);
    void submitCommit(qpid::broker::TxnHandle& txnHandle,
                      qpid::broker::ResultCallback resultCb,
                      qpid::broker::BrokerContext* brokerCtxt);
    void submitAbort(qpid::broker::TxnHandle& txnHandle,
                     qpid::broker::ResultCallback resultCb,
                     qpid::broker::BrokerContext* brokerCtxt);

    void submitCreate(qpid::broker::ConfigHandle& cfgHandle,
                      const qpid::broker::DataSource* dataSrc,
                      qpid::broker::ResultCallback resultCb,
                      qpid::broker::BrokerContext* brokerCtxt);
    void submitDestroy(qpid::broker::ConfigHandle& cfgHandle,
                       qpid::broker::ResultCallback resultCb,
                       qpid::broker::BrokerContext* brokerCtxt);

    void submitCreate(qpid::broker::QueueHandle& queueHandle,
                      const qpid::broker::DataSource* dataSrc,
                      qpid::broker::ResultCallback resultCb,
                      qpid::broker::BrokerContext* brokerCtxt);
    void submitDestroy(qpid::broker::QueueHandle& queueHandle,
                       qpid::broker::ResultCallback resultCb,
                       qpid::broker::BrokerContext* brokerCtxt);
    void submitFlush(qpid::broker::QueueHandle& queueHandle,
                     qpid::broker::ResultCallback resultCb,
                     qpid::broker::BrokerContext* brokerCtxt);

    void submitCreate(qpid::broker::EventHandle& eventHandle,
                      const qpid::broker::DataSource* dataSrc,
                      qpid::broker::ResultCallback resultCb,
                      qpid::broker::BrokerContext* brokerCtxt);
    void submitCreate(qpid::broker::EventHandle& eventHandle,
                      const qpid::broker::DataSource* dataSrc,
                      qpid::broker::TxnHandle& txnHandle,
                      qpid::broker::ResultCallback resultCb,
                      qpid::broker::BrokerContext* brokerCtxt);
    void submitDestroy(qpid::broker::EventHandle& eventHandle,
                      qpid::broker::ResultCallback resultCb,
                      qpid::broker::BrokerContext* brokerCtxt);
    void submitDestroy(qpid::broker::EventHandle& eventHandle,
                      qpid::broker::TxnHandle& txnHandle,
                      qpid::broker::ResultCallback resultCb,
                      qpid::broker::BrokerContext* brokerCtxt);

    void submitEnqueue(qpid::broker::EnqueueHandle& enqHandle,
                       qpid::broker::ResultCallback resultCb,
                       qpid::broker::BrokerContext* brokerCtxt);
    void submitEnqueue(qpid::broker::EnqueueHandle& enqHandle,
                       qpid::broker::TxnHandle& txnHandle,
                       qpid::broker::ResultCallback resultCb,
                       qpid::broker::BrokerContext* brokerCtxt);
    void submitDequeue(qpid::broker::EnqueueHandle& enqHandle,
                       qpid::broker::ResultCallback resultCb,
                       qpid::broker::BrokerContext* brokerCtxt);
    void submitDequeue(qpid::broker::EnqueueHandle& enqHandle,
                       qpid::broker::TxnHandle& txnHandle,
                       qpid::broker::ResultCallback resultCb,
                       qpid::broker::BrokerContext* brokerCtxt);

    // Legacy - Restore FTD message, is NOT async!
    virtual int loadContent(qpid::broker::MessageHandle& msgHandle,
                            qpid::broker::QueueHandle& queueHandle,
                            char* data,
                            uint64_t offset,
                            const uint64_t length);

protected:
    boost::shared_ptr<qpid::sys::Poller> m_poller;
    AsyncStoreOptions m_opts;
    RunState m_runState;
    OperationQueue m_operations;
    qpid::asyncStore::jrnl2::RecordIdCounter_t m_ridCntr;
};

}} // namespace qpid::asyncStore

#endif // qpid_asyncStore_AsyncStoreImpl_h_
