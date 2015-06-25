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

#ifndef QPID_LINEARSTORE_JOURNAL_LINEARFILECONTROLLER_H_
#define QPID_LINEARSTORE_JOURNAL_LINEARFILECONTROLLER_H_

#include <deque>
#include "qpid/linearstore/journal/aio.h"
#include "qpid/linearstore/journal/AtomicCounter.h"
#include "qpid/linearstore/journal/EmptyFilePoolTypes.h"

namespace qpid {
namespace linearstore {
namespace journal {

class EmptyFilePool;
class jcntl;
class JournalFile;

class LinearFileController
{
protected:
    typedef std::deque<JournalFile*> JournalFileList_t;
    typedef JournalFileList_t::iterator JournalFileListItr_t;

    jcntl& jcntlRef_;
    std::string journalDirectory_;
    EmptyFilePool* emptyFilePoolPtr_;
    AtomicCounter<uint64_t> fileSeqCounter_;
    AtomicCounter<uint64_t> recordIdCounter_;
    AtomicCounter<uint64_t> decrCounter_;

    JournalFileList_t journalFileList_;
    JournalFile* currentJournalFilePtr_;
    smutex journalFileListMutex_;

public:
    LinearFileController(jcntl& jcntlRef);
    virtual ~LinearFileController();

    void initialize(const std::string& journalDirectory,
                    EmptyFilePool* emptyFilePoolPtr,
                    uint64_t initialFileNumberVal);
    void finalize();

    void addJournalFile(JournalFile* journalFilePtr,
                        const uint32_t completedDblkCount,
                        const bool makeCurrentFlag);

    efpDataSize_sblks_t dataSize_sblks() const;
    efpFileSize_sblks_t fileSize_sblks() const;
    void getNextJournalFile();
    uint64_t getNextRecordId();
    void removeFileToEfp(const std::string& fileName);
    void restoreEmptyFile(const std::string& fileName);
    void purgeEmptyFilesToEfp();

    // Functions for manipulating counts of non-current JournalFile instances in journalFileList_
    uint32_t getEnqueuedRecordCount(const uint64_t fileSeqNumber);
    uint32_t incrEnqueuedRecordCount(const uint64_t fileSeqNumber);
    uint32_t decrEnqueuedRecordCount(const uint64_t fileSeqNumber);
    uint32_t addWriteCompletedDblkCount(const uint64_t fileSeqNumber,
                                        const uint32_t a);
    uint16_t decrOutstandingAioOperationCount(const uint64_t fileSeqNumber);

    // Pass-through functions for current JournalFile class
    void asyncFileHeaderWrite(io_context_t ioContextPtr,
                              const uint16_t userFlags,
                              const uint64_t recordId,
                              const uint64_t firstRecordOffset);
    void asyncPageWrite(io_context_t ioContextPtr,
                        aio_cb* aioControlBlockPtr,
                        void* data,
                        uint32_t dataSize_dblks);

    uint64_t getCurrentFileSeqNum() const;
    uint64_t getCurrentSerial() const;
    bool isEmpty() const;

    // Debug aid
    const std::string status(const uint8_t indentDepth) const;

protected:
    void addJournalFile(const std::string& fileName,
                        const efpIdentity_t& efpIdentity,
                        const uint64_t fileSeqNumber,
                        const uint32_t completedDblkCount);
    void assertCurrentJournalFileValid(const char* const functionName) const;
    bool checkCurrentJournalFileValid() const;
    JournalFile* find(const uint64_t fileSeqNumber);
    uint64_t getNextFileSeqNum();
    void pullEmptyFileFromEfp();
};

typedef void (LinearFileController::*lfcAddJournalFileFn)(JournalFile* journalFilePtr,
                                                          const uint32_t completedDblkCount,
                                                          const bool makeCurrentFlag);

}}}

#endif // QPID_LINEARSTORE_JOURNAL_LINEARFILECONTROLLER_H_
