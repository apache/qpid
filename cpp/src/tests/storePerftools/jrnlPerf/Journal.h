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
 * \file Journal.h
 */

#ifndef tests_storePerftools_jrnlPerf_Journal_h_
#define tests_storePerftools_jrnlPerf_Journal_h_

#ifdef JOURNAL2
#   include "qpid/asyncStore/jrnl2/AioCallback.h"
#   include "qpid/asyncStore/jrnl2/AsyncJournal.h"
#   include "qpid/asyncStore/jrnl2/ScopedLock.h"
#else
#   include "jrnl/aio_callback.hpp"
#   include "jrnl/smutex.hpp"
#endif

#include <stdint.h> // uint16_t, uint32_t

#ifdef JOURNAL2
#   define X_AIO_CALLBACK  qpid::asyncStore::jrnl2::AioCallback
#   define X_AIO_RD_CALLBACK readAioCompleteCallback
#   define X_AIO_WR_CALLBACK writeAioCompleteCallback
#   define X_ASYNC_JOURNAL qpid::asyncStore::jrnl2::AsyncJournal
#   define X_DATA_TOKEN qpid::asyncStore::jrnl2::DataToken
#   define X_SCOPED_MUTEX qpid::asyncStore::jrnl2::ScopedMutex
#else
#   define X_AIO_CALLBACK  mrg::journal::aio_callback
#   define X_AIO_RD_CALLBACK rd_aio_cb
#   define X_AIO_WR_CALLBACK wr_aio_cb
#   define X_ASYNC_JOURNAL mrg::journal::jcntl
#   define X_DATA_TOKEN mrg::journal::data_tok
#   define X_SCOPED_MUTEX mrg::journal::smutex
#endif

#ifndef JOURNAL2
namespace mrg {
namespace journal {
class jcntl;
}} // namespace mrg::journal
namespace qpid {
namespace asyncStore {
namespace jrnl2 {
class AsyncJournal;
}}} // namespace qpid::asyncStore::jrnl2
#endif

namespace tests {
namespace storePerftools {
namespace jrnlPerf {

/**
 * \brief Test journal instance. Each queue to be tested results in one instance of this class.
 *
 * Journal test harness which contains the journal to be tested. Each queue to be tested in the test parameters
 * results in one instance of this class being instantiated, and consequently one set of journals on disk. The
 * journal instance is provided as a pointer to the constructor.
 */
class Journal : public X_AIO_CALLBACK
{
public:
    /**
     * \brief Constructor
     *
     * \param numMsgs Number of messages per thread to be enqueued then dequeued (ie both ways through broker)
     * \param msgSize Size of each message being enqueued
     * \param msgData Pointer to message content (all messages have identical content)
     * \param jrnlPtr Pinter to journal instance which is to be tested
     */
    Journal(const uint32_t numMsgs,
            const uint32_t msgSize,
            const char* msgData,
            X_ASYNC_JOURNAL* const jrnlPtr);

    /**
     * \brief virtual destructor
     */
    virtual ~Journal();

    /**
     * \brief Worker thread enqueue task
     *
     * This function is the worker thread enqueue task entry point. It enqueues _numMsgs onto the journal instance.
     * A data tokens is created for each record, this is the start of the data token life cycle. All possible
     * returns from the journal are handled appropriately. Since the enqueue threads also perform
     * callbacks on completed AIO operations, the data tokens from completed enqueues are placed onto the
     * unprocessed callback list (_unprocCallbackList) for dequeueing by the dequeue worker thread(s).
     *
     * This function must be thread safe.
     */
    void* runEnqueues();

    /**
     * \brief Worker thread dequeue task
     *
     * This function is the worker thread dequeue task entry point. It dequeues messages which are on the
     * unprocessed callback list (_unprocCallbackList).
     *
     * This function must be thread safe.
     */
    void* runDequeues();

    /**
     * \brief Helper function to launch the run() function when starting a thread.
     */
    static void* startEnqueues(void* ptr);

    /**
     * \brief Helper function to launch the run() function when starting a thread.
     */
    static void* startDequeues(void* ptr);

    /**
     * \brief Write callback function. When AIO operations return, this function is called.
     *
     * When AIO operations return, this function will sort the enqueue ops from the rest and place the data tokens
     * of these records onto the unprocessed callback list (_unprocCallbackList) for dequeueing by another thread.
     *
     * Returning dequeue ops have their data tokens destroyed, as this is the end of the life cycle of the data
     * tokens.
     *
     * Required by all subclasses of mrg::journal::aio_callback.
     *
     * \param dataTokenList A vector of data tokens for those messages which have completed their AIO write
     *  operations
     */
    void X_AIO_WR_CALLBACK(std::vector<X_DATA_TOKEN*>& dataTokenList);

    /**
     * \brief Read callback function. When read AIO operations return, this function is called.
     *
     * Not used in this test, but required by all subclasses of mrg::journal::aio_callback.
     *
     * \param buffPageCtrlBlkIndexList A vector of indices to the buffer page control blocks for completed reads
     */
    void X_AIO_RD_CALLBACK(std::vector<uint16_t>& buffPageCtrlBlkIndexList);

private:
    const uint32_t m_numMsgs;                       ///< Number of messages to be processed by this journal instance
    const uint32_t m_msgSize;                       ///< Size of each message (in bytes)
    const char* m_msgData;                          ///< Pointer to message content to be used for each message.
    X_ASYNC_JOURNAL* const m_jrnlPtr;               ///< Journal instance pointer
    std::vector<X_DATA_TOKEN*> m_unprocCallbacks;   ///< List of unprocessed callbacks to be dequeued
    X_SCOPED_MUTEX m_unprocCallbacksMutex;          ///< Mutex which protects the unprocessed callback queue

};

}}} // namespace tests::storePerftools::jrnlPerf

#endif // tests_storePerftools_jrnlPerf_Journal_h_
