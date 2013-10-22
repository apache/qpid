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

#include "qpid/linearstore/jrnl/RecoveryManager.h"

#include <algorithm>
#include <cstdlib>
#include <iomanip>
#include "qpid/linearstore/jrnl/data_tok.h"
#include "qpid/linearstore/jrnl/deq_rec.h"
#include "qpid/linearstore/jrnl/EmptyFilePoolManager.h"
#include "qpid/linearstore/jrnl/enq_map.h"
#include "qpid/linearstore/jrnl/enq_rec.h"
#include "qpid/linearstore/jrnl/jcfg.h"
#include "qpid/linearstore/jrnl/jdir.h"
#include "qpid/linearstore/jrnl/JournalLog.h"
#include "qpid/linearstore/jrnl/jrec.h"
#include "qpid/linearstore/jrnl/LinearFileController.h"
#include "qpid/linearstore/jrnl/txn_map.h"
#include "qpid/linearstore/jrnl/txn_rec.h"
#include "qpid/linearstore/jrnl/utils/enq_hdr.h"
#include "qpid/linearstore/jrnl/utils/file_hdr.h"
#include <sstream>
#include <string>
#include <vector>

namespace qpid {
namespace qls_jrnl
{

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
                                                 fileSize_kib_(0)
{}

RecoveryManager::~RecoveryManager() {}

void RecoveryManager::analyzeJournals(const std::vector<std::string>* preparedTransactionListPtr,
                                      EmptyFilePoolManager* emptyFilePoolManager,
                                      EmptyFilePool** emptyFilePoolPtrPtr) {
    // Analyze file headers of existing journal files
    efpIdentity_t efpIdentity;
    analyzeJournalFileHeaders(efpIdentity);
    *emptyFilePoolPtrPtr = emptyFilePoolManager->getEmptyFilePool(efpIdentity);
    fileSize_kib_ = (*emptyFilePoolPtrPtr)->fileSize_kib();
    // Check for file full condition
    lastFileFullFlag_ = endOffset_ == (std::streamoff)(*emptyFilePoolPtrPtr)->fileSize_kib() * 1024;

    // Restore all read and write pointers and transactions
    if (!journalEmptyFlag_) {
        while (getNextRecordHeader()) {

        }
        if (inFileStream_.is_open()) {
            inFileStream_.close();
        }
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
                    txn_data_list tdl = transactionMapRef_.get_remove_tdata_list(*itr); // tdl will be empty if xid not found
                    // Unlock any affected enqueues in emap
                    for (tdl_itr i=tdl.begin(); i<tdl.end(); i++) {
                        if (i->_enq_flag) { // enq op - decrement enqueue count
                            enqueueCountList_[i->_pfid]--;
                        } else if (enqueueMapRef_.is_enqueued(i->_drid, true)) { // deq op - unlock enq record
                            int16_t ret = enqueueMapRef_.unlock(i->_drid);
                            if (ret < enq_map::EMAP_OK) { // fail
                                // enq_map::unlock()'s only error is enq_map::EMAP_RID_NOT_FOUND
                                std::ostringstream oss;
                                oss << std::hex << "_emap.unlock(): drid=0x\"" << i->_drid;
                                throw jexception(jerrno::JERR_MAP_NOTFOUND, oss.str(), "RecoveryManager", "analyzeJournals");
                            }
                        }
                    }
                }
            }
        }
        enqueueMapRef_.rid_list(recordIdList_);
        recordIdListConstItr_ = recordIdList_.begin();
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
                                              bool /*ignore_pending_txns*/) {
    if (!dtokp->is_readable()) {
        std::ostringstream oss;
        oss << std::hex << std::setfill('0') << "dtok_id=0x" << std::setw(8) << dtokp->id();
        oss << "; dtok_rid=0x" << std::setw(16) << dtokp->rid() << "; dtok_wstate=" << dtokp->wstate_str();
        throw jexception(jerrno::JERR_JCNTL_ENQSTATE, oss.str(), "RecoveryManager", "readNextRemainingRecord");
    }
    if (recordIdListConstItr_ == recordIdList_.end()) {
        return false;
    }
    enq_map::emap_data_struct_t eds;
    enqueueMapRef_.get_data(*recordIdListConstItr_, eds);
    uint64_t fileNumber = eds._pfid;
    currentJournalFileConstItr_ = fileNumberNameMap_.find(fileNumber);
    getNextFile(false);

    inFileStream_.seekg(eds._file_posn, std::ifstream::beg);
    if (!inFileStream_.good()) {
        std::ostringstream oss;
        oss << "Could not find offset 0x" << std::hex << eds._file_posn << " in file " << getCurrentFileName();
        throw jexception(jerrno::JERR__FILEIO, oss.str(), "RecoveryManager", "readNextRemainingRecord");
    }
    ::enq_hdr_t enqueueHeader;
    inFileStream_.read((char*)&enqueueHeader, sizeof(::enq_hdr_t));
    if (inFileStream_.gcount() != sizeof(::enq_hdr_t)) {
        std::ostringstream oss;
        oss << "Could not read enqueue header from file " << getCurrentFileName() << " at offset 0x" << std::hex << eds._file_posn;
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
    return true;
}

void RecoveryManager::setLinearFileControllerJournals(lfcAddJournalFileFn fnPtr,
                                                      LinearFileController* lfcPtr) {
//std::cout << "****** RecoveryManager::setLinearFileControllerJournals():" << std::endl; // DEBUG
    for (fileNumberNameMapConstItr_t i = fileNumberNameMap_.begin(); i != fileNumberNameMap_.end(); ++i) {
        uint32_t fileDblkCount = i->first == highestFileNumber_ ?               // Is this this last file?
                                 endOffset_ / QLS_DBLK_SIZE_BYTES :            // Last file uses _endOffset
                                 fileSize_kib_ * 1024 / QLS_DBLK_SIZE_BYTES;   // All others use file size to make them full
        (lfcPtr->*fnPtr)(i->second, i->first, fileSize_kib_, fileDblkCount);
//std::cout << "   ** f=" << i->second.substr(i->second.rfind('/')+1) << ",fn=" << i->first << ",s=" << _fileSize_kib << ",eo=" << fileDblkCount << "(" << (fileDblkCount * QLS_DBLK_SIZE_BYTES / 1024) << "kiB)" << std::endl; // DEBUG
    }
}

std::string RecoveryManager::toString(const std::string& jid,
                                      bool compact) {
    std::ostringstream oss;
    if (compact) {
        oss << "Recovery journal analysis (jid=\"" << jid << "\"):";
        oss << " jfl=[";
        for (fileNumberNameMapConstItr_t i=fileNumberNameMap_.begin(); i!=fileNumberNameMap_.end(); ++i) {
            if (i!=fileNumberNameMap_.begin()) oss << " ";
            oss << i->first << ":" << i->second.substr(i->second.rfind('/')+1);
        }
        oss << "] ecl=[ ";
        for (enqueueCountListConstItr_t j = enqueueCountList_.begin(); j!=enqueueCountList_.end(); ++j) {
            if (j != enqueueCountList_.begin()) oss << " ";
            oss << *j;
        }
        oss << " ] empty=" << (journalEmptyFlag_ ? "T" : "F");
        oss << " fro=0x" << std::hex << firstRecordOffset_ << std::dec << " (" << (firstRecordOffset_/QLS_DBLK_SIZE_BYTES) << " dblks)";
        oss << " eo=0x" << std::hex << endOffset_ << std::dec << " ("  << (endOffset_/QLS_DBLK_SIZE_BYTES) << " dblks)";
        oss << " hrid=0x" << std::hex << highestRecordId_ << std::dec;
        oss << " hfnum=0x" << std::hex << highestFileNumber_ << std::dec;
        oss << " lffull=" << (lastFileFullFlag_ ? "T" : "F");
    } else {
        oss << "Recovery journal analysis (jid=\"" << jid << "\"):" << std::endl;
        oss << "  Number of journal files = " << fileNumberNameMap_.size() << std::endl;
        oss << "  Journal File List:" << std::endl;
        for (fileNumberNameMapConstItr_t i=fileNumberNameMap_.begin(); i!=fileNumberNameMap_.end(); ++i) {
            oss << "    " << i->first << ": " << i->second.substr(i->second.rfind('/')+1) << std::endl;
        }
        oss << "  Enqueue Counts: [ " << std::endl;
        for (enqueueCountListConstItr_t j = enqueueCountList_.begin(); j!=enqueueCountList_.end(); ++j) {
            if (j != enqueueCountList_.begin()) oss << ", ";
            oss << *j;
        }
        oss << " ]" << std::endl;
        oss << "  Journal empty = " << (journalEmptyFlag_ ? "TRUE" : "FALSE") << std::endl;
        oss << "  First record offset in first file = 0x" << std::hex << firstRecordOffset_ <<
                std::dec << " (" << (firstRecordOffset_/QLS_DBLK_SIZE_BYTES) << " dblks)" << std::endl;
        oss << "  End offset = 0x" << std::hex << endOffset_ << std::dec << " ("  <<
                (endOffset_/QLS_DBLK_SIZE_BYTES) << " dblks)" << std::endl;
        oss << "  Highest rid = 0x" << std::hex << highestRecordId_ << std::dec << std::endl;
        oss << "  Highest file number = 0x" << std::hex << highestFileNumber_ << std::dec << std::endl;
        oss << "  Last file full = " << (lastFileFullFlag_ ? "TRUE" : "FALSE") << std::endl;
        oss << "  Enqueued records (txn & non-txn):" << std::endl;
    }
    return oss.str();
}

// --- protected functions ---

void RecoveryManager::analyzeJournalFileHeaders(efpIdentity_t& efpIdentity) {
    std::string headerQueueName;
    ::file_hdr_t fileHeader;
    directoryList_t directoryList;
    jdir::read_dir(journalDirectory_, directoryList, false, true, false, true);
    for (directoryListConstItr_t i = directoryList.begin(); i != directoryList.end(); ++i) {
        readJournalFileHeader(*i, fileHeader, headerQueueName);
        if (headerQueueName.compare(queueName_) != 0) {
            std::ostringstream oss;
            oss << "Journal file " << (*i) << " belongs to queue \"" << headerQueueName << "\": ignoring";
            journalLogRef_.log(JournalLog::LOG_WARN, queueName_, oss.str());
        } else {
            fileNumberNameMap_[fileHeader._file_number] = *i;
            if (fileHeader._file_number > highestFileNumber_) {
                highestFileNumber_ = fileHeader._file_number;
            }
        }
    }
    efpIdentity.first = fileHeader._efp_partition;
    efpIdentity.second = fileHeader._file_size_kib;
    enqueueCountList_.resize(fileNumberNameMap_.size(), 0);
    currentJournalFileConstItr_ = fileNumberNameMap_.begin();
}

void RecoveryManager::checkFileStreamOk(bool checkEof) {
    if (inFileStream_.fail() || inFileStream_.bad() || checkEof ? inFileStream_.eof() : false) {
        throw jexception("read failure"); // TODO complete exception
    }
}

void RecoveryManager::checkJournalAlignment(const std::streampos recordPosition) {
    std::streampos currentPosn = recordPosition;
    unsigned sblkOffset = currentPosn % QLS_SBLK_SIZE_BYTES;
    if (sblkOffset)
    {
        std::ostringstream oss1;
        oss1 << std::hex << "Bad record alignment found at fid=0x" << getCurrentFileNumber();
        oss1 << " offs=0x" << currentPosn << " (likely journal overwrite boundary); " << std::dec;
        oss1 << (QLS_SBLK_SIZE_DBLKS - (sblkOffset/QLS_DBLK_SIZE_BYTES)) << " filler record(s) required.";
        journalLogRef_.log(JournalLog::LOG_WARN, queueName_, oss1.str());

        std::ofstream outFileStream(getCurrentFileName().c_str(), std::ios_base::in | std::ios_base::out | std::ios_base::binary);
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
            oss2 << std::hex << "Recover phase write: Wrote filler record: fid=0x" << getCurrentFileNumber();
            oss2 << " offs=0x" << currentPosn;
            journalLogRef_.log(JournalLog::LOG_NOTICE, queueName_, oss2.str());
            currentPosn = outFileStream.tellp();
        }
        outFileStream.close();
        std::free(writeBuffer);
        journalLogRef_.log(JournalLog::LOG_INFO, queueName_, "Bad record alignment fixed.");
    }
    endOffset_ = currentPosn;
}

