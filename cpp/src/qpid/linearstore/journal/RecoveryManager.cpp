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

#include "qpid/linearstore/journal/RecoveryManager.h"

#include <algorithm>
#include <cstdlib>
#include <iomanip>
#include "qpid/linearstore/journal/Checksum.h"
#include "qpid/linearstore/journal/data_tok.h"
#include "qpid/linearstore/journal/deq_rec.h"
#include "qpid/linearstore/journal/EmptyFilePool.h"
#include "qpid/linearstore/journal/EmptyFilePoolManager.h"
#include "qpid/linearstore/journal/enq_map.h"
#include "qpid/linearstore/journal/enq_rec.h"
#include "qpid/linearstore/journal/jcfg.h"
#include "qpid/linearstore/journal/jdir.h"
#include "qpid/linearstore/journal/JournalFile.h"
#include "qpid/linearstore/journal/JournalLog.h"
#include "qpid/linearstore/journal/jrec.h"
#include "qpid/linearstore/journal/LinearFileController.h"
#include "qpid/linearstore/journal/txn_map.h"
#include "qpid/linearstore/journal/txn_rec.h"
#include "qpid/linearstore/journal/utils/enq_hdr.h"
#include "qpid/linearstore/journal/utils/file_hdr.h"
#include <sstream>
#include <string>
#include <unistd.h>
#include <vector>

