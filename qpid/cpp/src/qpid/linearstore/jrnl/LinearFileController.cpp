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

#include <iostream> // DEBUG

namespace qpid {
namespace qls_jrnl {

LinearFileController::LinearFileController(jcntl& jcntlRef_) :
            jcntlRef(jcntlRef_),
            emptyFilePoolPtr(0),
            currentJournalFilePtr(0),
            fileSeqCounter(0),
            recordIdCounter(0)
{}

LinearFileController::~LinearFileController() {}

void
LinearFileController::initialize(const std::string& journalDirectory_,
                                 EmptyFilePool* emptyFilePoolPtr_) {
    journalDirectory.assign(journalDirectory_);
    emptyFilePoolPtr = emptyFilePoolPtr_;
}

void
LinearFileController::finalize() {
    while (!journalFileList.empty()) {
        delete journalFileList.front();
        journalFileList.pop_front();
    }
}

void
LinearFileController::pullEmptyFileFromEfp() {
    if (currentJournalFilePtr)
        currentJournalFilePtr->close();
    std::string ef = emptyFilePoolPtr->takeEmptyFile(journalDirectory); // Moves file from EFP only, returns new file name
    std::cout << "*** LinearFileController::pullEmptyFileFromEfp() qn=" << jcntlRef.id() << " ef=" << ef << std::endl; // DEBUG
    currentJournalFilePtr = new JournalFile(ef, getNextFileSeqNum(), emptyFilePoolPtr->dataSize_kib());
    currentJournalFilePtr->initialize();
    {
        slock l(journalFileListMutex);
        journalFileList.push_back(currentJournalFilePtr);
    }
    currentJournalFilePtr->open();
}

void
LinearFileController::purgeFilesToEfp() {
    slock l(journalFileListMutex);
    while (journalFileList.front()->isNoEnqueuedRecordsRemaining()) {
        emptyFilePoolPtr->returnEmptyFile(journalFileList.front());
        delete journalFileList.front();
        journalFileList.pop_front();
    }
}

efpDataSize_kib_t
LinearFileController::dataSize_kib() const {
    return emptyFilePoolPtr->dataSize_kib();
}

efpFileSize_kib_t
LinearFileController::fileSize_kib() const {
    return emptyFilePoolPtr->fileSize_kib();
}

efpDataSize_sblks_t
LinearFileController::dataSize_sblks() const {
    return emptyFilePoolPtr->dataSize_sblks();
}

efpFileSize_sblks_t
LinearFileController::fileSize_sblks() const {
    return emptyFilePoolPtr->fileSize_sblks();
}

uint64_t
LinearFileController::getNextRecordId() {
    return recordIdCounter.increment();
}

uint32_t
LinearFileController::decrEnqueuedRecordCount(const efpFileCount_t fileSeqNumber) {
    slock l(journalFileListMutex);
    return find(fileSeqNumber)->decrEnqueuedRecordCount();
}

uint32_t
LinearFileController::addWriteCompletedDblkCount(const efpFileCount_t fileSeqNumber, const uint32_t a) {
    slock l(journalFileListMutex);
    return find(fileSeqNumber)->addCompletedDblkCount(a);
}

uint16_t
LinearFileController::decrOutstandingAioOperationCount(const efpFileCount_t fileSeqNumber) {
    slock l(journalFileListMutex);
    return find(fileSeqNumber)->decrOutstandingAioOperationCount();
}

void
LinearFileController::asyncFileHeaderWrite(io_context_t ioContextPtr,
                                           const uint16_t userFlags,
                                           const uint64_t recordId,
                                           const uint64_t firstRecordOffset) {
    currentJournalFilePtr->asyncFileHeaderWrite(ioContextPtr,
                                                emptyFilePoolPtr->getPartitionNumber(),
                                                emptyFilePoolPtr->dataSize_kib(),
                                                userFlags,
                                                recordId,
                                                firstRecordOffset,
                                                jcntlRef.id());
}

void
LinearFileController::asyncPageWrite(io_context_t ioContextPtr,
                                     aio_cb* aioControlBlockPtr,
                                     void* data,
                                     uint32_t dataSize_dblks) {
    assertCurrentJournalFileValid("asyncPageWrite");
    currentJournalFilePtr->asyncPageWrite(ioContextPtr, aioControlBlockPtr, data, dataSize_dblks);
}

uint64_t
LinearFileController::getCurrentFileSeqNum() const {
    assertCurrentJournalFileValid("getCurrentFileSeqNum");
    return currentJournalFilePtr->getFileSeqNum();
}

uint32_t
LinearFileController::getEnqueuedRecordCount() const {
    assertCurrentJournalFileValid("getEnqueuedRecordCount");
    return currentJournalFilePtr->getEnqueuedRecordCount();
}

uint32_t
LinearFileController::incrEnqueuedRecordCount() {
    assertCurrentJournalFileValid("incrEnqueuedRecordCount");
    return currentJournalFilePtr->incrEnqueuedRecordCount();
}

uint32_t
LinearFileController::addEnqueuedRecordCount(const uint32_t a) {
    assertCurrentJournalFileValid("addEnqueuedRecordCount");
    return currentJournalFilePtr->addEnqueuedRecordCount(a);
}

uint32_t
LinearFileController::decrEnqueuedRecordCount() {
    assertCurrentJournalFileValid("decrEnqueuedRecordCount");
    return currentJournalFilePtr->decrEnqueuedRecordCount();
}

uint32_t
LinearFileController::subtrEnqueuedRecordCount(const uint32_t s) {
    assertCurrentJournalFileValid("subtrEnqueuedRecordCount");
    return currentJournalFilePtr->subtrEnqueuedRecordCount(s);
}

uint32_t
LinearFileController::getWriteSubmittedDblkCount() const {
    assertCurrentJournalFileValid("getWriteSubmittedDblkCount");
    return currentJournalFilePtr->getSubmittedDblkCount();
}

uint32_t
LinearFileController::addWriteSubmittedDblkCount(const uint32_t a) {
    assertCurrentJournalFileValid("addWriteSubmittedDblkCount");
    return currentJournalFilePtr->addSubmittedDblkCount(a);
}

uint32_t
LinearFileController::getWriteCompletedDblkCount() const {
    assertCurrentJournalFileValid("getWriteCompletedDblkCount");
    return currentJournalFilePtr->getCompletedDblkCount();
}

uint32_t
LinearFileController::addWriteCompletedDblkCount(const uint32_t a) {
    assertCurrentJournalFileValid("addWriteCompletedDblkCount");
    return currentJournalFilePtr->addCompletedDblkCount(a);
}

uint16_t
LinearFileController::getOutstandingAioOperationCount() const {
    assertCurrentJournalFileValid("getOutstandingAioOperationCount");
    return currentJournalFilePtr->getOutstandingAioOperationCount();
}

uint16_t
LinearFileController::incrOutstandingAioOperationCount() {
    assertCurrentJournalFileValid("incrOutstandingAioOperationCount");
    return currentJournalFilePtr->incrOutstandingAioOperationCount();
}

uint16_t
LinearFileController::decrOutstandingAioOperationCount() {
    assertCurrentJournalFileValid("decrOutstandingAioOperationCount");
    return currentJournalFilePtr->decrOutstandingAioOperationCount();
}

bool
LinearFileController::isEmpty() const {
    assertCurrentJournalFileValid("isEmpty");
    return currentJournalFilePtr->isEmpty();
}

bool
LinearFileController::isDataEmpty() const {
    assertCurrentJournalFileValid("isDataEmpty");
    return currentJournalFilePtr->isDataEmpty();
}

u_int32_t
LinearFileController::dblksRemaining() const {
    assertCurrentJournalFileValid("dblksRemaining");
    return currentJournalFilePtr->dblksRemaining();
}

bool
LinearFileController::isFull() const {
    assertCurrentJournalFileValid("isFull");
    return currentJournalFilePtr->isFull();
}

bool
LinearFileController::isFullAndComplete() const {
    assertCurrentJournalFileValid("isFullAndComplete");
    return currentJournalFilePtr->isFullAndComplete();
}

u_int32_t
LinearFileController::getOutstandingAioDblks() const {
    assertCurrentJournalFileValid("getOutstandingAioDblks");
    return currentJournalFilePtr->getOutstandingAioDblks();
}

bool
LinearFileController::getNextFile() const {
    assertCurrentJournalFileValid("getNextFile");
    return currentJournalFilePtr->getNextFile();
}

const std::string
LinearFileController::status(const uint8_t indentDepth) const {
    std::string indent((size_t)indentDepth, '.');
    std::ostringstream oss;
    oss << indent << "LinearFileController: queue=" << jcntlRef.id() << std::endl;
    oss << indent << "  journalDirectory=" << journalDirectory << std::endl;
    oss << indent << "  fileSeqCounter=" << fileSeqCounter.get() << std::endl;
    oss << indent << "  recordIdCounter=" << recordIdCounter.get() << std::endl;
    oss << indent << "  journalFileList.size=" << journalFileList.size() << std::endl;
    if (checkCurrentJournalFileValid()) {
        oss << currentJournalFilePtr->status_str(indentDepth+2);
    } else {
        oss << indent << "  <No current journal file>" << std::endl;
    }
    return oss.str();
}

// protected

bool
LinearFileController::checkCurrentJournalFileValid() const {
    return currentJournalFilePtr != 0;
}

void
LinearFileController::assertCurrentJournalFileValid(const char* const functionName) const {
    if (!checkCurrentJournalFileValid()) {
        throw jexception(jerrno::JERR__NULL, "LinearFileController", functionName);
    }
}

// NOTE: NOT THREAD SAFE - journalFileList is accessed by multiple threads - use under external lock
JournalFile*
LinearFileController::find(const efpFileCount_t fileSeqNumber) {
    if (currentJournalFilePtr != 0 && currentJournalFilePtr->getFileSeqNum() == fileSeqNumber)
        return currentJournalFilePtr;
    for (JournalFileListItr_t i=journalFileList.begin(); i!=journalFileList.end(); ++i) {
        if ((*i)->getFileSeqNum() == fileSeqNumber) {
            return *i;
        }
    }
    std::ostringstream oss;
    oss << "fileSeqNumber=" << fileSeqNumber;
    throw jexception(jerrno::JERR_LFCR_SEQNUMNOTFOUND, oss.str(), "LinearFileController", "find");
}

uint64_t
LinearFileController::getNextFileSeqNum() {
    return fileSeqCounter.increment();
}

}} // namespace qpid::qls_jrnl