bool RecoveryManager::decodeRecord(jrec& record,
                              std::size_t& cumulativeSizeRead,
                              ::rec_hdr_t& headerRecord,
                              std::streampos& fileOffset)
{
//    uint16_t start_fid = getCurrentFileNumber();
    std::streampos start_file_offs = fileOffset;

    if (highestRecordId_ == 0) {
        highestRecordId_ = headerRecord._rid;
    } else if (headerRecord._rid - highestRecordId_ < 0x8000000000000000ULL) { // RFC 1982 comparison for unsigned 64-bit
        highestRecordId_ = headerRecord._rid;
    }

    bool done = false;
    while (!done) {
        try {
            done = record.rcv_decode(headerRecord, &inFileStream_, cumulativeSizeRead);
        }
        catch (const jexception& e) {
// TODO - review this logic and tidy up how rd._lfid is assigned. See new jinf.get_end_file() fn.
// Original
//             if (e.err_code() != jerrno::JERR_JREC_BADRECTAIL ||
//                     fid != (rd._ffid ? rd._ffid - 1 : _num_jfiles - 1)) throw;
// Tried this, but did not work
//             if (e.err_code() != jerrno::JERR_JREC_BADRECTAIL || h._magic != 0) throw;
            checkJournalAlignment(start_file_offs);
//             rd._lfid = start_fid;
            return false;
        }
        if (!done && !getNextFile(false)) {
            checkJournalAlignment(start_file_offs);
            return false;
        }
    }
    return true;
}

