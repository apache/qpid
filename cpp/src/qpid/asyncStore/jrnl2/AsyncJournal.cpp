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
 * \file AsyncJournal.cpp
 */

#include "AsyncJournal.h"

#include "AioCallback.h"
#include "DataToken.h"

// --- temp code ---
#include <fstream>
#include <iostream>
// --- end temp code ---

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

std::string
g_ioResAsString(const jrnlOpRes /*res*/) {
    /// \todo TODO - provide implementation
    return ".[g_ioResAsString].";
}

AsyncJournal::AsyncJournal(const std::string& jrnlId,
                           const std::string& jrnlDir,
                           const std::string& baseFileName) :
        m_jrnlId(jrnlId),
        m_jrnlDir(jrnlDir),
        m_baseFileName(baseFileName),
        m_jrnlParamsPtr(0),
        m_aioCallbackPtr(0)
{
    m_writeDataTokens.reserve(s_listSizeThreshold);
    m_callbackDataTokens.reserve(s_listSizeThreshold);
}

std::string
AsyncJournal::getId() const {
    return m_jrnlId;
}

JournalDirectory
AsyncJournal::getJournalDir() const {
    return m_jrnlDir;
}

std::string
AsyncJournal::getJournalDirName() const {
    return m_jrnlDir.getFqName();
}

std::string
AsyncJournal::getBaseFileName() const {
    return m_baseFileName;
}

const JournalRunState&
AsyncJournal::getState() const {
    return m_jrnlState;
}

const JournalParameters*
AsyncJournal::getParameters() const {
    return m_jrnlParamsPtr;
}

void
AsyncJournal::initialize(const JournalParameters* jpPtr,
                         AioCallback* const aiocbPtr) {
    m_jrnlParamsPtr = jpPtr;
    m_aioCallbackPtr = aiocbPtr;
    // --- temp code ---
    m_jrnlDir.create();
    /// --- end temp code ---
}

jrnlOpRes
AsyncJournal::enqueue(DataToken* dtokPtr,
                      const void* /*dataPtr*/,
                      const std::size_t /*dataLen*/,
                      const void* /*tidPtr*/,
                      const std::size_t /*tidLen*/,
                      const bool /*transientFlag*/) {
    dtokPtr->getDataOpState().enqueue();
    // --- temp code ---
    { // --- START OF CRITICAL SECTION ---
        ScopedLock l(m_writeDataTokensLock);
        m_writeDataTokens.push_back(dtokPtr);
        if (m_writeDataTokens.size() >= s_listSizeThreshold) {
            flushNoLock();
        }
    } // --- END OF CRITICAL SECTION ---
    //processCompletedAioWriteEvents(0);
    // --- end temp code ---
    return 0;
}

jrnlOpRes
AsyncJournal::dequeue(DataToken* const dtokPtr,
                      const void* /*tidPtr*/,
                      const std::size_t /*tidLen*/) {
    dtokPtr->getDataOpState().dequeue();
    dtokPtr->setDequeueRecordId(dtokPtr->getRecordId());
    // --- temp code ---
    { // --- START OF CRITICAL SECTION ---
        ScopedLock l(m_writeDataTokensLock);
        m_writeDataTokens.push_back(dtokPtr);
        if (m_writeDataTokens.size() >= s_listSizeThreshold) {
            flushNoLock();
        }
    } // --- END OF CRITICAL SECTION ---
    //processCompletedAioWriteEvents(0);
    // --- end temp code ---
    return 0;
}

jrnlOpRes
AsyncJournal::commit() {
    /// \todo TODO - provide implementation
    return 0;
}

jrnlOpRes
AsyncJournal::abort() {
    /// \todo TODO - provide implementation
    return 0;
}

jrnlOpRes
AsyncJournal::flush() {
    // --- temp code ---
    // --- START OF CRITICAL SECTION ---
    ScopedTryLock l(m_writeDataTokensLock);
    if (l.isLocked()) {
        return flushNoLock();
    }
    return 0;
    // --- END OF CRITICAL SECTION ---
    // --- end temp code ---
}

// protected
jrnlOpRes
AsyncJournal::flushNoLock() {
    // --- temp code ---
    // Normally the page would be written to disk using libaio here (still to do).
    uint32_t cnt = 0UL;
    ScopedLock l(m_callbackDataTokensLock);
    while (!m_writeDataTokens.empty()) {
        m_callbackDataTokens.push_back(m_writeDataTokens.back());
        m_writeDataTokens.pop_back();
        ++cnt;
    }
    return 0;
    // --- end temp code ---
}

jrnlOpRes
AsyncJournal::sync(const double timeout) {
    // --- temp code ---
    // --- START OF CRITICAL SECTION ---
    ScopedTryLock l(m_writeDataTokensLock);
    if (l.isLocked()) {
        return syncNoLock(timeout);
    }
    return 0;
    // --- END OF CRITICAL SECTION ---
    // --- end temp code ---
}

// protected
jrnlOpRes
AsyncJournal::syncNoLock(const double /*timeout*/) {
    // --- temp code ---
    if (m_callbackDataTokens.size()) {
        processCompletedAioWriteEvents();
    }
    return 0;
    // --- end temp code ---
}

void
AsyncJournal::processCompletedAioWriteEvents(const double /*timeout*/) {
    // --- temp code ---
    // --- START OF CRITICAL SECTION 1 ---
    ScopedLock l1(m_callbackDataTokensLock);
    m_aioCallbackPtr->writeAioCompleteCallback(m_callbackDataTokens);
    // --- END OF CRITICAL SECTION 1 ---
    // --- end temp code ---
}

}}} // namespace qpid::asyncStore::jrnl2


