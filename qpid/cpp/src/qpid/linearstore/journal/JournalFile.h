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

#ifndef QPID_LINEARSTORE_JOURNAL_JOURNALFILE_H_
#define QPID_LINEARSTORE_JOURNAL_JOURNALFILE_H_

#include "qpid/linearstore/journal/aio.h"
#include "qpid/linearstore/journal/AtomicCounter.h"
#include "qpid/linearstore/journal/EmptyFilePoolTypes.h"

class file_hdr_t;

namespace qpid {
namespace linearstore {
namespace journal {

class JournalFile
{
protected:
    const efpIdentity_t efpIdentity_;
    const std::string fqFileName_;
    const uint64_t fileSeqNum_;
    const std::string queueName_;
    const uint64_t serial_;
    uint64_t firstRecordOffset_;
    int fileHandle_;
    bool fileCloseFlag_;
    void* fileHeaderBasePtr_;
    ::file_hdr_t* fileHeaderPtr_;
    aio_cb* aioControlBlockPtr_;
    uint32_t fileSize_dblks_;                           ///< File size in data blocks, including file header
    bool initializedFlag_;

    AtomicCounter<uint32_t> enqueuedRecordCount_;       ///< Count of enqueued records
    AtomicCounter<uint32_t> submittedDblkCount_;        ///< Write file count (data blocks) for submitted AIO
    AtomicCounter<uint32_t> completedDblkCount_;        ///< Write file count (data blocks) for completed AIO
    AtomicCounter<uint16_t> outstandingAioOpsCount_;    ///< Outstanding AIO operations on this file

public:
    // Constructor for creating new file with known fileSeqNum and random serial
    JournalFile(const std::string& fqFileName,
                const efpIdentity_t& efpIdentity,
                const uint64_t fileSeqNum,
                const std::string queueName);
    // Constructor for recovery in which fileSeqNum and serial are recovered from fileHeader param
    JournalFile(const std::string& fqFileName,
                const ::file_hdr_t& fileHeader,
                const std::string queueName);
    virtual ~JournalFile();

    void initialize(const uint32_t completedDblkCount);
    void finalize();

    const std::string getFqFileName() const;
    uint64_t getFileSeqNum() const;
    uint64_t getSerial() const;

    int open();
    void close();
    void asyncFileHeaderWrite(io_context_t ioContextPtr,
                              const efpPartitionNumber_t efpPartitionNumber,
                              const efpDataSize_kib_t efpDataSize_kib,
                              const uint16_t userFlags,
                              const uint64_t recordId,
                              const uint64_t firstRecordOffset);
    void asyncPageWrite(io_context_t ioContextPtr,
                        aio_cb* aioControlBlockPtr,
                        void* data,
                        uint32_t dataSize_dblks);

    uint32_t getSubmittedDblkCount() const;
    uint32_t getEnqueuedRecordCount() const;
    uint32_t incrEnqueuedRecordCount();
    uint32_t decrEnqueuedRecordCount();

    uint32_t addCompletedDblkCount(const uint32_t a);

    uint16_t getOutstandingAioOperationCount() const;
    uint16_t decrOutstandingAioOperationCount();

    efpIdentity_t getEfpIdentity() const;
    uint64_t getFirstRecordOffset() const;
    void setFirstRecordOffset(const uint64_t firstRecordOffset);

    // Status helper functions
    bool isEmpty() const;                      ///< True if no writes of any kind have occurred
    bool isNoEnqueuedRecordsRemaining() const; ///< True when all enqueued records (or parts) have been dequeued

    // debug aid
    const std::string status_str(const uint8_t indentDepth) const;

protected:
    const std::string getDirectory() const;
    const std::string getFileName() const;
    static uint64_t getRandom64();
    bool isOpen() const;

    uint32_t addSubmittedDblkCount(const uint32_t a);

    uint32_t getCompletedDblkCount() const;

    uint16_t incrOutstandingAioOperationCount();

    u_int32_t dblksRemaining() const;          ///< Dblks remaining until full
    bool getNextFile() const;                  ///< True when next file is needed
    u_int32_t getOutstandingAioDblks() const;  ///< Dblks still to be written
    bool isDataEmpty() const;                  ///< True if only file header written, data is still empty
    bool isFull() const;                       ///< True if all possible dblks have been submitted (but may not yet have returned from AIO)
    bool isFullAndComplete() const;            ///< True if all submitted dblks have returned from AIO
};

}}}

#endif // QPID_LINEARSTORE_JOURNAL_JOURNALFILE_H_
