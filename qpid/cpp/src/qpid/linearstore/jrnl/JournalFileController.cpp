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

#include "qpid/linearstore/jrnl/JournalFileController.h"

#include <fstream>
#include "qpid/linearstore/jrnl/EmptyFilePool.h"
#include "qpid/linearstore/jrnl/jcfg.h"
#include "qpid/linearstore/jrnl/JournalFile.h"
#include "qpid/linearstore/jrnl/slock.h"
#include "qpid/linearstore/jrnl/utils/file_hdr.h"

#include <iostream> // DEBUG

namespace qpid {
namespace qls_jrnl {

JournalFileController::JournalFileController(const std::string& dir_,
                                             EmptyFilePool* efpp_) :
            dir(dir_),
            efpp(efpp_),
            fileSeqCounter(0)
{
    //std::cout << "*** JournalFileController::JournalFileController() dir=" << dir << std::endl;
}

JournalFileController::~JournalFileController() {}

void
JournalFileController::pullEmptyFileFromEfp(const uint64_t recId_,
                                            const uint64_t firstRecOffs_,
                                            const std::string& queueName_) {
    std::string ef = efpp->takeEmptyFile(dir);
    //std::cout << "*** JournalFileController::pullEmptyFileFromEfp() qn=" << queueName_ << " ef=" << ef << std::endl;
    const JournalFile* jfp = new JournalFile(ef/*efpp->takeEmptyFile(dir)*/);
    initialzeFileHeader(jfp->fqFileName(), recId_, firstRecOffs_, getNextFileSeqNum(), queueName_);
    {
        slock l(journalFileListMutex);
        journalFileList.push_back(jfp);
    }
}

void
JournalFileController::purgeFilesToEfp() {
    slock l(journalFileListMutex);
    while (journalFileList.front()->empty()) {

        efpp->returnEmptyFile(journalFileList.front());
        delete journalFileList.front();
        journalFileList.pop_front();
    }
}

void
JournalFileController::finalize() {}

void
JournalFileController::setFileSeqNum(const uint64_t fileSeqNum) {
    fileSeqCounter = fileSeqNum;
}

// protected

std::string
JournalFileController::readFileHeader(file_hdr_t* fhdr_,
                                      const std::string& fileName_) {
    //std::cout << "*** JournalFileController::readFileHeader() fn=" << fileName_ << std::endl;
    char buff[JRNL_SBLK_SIZE];
    std::ifstream ifs(fileName_.c_str(), std::ifstream::in | std::ifstream::binary);
    if (ifs.good()) {
        ifs.read(buff, JRNL_SBLK_SIZE);
        ifs.close();
        std::memcpy(fhdr_, buff, sizeof(file_hdr_t));
        return std::string(buff + sizeof(file_hdr_t), fhdr_->_queue_name_len);
    } else {
        std::cerr << "ERROR: Could not open file \"" << fileName_ << "\" for reading." << std::endl;
    }
    return std::string("");
}

void
JournalFileController::writeFileHeader(const file_hdr_t* fhdr_,
                                       const std::string& queueName_,
                                       const std::string& fileName_) {
    //std::cout << "*** JournalFileController::writeFileHeader() qn=" << queueName_ << " fn=" << fileName_ << std::endl;
    std::fstream fs(fileName_.c_str(), std::fstream::in | std::fstream::out | std::fstream::binary);
    if (fs.good()) {
        fs.seekp(0);
        fs.write((const char*)fhdr_, sizeof(file_hdr_t));
        fs.write(queueName_.data(), fhdr_->_queue_name_len);
        fs.close();
    } else {
        std::cerr << "ERROR: Could not open file \"" << fileName_ << "\" for writing." << std::endl;
    }
}

void
JournalFileController::resetFileHeader(const std::string& fileName_) {
    //std::cout << "*** JournalFileController::resetFileHeader() fn=" << fileName_ << std::endl;
    file_hdr_t fhdr;
    readFileHeader(&fhdr, fileName_);
    ::file_hdr_reset(&fhdr);
    writeFileHeader(&fhdr, std::string(""), fileName_);
}

void
JournalFileController::initialzeFileHeader(const std::string& fileName_,
                                           const uint64_t recId_,
                                           const uint64_t firstRecOffs_,
                                           const uint64_t fileSeqNum_,
                                           const std::string& queueName_) {
    //std::cout << "*** JournalFileController::initialzeFileHeader() fn=" << fileName_ << " sn=" << fileSeqNum_ << " qn=" << queueName_ << std::endl;
    file_hdr_t fhdr;
    readFileHeader(&fhdr, fileName_);
    ::file_hdr_init(&fhdr, 0, recId_, firstRecOffs_, fileSeqNum_, queueName_.length(), queueName_.data());
    writeFileHeader(&fhdr, queueName_, fileName_);
}

uint64_t
JournalFileController::getNextFileSeqNum() {
    return __sync_add_and_fetch(&fileSeqCounter, 1); // GCC atomic increment, not portable
}

}} // namespace qpid::qls_jrnl
