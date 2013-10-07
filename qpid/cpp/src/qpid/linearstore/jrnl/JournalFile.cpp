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

#include "qpid/linearstore/jrnl/JournalFile.h"

#include <fcntl.h>
#include "qpid/linearstore/jrnl/jcfg.h"
#include "qpid/linearstore/jrnl/jexception.h"
#include "qpid/linearstore/jrnl/pmgr.h"
#include "qpid/linearstore/jrnl/utils/file_hdr.h"
#include <unistd.h>

namespace qpid {
namespace qls_jrnl {

JournalFile::JournalFile(const std::string& fqFileName_,
                         const uint64_t fileSeqNum_,
                         const uint32_t fileSize_kib_) :
            fqFileName(fqFileName_),
            fileSeqNum(fileSeqNum_),
            fileHandle(-1),
            fileCloseFlag(false),
            fileHeaderBasePtr (0),
            fileHeaderPtr(0),
            aioControlBlockPtr(0),
            fileSizeDblks(((fileSize_kib_ * 1024) + (QLS_JRNL_FHDR_RES_SIZE_SBLKS * JRNL_SBLK_SIZE_BYTES)) / JRNL_DBLK_SIZE_BYTES),
            enqueuedRecordCount(0),
            submittedDblkCount(0),
            completedDblkCount(0),
            outstandingAioOpsCount(0)
{}

JournalFile::~JournalFile() {
    finalize();
}

void
JournalFile::initialize() {
    if (::posix_memalign(&fileHeaderBasePtr, QLS_AIO_ALIGN_BOUNDARY, QLS_JRNL_FHDR_RES_SIZE_SBLKS * JRNL_SBLK_SIZE_KIB * 1024))
    {
        std::ostringstream oss;
        oss << "posix_memalign(): blksize=" << QLS_AIO_ALIGN_BOUNDARY << " size=" << (QLS_JRNL_FHDR_RES_SIZE_SBLKS * JRNL_SBLK_SIZE_KIB * 1024);
        oss << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR__MALLOC, oss.str(), "JournalFile", "initialize");
    }
    fileHeaderPtr = (::file_hdr_t*)fileHeaderBasePtr;
    aioControlBlockPtr = new aio_cb;
}

void
JournalFile::finalize() {
    if (fileHeaderBasePtr != 0) {
        std::free(fileHeaderBasePtr);
        fileHeaderBasePtr = 0;
        fileHeaderPtr = 0;
    }
    if (aioControlBlockPtr != 0) {
        std::free(aioControlBlockPtr);
        aioControlBlockPtr = 0;
    }
}

const std::string
JournalFile::getDirectory() const {
    return fqFileName.substr(0, fqFileName.rfind('/'));
}

const std::string
JournalFile::getFileName() const {
    return fqFileName.substr(fqFileName.rfind('/')+1);
}

const std::string
JournalFile::getFqFileName() const {
    return fqFileName;
}

uint64_t
JournalFile::getFileSeqNum() const {
    return fileSeqNum;
}

int
JournalFile::open() {
    fileHandle = ::open(fqFileName.c_str(), O_WRONLY | O_DIRECT, S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH); // 0644 -rw-r--r--
    if (fileHandle < 0) {
        std::ostringstream oss;
        oss << "file=\"" << fqFileName << "\"" << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_JNLF_OPEN, oss.str(), "JournalFile", "open");
    }
    return fileHandle;
}

bool
JournalFile::isOpen() const {
    return fileHandle >= 0;
}

void
JournalFile::close() {
    if (fileHandle >= 0) {
        if (getOutstandingAioDblks()) {
            fileCloseFlag = true; // Close later when all outstanding AIOs have returned
        } else {
            int res = ::close(fileHandle);
            fileHandle = -1;
            if (res != 0) {
                std::ostringstream oss;
                oss << "file=\"" << fqFileName << "\"" << FORMAT_SYSERR(errno);
                throw jexception(jerrno::JERR_JNLF_CLOSE, oss.str(), "JournalFile", "open");
            }
        }
    }
}

void
JournalFile::asyncFileHeaderWrite(io_context_t ioContextPtr_,
                                  const efpPartitionNumber_t efpPartitionNumber_,
                                  const efpDataSize_kib_t efpDataSize_kib_,
                                  const uint16_t userFlags_,
                                  const uint64_t recordId_,
                                  const uint64_t firstRecordOffset_,
                                  const std::string queueName_) {
    ::file_hdr_create(fileHeaderPtr, QLS_FILE_MAGIC, QLS_JRNL_VERSION, QLS_JRNL_FHDR_RES_SIZE_SBLKS, efpPartitionNumber_, efpDataSize_kib_);
    ::file_hdr_init(fileHeaderBasePtr, QLS_JRNL_FHDR_RES_SIZE_SBLKS * JRNL_SBLK_SIZE_KIB * 1024, userFlags_, recordId_, firstRecordOffset_, fileSeqNum, queueName_.size(), queueName_.data());
    aio::prep_pwrite(aioControlBlockPtr, fileHandle, (void*)fileHeaderBasePtr, QLS_JRNL_FHDR_RES_SIZE_SBLKS * JRNL_SBLK_SIZE_KIB * 1024, 0UL);
    if (aio::submit(ioContextPtr_, 1, &aioControlBlockPtr) < 0)
        throw jexception(jerrno::JERR__AIO, "JournalFile", "asyncPageWrite");
    addSubmittedDblkCount(QLS_JRNL_FHDR_RES_SIZE_SBLKS * JRNL_SBLK_SIZE_DBLKS);
    incrOutstandingAioOperationCount();
}

