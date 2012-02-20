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

#include "AsyncStore.h"

namespace qpid {
namespace broker {

    AsyncStorePlus::storeConfigDataReturn_t
    AsyncStorePlus::storeConfigData(const boost::intrusive_ptr<StoredData> configData,
                                    const successCbFn_t successCb,
                                    const failCbFn_t failCb,
                                    const void* cbCtxt)
    {
        boost::intrusive_ptr<ConfigToken> configTok(nextConfigToken());
        AsyncStoreOp op(AsyncStoreOp::OP_CONFIG_CREATE, configData, configTok, 0, 0, successCb, failCb, cbCtxt);
        return AsyncStorePlus::storeConfigDataReturn_t(submit(op), configTok);
    }

    int
    AsyncStorePlus::destroyConfigData(const boost::intrusive_ptr<ConfigToken> configTok,
                                      const successCbFn_t successCb,
                                      const failCbFn_t failCb,
                                      const void* cbCtxt)
    {
        AsyncStoreOp op(AsyncStoreOp::OP_CONFIG_DESTROY, 0, configTok, 0, 0, successCb, failCb, cbCtxt);
        return submit(op);
    }

    AsyncStorePlus::createQueueReturn_t
    AsyncStorePlus::createQueue(const std::string& name,
                                const qpid::types::Variant::Map& options,
                                const boost::intrusive_ptr<StoredData> queueData,
                                const successCbFn_t successCb,
                                const failCbFn_t failCb,
                                const void* cbCtxt)
    {
        boost::intrusive_ptr<QueueToken> queueTok(nextQueueToken(name, options));
        AsyncStoreOp op(AsyncStoreOp::OP_CONFIG_CREATE, queueData, queueTok, 0, 0, successCb, failCb, cbCtxt);
        return AsyncStorePlus::createQueueReturn_t(submit(op), queueTok);
    }

    int
    AsyncStorePlus::destroyQueue(const boost::intrusive_ptr<QueueToken> queueTok,
                                 const successCbFn_t successCb,
                                 const failCbFn_t failCb,
                                 const void* cbCtxt)
    {
        AsyncStoreOp op(AsyncStoreOp::OP_CONFIG_DESTROY, 0, queueTok, 0, 0, successCb, failCb, cbCtxt);
        return submit(op);
    }

    AsyncStorePlus::txnReturn_t
    AsyncStorePlus::beginTxn(const std::string xid,
                             const successCbFn_t successCb,
                             const failCbFn_t failCb,
                             const void* cbCtxt)
    {
        boost::intrusive_ptr<TxnToken> txnTok(nextTxnToken(xid));
        AsyncStoreOp op(AsyncStoreOp::OP_TXN_BEGIN, 0, 0, 0, txnTok, successCb, failCb, cbCtxt);
        return AsyncStorePlus::txnReturn_t(submit(op), txnTok);
    }

    int
    AsyncStorePlus::prepareTxn(const boost::intrusive_ptr<TxnToken> txnTok,
                               const successCbFn_t successCb,
                               const failCbFn_t failCb,
                               const void* cbCtxt)
    {
        AsyncStoreOp op(AsyncStoreOp::OP_TXN_PREPARE, 0, 0, 0, txnTok, successCb, failCb, cbCtxt);
        return submit(op);
    }

    int
    AsyncStorePlus::commitTxn(const boost::intrusive_ptr<TxnToken> txnTok,
                              const successCbFn_t successCb,
                              const failCbFn_t failCb,
                              const void* cbCtxt)
    {
        AsyncStoreOp op(AsyncStoreOp::OP_TXN_COMMIT, 0, 0, 0, txnTok, successCb, failCb, cbCtxt);
        return submit(op);
    }

    int
    AsyncStorePlus::abortTxn(const boost::intrusive_ptr<TxnToken> txnTok,
                             const successCbFn_t successCb,
                             const failCbFn_t failCb,
                             const void* cbCtxt)
    {
        AsyncStoreOp op(AsyncStoreOp::OP_TXN_ABORT, 0, 0, 0, txnTok, successCb, failCb, cbCtxt);
        return submit(op);
    }

    AsyncStorePlus::enqReturn_t
    AsyncStorePlus::enqueueMsg(const boost::intrusive_ptr<StoredData> msgData,
                            const boost::intrusive_ptr<QueueToken> queueTok,
                            const boost::intrusive_ptr<TxnToken> txnTok,
                            const successCbFn_t successCb,
                            const failCbFn_t failCb,
                            const void* cbCtxt)
    {
        boost::intrusive_ptr<MessageToken> msgTok(nextMessageToken());
        AsyncStoreOp op(AsyncStoreOp::OP_ENQUEUE, msgData, msgTok, queueTok, txnTok, successCb, failCb, cbCtxt);
        return AsyncStorePlus::enqReturn_t(submit(op), msgTok);
    }

    int
    AsyncStorePlus::dequeueMsg(const boost::intrusive_ptr<MessageToken> msgTok,
                            const boost::intrusive_ptr<QueueToken> queueTok,
                            const boost::intrusive_ptr<TxnToken> txnTok,
                            const successCbFn_t successCb,
                            const failCbFn_t failCb,
                            const void* cbCtxt)
    {
        AsyncStoreOp op(AsyncStoreOp::OP_DEQUEUE, 0, msgTok, queueTok, txnTok, successCb, failCb, cbCtxt);
        return submit(op);
    }

    AsyncStorePlus::storeEventReturn_t
    AsyncStorePlus::storeQueueEvent(const boost::intrusive_ptr<StoredData> eventData,
                               const boost::intrusive_ptr<QueueToken> queueTok,
                               const boost::intrusive_ptr<TxnToken> txnTok,
                               const successCbFn_t successCb,
                               const failCbFn_t failCb,
                               const void* cbCtxt)
    {
        boost::intrusive_ptr<EventToken> eventTok(nextEventToken());
        AsyncStoreOp op(AsyncStoreOp::OP_EVENT_STORE, eventData, eventTok, queueTok, txnTok, successCb, failCb, cbCtxt);
        return AsyncStorePlus::storeEventReturn_t(submit(op), eventTok);
    }

    int
    AsyncStorePlus::flushQueue(const boost::intrusive_ptr<QueueToken> queueTok,
                          const successCbFn_t successCb,
                          const failCbFn_t failCb,
                          const void* cbCtxt)
    {
        AsyncStoreOp op(AsyncStoreOp::OP_FLUSH, 0, 0, queueTok, 0, successCb, failCb, cbCtxt);
        return submit(op);
    }

}} // namespace qpid::broker
