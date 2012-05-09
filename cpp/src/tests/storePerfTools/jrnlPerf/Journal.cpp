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
 * \file Journal.cpp
 */

#include "Journal.h"

#ifdef JOURNAL2
#   include "qpid/asyncStore/jrnl2/DataToken.h"

#   define X_JRNL_FN_DEQUEUE(dtok) dequeue(dtok, 0, 0)
#   define X_JRNL_FN_ENQUEUE(dtok, msgData, msgSize) enqueue(dtok, msgData, msgSize, 0, 0, false)
#   define X_JRNL_FN_FLUSH(jrnlPtr) { jrnlPtr->flush(); jrnlPtr->sync(); }
#   define X_JRNL_FN_GETDTOKSTATUS(dtok) dtok
#   define X_JRNL_FN_GETEVENTS(timeout) processCompletedAioWriteEvents(timeout)
#   define X_JRNL_FN_GETIOSTR(iores) qpid::asyncStore::jrnl2::g_ioResAsString(iores)
#   define X_JRNL_IO_OP_RES qpid::asyncStore::jrnl2::jrnlOpRes
#   define X_JRNL_IO_OP_RES_BUSY qpid::asyncStore::jrnl2::RHM_IORES_BUSY
#   define X_JRNL_IO_OP_RES_ENQCAPTHRESH qpid::asyncStore::jrnl2::RHM_IORES_ENQCAPTHRESH
#   define X_JRNL_IO_OP_RES_SUCCESS 0
#   define X_SCOPED_LOCK qpid::asyncStore::jrnl2::ScopedLock
#else
#   include "jrnl/jcntl.hpp"
#   include "jrnl/data_tok.hpp"

#   define X_JRNL_FN_DEQUEUE(dtok) dequeue_data_record(dtok)
#   define X_JRNL_FN_ENQUEUE(dtok, msgData, msgSize) enqueue_data_record(msgData, msgSize, msgSize, dtok);
#   define X_JRNL_FN_FLUSH(jrnlPtr) jrnlPtr->flush(true)
#   define X_JRNL_FN_GETDTOKSTATUS(dtok) dtok->status_str()
#   define X_JRNL_FN_GETEVENTS(timeout) get_wr_events(timeout)
#   define X_JRNL_FN_GETIOSTR(iores) mrg::journal::iores_str(iores)
#   define X_JRNL_IO_OP_RES mrg::journal::iores
#   define X_JRNL_IO_OP_RES_BUSY mrg::journal::RHM_IORES_BUSY
#   define X_JRNL_IO_OP_RES_ENQCAPTHRESH mrg::journal::RHM_IORES_ENQCAPTHRESH
#   define X_JRNL_IO_OP_RES_SUCCESS mrg::journal::RHM_IORES_SUCCESS
#   define X_SCOPED_LOCK mrg::journal::slock
#endif

#include <iostream>

