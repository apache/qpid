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

#include "AsyncStoreErrors.h"
#include "AsyncStoreToken.h"
#include "qpid/broker/RecoveryManager.h"
#include "qpid/RefCounted.h"
#include "qpid/types/Variant.h"
#include <string>

#include <boost/intrusive_ptr.hpp>

namespace qpid {
namespace broker {

    // C functor-style definitions for async completion and failure callbacks
    /**
     * \brief Callback function used for successful completion of operation.
     *
     * \param ctxPtr Callback context pointer
     */
    typedef void (*successCbFn_t)(const void * ctxtPtr);

    /**
     * \brief Callback function used when an operation fails.
     *
     * \param errCode Erroro code which indicates the error or failure
     * \param errInfo Additional information about this specific error or failure in text format
     * \param ctxtPtr Callback context pointer
     */
    typedef void (*failCbFn_t)(const asyncStoreError_t errCode, const std::string& errInfo, const void* ctxtPtr);

    /**
     * \brief Struct used to pass operation code and parameters to AsyncStore::submit().
     */
    struct AsyncStoreOp         // Ops require the following tokens: (o=optional):
    {                           // data id queue txn successCb failCb cbCtxt
        typedef enum {          // -----------------------------------------------
            OP_NONE = 0,        //
            OP_CONFIG_CREATE,   //   Y   Y   N    N      o       o       o
            OP_CONFIG_DESTROY,  //   N   Y   N    N      o       o       o
            OP_FLUSH,           //   N   N   Y    N      o       o       o
            OP_TXN_BEGIN,       //   N   N   N    Y      o       o       o
            OP_TXN_PREPARE,     //   N   N   N    Y      o       o       o
            OP_TXN_ABORT,       //   N   N   N    Y      o       o       o
            OP_TXN_COMMIT,      //   N   N   N    Y      o       o       o
            OP_EVENT_STORE,     //   Y   Y   Y    o      o       o       o
            OP_ENQUEUE,         //   Y   Y   Y    o      o       o       o
            OP_DEQUEUE          //   N   Y   Y    o      o       o       o
        } opCode_t;

        opCode_t op;                                ///< Operation code
        boost::intrusive_ptr<StoredData> data;      ///< Data source for ops where data is persisted
        boost::intrusive_ptr<IdentityToken> id;     ///< Token for target of persistence operation
        boost::intrusive_ptr<QueueToken> queue;     ///< Token for queue where a queue context is required
        boost::intrusive_ptr<TxnToken> txn;         ///< Token for transaction context if required
        successCbFn_t successCb;                    ///< Successful completion callback function
        failCbFn_t failCb;                          ///< Failure callback function
        const void* cbCtxt;                         ///< Callback context passed back in callback functions

        // --- Convenience constructors ---

        AsyncStoreOp() : op(OP_NONE), data(0), id(0), queue(0), txn(0), successCb(0), failCb(0), cbCtxt(0) {}

        AsyncStoreOp(const opCode_t op,
                     const boost::intrusive_ptr<StoredData> data,
                     const boost::intrusive_ptr<IdentityToken> id,
                     const boost::intrusive_ptr<QueueToken> queue,
                     const boost::intrusive_ptr<TxnToken> txn,
                     const successCbFn_t successCb = 0,
                     const failCbFn_t failCb = 0,
                     const void* cbCtxt = 0) :
            op(op), data(data), id(id), queue(queue), txn(txn), successCb(successCb), failCb(failCb), cbCtxt(cbCtxt)
        {}

    }; // struct StoreOp


    /**
     * \brief This is the core async store interface class.
     */
    class AsyncStore: public Recoverable
    {
    public:
        // Submit async operation here
        virtual int submit(const AsyncStoreOp& op) = 0;

        // Functions to provide tokens for an operation
        virtual boost::intrusive_ptr<ConfigToken> nextConfigToken() = 0;
        virtual boost::intrusive_ptr<EventToken> nextEventToken() = 0;
        virtual boost::intrusive_ptr<MessageToken> nextMessageToken() = 0;
        virtual boost::intrusive_ptr<QueueToken> nextQueueToken(const std::string& name, const qpid::types::Variant::Map& options) = 0;
        virtual boost::intrusive_ptr<TxnToken> nextTxnToken(const std::string& xid=std::string()) = 0;

        // Legacy - Restore FTD message, NOT async!
        virtual int loadContent(boost::intrusive_ptr<MessageToken> msgTok,
                                const boost::intrusive_ptr<QueueToken> queueTok,
                                char* data,
                                uint64_t offset,
                                uint64_t length) = 0;
    }; // class AsyncStore


