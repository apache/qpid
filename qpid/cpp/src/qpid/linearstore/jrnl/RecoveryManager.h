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

#ifndef QPID_LINEARSTORE_RECOVERYSTATE_H_
#define QPID_LINEARSTORE_RECOVERYSTATE_H_

#include <deque>
#include <fstream>
#include <map>
#include "qpid/linearstore/jrnl/LinearFileController.h"
#include <stdint.h>
#include <vector>

struct file_hdr_t;
struct rec_hdr_t;

namespace qpid {
namespace qls_jrnl {

class data_tok;
class enq_map;
class EmptyFilePool;
class EmptyFilePoolManager;
class JournalLog;
class jrec;
class txn_map;

class RecoveryManager
{
protected:
    // Types
    typedef std::vector<std::string> directoryList_t;
    typedef directoryList_t::const_iterator directoryListConstItr_t;
    typedef std::map<uint64_t, std::string> fileNumberNameMap_t;
    typedef fileNumberNameMap_t::iterator fileNumberNameMapItr_t;
    typedef fileNumberNameMap_t::const_iterator fileNumberNameMapConstItr_t;
    typedef std::deque<uint32_t> enqueueCountList_t;
    typedef enqueueCountList_t::const_iterator enqueueCountListConstItr_t;
    typedef std::vector<uint64_t> recordIdList_t;
    typedef recordIdList_t::const_iterator recordIdListConstItr_t;

    // Location and identity
    const std::string journalDirectory_;
    const std::string queueName_;
    enq_map& enqueueMapRef_;
    txn_map& transactionMapRef_;
    JournalLog& journalLogRef_;

    // Initial journal analysis data
    fileNumberNameMap_t fileNumberNameMap_;     ///< File number - name map
    enqueueCountList_t enqueueCountList_;       ///< Number enqueued records found for each file
    bool journalEmptyFlag_;                     ///< Journal data files empty
    std::streamoff firstRecordOffset_;          ///< First record offset in ffid
    std::streamoff endOffset_;                  ///< End offset (first byte past last record)
    uint64_t highestRecordId_;                  ///< Highest rid found
    uint64_t highestFileNumber_;                ///< Highest file number found
    bool lastFileFullFlag_;                     ///< Last file is full

    // State for recovery of individual enqueued records
    uint32_t fileSize_kib_;
    fileNumberNameMapConstItr_t currentJournalFileConstItr_;
    std::string currentFileName_;
    std::ifstream inFileStream_;
    recordIdList_t recordIdList_;
    recordIdListConstItr_t recordIdListConstItr_;

public:
    RecoveryManager(const std::string& journalDirectory,
                    const std::string& queuename,
                    enq_map& enqueueMapRef,
                    txn_map& transactionMapRef,
                    JournalLog& journalLogRef);
    virtual ~RecoveryManager();

    void analyzeJournals(const std::vector<std::string>* preparedTransactionListPtr,
                         EmptyFilePoolManager* emptyFilePoolManager,
                         EmptyFilePool** emptyFilePoolPtrPtr);
    std::streamoff getEndOffset() const;
    uint64_t getHighestFileNumber() const;
    uint64_t getHighestRecordId() const;
    bool isLastFileFull() const;
    bool readNextRemainingRecord(void** const dataPtrPtr,
                                 std::size_t& dataSize,
                                 void** const xidPtrPtr,
                                 std::size_t& xidSize,
                                 bool& transient,
                                 bool& external,
                                 data_tok* const dtokp,
                                 bool ignore_pending_txns);
    void setLinearFileControllerJournals(lfcAddJournalFileFn fnPtr,
                                         LinearFileController* lfcPtr);
    std::string toString(const std::string& jid,
                         bool compact = true);
protected:
    void analyzeJournalFileHeaders(efpIdentity_t& efpIdentity);
    void checkFileStreamOk(bool checkEof);
    void checkJournalAlignment(const std::streampos recordPosition);
    bool decodeRecord(jrec& record,
                      std::size_t& cumulativeSizeRead,
                      ::rec_hdr_t& recordHeader,
                      std::streampos& fileOffset);
    std::string getCurrentFileName() const;
    uint64_t getCurrentFileNumber() const;
    bool getNextFile(bool jumpToFirstRecordOffsetFlag);
    bool getNextRecordHeader();
    void readJournalData(char* target, const std::streamsize size);
    void removeEmptyFiles(EmptyFilePool* emptyFilePoolPtr);

    static void readJournalFileHeader(const std::string& journalFileName,
                                      ::file_hdr_t& fileHeaderRef,
                                      std::string& queueName);
};

}} // namespace qpid::qls_jrnl

#endif // QPID_LINEARSTORE_RECOVERYSTATE_H_