namespace tests {
namespace storePerftools {
namespace jrnlPerf {

Journal::Journal(const uint32_t numMsgs,
                 const uint32_t msgSize,
                 const char* msgData,
                 X_ASYNC_JOURNAL* const jrnlPtr) :
        m_numMsgs(numMsgs),
        m_msgSize(msgSize),
        m_msgData(msgData),
        m_jrnlPtr(jrnlPtr)
{}

Journal::~Journal()
{
    delete m_jrnlPtr;
}


// *** MUST BE THREAD-SAFE ****
// This method will be called by multiple threads simultaneously
// Enqueue thread entry point
void*
Journal::runEnqueues()
{
    bool misfireFlag = false;
    uint32_t i = 0;
    while (i < m_numMsgs) {
        X_DATA_TOKEN* mtokPtr = new X_DATA_TOKEN();
        X_JRNL_IO_OP_RES jrnlIoRes = m_jrnlPtr->X_JRNL_FN_ENQUEUE(mtokPtr, m_msgData, m_msgSize);
        switch (jrnlIoRes) {
            case X_JRNL_IO_OP_RES_SUCCESS:
                i++;
                misfireFlag = false;
                break;
            case X_JRNL_IO_OP_RES_BUSY:
                if (!misfireFlag) {
                    std::cout << "-" << std::flush;
                }
                delete mtokPtr;
                misfireFlag = true;
                break;
            case X_JRNL_IO_OP_RES_ENQCAPTHRESH:
                if (!misfireFlag) {
                    std::cout << "*" << std::flush;
                }
                delete mtokPtr;
                misfireFlag = true;
                ::usleep(10);
                break;
            default:
                delete mtokPtr;
                std::cerr << "enqueue_data_record FAILED with " << X_JRNL_FN_GETIOSTR(jrnlIoRes) << std::endl;
        }
    }
    /// \todo handle these results
    X_JRNL_FN_FLUSH(m_jrnlPtr);
    return NULL;
}


// *** MUST BE THREAD-SAFE ****
// This method will be called by multiple threads simultaneously
// Dequeue thread entry point
void*
Journal::runDequeues()
{
    uint32_t i = 0;
    X_JRNL_IO_OP_RES jrnlIoRes;
    while (i < m_numMsgs) {
        X_DATA_TOKEN* mtokPtr = 0;
        while (!mtokPtr) {
            bool procAioEventsFlag;
            { // --- START OF CRITICAL SECTION ---
                X_SCOPED_LOCK l(m_unprocCallbacksMutex);
                procAioEventsFlag = m_unprocCallbacks.empty();
                if (!procAioEventsFlag) {
                    mtokPtr = m_unprocCallbacks.back();
                    m_unprocCallbacks.pop_back();
                }
            } // --- END OF CRITICAL SECTION ---
            if (procAioEventsFlag) {
                m_jrnlPtr->X_JRNL_FN_GETEVENTS(0);
                ::usleep(1);
            }
        }
        bool done = false;
        while (!done) {
            jrnlIoRes = m_jrnlPtr->X_JRNL_FN_DEQUEUE(mtokPtr);
            switch (jrnlIoRes) {
                case X_JRNL_IO_OP_RES_SUCCESS:
                    i ++;
                    done = true;
                    break;
                case X_JRNL_IO_OP_RES_BUSY:
                    //::usleep(10);
                    break;
                default:
                    std::cerr << "dequeue_data_record FAILED with " << X_JRNL_FN_GETIOSTR(jrnlIoRes) << ": "
                              << X_JRNL_FN_GETDTOKSTATUS(mtokPtr) << std::endl;
                    delete mtokPtr;
                    done = true;
            }
        }
        m_jrnlPtr->X_JRNL_FN_GETEVENTS(0);
    }
    /// \todo handle these results
    X_JRNL_FN_FLUSH(m_jrnlPtr);
    return NULL;
}

//static
void*
Journal::startEnqueues(void* ptr)
{
    return reinterpret_cast<Journal*>(ptr)->runEnqueues();
}

//static
void*
Journal:: startDequeues(void* ptr)
{
    return reinterpret_cast<Journal*>(ptr)->runDequeues();
}

// *** MUST BE THREAD-SAFE ****
// This method will be called by multiple threads simultaneously
void
Journal::X_AIO_WR_CALLBACK(std::vector<X_DATA_TOKEN*>& msgTokenList)
{
    X_DATA_TOKEN* mtokPtr;
    while (msgTokenList.size()) {
        mtokPtr = msgTokenList.back();
        msgTokenList.pop_back();
#ifdef JOURNAL2
        switch (mtokPtr->getDataOpState().get()) {
            case qpid::asyncStore::jrnl2::OP_ENQUEUE:
#else
        switch (mtokPtr->wstate()) {
            case X_DATA_TOKEN::ENQ:
#endif
                { // --- START OF CRITICAL SECTION ---
                    X_SCOPED_LOCK l(m_unprocCallbacksMutex);
                    m_unprocCallbacks.push_back(mtokPtr);
                } // --- END OF CRITICAL SECTION ---
                break;
            default:
                delete mtokPtr;
        }
    }
}

// *** MUST BE THREAD-SAFE ****
// This method will be called by multiple threads simultaneously
void
Journal::X_AIO_RD_CALLBACK(std::vector<uint16_t>& /*buffPageCtrlBlkIndexList*/)
{}

}}} // namespace tests::storePerftools::jrnlPerf
