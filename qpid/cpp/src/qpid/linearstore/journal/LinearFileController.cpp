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

#include "qpid/linearstore/journal/LinearFileController.h"

#include "qpid/linearstore/journal/EmptyFilePool.h"
#include "qpid/linearstore/journal/jcntl.h"
#include "qpid/linearstore/journal/JournalFile.h"

namespace qpid {
namespace linearstore {
namespace journal {

LinearFileController::LinearFileController(jcntl& jcntlRef) :
            jcntlRef_(jcntlRef),
            emptyFilePoolPtr_(0),
            fileSeqCounter_("LinearFileController::fileSeqCounter", 0),
            recordIdCounter_("LinearFileController::recordIdCounter", 0),
            decrCounter_("LinearFileController::decrCounter", 0),
            currentJournalFilePtr_(0)
{}

LinearFileController::~LinearFileController() {}

void LinearFileController::initialize(const std::string& journalDirectory,
                                      EmptyFilePool* emptyFilePoolPtr,
                                      uint64_t initialFileNumberVal) {
    journalDirectory_.assign(journalDirectory);
    emptyFilePoolPtr_ = emptyFilePoolPtr;
    fileSeqCounter_.set(initialFileNumberVal);
}

void LinearFileController::finalize() {
    if (currentJournalFilePtr_) {
        currentJournalFilePtr_->close();
        currentJournalFilePtr_ = 0;
    }
    while (!journalFileList_.empty()) {
        delete journalFileList_.front();
        journalFileList_.pop_front();
    }
}

void LinearFileController::addJournalFile(JournalFile* journalFilePtr,
                                          const uint32_t completedDblkCount,
                                          const bool makeCurrentFlag) {
    if (makeCurrentFlag && currentJournalFilePtr_) {
        currentJournalFilePtr_->close();
        currentJournalFilePtr_ = 0;
    }
    journalFilePtr->initialize(completedDblkCount);
    {
        slock l(journalFileListMutex_);
        journalFileList_.push_back(journalFilePtr);
    }
    if (makeCurrentFlag) {
        currentJournalFilePtr_ = journalFilePtr;
    }
}

efpDataSize_sblks_t LinearFileController::dataSize_sblks() const {
    return emptyFilePoolPtr_->dataSize_sblks();
}

efpFileSize_sblks_t LinearFileController::fileSize_sblks() const {
    return emptyFilePoolPtr_->fileSize_sblks();
}

void LinearFileController::getNextJournalFile() {
    if (currentJournalFilePtr_)
        currentJournalFilePtr_->close();
    pullEmptyFileFromEfp();
}

uint64_t LinearFileController::getNextRecordId() {
    return recordIdCounter_.increment();
}

void LinearFileController::removeFileToEfp(const std::string& fileName) {
    if (emptyFilePoolPtr_) {
        emptyFilePoolPtr_->returnEmptyFileSymlink(fileName);
    }
}

void LinearFileController::restoreEmptyFile(const std::string& fileName) {
    // TODO: Add checks that this file is of a valid size; if not, delete this and get one from the EFP
    addJournalFile(fileName, emptyFilePoolPtr_->getIdentity(), getNextFileSeqNum(), 0);
}

void LinearFileController::purgeEmptyFilesToEfp() {
    slock l(journalFileListMutex_);
    while (journalFileList_.front()->isNoEnqueuedRecordsRemaining() && journalFileList_.size() > 1) { // Can't purge last file, even if it has no enqueued records
        emptyFilePoolPtr_->returnEmptyFileSymlink(journalFileList_.front()->getFqFileName());
        delete journalFileList_.front();
        journalFileList_.pop_front();
    }
}

uint32_t LinearFileController::getEnqueuedRecordCount(const uint64_t fileSeqNumber) {
    return find(fileSeqNumber)->getEnqueuedRecordCount();
}

uint32_t LinearFileController::incrEnqueuedRecordCount(const uint64_t fileSeqNumber) {
    return find(fileSeqNumber)->incrEnqueuedRecordCount();
}

uint32_t LinearFileController::decrEnqueuedRecordCount(const uint64_t fileSeqNumber) {
    uint32_t r = find(fileSeqNumber)->decrEnqueuedRecordCount();

    // TODO: Re-evaluate after testing and profiling
    // This is the first go at implementing auto-purge, which checks for all trailing empty files and recycles
    // them back to the EFP. This version checks every 100 decrements using decrCounter_ (an action which releases
    // records). We need to check this rather simple scheme works for outlying scenarios (large and tiny data
    // records) without impacting performance or performing badly (leaving excessive empty files in the journals).
    if (decrCounter_.increment() % 100ULL == 0ULL) {
        purgeEmptyFilesToEfp();
    }
    return r;
}

uint32_t LinearFileController::addWriteCompletedDblkCount(const uint64_t fileSeqNumber, const uint32_t a) {
    return find(fileSeqNumber)->addCompletedDblkCount(a);
}

uint16_t LinearFileController::decrOutstandingAioOperationCount(const uint64_t fileSeqNumber) {
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
                                              firstRecordOffset);
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

uint64_t LinearFileController::getCurrentSerial() const {
    assertCurrentJournalFileValid("getCurrentSerial");
    return currentJournalFilePtr_->getSerial();
}

bool LinearFileController::isEmpty() const {
    assertCurrentJournalFileValid("isEmpty");
    return currentJournalFilePtr_->isEmpty();
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

void LinearFileController::addJournalFile(const std::string& fileName,
                                          const efpIdentity_t& efpIdentity,
                                          const uint64_t fileSeqNumber,
                                          const uint32_t completedDblkCount) {
    JournalFile* jfp = new JournalFile(fileName, efpIdentity, fileSeqNumber, jcntlRef_.id());
    addJournalFile(jfp, completedDblkCount, true);
}

void LinearFileController::assertCurrentJournalFileValid(const char* const functionName) const {
    if (!checkCurrentJournalFileValid()) {
        throw jexception(jerrno::JERR__NULL, "LinearFileController", functionName);
    }
}

bool LinearFileController::checkCurrentJournalFileValid() const {
    return currentJournalFilePtr_ != 0;
}

JournalFile* LinearFileController::find(const uint64_t fileSeqNumber) {
    if (currentJournalFilePtr_ && currentJournalFilePtr_->getFileSeqNum() == fileSeqNumber)
        return currentJournalFilePtr_;

    slock l(journalFileListMutex_);
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

void LinearFileController::pullEmptyFileFromEfp() {
    std::string efn = emptyFilePoolPtr_->takeEmptyFile(journalDirectory_); // Moves file from EFP only (ie no file init), returns new file name
    addJournalFile(efn, emptyFilePoolPtr_->getIdentity(), getNextFileSeqNum(), 0);
}

}}}
