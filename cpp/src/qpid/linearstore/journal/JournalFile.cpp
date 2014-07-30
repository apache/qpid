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

#include "qpid/linearstore/journal/JournalFile.h"

#include <fcntl.h>
#include "qpid/linearstore/journal/jcfg.h"
#include "qpid/linearstore/journal/pmgr.h"
#include "qpid/linearstore/journal/utils/file_hdr.h"
#include <unistd.h>

namespace qpid {
namespace linearstore {
namespace journal {

JournalFile::JournalFile(const std::string& fqFileName,
                         const efpIdentity_t& efpIdentity,
                         const uint64_t fileSeqNum,
                         const std::string queueName) :
            efpIdentity_(efpIdentity),
            fqFileName_(fqFileName),
            fileSeqNum_(fileSeqNum),
            queueName_(queueName),
            serial_(getRandom64()),
            firstRecordOffset_(0ULL),
            fileHandle_(-1),
            fileCloseFlag_(false),
            fileHeaderBasePtr_ (0),
            fileHeaderPtr_(0),
            aioControlBlockPtr_(0),
            fileSize_dblks_(((efpIdentity.ds_ * 1024) + (QLS_JRNL_FHDR_RES_SIZE_SBLKS * QLS_SBLK_SIZE_BYTES)) / QLS_DBLK_SIZE_BYTES),
            initializedFlag_(false),
            enqueuedRecordCount_("JournalFile::enqueuedRecordCount", 0),
            submittedDblkCount_("JournalFile::submittedDblkCount", 0),
            completedDblkCount_("JournalFile::completedDblkCount", 0),
            outstandingAioOpsCount_("JournalFile::outstandingAioOpsCount", 0)
{}

JournalFile::JournalFile(const std::string& fqFileName,
                         const ::file_hdr_t& fileHeader,
                         const std::string queueName) :
            efpIdentity_(fileHeader._efp_partition, fileHeader._data_size_kib),
            fqFileName_(fqFileName),
            fileSeqNum_(fileHeader._file_number),
            queueName_(queueName),
            serial_(fileHeader._rhdr._serial),
            firstRecordOffset_(fileHeader._fro),
            fileHandle_(-1),
            fileCloseFlag_(false),
            fileHeaderBasePtr_ (0),
            fileHeaderPtr_(0),
            aioControlBlockPtr_(0),
            fileSize_dblks_(((fileHeader._data_size_kib * 1024) + (QLS_JRNL_FHDR_RES_SIZE_SBLKS * QLS_SBLK_SIZE_BYTES)) / QLS_DBLK_SIZE_BYTES),
            initializedFlag_(false),
            enqueuedRecordCount_("JournalFile::enqueuedRecordCount", 0),
            submittedDblkCount_("JournalFile::submittedDblkCount", 0),
            completedDblkCount_("JournalFile::completedDblkCount", 0),
            outstandingAioOpsCount_("JournalFile::outstandingAioOpsCount", 0)
{}

JournalFile::~JournalFile() {
    finalize();
}

void
JournalFile::initialize(const uint32_t completedDblkCount) {
    if (!initializedFlag_) {
        if (::posix_memalign(&fileHeaderBasePtr_, QLS_AIO_ALIGN_BOUNDARY_BYTES, QLS_JRNL_FHDR_RES_SIZE_SBLKS * QLS_SBLK_SIZE_KIB * 1024))
        {
            std::ostringstream oss;
            oss << "posix_memalign(): blksize=" << QLS_AIO_ALIGN_BOUNDARY_BYTES << " size=" << (QLS_JRNL_FHDR_RES_SIZE_SBLKS * QLS_SBLK_SIZE_KIB * 1024);
            oss << FORMAT_SYSERR(errno);
            throw jexception(jerrno::JERR__MALLOC, oss.str(), "JournalFile", "initialize");
        }
        fileHeaderPtr_ = (::file_hdr_t*)fileHeaderBasePtr_;
        aioControlBlockPtr_ = new aio_cb;
        initializedFlag_ = true;
    }
    if (completedDblkCount > 0UL) {
        submittedDblkCount_.set(completedDblkCount);
        completedDblkCount_.set(completedDblkCount);
    }
}

void
JournalFile::finalize() {
    if (fileHeaderBasePtr_ != 0) {
        std::free(fileHeaderBasePtr_);
        fileHeaderBasePtr_ = 0;
        fileHeaderPtr_ = 0;
    }
    if (aioControlBlockPtr_ != 0) {
        delete(aioControlBlockPtr_);
        aioControlBlockPtr_ = 0;
    }
}

const std::string JournalFile::getFqFileName() const {
    return fqFileName_;
}

uint64_t JournalFile::getFileSeqNum() const {
    return fileSeqNum_;
}

uint64_t JournalFile::getSerial() const {
    return serial_;
}

int JournalFile::open() {
    fileHandle_ = ::open(fqFileName_.c_str(), O_WRONLY | O_DIRECT, S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH); // 0644 -rw-r--r--
    if (fileHandle_ < 0) {
        std::ostringstream oss;
        oss << "file=\"" << fqFileName_ << "\"" << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_JNLF_OPEN, oss.str(), "JournalFile", "open");
    }
    return fileHandle_;
}

void JournalFile::close() {
    if (fileHandle_ >= 0) {
        if (getOutstandingAioDblks()) {
            fileCloseFlag_ = true; // Close later when all outstanding AIOs have returned
        } else {
            int res = ::close(fileHandle_);
            fileHandle_ = -1;
            if (res != 0) {
                std::ostringstream oss;
                oss << "file=\"" << fqFileName_ << "\"" << FORMAT_SYSERR(errno);
                throw jexception(jerrno::JERR_JNLF_CLOSE, oss.str(), "JournalFile", "open");
            }
        }
    }
}

void JournalFile::asyncFileHeaderWrite(io_context_t ioContextPtr,
                                       const efpPartitionNumber_t efpPartitionNumber,
                                       const efpDataSize_kib_t efpDataSize_kib,
                                       const uint16_t userFlags,
                                       const uint64_t recordId,
                                       const uint64_t firstRecordOffset) {
    firstRecordOffset_ = firstRecordOffset;
    ::file_hdr_create(fileHeaderPtr_, QLS_FILE_MAGIC, QLS_JRNL_VERSION, QLS_JRNL_FHDR_RES_SIZE_SBLKS, efpPartitionNumber, efpDataSize_kib);
    ::file_hdr_init(fileHeaderBasePtr_,
                    QLS_JRNL_FHDR_RES_SIZE_SBLKS * QLS_SBLK_SIZE_KIB * 1024,
                    userFlags,
                    serial_,
                    recordId,
                    firstRecordOffset,
                    fileSeqNum_,
                    queueName_.size(),
                    queueName_.data());
    const std::size_t wr_size = QLS_JRNL_FHDR_RES_SIZE_SBLKS * QLS_SBLK_SIZE_KIB * 1024;
    if (!isOpen()) open();
    aio::prep_pwrite(aioControlBlockPtr_, fileHandle_, (void*)fileHeaderBasePtr_, wr_size, 0UL);
    if (!aio::is_aligned(aioControlBlockPtr_->u.c.buf, QLS_AIO_ALIGN_BOUNDARY_BYTES)) {
        std::ostringstream oss;
        oss << "AIO operation on misaligned buffer: iocb->u.c.buf=" << aioControlBlockPtr_->u.c.buf << std::endl;
        throw jexception(jerrno::JERR__AIO, oss.str(), "JournalFile", "asyncFileHeaderWrite");
    }
    if (aio::submit(ioContextPtr, 1, &aioControlBlockPtr_) < 0) {
        std::ostringstream oss;
        oss << "queue=\"" << queueName_ << "\" fid=0x" << std::hex <<  fileSeqNum_ << " wr_size=0x" << wr_size << " foffs=0x0";
        throw jexception(jerrno::JERR__AIO, oss.str(), "JournalFile", "asyncFileHeaderWrite");
    }
    addSubmittedDblkCount(QLS_JRNL_FHDR_RES_SIZE_SBLKS * QLS_SBLK_SIZE_DBLKS);
    incrOutstandingAioOperationCount();
}

void JournalFile::asyncPageWrite(io_context_t ioContextPtr,
                                 aio_cb* aioControlBlockPtr,
                                 void* data,
                                 uint32_t dataSize_dblks) {
    const std::size_t wr_size = dataSize_dblks * QLS_DBLK_SIZE_BYTES;
    const uint64_t foffs = submittedDblkCount_.get() * QLS_DBLK_SIZE_BYTES;
    if (!isOpen()) open();
    aio::prep_pwrite_2(aioControlBlockPtr, fileHandle_, data, wr_size, foffs);
    if (!aio::is_aligned(aioControlBlockPtr->u.c.buf, QLS_AIO_ALIGN_BOUNDARY_BYTES)) {
        std::ostringstream oss;
        oss << "AIO operation on misaligned buffer: iocb->u.c.buf=" << aioControlBlockPtr->u.c.buf << std::endl;
        throw jexception(jerrno::JERR__AIO, oss.str(), "JournalFile", "asyncPageWrite");
    }
    pmgr::page_cb* pcbp = (pmgr::page_cb*)(aioControlBlockPtr->data); // This page's control block (pcb)
    pcbp->_wdblks = dataSize_dblks;
    pcbp->_jfp = this;
    if (aio::submit(ioContextPtr, 1, &aioControlBlockPtr) < 0) {
        std::ostringstream oss;
        oss << "queue=\"" << queueName_ << "\" fid=0x" << std::hex <<  fileSeqNum_ << " wr_size=0x" << wr_size << " foffs=0x" << foffs;
        throw jexception(jerrno::JERR__AIO, oss.str(), "JournalFile", "asyncPageWrite");
    }
    addSubmittedDblkCount(dataSize_dblks);
    incrOutstandingAioOperationCount();
}

uint32_t JournalFile::getEnqueuedRecordCount() const {
    return enqueuedRecordCount_.get();
}

uint32_t JournalFile::incrEnqueuedRecordCount() {
    return enqueuedRecordCount_.increment();
}

uint32_t JournalFile::decrEnqueuedRecordCount() {
    return enqueuedRecordCount_.decrementLimit();
}

uint32_t JournalFile::addCompletedDblkCount(const uint32_t a) {
    return completedDblkCount_.addLimit(a, submittedDblkCount_.get(), jerrno::JERR_JNLF_CMPLOFFSOVFL);
}

uint16_t JournalFile::getOutstandingAioOperationCount() const {
    return outstandingAioOpsCount_.get();
}

uint16_t JournalFile::decrOutstandingAioOperationCount() {
    uint16_t r = outstandingAioOpsCount_.decrementLimit();
    if (fileCloseFlag_ && outstandingAioOpsCount_ == 0) { // Delayed close
        close();
    }
    return r;
}

efpIdentity_t JournalFile::getEfpIdentity() const {
    return efpIdentity_;
}

uint64_t JournalFile::getFirstRecordOffset() const {
    return firstRecordOffset_;
}

void JournalFile::setFirstRecordOffset(const uint64_t firstRecordOffset) {
    firstRecordOffset_ = firstRecordOffset;
}

// --- Status helper functions ---

bool JournalFile::isEmpty() const {
    return submittedDblkCount_ == 0;
}

bool JournalFile::isNoEnqueuedRecordsRemaining() const {
    return /*!enqueueStarted_ &&*/          // Not part-way through encoding an enqueue
           isFullAndComplete() &&       // Full with all AIO returned
           enqueuedRecordCount_ == 0;   // No remaining enqueued records
}

// debug aid
const std::string JournalFile::status_str(const uint8_t indentDepth) const {
    std::string indent((size_t)indentDepth, '.');
    std::ostringstream oss;
    oss << indent << "JournalFile: fileName=" << getFileName() << std::endl;
    oss << indent << "  directory=" << getDirectory() << std::endl;
    oss << indent << "  fileSizeDblks=" << fileSize_dblks_ << std::endl;
    oss << indent << "  open=" << (isOpen() ? "T" : "F") << std::endl;
    oss << indent << "  fileHandle=" << fileHandle_ << std::endl;
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

// --- protected functions ---

const std::string JournalFile::getDirectory() const {
    return fqFileName_.substr(0, fqFileName_.rfind('/'));
}

const std::string JournalFile::getFileName() const {
    return fqFileName_.substr(fqFileName_.rfind('/')+1);
}

//static
uint64_t JournalFile::getRandom64() {
    // TODO: ::rand() is not thread safe, either lock or use rand_r(seed) with a thread-local seed.
    return ((uint64_t)::rand() << QLS_RAND_SHIFT1) | ((uint64_t)::rand() << QLS_RAND_SHIFT2) | (::rand() & QLS_RAND_MASK);
}

bool JournalFile::isOpen() const {
    return fileHandle_ >= 0;
}

uint32_t JournalFile::getSubmittedDblkCount() const {
    return submittedDblkCount_.get();
}

uint32_t JournalFile::addSubmittedDblkCount(const uint32_t a) {
    return submittedDblkCount_.addLimit(a, fileSize_dblks_, jerrno::JERR_JNLF_FILEOFFSOVFL);
}

uint32_t JournalFile::getCompletedDblkCount() const {
    return completedDblkCount_.get();
}

uint16_t JournalFile::incrOutstandingAioOperationCount() {
    return outstandingAioOpsCount_.increment();
}

u_int32_t JournalFile::dblksRemaining() const {
    return fileSize_dblks_ - submittedDblkCount_;
}

bool JournalFile::getNextFile() const {
    return isFull();
}

u_int32_t JournalFile::getOutstandingAioDblks() const {
    return submittedDblkCount_ - completedDblkCount_;
}

bool JournalFile::isDataEmpty() const {
    return submittedDblkCount_ <= QLS_JRNL_FHDR_RES_SIZE_SBLKS * QLS_SBLK_SIZE_DBLKS;
}

bool JournalFile::isFull() const {
    return submittedDblkCount_ == fileSize_dblks_;
}

bool JournalFile::isFullAndComplete() const {
    return completedDblkCount_ == fileSize_dblks_;
}


}}}
