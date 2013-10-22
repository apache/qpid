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

#include "qpid/linearstore/jrnl/LinearFileController.h"

#include <fstream>
#include "qpid/linearstore/jrnl/EmptyFilePool.h"
#include "qpid/linearstore/jrnl/jcfg.h"
#include "qpid/linearstore/jrnl/jcntl.h"
#include "qpid/linearstore/jrnl/JournalFile.h"
#include "qpid/linearstore/jrnl/slock.h"
#include "qpid/linearstore/jrnl/utils/file_hdr.h"

namespace qpid {
namespace qls_jrnl {

LinearFileController::LinearFileController(jcntl& jcntlRef) :
            jcntlRef_(jcntlRef),
            emptyFilePoolPtr_(0),
            currentJournalFilePtr_(0),
            fileSeqCounter_(0),
            recordIdCounter_(0)
{}

LinearFileController::~LinearFileController() {}

void LinearFileController::initialize(const std::string& journalDirectory,
                                      EmptyFilePool* emptyFilePoolPtr,
                                      uint64_t initialFileNumberVal) {
    journalDirectory_.assign(journalDirectory);
    emptyFilePoolPtr_ = emptyFilePoolPtr;
    fileSeqCounter_ = initialFileNumberVal;
}

void LinearFileController::finalize() {
    while (!journalFileList_.empty()) {
        delete journalFileList_.front();
        journalFileList_.pop_front();
    }
}

void LinearFileController::addJournalFile(const std::string& fileName,
                                          const uint64_t fileNumber,
                                          const uint32_t fileSize_kib,
                                          const uint32_t completedDblkCount) {
    if (currentJournalFilePtr_)
        currentJournalFilePtr_->close();
    currentJournalFilePtr_ = new JournalFile(fileName, fileNumber, fileSize_kib);
    currentJournalFilePtr_->initialize(completedDblkCount);
    {
        slock l(journalFileListMutex_);
        journalFileList_.push_back(currentJournalFilePtr_);
    }
    currentJournalFilePtr_->open();
}

efpDataSize_kib_t LinearFileController::dataSize_kib() const {
    return emptyFilePoolPtr_->dataSize_kib();
}

efpDataSize_sblks_t LinearFileController::dataSize_sblks() const {
    return emptyFilePoolPtr_->dataSize_sblks();
}

efpFileSize_kib_t LinearFileController::fileSize_kib() const {
    return emptyFilePoolPtr_->fileSize_kib();
}

efpFileSize_sblks_t LinearFileController::fileSize_sblks() const {
    return emptyFilePoolPtr_->fileSize_sblks();
}

uint64_t LinearFileController::getNextRecordId() {
    return recordIdCounter_.increment();
}

void LinearFileController::pullEmptyFileFromEfp() {
    if (currentJournalFilePtr_)
        currentJournalFilePtr_->close();
    std::string ef = emptyFilePoolPtr_->takeEmptyFile(journalDirectory_); // Moves file from EFP only, returns new file name
//std::cout << "*** LinearFileController::pullEmptyFileFromEfp() qn=" << jcntlRef.id() << " ef=" << ef << std::endl; // DEBUG
    addJournalFile(ef, getNextFileSeqNum(), emptyFilePoolPtr_->dataSize_kib(), 0);
}

void LinearFileController::purgeFilesToEfp() {
    slock l(journalFileListMutex_);
    while (journalFileList_.front()->isNoEnqueuedRecordsRemaining()) {
        emptyFilePoolPtr_->returnEmptyFile(journalFileList_.front()->getFqFileName());
        delete journalFileList_.front();
        journalFileList_.pop_front();
    }
}

uint32_t LinearFileController::getEnqueuedRecordCount(const efpFileCount_t fileSeqNumber) {
    slock l(journalFileListMutex_);
    return find(fileSeqNumber)->getEnqueuedRecordCount();
}

uint32_t LinearFileController::incrEnqueuedRecordCount(const efpFileCount_t fileSeqNumber) {
    assertCurrentJournalFileValid("incrEnqueuedRecordCount");
    return find(fileSeqNumber)->incrEnqueuedRecordCount();
}

uint32_t LinearFileController::decrEnqueuedRecordCount(const efpFileCount_t fileSeqNumber) {
    slock l(journalFileListMutex_);
    return find(fileSeqNumber)->decrEnqueuedRecordCount();
}

uint32_t LinearFileController::addWriteCompletedDblkCount(const efpFileCount_t fileSeqNumber, const uint32_t a) {
    slock l(journalFileListMutex_);
    return find(fileSeqNumber)->addCompletedDblkCount(a);
}

uint16_t LinearFileController::decrOutstandingAioOperationCount(const efpFileCount_t fileSeqNumber) {
    slock l(journalFileListMutex_);
    return find(fileSeqNumber)->decrOutstandingAioOperationCount();
}

void LinearFileController::asyncFileHeaderWrite(io_context_t ioContextPtr,
                                                const uint16_t userFlags,
                                                const uint64_t recordId,
                                                const uint64_t firstRecordOffset) {
    currentJournalFilePtr_->asyncFileHeaderWrite(ioContextPtr,
                                                 emptyFilePoolPtr_->getPartitionNumber(),
                                                 emptyFilePoolPtr_->dataSize_kib(),
                                                 userFlags,
                                                 recordId,
                                                 firstRecordOffset,
                                                 jcntlRef_.id());
}

void LinearFileController::asyncPageWrite(io_context_t ioContextPtr,
                                          aio_cb* aioControlBlockPtr,
                                          void* data,
                                          uint32_t dataSize_dblks) {
    assertCurrentJournalFileValid("asyncPageWrite");
    currentJournalFilePtr_->asyncPageWrite(ioContextPtr, aioControlBlockPtr, data, dataSize_dblks);
}

uint64_t LinearFileController::getCurrentFileSeqNum() const {
    assertCurrentJournalFileValid("getCurrentFileSeqNum");
    return currentJournalFilePtr_->getFileSeqNum();
}

uint32_t LinearFileController::getEnqueuedRecordCount() const {
    assertCurrentJournalFileValid("getEnqueuedRecordCount");
    return currentJournalFilePtr_->getEnqueuedRecordCount();
}

uint32_t LinearFileController::incrEnqueuedRecordCount() {
    assertCurrentJournalFileValid("incrEnqueuedRecordCount");
    return currentJournalFilePtr_->incrEnqueuedRecordCount();
}

uint32_t LinearFileController::addEnqueuedRecordCount(const uint32_t a) {
    assertCurrentJournalFileValid("addEnqueuedRecordCount");
    return currentJournalFilePtr_->addEnqueuedRecordCount(a);
}

uint32_t LinearFileController::decrEnqueuedRecordCount() {
    assertCurrentJournalFileValid("decrEnqueuedRecordCount");
    return currentJournalFilePtr_->decrEnqueuedRecordCount();
}

uint32_t LinearFileController::subtrEnqueuedRecordCount(const uint32_t s) {
    assertCurrentJournalFileValid("subtrEnqueuedRecordCount");
    return currentJournalFilePtr_->subtrEnqueuedRecordCount(s);
}

uint32_t LinearFileController::getWriteSubmittedDblkCount() const {
    assertCurrentJournalFileValid("getWriteSubmittedDblkCount");
    return currentJournalFilePtr_->getSubmittedDblkCount();
}

uint32_t LinearFileController::addWriteSubmittedDblkCount(const uint32_t a) {
    assertCurrentJournalFileValid("addWriteSubmittedDblkCount");
    return currentJournalFilePtr_->addSubmittedDblkCount(a);
}

uint32_t LinearFileController::getWriteCompletedDblkCount() const {
    assertCurrentJournalFileValid("getWriteCompletedDblkCount");
    return currentJournalFilePtr_->getCompletedDblkCount();
}

uint32_t LinearFileController::addWriteCompletedDblkCount(const uint32_t a) {
    assertCurrentJournalFileValid("addWriteCompletedDblkCount");
    return currentJournalFilePtr_->addCompletedDblkCount(a);
}

uint16_t LinearFileController::getOutstandingAioOperationCount() const {
    assertCurrentJournalFileValid("getOutstandingAioOperationCount");
    return currentJournalFilePtr_->getOutstandingAioOperationCount();
}

uint16_t LinearFileController::incrOutstandingAioOperationCount() {
    assertCurrentJournalFileValid("incrOutstandingAioOperationCount");
    return currentJournalFilePtr_->incrOutstandingAioOperationCount();
}

uint16_t LinearFileController::decrOutstandingAioOperationCount() {
    assertCurrentJournalFileValid("decrOutstandingAioOperationCount");
    return currentJournalFilePtr_->decrOutstandingAioOperationCount();
}

bool LinearFileController::isEmpty() const {
    assertCurrentJournalFileValid("isEmpty");
    return currentJournalFilePtr_->isEmpty();
}

bool LinearFileController::isDataEmpty() const {
    assertCurrentJournalFileValid("isDataEmpty");
    return currentJournalFilePtr_->isDataEmpty();
}

u_int32_t LinearFileController::dblksRemaining() const {
    assertCurrentJournalFileValid("dblksRemaining");
    return currentJournalFilePtr_->dblksRemaining();
}

bool LinearFileController::isFull() const {
    assertCurrentJournalFileValid("isFull");
    return currentJournalFilePtr_->isFull();
}

bool LinearFileController::isFullAndComplete() const {
    assertCurrentJournalFileValid("isFullAndComplete");
    return currentJournalFilePtr_->isFullAndComplete();
}

u_int32_t LinearFileController::getOutstandingAioDblks() const {
    assertCurrentJournalFileValid("getOutstandingAioDblks");
    return currentJournalFilePtr_->getOutstandingAioDblks();
}

bool LinearFileController::needNextFile() const {
    assertCurrentJournalFileValid("getNextFile");
    return currentJournalFilePtr_->getNextFile();
}

const std::string LinearFileController::status(const uint8_t indentDepth) const {
    std::string indent((size_t)indentDepth, '.');
    std::ostringstream oss;
    oss << indent << "LinearFileController: queue=" << jcntlRef_.id() << std::endl;
    oss << indent << "  journalDirectory=" << journalDirectory_ << std::endl;
    oss << indent << "  fileSeqCounter=" << fileSeqCounter_.get() << std::endl;
    oss << indent << "  recordIdCounter=" << recordIdCounter_.get() << std::endl;
    oss << indent << "  journalFileList.size=" << journalFileList_.size() << std::endl;
    if (checkCurrentJournalFileValid()) {
        oss << currentJournalFilePtr_->status_str(indentDepth+2);
    } else {
        oss << indent << "  <No current journal file>" << std::endl;
    }
    return oss.str();
}

// --- protected functions ---

bool LinearFileController::checkCurrentJournalFileValid() const {
    return currentJournalFilePtr_ != 0;
}

void LinearFileController::assertCurrentJournalFileValid(const char* const functionName) const {
    if (!checkCurrentJournalFileValid()) {
        throw jexception(jerrno::JERR__NULL, "LinearFileController", functionName);
    }
}

// NOTE: NOT THREAD SAFE - journalFileList is accessed by multiple threads - use under external lock
JournalFile* LinearFileController::find(const efpFileCount_t fileSeqNumber) {
    if (currentJournalFilePtr_ != 0 && currentJournalFilePtr_->getFileSeqNum() == fileSeqNumber)
        return currentJournalFilePtr_;
    for (JournalFileListItr_t i=journalFileList_.begin(); i!=journalFileList_.end(); ++i) {
        if ((*i)->getFileSeqNum() == fileSeqNumber) {
            return *i;
        }
    }
    std::ostringstream oss;
    oss << "fileSeqNumber=" << fileSeqNumber;
    throw jexception(jerrno::JERR_LFCR_SEQNUMNOTFOUND, oss.str(), "LinearFileController", "find");
}

uint64_t LinearFileController::getNextFileSeqNum() {
    return fileSeqCounter_.increment();
}

}} // namespace qpid::qls_jrnl
