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

#ifndef QPID_LINEARSTORE_JOURNALFILE_H_
#define QPID_LINEARSTORE_JOURNALFILE_H_

#include "qpid/linearstore/jrnl/aio.h"
#include "qpid/linearstore/jrnl/AtomicCounter.h"
#include "qpid/linearstore/jrnl/EmptyFilePoolTypes.h"
#include <stdint.h>
#include <string>

class file_hdr_t;

namespace qpid {
namespace qls_jrnl {

class JournalFile
{
protected:
    const std::string fqFileName_;
    const uint64_t fileSeqNum_;
    int fileHandle_;
    bool fileCloseFlag_;
    void* fileHeaderBasePtr_;
    ::file_hdr_t* fileHeaderPtr_;
    aio_cb* aioControlBlockPtr_;
    uint32_t fileSize_dblks_;                           ///< File size in data blocks, including file header

    AtomicCounter<uint32_t> enqueuedRecordCount_;       ///< Count of enqueued records
    AtomicCounter<uint32_t> submittedDblkCount_;        ///< Write file count (data blocks) for submitted AIO
    AtomicCounter<uint32_t> completedDblkCount_;        ///< Write file count (data blocks) for completed AIO
    AtomicCounter<uint16_t> outstandingAioOpsCount_;    ///< Outstanding AIO operations on this file

public:
    JournalFile(const std::string& fqFileName,
                const uint64_t fileSeqNum,
                const efpDataSize_kib_t efpDataSize_kib);
    virtual ~JournalFile();

    void initialize(const uint32_t completedDblkCount);
    void finalize();

    const std::string getDirectory() const;
    const std::string getFileName() const;
    const std::string getFqFileName() const;
    uint64_t getFileSeqNum() const;

    bool isOpen() const;
    int open();
    void close();
    void asyncFileHeaderWrite(io_context_t ioContextPtr,
                              const efpPartitionNumber_t efpPartitionNumber,
                              const efpDataSize_kib_t efpDataSize_kib,
                              const uint16_t userFlags,
                              const uint64_t recordId,
                              const uint64_t firstRecordOffset,
                              const std::string queueName);
    void asyncPageWrite(io_context_t ioContextPtr,
                        aio_cb* aioControlBlockPtr,
                        void* data,
                        uint32_t dataSize_dblks);

    uint32_t getEnqueuedRecordCount() const;
    uint32_t incrEnqueuedRecordCount();
    uint32_t addEnqueuedRecordCount(const uint32_t a);
    uint32_t decrEnqueuedRecordCount();
    uint32_t subtrEnqueuedRecordCount(const uint32_t s);

    uint32_t getSubmittedDblkCount() const;
    uint32_t addSubmittedDblkCount(const uint32_t a);

    uint32_t getCompletedDblkCount() const;
    uint32_t addCompletedDblkCount(const uint32_t a);

    uint16_t getOutstandingAioOperationCount() const;
    uint16_t incrOutstandingAioOperationCount();
    uint16_t decrOutstandingAioOperationCount();

    // Status helper functions
    bool isEmpty() const;                      ///< True if no writes of any kind have occurred
    bool isDataEmpty() const;                  ///< True if only file header written, data is still empty
    u_int32_t dblksRemaining() const;          ///< Dblks remaining until full
    bool isFull() const;                       ///< True if all possible dblks have been submitted (but may not yet have returned from AIO)
    bool isFullAndComplete() const;            ///< True if all submitted dblks have returned from AIO
    u_int32_t getOutstandingAioDblks() const;  ///< Dblks still to be written
    bool getNextFile() const;                  ///< True when next file is needed
    bool isNoEnqueuedRecordsRemaining() const; ///< True when all enqueued records (or parts) have been dequeued

    // debug aid
    const std::string status_str(const uint8_t indentDepth) const;
};

}} // namespace qpid::qls_jrnl

#endif // QPID_LINEARSTORE_JOURNALFILE_H_