namespace qpid {
namespace linearstore {
namespace journal {

RecoveredRecordData_t::RecoveredRecordData_t(const uint64_t rid, const uint64_t fid, const std::streampos foffs, bool ptxn) :
                    recordId_(rid),
                    fileId_(fid),
                    fileOffset_(foffs),
                    pendingTransaction_(ptxn)
{}

bool recordIdListCompare(RecoveredRecordData_t a, RecoveredRecordData_t b) {
    return a.recordId_ < b.recordId_;
}

RecoveredFileData_t::RecoveredFileData_t(JournalFile* journalFilePtr, const uint32_t completedDblkCount) :
                    journalFilePtr_(journalFilePtr),
                    completedDblkCount_(completedDblkCount)
{}

RecoveryManager::RecoveryManager(const std::string& journalDirectory,
                                 const std::string& queuename,
                                 enq_map& enqueueMapRef,
                                 txn_map& transactionMapRef,
                                 JournalLog& journalLogRef) :
                                                 journalDirectory_(journalDirectory),
                                                 queueName_(queuename),
                                                 enqueueMapRef_(enqueueMapRef),
                                                 transactionMapRef_(transactionMapRef),
                                                 journalLogRef_(journalLogRef),
                                                 journalEmptyFlag_(false),
                                                 firstRecordOffset_(0),
                                                 endOffset_(0),
                                                 highestRecordId_(0ULL),
                                                 highestFileNumber_(0ULL),
                                                 lastFileFullFlag_(false),
                                                 initial_fid_(0),
                                                 currentSerial_(0),
                                                 efpFileSize_kib_(0)
{}

RecoveryManager::~RecoveryManager() {
    for (fileNumberMapItr_t i = fileNumberMap_.begin(); i != fileNumberMap_.end(); ++i) {
        delete i->second;
    }
    fileNumberMap_.clear();
}

void RecoveryManager::analyzeJournals(const std::vector<std::string>* preparedTransactionListPtr,
                                      EmptyFilePoolManager* emptyFilePoolManager,
                                      EmptyFilePool** emptyFilePoolPtrPtr) {
    // Analyze file headers of existing journal files
    efpIdentity_t efpIdentity;
    analyzeJournalFileHeaders(efpIdentity);

    if (journalEmptyFlag_) {
        if (uninitFileList_.empty()) {
            *emptyFilePoolPtrPtr = emptyFilePoolManager->getEmptyFilePool(0, 0); // Use default EFP
        } else {
            *emptyFilePoolPtrPtr = emptyFilePoolManager->getEmptyFilePool(efpIdentity);
        }
    } else {
        *emptyFilePoolPtrPtr = emptyFilePoolManager->getEmptyFilePool(efpIdentity);
        if (! *emptyFilePoolPtrPtr) {
            // TODO: At a later time, this could be used to establish a new pool size provided the partition exists.
            // If the partition does not exist, this is always an error. For now, throw an exception, as this should
            // not occur in any practical application. Once multiple partitions and mixed EFPs are supported, this
            // needs to be resolved. Note that EFP size is always a multiple of QLS_SBLK_SIZE_BYTES (currently 4096
            // bytes, any other value cannot be used and should be rejected as an error.
            std::ostringstream oss;
            oss << "Invalid EFP identity: Partition=" << efpIdentity.pn_ << " Size=" << efpIdentity.ds_ << "k";
            throw jexception(jerrno::JERR_RCVM_INVALIDEFPID, oss.str(), "RecoveryManager", "analyzeJournals");
        }
        efpFileSize_kib_ = (*emptyFilePoolPtrPtr)->fileSize_kib();

        // Read all records, establish remaining enqueued records
        if (inFileStream_.is_open()) {
            inFileStream_.close();
        }
        while (getNextRecordHeader()) {}
        if (inFileStream_.is_open()) {
            inFileStream_.close();
        }

        // Check for file full condition
        lastFileFullFlag_ = endOffset_ == (std::streamoff)(*emptyFilePoolPtrPtr)->fileSize_kib() * 1024;

        // Remove leading files which have no enqueued records
        removeEmptyFiles(*emptyFilePoolPtrPtr);

        // Remove all txns from tmap that are not in the prepared list
        if (preparedTransactionListPtr) {
            std::vector<std::string> xidList;
            transactionMapRef_.xid_list(xidList);
            for (std::vector<std::string>::iterator itr = xidList.begin(); itr != xidList.end(); itr++) {
                std::vector<std::string>::const_iterator pitr =
                        std::find(preparedTransactionListPtr->begin(), preparedTransactionListPtr->end(), *itr);
                if (pitr == preparedTransactionListPtr->end()) { // not found in prepared list
                    txn_data_list_t tdl = transactionMapRef_.get_remove_tdata_list(*itr); // tdl will be empty if xid not found
                    // Unlock any affected enqueues in emap
                    for (tdl_itr_t i=tdl.begin(); i<tdl.end(); i++) {
                        if (i->enq_flag_) { // enq op - decrement enqueue count
                            fileNumberMap_[i->fid_]->journalFilePtr_->decrEnqueuedRecordCount();
                        } else if (enqueueMapRef_.is_enqueued(i->drid_, true)) { // deq op - unlock enq record
                            if (enqueueMapRef_.unlock(i->drid_) < enq_map::EMAP_OK) { // fail
                                // enq_map::unlock()'s only error is enq_map::EMAP_RID_NOT_FOUND
                                std::ostringstream oss;
                                oss << std::hex << "_emap.unlock(): drid=0x\"" << i->drid_;
                                throw jexception(jerrno::JERR_MAP_NOTFOUND, oss.str(), "RecoveryManager", "analyzeJournals");
                            }
                        }
                    }
                }
            }
        }
        prepareRecordList();
    }
}

std::streamoff RecoveryManager::getEndOffset() const {
    return endOffset_;
}

uint64_t RecoveryManager::getHighestFileNumber() const {
    return highestFileNumber_;
}

uint64_t RecoveryManager::getHighestRecordId() const {
    return highestRecordId_;
}

bool RecoveryManager::isLastFileFull() const {
    return lastFileFullFlag_;
}

bool RecoveryManager::readNextRemainingRecord(void** const dataPtrPtr,
                                              std::size_t& dataSize,
                                              void** const xidPtrPtr,
                                              std::size_t& xidSize,
                                              bool& transient,
                                              bool& external,
                                              data_tok* const dtokp,
                                              bool ignore_pending_txns) {
    bool foundRecord = false;
    do {
        if (recordIdListConstItr_ == recordIdList_.end()) {
            return false;
        }
        if (recordIdListConstItr_->pendingTransaction_ && ignore_pending_txns) { // Pending transaction
            ++recordIdListConstItr_; // ignore, go to next record
        } else {
            foundRecord = true;
        }
    } while (!foundRecord);

    if (!inFileStream_.is_open() || currentJournalFileItr_->first != recordIdListConstItr_->fileId_) {
        if (!getFile(recordIdListConstItr_->fileId_, false)) {
            std::ostringstream oss;
            oss << "Failed to open file with file-id=" << recordIdListConstItr_->fileId_;
            throw jexception(jerrno::JERR__FILEIO, oss.str(), "RecoveryManager", "readNextRemainingRecord");
        }
    }
    inFileStream_.seekg(recordIdListConstItr_->fileOffset_, std::ifstream::beg);
    if (!inFileStream_.good()) {
        std::ostringstream oss;
        oss << "Could not find offset 0x" << std::hex << recordIdListConstItr_->fileOffset_ << " in file " << getCurrentFileName();
        throw jexception(jerrno::JERR__FILEIO, oss.str(), "RecoveryManager", "readNextRemainingRecord");
    }

    ::enq_hdr_t enqueueHeader;
    inFileStream_.read((char*)&enqueueHeader, sizeof(::enq_hdr_t));
    if (inFileStream_.gcount() != sizeof(::enq_hdr_t)) {
        std::ostringstream oss;
        oss << "Could not read enqueue header from file " << getCurrentFileName() << " at offset 0x" << std::hex << recordIdListConstItr_->fileOffset_;
        throw jexception(jerrno::JERR__FILEIO, oss.str(), "RecoveryManager", "readNextRemainingRecord");
    }
    // check flags
    transient = ::is_enq_transient(&enqueueHeader);
    external = ::is_enq_external(&enqueueHeader);

    // read xid
    xidSize = enqueueHeader._xidsize;
    *xidPtrPtr = ::malloc(xidSize);
    if (*xidPtrPtr == 0) {
        std::ostringstream oss;
        oss << "xidPtr, size=0x" << std::hex << xidSize;
        throw jexception(jerrno::JERR__MALLOC, oss.str(), "RecoveryManager", "readNextRemainingRecord");
    }
    readJournalData((char*)*xidPtrPtr, xidSize);

    // read data
    dataSize = enqueueHeader._dsize;
    *dataPtrPtr = ::malloc(dataSize);
    if (*xidPtrPtr == 0) {
        std::ostringstream oss;
        oss << "dataPtr, size=0x" << std::hex << dataSize;
        throw jexception(jerrno::JERR__MALLOC, oss.str(), "RecoveryManager", "readNextRemainingRecord");
    }
    readJournalData((char*)*dataPtrPtr, dataSize);

    // Check enqueue record checksum
    Checksum checksum;
    checksum.addData((const unsigned char*)&enqueueHeader, sizeof(::enq_hdr_t));
    if (xidSize > 0) {
        checksum.addData((const unsigned char*)*xidPtrPtr, xidSize);
    }
    if (dataSize > 0) {
        checksum.addData((const unsigned char*)*dataPtrPtr, dataSize);
    }
    ::rec_tail_t enqueueTail;
    readJournalData((char*)&enqueueTail, sizeof(::rec_tail_t));
    uint32_t cs = checksum.getChecksum();
    uint16_t res = ::rec_tail_check(&enqueueTail, &enqueueHeader._rhdr, cs);
    if (res != 0) {
        std::stringstream oss;
        oss << "Bad record tail:" << std::hex;
        if (res & ::REC_TAIL_MAGIC_ERR_MASK) {
            oss << std::endl << "  Magic: expected 0x" << ~enqueueHeader._rhdr._magic << "; found 0x" << enqueueTail._xmagic;
        }
        if (res & ::REC_TAIL_SERIAL_ERR_MASK) {
            oss << std::endl << "  Serial: expected 0x" << enqueueHeader._rhdr._serial << "; found 0x" << enqueueTail._serial;
        }
        if (res & ::REC_TAIL_RID_ERR_MASK) {
            oss << std::endl << "  Record Id: expected 0x" << enqueueHeader._rhdr._rid << "; found 0x" << enqueueTail._rid;
        }
        if (res & ::REC_TAIL_CHECKSUM_ERR_MASK) {
            oss << std::endl << "  Checksum: expected 0x" << cs << "; found 0x" << enqueueTail._checksum;
        }
        throw jexception(jerrno::JERR_JREC_BADRECTAIL, oss.str(), "RecoveryManager", "readNextRemainingRecord"); // TODO: Don't throw exception, log info
    }

    // Set data token
    dtokp->set_wstate(data_tok::ENQ);
    dtokp->set_rid(enqueueHeader._rhdr._rid);
    dtokp->set_dsize(dataSize);
    if (xidSize) {
        dtokp->set_xid(*xidPtrPtr, xidSize);
    }

    ++recordIdListConstItr_;
    return true;
}

void RecoveryManager::recoveryComplete() {
    if(inFileStream_.is_open()) {
        inFileStream_.close();
    }
}

void RecoveryManager::setLinearFileControllerJournals(lfcAddJournalFileFn fnPtr,
                                                      LinearFileController* lfcPtr) {
    if (journalEmptyFlag_) {
        if (uninitFileList_.size() > 0) {
            // TODO: Handle case if uninitFileList_.size() > 1, but this should not happen in normal operation. Here we assume only one item in the list.
            std::string uninitFile = uninitFileList_.back();
            uninitFileList_.pop_back();
            lfcPtr->restoreEmptyFile(uninitFile);
        }
    } else {
        if (initial_fid_ == 0) {
            throw jexception(jerrno::JERR_RCVM_NULLFID, "RecoveryManager", "setLinearFileControllerJournals");
        }
        for (fileNumberMapConstItr_t i = fileNumberMap_.begin(); i != fileNumberMap_.end(); ++i) {
            (lfcPtr->*fnPtr)(i->second->journalFilePtr_, i->second->completedDblkCount_, i->first == initial_fid_);
        }
    }

    std::ostringstream oss;
    bool logFlag = !notNeededFilesList_.empty();
    if (logFlag) {
        oss << "Files removed from head of journal: prior truncation during recovery:";
    }
    while (!notNeededFilesList_.empty()) {
        lfcPtr->removeFileToEfp(notNeededFilesList_.back());
        oss << std::endl << " * " << notNeededFilesList_.back();
        notNeededFilesList_.pop_back();
    }
    if (logFlag) {
        journalLogRef_.log(JournalLog::LOG_NOTICE, queueName_, oss.str());
    }
}

std::string RecoveryManager::toString(const std::string& jid, const uint16_t indent) const {
    std::string indentStr(indent, ' ');
    std::ostringstream oss;
    oss << std::endl << indentStr  << "Journal recovery analysis (jid=\"" << jid << "\"):" << std::endl;
    if (journalEmptyFlag_) {
        oss << indentStr << "<Journal empty, no journal files found>" << std::endl;
    } else {
        oss << indentStr << std::setw(7) << "file_id"
                         << std::setw(43) << "file_name"
                         << std::setw(12) << "record_cnt"
                         << std::setw(16) << "fro"
                         << std::setw(12) << "efp_id"
                         << std::endl;
        oss << indentStr << std::setw(7) << "-------"
                         << std::setw(43) << "-----------------------------------------"
                         << std::setw(12) << "----------"
                         << std::setw(16) << "--------------"
                         << std::setw(12) << "----------"
                         << std::endl;
        uint32_t totalRecordCount(0UL);
        for (fileNumberMapConstItr_t k=fileNumberMap_.begin(); k!=fileNumberMap_.end(); ++k) {
            std::string fqFileName = k->second->journalFilePtr_->getFqFileName();
            std::ostringstream fid;
            fid << std::hex << "0x" << k->first;
            std::ostringstream fro;
            fro << std::hex << "0x" << k->second->journalFilePtr_->getFirstRecordOffset();
            oss << indentStr << std::setw(7) << fid.str()
                             << std::setw(43) << fqFileName.substr(fqFileName.rfind('/')+1)
                             << std::setw(12) << k->second->journalFilePtr_->getEnqueuedRecordCount()
                             << std::setw(16) << fro.str()
                             << std::setw(12) << k->second->journalFilePtr_->getEfpIdentity()
                             << std::endl;
            totalRecordCount += k->second->journalFilePtr_->getEnqueuedRecordCount();
        }
        oss << indentStr << std::setw(62) << "----------" << std::endl;
        oss << indentStr << std::setw(62) << totalRecordCount << std::endl;
        oss << indentStr << "First record offset in first file = 0x" << std::hex << firstRecordOffset_ <<
                std::dec << " (" << (firstRecordOffset_/QLS_DBLK_SIZE_BYTES) << " dblks)" << std::endl;
        oss << indentStr << "End offset in last file = 0x" << std::hex << endOffset_ << std::dec << " ("  <<
                (endOffset_/QLS_DBLK_SIZE_BYTES) << " dblks)" << std::endl;
        oss << indentStr << "Highest rid found = 0x" << std::hex << highestRecordId_ << std::dec << std::endl;
        oss << indentStr << "Last file full = " << (lastFileFullFlag_ ? "TRUE" : "FALSE") << std::endl;
    }
    return oss.str();
}

// --- protected functions ---

void RecoveryManager::analyzeJournalFileHeaders(efpIdentity_t& efpIdentity) {
    std::string headerQueueName;
    ::file_hdr_t fileHeader;
    stringList_t directoryList;
    jdir::read_dir(journalDirectory_, directoryList, false, true, false, true);
    for (stringListConstItr_t i = directoryList.begin(); i != directoryList.end(); ++i) {
        bool hdrOk = readJournalFileHeader(*i, fileHeader, headerQueueName);
        bool hdrEmpty = ::is_file_hdr_reset(&fileHeader);
        if (!hdrOk) {
            std::ostringstream oss;
            oss << "Journal file " << (*i) << " is corrupted or invalid";
            journalLogRef_.log(JournalLog::LOG_WARN, queueName_, oss.str());
        } else if (hdrEmpty) {
            // Read symlink, find efp directory name which is efp size in KiB
            // TODO: place this bit into a common function as it is also used in EmptyFilePool.cpp::deleteSymlink()
            char buff[1024];
            ssize_t len = ::readlink((*i).c_str(), buff, 1024);
            if (len < 0) {
                std::ostringstream oss;
                oss << "symlink=\"" << (*i) << "\"" << FORMAT_SYSERR(errno);
                throw jexception(jerrno::JERR__SYMLINK, oss.str(), "RecoveryManager", "analyzeJournalFileHeaders");
            }
            // Find second and third '/' from back of string, which contains the EFP directory name
            *(::strrchr(buff, '/')) = '\0';
            *(::strrchr(buff, '/')) = '\0';
            int efpDataSize_kib = atoi(::strrchr(buff, '/') + 1);
            uninitFileList_.push_back(*i);
            efpIdentity.pn_ = fileHeader._efp_partition;
            efpIdentity.ds_ = efpDataSize_kib;
        } else if (headerQueueName.compare(queueName_) != 0) {
            std::ostringstream oss;
            oss << "Journal file " << (*i) << " belongs to queue \"" << headerQueueName << "\": ignoring";
            journalLogRef_.log(JournalLog::LOG_WARN, queueName_, oss.str());
        } else {
            JournalFile* jfp = new JournalFile(*i, fileHeader, queueName_);
            std::pair<fileNumberMapItr_t, bool> res = fileNumberMap_.insert(
                            std::pair<uint64_t, RecoveredFileData_t*>(fileHeader._file_number, new RecoveredFileData_t(jfp, 0)));
            if (!res.second) {
                std::ostringstream oss;
                oss << "Journal file " << (*i) << " has fid=0x" << std::hex << jfp->getFileSeqNum() << " which already exists for this journal.";
                throw jexception(oss.str()); // TODO: complete this exception
            }
            if (fileHeader._file_number > highestFileNumber_) {
                highestFileNumber_ = fileHeader._file_number;
            }
            // TODO: Logic weak here for detecting error conditions in journal, specifically when no
            // valid files exist, or files from mixed EFPs. Currently last read file header determines
            // efpIdentity.
            efpIdentity.pn_ = fileHeader._efp_partition;
            efpIdentity.ds_ = fileHeader._data_size_kib;
        }
    }

//std::cerr << "*** RecoveryManager::analyzeJournalFileHeaders() fileNumberMap_.size()=" << fileNumberMap_.size() << std::endl; // DEBUG
    if (fileNumberMap_.empty()) {
        journalEmptyFlag_ = true;
    } else {
        currentJournalFileItr_ = fileNumberMap_.begin();
    }
}

void RecoveryManager::checkFileStreamOk(bool checkEof) {
    if (inFileStream_.fail() || inFileStream_.bad() || checkEof ? inFileStream_.eof() : false) {
        std::ostringstream oss;
        oss << "Stream status: fail=" << (inFileStream_.fail()?"T":"F") << " bad=" << (inFileStream_.bad()?"T":"F");
        if (checkEof) {
            oss << " eof=" << (inFileStream_.eof()?"T":"F");
        }
        throw jexception(jerrno::JERR_RCVM_STREAMBAD, oss.str(), "RecoveryManager", "checkFileStreamOk");
    }
}

void RecoveryManager::checkJournalAlignment(const uint64_t start_fid, const std::streampos recordPosition) {
    if (recordPosition % QLS_DBLK_SIZE_BYTES != 0) {
        std::ostringstream oss;
        oss << "Current read pointer not dblk aligned: recordPosition=0x" << std::hex << recordPosition;
        oss << " (dblk alignment offset = 0x" << (recordPosition % QLS_DBLK_SIZE_BYTES);
        throw jexception(jerrno::JERR_RCVM_NOTDBLKALIGNED, oss.str(), "RecoveryManager", "checkJournalAlignment");
    }
    std::streampos currentPosn = recordPosition;
    unsigned sblkOffset = currentPosn % QLS_SBLK_SIZE_BYTES;
    if (sblkOffset)
    {
        std::ostringstream oss1;
        oss1 << std::hex << "Bad record alignment found at fid=0x" << start_fid;
        oss1 << " offs=0x" << currentPosn << " (likely journal overwrite boundary); " << std::dec;
        oss1 << (QLS_SBLK_SIZE_DBLKS - (sblkOffset/QLS_DBLK_SIZE_BYTES)) << " filler record(s) required.";
        journalLogRef_.log(JournalLog::LOG_WARN, queueName_, oss1.str());

        fileNumberMapConstItr_t fnmItr = fileNumberMap_.find(start_fid);
        std::ofstream outFileStream(fnmItr->second->journalFilePtr_->getFqFileName().c_str(), std::ios_base::in | std::ios_base::out | std::ios_base::binary);
        if (!outFileStream.good()) {
            throw jexception(jerrno::JERR__FILEIO, getCurrentFileName(), "RecoveryManager", "checkJournalAlignment");
        }
        outFileStream.seekp(currentPosn);

        // Prepare write buffer containing a single empty record (1 dblk)
        void* writeBuffer = std::malloc(QLS_DBLK_SIZE_BYTES);
        if (writeBuffer == 0) {
            throw jexception(jerrno::JERR__MALLOC, "RecoveryManager", "checkJournalAlignment");
        }
        const uint32_t xmagic = QLS_EMPTY_MAGIC;
        ::memcpy(writeBuffer, (const void*)&xmagic, sizeof(xmagic));
        ::memset((char*)writeBuffer + sizeof(xmagic), QLS_CLEAN_CHAR, QLS_DBLK_SIZE_BYTES - sizeof(xmagic));

        // Write as many empty records as are needed to get to sblk boundary
        while (currentPosn % QLS_SBLK_SIZE_BYTES) {
            outFileStream.write((const char*)writeBuffer, QLS_DBLK_SIZE_BYTES);
            if (outFileStream.fail()) {
                throw jexception(jerrno::JERR_RCVM_WRITE, "RecoveryManager", "checkJournalAlignment");
            }
            std::ostringstream oss2;
            oss2 << std::hex << "Recover phase write: Wrote filler record: fid=0x" << start_fid;
            oss2 << " offs=0x" << currentPosn;
            journalLogRef_.log(JournalLog::LOG_NOTICE, queueName_, oss2.str());
            currentPosn = outFileStream.tellp();
        }
        outFileStream.close();
        std::free(writeBuffer);
        journalLogRef_.log(JournalLog::LOG_INFO, queueName_, "Bad record alignment fixed.");
    }
    lastRecord(start_fid, currentPosn);
}

bool RecoveryManager::decodeRecord(jrec& record,
                                   std::size_t& cumulativeSizeRead,
                                   ::rec_hdr_t& headerRecord,
                                   const uint64_t start_fid,
                                   const std::streampos recordOffset)
{
    if (highestRecordId_ == 0) {
        highestRecordId_ = headerRecord._rid;
    } else if (headerRecord._rid - highestRecordId_ < 0x8000000000000000ULL) { // RFC 1982 comparison for unsigned 64-bit
        highestRecordId_ = headerRecord._rid;
    }

    bool done = false;
    while (!done) {
        try {
            done = record.decode(headerRecord, &inFileStream_, cumulativeSizeRead, recordOffset);
        }
        catch (const jexception& e) {
            if (e.err_code() == jerrno::JERR_JREC_BADRECTAIL) {
                std::ostringstream oss;
                oss << jerrno::err_msg(e.err_code()) << e.additional_info();
                journalLogRef_.log(JournalLog::LOG_INFO, queueName_, oss.str());
            } else {
                journalLogRef_.log(JournalLog::LOG_INFO, queueName_, e.what());
            }
            checkJournalAlignment(start_fid, recordOffset);
            return false;
        }
        if (!done && needNextFile()) {
            if (!getNextFile(false)) {
                checkJournalAlignment(start_fid, recordOffset);
                return false;
            }
        }
    }
    return true;
}

std::string RecoveryManager::getCurrentFileName() const {
    return currentJournalFileItr_->second->journalFilePtr_->getFqFileName();
}

uint64_t RecoveryManager::getCurrentFileNumber() const {
    return currentJournalFileItr_->first;
}

bool RecoveryManager::getFile(const uint64_t fileNumber, bool jumpToFirstRecordOffsetFlag) {
    if (inFileStream_.is_open()) {
        inFileStream_.close();
//std::cout << " f=" << getCurrentFileName() << "]" << std::flush; // DEBUG
        inFileStream_.clear(); // clear eof flag, req'd for older versions of c++
    }
    currentJournalFileItr_ = fileNumberMap_.find(fileNumber);
    if (currentJournalFileItr_ == fileNumberMap_.end()) {
        return false;
    }
    inFileStream_.open(getCurrentFileName().c_str(), std::ios_base::in | std::ios_base::binary);
    if (!inFileStream_.good()) {
        throw jexception(jerrno::JERR__FILEIO, getCurrentFileName(), "RecoveryManager", "getFile");
    }
//std::cout << " [F=" << getCurrentFileName() << std::flush; // DEBUG

    if (!readFileHeader()) {
        return false;
    }
    std::streamoff foffs = jumpToFirstRecordOffsetFlag ? firstRecordOffset_ : QLS_JRNL_FHDR_RES_SIZE_SBLKS * QLS_SBLK_SIZE_BYTES;
    inFileStream_.seekg(foffs);
    return true;
}

bool RecoveryManager::getNextFile(bool jumpToFirstRecordOffsetFlag) {
    if (fileNumberMap_.empty()) {
        return false;
    }
    if (inFileStream_.is_open()) {
        inFileStream_.close();
//std::cout << " .f=" << getCurrentFileName() << "]" << std::flush; // DEBUG
        currentJournalFileItr_->second->completedDblkCount_ = efpFileSize_kib_ * 1024 / QLS_DBLK_SIZE_BYTES;
        if (++currentJournalFileItr_ == fileNumberMap_.end()) {
            return false;
        }
        inFileStream_.clear(); // clear eof flag, req'd for older versions of c++
    }
    inFileStream_.open(getCurrentFileName().c_str(), std::ios_base::in | std::ios_base::binary);
    if (!inFileStream_.good()) {
        throw jexception(jerrno::JERR__FILEIO, getCurrentFileName(), "RecoveryManager", "getNextFile");
    }
//std::cout << " [.F=" << getCurrentFileName() << std::flush; // DEBUG

    if (!readFileHeader()) {
        return false;
    }
    std::streamoff foffs = jumpToFirstRecordOffsetFlag ? firstRecordOffset_ : QLS_JRNL_FHDR_RES_SIZE_SBLKS * QLS_SBLK_SIZE_BYTES;
    inFileStream_.seekg(foffs);
    return true;
}

bool RecoveryManager::getNextRecordHeader()
{
    std::size_t cum_size_read = 0;
    void* xidp = 0;
    rec_hdr_t h;

    bool hdr_ok = false;
    uint64_t file_id = currentJournalFileItr_->second->journalFilePtr_->getFileSeqNum();
    std::streampos file_pos = 0;
    if (inFileStream_.is_open()) {
        inFileStream_.clear();
        file_pos = inFileStream_.tellg();
    }
    if (file_pos == std::streampos(-1)) {
        std::ostringstream oss;
        oss << "tellg() failure: fail=" << (inFileStream_.fail()?"T":"F") << " bad=" << (inFileStream_.bad()?"T":"F");
        oss << " eof=" << (inFileStream_.eof()?"T":"F") << " good=" << (inFileStream_.good()?"T":"F");
        oss << " rdstate=0x" << std::hex << inFileStream_.rdstate() << std::dec;
        throw jexception(jerrno::JERR_RCVM_STREAMBAD, oss.str(), "RecoveryManager", "getNextRecordHeader");
    }
    while (!hdr_ok) {
        if (needNextFile()) {
            if (!getNextFile(true)) {
                lastRecord(file_id, file_pos);
                return false;
            }
        }
        file_id = currentJournalFileItr_->second->journalFilePtr_->getFileSeqNum();
        file_pos = inFileStream_.tellg();
        if (file_pos == std::streampos(-1)) {
            std::ostringstream oss;
            oss << "tellg() failure: fail=" << (inFileStream_.fail()?"T":"F") << " bad=" << (inFileStream_.bad()?"T":"F");
            oss << " eof=" << (inFileStream_.eof()?"T":"F") << " good=" << (inFileStream_.good()?"T":"F");
            oss << " rdstate=0x" << std::hex << inFileStream_.rdstate() << std::dec;
            throw jexception(jerrno::JERR_RCVM_STREAMBAD, oss.str(), "RecoveryManager", "getNextRecordHeader");
        }
        inFileStream_.read((char*)&h, sizeof(rec_hdr_t));
        if (inFileStream_.gcount() == sizeof(rec_hdr_t)) {
            hdr_ok = true;
        } else {
            if (needNextFile()) {
                if (!getNextFile(true)) {
                    lastRecord(file_id, file_pos);
                    return false;
                }
            }
        }
    }

    uint64_t start_fid = getCurrentFileNumber(); // fid may increment in decode() if record folds over file boundary
    switch(h._magic) {
        case QLS_ENQ_MAGIC:
            {
//std::cout << " 0x" << std::hex << file_pos << ".e.0x" << h._rid << std::dec << std::flush; // DEBUG
                if (::rec_hdr_check(&h, QLS_ENQ_MAGIC, QLS_JRNL_VERSION, currentSerial_) != 0) {
                    checkJournalAlignment(file_id, file_pos);
                    return false;
                }
                enq_rec er;
                if (!decodeRecord(er, cum_size_read, h, start_fid, file_pos)) {
                    return false;
                }
                if (!er.is_transient()) { // Ignore transient msgs
                    fileNumberMap_[start_fid]->journalFilePtr_->incrEnqueuedRecordCount();
                    if (er.xid_size()) {
                        er.get_xid(&xidp);
                        if (xidp == 0) {
                            throw jexception(jerrno::JERR_RCVM_NULLXID, "ENQ", "RecoveryManager", "getNextRecordHeader");
                        }
                        std::string xid((char*)xidp, er.xid_size());
                        transactionMapRef_.insert_txn_data(xid, txn_data_t(h._rid, 0, start_fid, file_pos, true, false, false));
                        if (transactionMapRef_.set_aio_compl(xid, h._rid) < txn_map::TMAP_OK) { // fail - xid or rid not found
                            std::ostringstream oss;
                            oss << std::hex << "_tmap.set_aio_compl: txn_enq xid=\"" << xid << "\" rid=0x" << h._rid;
                            throw jexception(jerrno::JERR_MAP_NOTFOUND, oss.str(), "RecoveryManager", "getNextRecordHeader");
                        }
                    } else {
                        if (enqueueMapRef_.insert_pfid(h._rid, start_fid, file_pos) < enq_map::EMAP_OK) { // fail
                            // The only error code emap::insert_pfid() returns is enq_map::EMAP_DUP_RID.
                            std::ostringstream oss;
                            oss << std::hex << "rid=0x" << h._rid << " _pfid=0x" << start_fid;
                            throw jexception(jerrno::JERR_MAP_DUPLICATE, oss.str(), "RecoveryManager", "getNextRecordHeader");
                        }
                    }
                }
            }
            break;
        case QLS_DEQ_MAGIC:
            {
//std::cout << " 0x" << std::hex << file_pos << ".d.0x" << h._rid << std::dec << std::flush; // DEBUG
                if (::rec_hdr_check(&h, QLS_DEQ_MAGIC, QLS_JRNL_VERSION, currentSerial_) != 0) {
                    checkJournalAlignment(file_id, file_pos);
                    return false;
                }
                deq_rec dr;
                if (!decodeRecord(dr, cum_size_read, h, start_fid, file_pos)) {
                    return false;
                }
                if (dr.xid_size()) {
                    // If the enqueue is part of a pending txn, it will not yet be in emap
                    enqueueMapRef_.lock(dr.deq_rid()); // ignore not found error
                    dr.get_xid(&xidp);
                    if (xidp == 0) {
                        throw jexception(jerrno::JERR_RCVM_NULLXID, "DEQ", "RecoveryManager", "getNextRecordHeader");
                    }
                    std::string xid((char*)xidp, dr.xid_size());
                    transactionMapRef_.insert_txn_data(xid, txn_data_t(dr.rid(), dr.deq_rid(), start_fid, file_pos,
                                                       false, false, dr.is_txn_coml_commit()));
                    if (transactionMapRef_.set_aio_compl(xid, dr.rid()) < txn_map::TMAP_OK) { // fail - xid or rid not found
                        std::ostringstream oss;
                        oss << std::hex << "_tmap.set_aio_compl: txn_deq xid=\"" << xid << "\" rid=0x" << dr.rid();
                        throw jexception(jerrno::JERR_MAP_NOTFOUND, oss.str(), "RecoveryManager", "getNextRecordHeader");
                    }
                } else {
                    uint64_t enq_fid;
                    if (enqueueMapRef_.get_remove_pfid(dr.deq_rid(), enq_fid, true) == enq_map::EMAP_OK) { // ignore not found error
                        fileNumberMap_[enq_fid]->journalFilePtr_->decrEnqueuedRecordCount();
                    }
                }
            }
            break;
        case QLS_TXA_MAGIC:
            {
//std::cout << " 0x" << std::hex << file_pos << ".a.0x" << h._rid << std::dec << std::flush; // DEBUG
                if (::rec_hdr_check(&h, QLS_TXA_MAGIC, QLS_JRNL_VERSION, currentSerial_) != 0) {
                    checkJournalAlignment(file_id, file_pos);
                    return false;
                }
                txn_rec ar;
                if (!decodeRecord(ar, cum_size_read, h, start_fid, file_pos)) {
                    return false;
                }
                // Delete this txn from tmap, unlock any locked records in emap
                ar.get_xid(&xidp);
                if (xidp == 0) {
                    throw jexception(jerrno::JERR_RCVM_NULLXID, "ABT", "RecoveryManager", "getNextRecordHeader");
                }
                std::string xid((char*)xidp, ar.xid_size());
                txn_data_list_t tdl = transactionMapRef_.get_remove_tdata_list(xid); // tdl will be empty if xid not found
                for (tdl_itr_t itr = tdl.begin(); itr != tdl.end(); itr++) {
                    if (itr->enq_flag_) {
                        fileNumberMap_[itr->fid_]->journalFilePtr_->decrEnqueuedRecordCount();
                    } else {
                        enqueueMapRef_.unlock(itr->drid_); // ignore not found error
                    }
                }
            }
            break;
        case QLS_TXC_MAGIC:
            {
//std::cout << " 0x" << std::hex << file_pos << ".c.0x" << h._rid << std::dec << std::flush; // DEBUG
                if (::rec_hdr_check(&h, QLS_TXC_MAGIC, QLS_JRNL_VERSION, currentSerial_) != 0) {
                    checkJournalAlignment(file_id, file_pos);
                    return false;
                }
                txn_rec cr;
                if (!decodeRecord(cr, cum_size_read, h, start_fid, file_pos)) {
                    return false;
                }
                // Delete this txn from tmap, process records into emap
                cr.get_xid(&xidp);
                if (xidp == 0) {
                    throw jexception(jerrno::JERR_RCVM_NULLXID, "CMT", "RecoveryManager", "getNextRecordHeader");
                }
                std::string xid((char*)xidp, cr.xid_size());
                txn_data_list_t tdl = transactionMapRef_.get_remove_tdata_list(xid); // tdl will be empty if xid not found
                for (tdl_itr_t itr = tdl.begin(); itr != tdl.end(); itr++) {
                    if (itr->enq_flag_) { // txn enqueue
//std::cout << "[rid=0x" << std::hex << itr->rid_ << std::dec << " fid=" << itr->fid_ << " fpos=0x" << std::hex << itr->foffs_ << "]" << std::dec << std::flush; // DEBUG
                        if (enqueueMapRef_.insert_pfid(itr->rid_, itr->fid_, itr->foffs_) < enq_map::EMAP_OK) { // fail
                            // The only error code emap::insert_pfid() returns is enq_map::EMAP_DUP_RID.
                            std::ostringstream oss;
                            oss << std::hex << "rid=0x" << itr->rid_ << " _pfid=0x" << itr->fid_;
                            throw jexception(jerrno::JERR_MAP_DUPLICATE, oss.str(), "RecoveryManager", "getNextRecordHeader");
                        }
                    } else { // txn dequeue
                        uint64_t enq_fid;
                        if (enqueueMapRef_.get_remove_pfid(itr->drid_, enq_fid, true) == enq_map::EMAP_OK) // ignore not found error
                            fileNumberMap_[enq_fid]->journalFilePtr_->decrEnqueuedRecordCount();
                    }
                }
            }
            break;
        case QLS_EMPTY_MAGIC:
            {
//std::cout << ".x" << std::flush; // DEBUG
                uint32_t rec_dblks = jrec::size_dblks(sizeof(::rec_hdr_t));
                inFileStream_.ignore(rec_dblks * QLS_DBLK_SIZE_BYTES - sizeof(::rec_hdr_t));
                checkFileStreamOk(false);
                if (needNextFile()) {
                    file_pos += rec_dblks * QLS_DBLK_SIZE_BYTES;
                    if (!getNextFile(false)) {
                        lastRecord(start_fid, file_pos);
                        return false;
                    }
                }
            }
            break;
        case 0:
//std::cout << " 0x" << std::hex << file_pos << ".0" << std::dec << std::endl << std::flush; // DEBUG
            checkJournalAlignment(getCurrentFileNumber(), file_pos);
            return false;
        default:
//std::cout << " 0x" << std::hex << file_pos << ".?" << std::dec << std::endl << std::flush; // DEBUG
            // Stop as this is the overwrite boundary.
            checkJournalAlignment(getCurrentFileNumber(), file_pos);
            return false;
    }
    return true;
}

void RecoveryManager::lastRecord(const uint64_t file_id, const std::streamoff endOffset) {
    endOffset_ = endOffset;
    initial_fid_ = file_id;
    fileNumberMap_[file_id]->completedDblkCount_ = endOffset_ / QLS_DBLK_SIZE_BYTES;

    // Remove any files in fileNumberMap_ beyond initial_fid_
    fileNumberMapItr_t unwantedFirstItr = fileNumberMap_.find(file_id);
    if (++unwantedFirstItr != fileNumberMap_.end()) {
        fileNumberMapItr_t itr = unwantedFirstItr;
        notNeededFilesList_.push_back(unwantedFirstItr->second->journalFilePtr_->getFqFileName());
        while (++itr != fileNumberMap_.end()) {
            notNeededFilesList_.push_back(itr->second->journalFilePtr_->getFqFileName());
            delete itr->second->journalFilePtr_;
            delete itr->second;
        }
        fileNumberMap_.erase(unwantedFirstItr, fileNumberMap_.end());
    }
}

bool RecoveryManager::needNextFile() {
    if (inFileStream_.is_open()) {
        return inFileStream_.eof() || inFileStream_.tellg() >= std::streampos(efpFileSize_kib_ * 1024);
    }
    return true;
}

void RecoveryManager::prepareRecordList() {
    // Set up recordIdList_ from enqueue map and transaction map
    recordIdList_.clear();

    // Extract records from enqueue list
    std::vector<uint64_t> ridList;
    enqueueMapRef_.rid_list(ridList);
    qpid::linearstore::journal::enq_map::emap_data_struct_t eds;
    for (std::vector<uint64_t>::const_iterator i=ridList.begin(); i!=ridList.end(); ++i) {
        enqueueMapRef_.get_data(*i, eds);
        recordIdList_.push_back(RecoveredRecordData_t(*i, eds._pfid, eds._file_posn, false));
    }

    // Extract records from pending transaction enqueues
    std::vector<std::string> xidList;
    transactionMapRef_.xid_list(xidList);
    for (std::vector<std::string>::const_iterator j=xidList.begin(); j!=xidList.end(); ++j) {
        qpid::linearstore::journal::txn_data_list_t tdsl = transactionMapRef_.get_tdata_list(*j);
        for (qpid::linearstore::journal::tdl_itr_t k=tdsl.begin(); k!=tdsl.end(); ++k) {
            if (k->enq_flag_) {
                recordIdList_.push_back(RecoveredRecordData_t(k->rid_, k->fid_, k->foffs_, true));
            }
        }
    }

    std::sort(recordIdList_.begin(), recordIdList_.end(), recordIdListCompare);
    recordIdListConstItr_ = recordIdList_.begin();
}

void RecoveryManager::readJournalData(char* target,
                                      const std::streamsize readSize) {
    std::streamoff bytesRead = 0;
    while (bytesRead < readSize) {
        std::streampos file_pos = inFileStream_.tellg();
        if (file_pos == std::streampos(-1)) {
            std::ostringstream oss;
            oss << "tellg() failure: fail=" << (inFileStream_.fail()?"T":"F") << " bad=" << (inFileStream_.bad()?"T":"F");
            throw jexception(jerrno::JERR_RCVM_STREAMBAD, oss.str(), "RecoveryManager", "readJournalData");
        }
        inFileStream_.read(target + bytesRead, readSize - bytesRead);
        std::streamoff thisReadSize = inFileStream_.gcount();
        if (thisReadSize < readSize) {
            if (needNextFile()) {
                getNextFile(false);
            }
            file_pos = inFileStream_.tellg();
            if (file_pos == std::streampos(-1)) {
                std::ostringstream oss;
                oss << "tellg() failure: fail=" << (inFileStream_.fail()?"T":"F") << " bad=" << (inFileStream_.bad()?"T":"F");
                throw jexception(jerrno::JERR_RCVM_STREAMBAD, oss.str(), "RecoveryManager", "readJournalData");
            }
        }
        bytesRead += thisReadSize;
    }
}

bool RecoveryManager::readFileHeader() {
    file_hdr_t fhdr;
    inFileStream_.read((char*)&fhdr, sizeof(fhdr));
    checkFileStreamOk(true);
    if (::file_hdr_check(&fhdr, QLS_FILE_MAGIC, QLS_JRNL_VERSION, efpFileSize_kib_, QLS_MAX_QUEUE_NAME_LEN) != 0) {
        firstRecordOffset_ = fhdr._fro;
        currentSerial_ = fhdr._rhdr._serial;
    } else {
        inFileStream_.close();
        if (currentJournalFileItr_ == fileNumberMap_.begin()) {
            journalEmptyFlag_ = true;
        }
        return false;
    }
    return true;
}

// static private
bool RecoveryManager::readJournalFileHeader(const std::string& journalFileName,
                                            ::file_hdr_t& fileHeaderRef,
                                            std::string& queueName) {
    const std::size_t headerBlockSize = QLS_JRNL_FHDR_RES_SIZE_SBLKS * QLS_SBLK_SIZE_KIB * 1024;
    char buffer[headerBlockSize];
    std::ifstream ifs(journalFileName.c_str(), std::ifstream::in | std::ifstream::binary);
    if (!ifs.good()) {
        std::ostringstream oss;
        oss << "File=" << journalFileName;
        throw jexception(jerrno::JERR_RCVM_OPENRD, oss.str(), "RecoveryManager", "readJournalFileHeader");
    }
    ifs.read(buffer, headerBlockSize);
    if (!ifs) {
        std::streamsize s = ifs.gcount();
        ifs.close();
        std::ostringstream oss;
        oss << "File=" << journalFileName << "; attempted_read_size=" << headerBlockSize << "; actual_read_size=" << s;
        throw jexception(jerrno::JERR_RCVM_READ, oss.str(), "RecoveryManager", "readJournalFileHeader");
    }
    ifs.close();
    ::memcpy(&fileHeaderRef, buffer, sizeof(::file_hdr_t));
    if (::file_hdr_check(&fileHeaderRef, QLS_FILE_MAGIC, QLS_JRNL_VERSION, 0, QLS_MAX_QUEUE_NAME_LEN)) {
        return false;
    }
    queueName.assign(buffer + sizeof(::file_hdr_t), fileHeaderRef._queue_name_len);
    return true;
}

void RecoveryManager::removeEmptyFiles(EmptyFilePool* emptyFilePoolPtr) {
    while (fileNumberMap_.begin()->second->journalFilePtr_->getEnqueuedRecordCount() == 0 && fileNumberMap_.size() > 1) {
        RecoveredFileData_t* rfdp = fileNumberMap_.begin()->second;
        emptyFilePoolPtr->returnEmptyFileSymlink(rfdp->journalFilePtr_->getFqFileName());
        delete rfdp->journalFilePtr_;
        delete rfdp;
        fileNumberMap_.erase(fileNumberMap_.begin()->first);
    }
}

}}}