std::string RecoveryManager::getCurrentFileName() const {
    return currentJournalFileConstItr_->second;
}

uint64_t RecoveryManager::getCurrentFileNumber() const {
    return currentJournalFileConstItr_->first;
}

bool RecoveryManager::getNextFile(bool jumpToFirstRecordOffsetFlag) {
    if (inFileStream_.is_open()) {
        if (inFileStream_.eof() || !inFileStream_.good())
        {
            inFileStream_.clear();
            endOffset_ = inFileStream_.tellg(); // remember file offset before closing
            if (endOffset_ == -1) { throw jexception("tellg() failure"); } // Check for error code -1 TODO: compelete exception
            inFileStream_.close();
            if (++currentJournalFileConstItr_ == fileNumberNameMap_.end()) {
                return false;
            }
        }
    }
    if (!inFileStream_.is_open())
    {
        inFileStream_.clear(); // clear eof flag, req'd for older versions of c++
        inFileStream_.open(getCurrentFileName().c_str(), std::ios_base::in | std::ios_base::binary);
        if (!inFileStream_.good()) {
            throw jexception(jerrno::JERR__FILEIO, getCurrentFileName(), "RecoveryManager", "getNextFile");
        }

        // Read file header
//std::cout << " F" << getCurrentFileNumber() << std::flush; // DEBUG
        file_hdr_t fhdr;
        inFileStream_.read((char*)&fhdr, sizeof(fhdr));
        checkFileStreamOk(true);
        if (fhdr._rhdr._magic == QLS_FILE_MAGIC) {
            firstRecordOffset_ = fhdr._fro;
            std::streamoff foffs = jumpToFirstRecordOffsetFlag ? firstRecordOffset_ : QLS_SBLK_SIZE_BYTES;
            inFileStream_.seekg(foffs);
        } else {
            inFileStream_.close();
            if (currentJournalFileConstItr_ == fileNumberNameMap_.begin()) {
                journalEmptyFlag_ = true;
            }
            return false;
        }
    }
    return true;
}