    /**
     * \brief This class contains helper functions that set up StoreOp and pass it to the submit() function.
     *
     * In some cases, a token instance is returned along with the return code of the submit;
     * a std::pair<int, tok> pattern is used for these calls.
     *
     * I don't want to call this a xxxImpl class, as it is still abstract and needs to be subclassed. Other ideas for
     * a class name here welcome!
     *
     * Usage pattern example:
     * ----------------------
     * queueTok <- createQueue(queueName, storeOpts, queueOpts); // Queue is persisted, store space initialized
     *
     * dtxTok -< beginTxn(xid); // Xid is supplied: distributed txn
     * msgTok <- enqueueMsg(msgData, queueTok, dtxTok); // Transactional enqueue of msgData on queue queueTok
     * prepareTxn(dtxTok); // dtx prepare
     * commitTxn(dtxTok); // end of distributed txn
     *
     * txnTok <- beginTxn(); // local txn
     * dequeue(msgTok, queueTok, txnTok); // Transactional dequeue of message msgTok on queue queueTok
     * commitTxn(txnTok); // end of local txn
     *
     * destroyQueue(queueTok);
     */
    class AsyncStorePlus : public AsyncStore
    {
        // --- Config data ---

        typedef std::pair<int, boost::intrusive_ptr<ConfigToken> > storeConfigDataReturn_t;
        storeConfigDataReturn_t storeConfigData(const boost::intrusive_ptr<StoredData> configData,
                                                const successCbFn_t complCb = 0,
                                                const failCbFn_t failCb = 0,
                                                const void* cbCtxt = 0);

        int destroyConfigData(const boost::intrusive_ptr<ConfigToken> configTok,
                              const successCbFn_t complCb = 0,
                              const failCbFn_t failCb = 0,
                              const void* cbCtxt = 0);


        // --- Queues ---

        typedef std::pair<int, boost::intrusive_ptr<QueueToken> > createQueueReturn_t;
        createQueueReturn_t createQueue(const std::string& name,
                                        const qpid::types::Variant::Map& options,
                                        const boost::intrusive_ptr<StoredData> queueData,
                                        const successCbFn_t complCb = 0,
                                        const failCbFn_t failCb = 0,
                                        const void* cbCtxt = 0);

        int flushQueue(const boost::intrusive_ptr<QueueToken> queueTok,
                       const successCbFn_t complCb = 0,
                       const failCbFn_t failCb = 0,
                       const void* cbCtxt = 0);

        int destroyQueue(const boost::intrusive_ptr<QueueToken> queueTok,
                         const successCbFn_t complCb = 0,
                         const failCbFn_t failCb = 0,
                         const void* cbCtxt = 0);


        // --- Transactions ---

        typedef std::pair<int, boost::intrusive_ptr<TxnToken> > txnReturn_t;
        txnReturn_t beginTxn(const std::string xid = std::string(),
                             const successCbFn_t complCb = 0,
                             const failCbFn_t failCb = 0,
                             const void* cbCtxt = 0);

        int prepareTxn(const boost::intrusive_ptr<TxnToken> txnTok,
                       const successCbFn_t complCb = 0,
                       const failCbFn_t failCb = 0,
                       const void* cbCtxt = 0);

        int commitTxn(const boost::intrusive_ptr<TxnToken> txnTok,
                      const successCbFn_t complCb = 0,
                      const failCbFn_t failCb = 0,
                      const void* cbCtxt = 0);

        int abortTxn(const boost::intrusive_ptr<TxnToken> txnTok,
                     const successCbFn_t complCb = 0,
                     const failCbFn_t failCb = 0,
                     const void* cbCtxt = 0);


        // --- Event storage ---

        typedef std::pair<int, boost::intrusive_ptr<EventToken> > storeEventReturn_t;
        storeEventReturn_t storeQueueEvent(const boost::intrusive_ptr<StoredData> eventData,
                                           const boost::intrusive_ptr<QueueToken> queueTok,
                                           const boost::intrusive_ptr<TxnToken> txnTok = 0,
                                           const successCbFn_t complCb = 0,
                                           const failCbFn_t failCb = 0,
                                           const void* cbCtxt = 0);


        // --- Messages ---

        typedef std::pair<int, boost::intrusive_ptr<MessageToken> > enqReturn_t;
        enqReturn_t enqueueMsg(const boost::intrusive_ptr<StoredData> msgData,
                               const boost::intrusive_ptr<QueueToken> queueTok,
                               const boost::intrusive_ptr<TxnToken> txnTok = 0,
                               const successCbFn_t complCb = 0,
                               const failCbFn_t failCb = 0,
                               const void* cbCtxt = 0);

        int dequeueMsg(const boost::intrusive_ptr<MessageToken> msgTok,
                       const boost::intrusive_ptr<QueueToken> queueTok,
                       const boost::intrusive_ptr<TxnToken> txnTok = 0,
                       const successCbFn_t complCb = 0,
                       const failCbFn_t failCb = 0,
                       const void* cbCtxt = 0);
    }; // class AsyncStorePlus

}} // namespace qpid::broker

#endif // qpid_broker_AsyncStore_h_
