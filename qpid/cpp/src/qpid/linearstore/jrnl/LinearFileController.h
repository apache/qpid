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

#ifndef QPID_LINEARSTORE_LINEARFILECONTROLLER_H_
#define QPID_LINEARSTORE_LINEARFILECONTROLLER_H_

#include <deque>
#include "qpid/linearstore/jrnl/aio.h"
#include "qpid/linearstore/jrnl/AtomicCounter.h"
#include "qpid/linearstore/jrnl/EmptyFilePoolTypes.h"
#include "qpid/linearstore/jrnl/smutex.h"

struct file_hdr_t;
namespace qpid {
namespace qls_jrnl {
class EmptyFilePool;
class jcntl;
class JournalFile;

class LinearFileController
{
protected:
    typedef std::deque<JournalFile*> JournalFileList_t;
    typedef JournalFileList_t::iterator JournalFileListItr_t;

    jcntl& jcntlRef;
    std::string journalDirectory;
    EmptyFilePool* emptyFilePoolPtr;
    JournalFile* currentJournalFilePtr;
    AtomicCounter<uint64_t> fileSeqCounter;
    AtomicCounter<uint64_t> recordIdCounter;

    JournalFileList_t journalFileList;
    smutex journalFileListMutex;

public:
    LinearFileController(jcntl& jcntlRef_);
    virtual ~LinearFileController();

    void initialize(const std::string& journalDirectory_, EmptyFilePool* emptyFilePoolPtr_);
    void finalize();

    void pullEmptyFileFromEfp();
    void purgeFilesToEfp();
    efpDataSize_kib_t dataSize_kib() const;
    efpFileSize_kib_t fileSize_kib() const;
    efpDataSize_sblks_t dataSize_sblks() const;
    efpFileSize_sblks_t fileSize_sblks() const;

    uint64_t getNextRecordId();

    // Functions for manipulating counts of non-current JournalFile instances in journalFileList
    uint32_t decrEnqueuedRecordCount(const efpFileCount_t fileSeqNumber);
    uint32_t addWriteCompletedDblkCount(const efpFileCount_t fileSeqNumber, const uint32_t a);
    uint16_t decrOutstandingAioOperationCount(const efpFileCount_t fileSeqNumber);

    // Pass-through functions for JournalFile class
    void asyncFileHeaderWrite(io_context_t ioContextPtr,
                              const uint16_t userFlags,
                              const uint64_t recordId,
                              const uint64_t firstRecordOffset);
    void asyncPageWrite(io_context_t ioContextPtr,
                        aio_cb* aioControlBlockPtr,
                        void* data,
                        uint32_t dataSize_dblks);

    uint64_t getCurrentFileSeqNum() const;

    uint32_t getEnqueuedRecordCount() const;
    uint32_t incrEnqueuedRecordCount();
    uint32_t addEnqueuedRecordCount(const uint32_t a);
    uint32_t decrEnqueuedRecordCount();
    uint32_t subtrEnqueuedRecordCount(const uint32_t s);

    uint32_t getWriteSubmittedDblkCount() const;
    uint32_t addWriteSubmittedDblkCount(const uint32_t a);

    uint32_t getWriteCompletedDblkCount() const;
    uint32_t addWriteCompletedDblkCount(const uint32_t a);

    uint16_t getOutstandingAioOperationCount() const;
    uint16_t incrOutstandingAioOperationCount();
    uint16_t decrOutstandingAioOperationCount();

    bool isEmpty() const;                      // True if no writes of any kind have occurred
    bool isDataEmpty() const;                  // True if only file header written, data is still empty
    u_int32_t dblksRemaining() const;          // Dblks remaining until full
    bool isFull() const;                       // True if all possible dblks have been submitted (but may not yet have returned from AIO)
    bool isFullAndComplete() const;            // True if all submitted dblks have returned from AIO
    u_int32_t getOutstandingAioDblks() const;  // Dblks still to be written
    bool getNextFile() const;                  // True when next file is needed

    // Debug aid
    const std::string status(const uint8_t indentDepth) const;

protected:
    bool checkCurrentJournalFileValid() const;
    void assertCurrentJournalFileValid(const char* const functionName) const;
    JournalFile* find(const efpFileCount_t fileSeqNumber); // NOT THREAD SAFE - use under external lock
    uint64_t getNextFileSeqNum();
};

}} // namespace qpid::qls_jrnl

#endif // QPID_LINEARSTORE_LINEARFILECONTROLLER_H_