bool RecoveryManager::getNextRecordHeader()
{
    std::size_t cum_size_read = 0;
    void* xidp = 0;
    rec_hdr_t h;

    bool hdr_ok = false;
    std::streampos file_pos;
    while (!hdr_ok) {
        if (!inFileStream_.is_open()) {
            if (!getNextFile(true)) {
                return false;
            }
        }
        file_pos = inFileStream_.tellg();
//std::cout << " 0x" << std::hex << file_pos << std::dec; // DEBUG
        inFileStream_.read((char*)&h, sizeof(rec_hdr_t));
        if (inFileStream_.gcount() == sizeof(rec_hdr_t)) {
            hdr_ok = true;
        } else {
            if (!getNextFile(true)) {
                return false;
            }
        }
    }

    switch(h._magic) {
        case QLS_ENQ_MAGIC:
            {
//std::cout << ".e" << std::flush; // DEBUG
                enq_rec er;
                uint64_t start_fid = getCurrentFileNumber(); // fid may increment in decode() if record folds over file boundary
                if (!decodeRecord(er, cum_size_read, h, file_pos)) {
                    return false;
                }
                if (!er.is_transient()) { // Ignore transient msgs
                    enqueueCountList_[start_fid]++;
                    if (er.xid_size()) {
                        er.get_xid(&xidp);
                        if (xidp != 0) { throw jexception("Null xid with non-null xid_size"); } // TODO complete exception
                        std::string xid((char*)xidp, er.xid_size());
                        transactionMapRef_.insert_txn_data(xid, txn_data(h._rid, 0, start_fid, true));
                        if (transactionMapRef_.set_aio_compl(xid, h._rid) < txn_map::TMAP_OK) { // fail - xid or rid not found
                            std::ostringstream oss;
                            oss << std::hex << "_tmap.set_aio_compl: txn_enq xid=\"" << xid << "\" rid=0x" << h._rid;
                            throw jexception(jerrno::JERR_MAP_NOTFOUND, oss.str(), "RecoveryManager", "getNextRecordHeader");
                        }
                        std::free(xidp);
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
//std::cout << ".d" << std::flush; // DEBUG
                deq_rec dr;
                uint16_t start_fid = getCurrentFileNumber(); // fid may increment in decode() if record folds over file boundary
                if (!decodeRecord(dr, cum_size_read, h, file_pos)) {
                    return false;
                }
                if (dr.xid_size()) {
                    // If the enqueue is part of a pending txn, it will not yet be in emap
                    enqueueMapRef_.lock(dr.deq_rid()); // ignore not found error
                    dr.get_xid(&xidp);
                    if (xidp != 0) { throw jexception("Null xid with non-null xid_size"); } // TODO complete exception
                    std::string xid((char*)xidp, dr.xid_size());
                    transactionMapRef_.insert_txn_data(xid, txn_data(dr.rid(), dr.deq_rid(), start_fid, false,
                            dr.is_txn_coml_commit()));
                    if (transactionMapRef_.set_aio_compl(xid, dr.rid()) < txn_map::TMAP_OK) { // fail - xid or rid not found
                        std::ostringstream oss;
                        oss << std::hex << "_tmap.set_aio_compl: txn_deq xid=\"" << xid << "\" rid=0x" << dr.rid();
                        throw jexception(jerrno::JERR_MAP_NOTFOUND, oss.str(), "RecoveryManager", "getNextRecordHeader");
                    }
                    std::free(xidp);
                } else {
                    uint64_t enq_fid;
                    if (enqueueMapRef_.get_remove_pfid(dr.deq_rid(), enq_fid, true) == enq_map::EMAP_OK) { // ignore not found error
                        enqueueCountList_[enq_fid]--;
                    }
                }
            }
            break;
        case QLS_TXA_MAGIC:
            {
//std::cout << ".a" << std::flush; // DEBUG
                txn_rec ar;
                if (!decodeRecord(ar, cum_size_read, h, file_pos)) {
                    return false;
                }
                // Delete this txn from tmap, unlock any locked records in emap
                ar.get_xid(&xidp);
                if (xidp != 0) {
                    throw jexception("Null xid with non-null xid_size"); // TODO complete exception
                }
                std::string xid((char*)xidp, ar.xid_size());
                txn_data_list tdl = transactionMapRef_.get_remove_tdata_list(xid); // tdl will be empty if xid not found
                for (tdl_itr itr = tdl.begin(); itr != tdl.end(); itr++) {
                    if (itr->_enq_flag) {
                        enqueueCountList_[itr->_pfid]--;
                    } else {
                        enqueueMapRef_.unlock(itr->_drid); // ignore not found error
                    }
                }
                std::free(xidp);
            }
            break;
        case QLS_TXC_MAGIC:
            {
//std::cout << ".t" << std::flush; // DEBUG
                txn_rec cr;
                if (!decodeRecord(cr, cum_size_read, h, file_pos)) {
                    return false;
                }
                // Delete this txn from tmap, process records into emap
                cr.get_xid(&xidp);
                if (xidp != 0) {
                    throw jexception("Null xid with non-null xid_size"); // TODO complete exception
                }
                std::string xid((char*)xidp, cr.xid_size());
                txn_data_list tdl = transactionMapRef_.get_remove_tdata_list(xid); // tdl will be empty if xid not found
                for (tdl_itr itr = tdl.begin(); itr != tdl.end(); itr++) {
                    if (itr->_enq_flag) { // txn enqueue
                        if (enqueueMapRef_.insert_pfid(itr->_rid, itr->_pfid, file_pos) < enq_map::EMAP_OK) { // fail
                            // The only error code emap::insert_pfid() returns is enq_map::EMAP_DUP_RID.
                            std::ostringstream oss;
                            oss << std::hex << "rid=0x" << itr->_rid << " _pfid=0x" << itr->_pfid;
                            throw jexception(jerrno::JERR_MAP_DUPLICATE, oss.str(), "RecoveryManager", "getNextRecordHeader");
                        }
                    } else { // txn dequeue
                        uint64_t enq_fid;
                        if (enqueueMapRef_.get_remove_pfid(itr->_drid, enq_fid, true) == enq_map::EMAP_OK) // ignore not found error
                            enqueueCountList_[enq_fid]--;
                    }
                }
                std::free(xidp);
            }
            break;
        case QLS_EMPTY_MAGIC:
            {
//std::cout << ".x" << std::flush; // DEBUG
                uint32_t rec_dblks = jrec::size_dblks(sizeof(::rec_hdr_t));
                inFileStream_.ignore(rec_dblks * QLS_DBLK_SIZE_BYTES - sizeof(::rec_hdr_t));
                checkFileStreamOk(false);
                if (!getNextFile(false)) {
                    return false;
                }
            }
            break;
        case 0:
//std::cout << ".0" << std::endl << std::flush; // DEBUG
            checkJournalAlignment(file_pos);
            return false;
        default:
//std::cout << ".?" << std::endl << std::flush; // DEBUG
            // Stop as this is the overwrite boundary.
            checkJournalAlignment(file_pos);
            return false;
    }
    return true;
}

void RecoveryManager::readJournalData(char* target,
                                      const std::streamsize readSize) {
    std::streamoff bytesRead = 0;
    while (bytesRead < readSize) {
        if (inFileStream_.eof()) {
            getNextFile(false);
        }
        bool readFitsInFile = inFileStream_.tellg() + readSize <= fileSize_kib_ * 1024;
        std::streamoff readSize = readFitsInFile ? readSize : (fileSize_kib_ * 1024) - inFileStream_.tellg();
        inFileStream_.read(target + bytesRead, readSize);
        if (inFileStream_.gcount() != readSize) {
            throw jexception(); // TODO - proper exception
        }
        bytesRead += readSize;
    }
}

// static private
void RecoveryManager::readJournalFileHeader(const std::string& journalFileName,
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
    queueName.assign(buffer + sizeof(::file_hdr_t), fileHeaderRef._queue_name_len);

}

void RecoveryManager::removeEmptyFiles(EmptyFilePool* emptyFilePoolPtr) {
    while (enqueueCountList_.front() == 0 && enqueueCountList_.size() > 1) {
        fileNumberNameMapItr_t i = fileNumberNameMap_.begin();
//std::cout << "*** File " << i->first << ": " << i->second << " is empty." << std::endl;
        emptyFilePoolPtr->returnEmptyFile(i->second);
        fileNumberNameMap_.erase(i);
        enqueueCountList_.pop_front();
    }
}

}} // namespace qpid::qls_jrnl