void
JournalFile::asyncPageWrite(io_context_t ioContextPtr_,
                            aio_cb* aioControlBlockPtr_,
                            void* data_,
                            uint32_t dataSize_dblks_) {
    aio::prep_pwrite_2(aioControlBlockPtr_, fileHandle, data_, dataSize_dblks_ * JRNL_DBLK_SIZE_BYTES, submittedDblkCount.get() * JRNL_DBLK_SIZE_BYTES);
    pmgr::page_cb* pcbp = (pmgr::page_cb*)(aioControlBlockPtr_->data); // This page's control block (pcb)
    pcbp->_wdblks = dataSize_dblks_;
    pcbp->_jfp = this;
    if (aio::submit(ioContextPtr_, 1, &aioControlBlockPtr_) < 0)
        throw jexception(jerrno::JERR__AIO, "JournalFile", "asyncPageWrite");
    addSubmittedDblkCount(dataSize_dblks_);
    incrOutstandingAioOperationCount();
}

uint32_t
JournalFile::getEnqueuedRecordCount() const {
    return enqueuedRecordCount.get();
}

uint32_t
JournalFile::incrEnqueuedRecordCount() {
    return enqueuedRecordCount.increment();
}

uint32_t
JournalFile::addEnqueuedRecordCount(const uint32_t a) {
    return enqueuedRecordCount.add(a);
}

uint32_t
JournalFile::decrEnqueuedRecordCount() {
    return enqueuedRecordCount.decrementLimit();
}

uint32_t
JournalFile::subtrEnqueuedRecordCount(const uint32_t s) {
    return enqueuedRecordCount.subtractLimit(s);
}

uint32_t
JournalFile::getSubmittedDblkCount() const {
    return submittedDblkCount.get();
}

uint32_t
JournalFile::addSubmittedDblkCount(const uint32_t a) {
    return submittedDblkCount.addLimit(a, fileSizeDblks, jerrno::JERR_JNLF_FILEOFFSOVFL);
}

uint32_t
JournalFile::getCompletedDblkCount() const {
    return completedDblkCount.get();
}

uint32_t
JournalFile::addCompletedDblkCount(const uint32_t a) {
    return completedDblkCount.addLimit(a, submittedDblkCount.get(), jerrno::JERR_JNLF_CMPLOFFSOVFL);
}

uint16_t JournalFile::getOutstandingAioOperationCount() const {
    return outstandingAioOpsCount.get();
}

uint16_t JournalFile::incrOutstandingAioOperationCount() {
    return outstandingAioOpsCount.increment();
}

uint16_t JournalFile::decrOutstandingAioOperationCount() {
    uint16_t r = outstandingAioOpsCount.decrementLimit();
    if (fileCloseFlag && outstandingAioOpsCount == 0) { // Delayed close
        close();
    }
    return r;
}

bool
JournalFile::isEmpty() const {
    return submittedDblkCount == 0;
}

bool
JournalFile::isDataEmpty() const {
    return submittedDblkCount <= QLS_JRNL_FHDR_RES_SIZE_SBLKS * JRNL_SBLK_SIZE_DBLKS;
}

u_int32_t
JournalFile::dblksRemaining() const {
    return fileSizeDblks - submittedDblkCount;
}

bool
JournalFile::isFull() const {
    return submittedDblkCount == fileSizeDblks;
}

bool
JournalFile::isFullAndComplete() const {
    return completedDblkCount == fileSizeDblks;
}

u_int32_t
JournalFile::getOutstandingAioDblks() const {
    return submittedDblkCount - completedDblkCount;
}

bool
JournalFile::getNextFile() const {
    return isFull();
}

bool
JournalFile::isNoEnqueuedRecordsRemaining() const {
    return !isDataEmpty() &&          // Must be written to, not empty
           enqueuedRecordCount == 0;  // No remaining enqueued records
}

const std::string
JournalFile::status_str(const uint8_t indentDepth_) const {
    std::string indent((size_t)indentDepth_, '.');
    std::ostringstream oss;
    oss << indent << "JournalFile: fileName=" << getFileName() << std::endl;
    oss << indent << "  directory=" << getDirectory() << std::endl;
    oss << indent << "  fileSizeDblks=" << fileSizeDblks << std::endl;
    oss << indent << "  open=" << (isOpen() ? "T" : "F") << std::endl;
    oss << indent << "  fileHandle=" << fileHandle << std::endl;
    oss << indent << "  enqueuedRecordCount=" << getEnqueuedRecordCount() << std::endl;
    oss << indent << "  submittedDblkCount=" << getSubmittedDblkCount() << std::endl;
    oss << indent << "  completedDblkCount=" << getCompletedDblkCount() << std::endl;
    oss << indent << "  outstandingAioOpsCount=" << getOutstandingAioOperationCount() << std::endl;
    oss << indent << "  isEmpty()=" << (isEmpty() ? "T" : "F") << std::endl;
    oss << indent << "  isDataEmpty()=" << (isDataEmpty() ? "T" : "F") << std::endl;
    oss << indent << "  dblksRemaining()=" << dblksRemaining() << std::endl;
    oss << indent << "  isFull()=" << (isFull() ? "T" : "F") << std::endl;
    oss << indent << "  isFullAndComplete()=" << (isFullAndComplete() ? "T" : "F") << std::endl;
    oss << indent << "  getOutstandingAioDblks()=" << getOutstandingAioDblks() << std::endl;
    oss << indent << "  getNextFile()=" << (getNextFile() ? "T" : "F") << std::endl;
    return oss.str();
}

}} // namespace qpid::qls_jrnl
