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

#ifndef QPID_LINEARSTORE_JOURNAL_RECOVERYSTATE_H_
#define QPID_LINEARSTORE_JOURNAL_RECOVERYSTATE_H_

#include <fstream>
#include <map>
#include "qpid/linearstore/journal/LinearFileController.h"
#include <stdint.h>
#include <vector>

struct file_hdr_t;
struct rec_hdr_t;

namespace qpid {
namespace linearstore {
namespace journal {

class data_tok;
class enq_map;
class EmptyFilePool;
class EmptyFilePoolManager;
class JournalLog;
class jrec;
class txn_map;

struct RecoveredRecordData_t {
    uint64_t recordId_;
    uint64_t fileId_;
    std::streampos fileOffset_;
    bool pendingTransaction_;
    RecoveredRecordData_t(const uint64_t rid, const uint64_t fid, const std::streampos foffs, bool ptxn);
};

bool recordIdListCompare(RecoveredRecordData_t a, RecoveredRecordData_t b);

class RecoveryManager
{
protected:
    // Types
    typedef std::vector<std::string> directoryList_t;
    typedef directoryList_t::const_iterator directoryListConstItr_t;
    typedef std::map<uint64_t, JournalFile*> fileNumberMap_t;
    typedef fileNumberMap_t::iterator fileNumberMapItr_t;
    typedef fileNumberMap_t::const_iterator fileNumberMapConstItr_t;
    typedef std::vector<RecoveredRecordData_t> recordIdList_t;
    typedef recordIdList_t::const_iterator recordIdListConstItr_t;

    // Location and identity
    const std::string journalDirectory_;
    const std::string queueName_;
    enq_map& enqueueMapRef_;
    txn_map& transactionMapRef_;
    JournalLog& journalLogRef_;

    // Initial journal analysis data
    fileNumberMap_t fileNumberMap_;             ///< File number - JournalFilePtr map
    bool journalEmptyFlag_;                     ///< Journal data files empty
    std::streamoff firstRecordOffset_;          ///< First record offset in ffid
    std::streamoff endOffset_;                  ///< End offset (first byte past last record)
    uint64_t highestRecordId_;                  ///< Highest rid found
    uint64_t highestFileNumber_;                ///< Highest file number found
    bool lastFileFullFlag_;                     ///< Last file is full
    std::string uninitializedJournal_;          ///< File name of uninitialized journal found during header analysis

    // State for recovery of individual enqueued records
    uint64_t currentSerial_;
    uint32_t efpFileSize_kib_;
    fileNumberMapConstItr_t currentJournalFileConstItr_;
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
    std::string toString(const std::string& jid);
    std::string toLog(const std::string& jid, const int indent);
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
    bool getFile(const uint64_t fileNumber, bool jumpToFirstRecordOffsetFlag);
    bool getNextFile(bool jumpToFirstRecordOffsetFlag);
    bool getNextRecordHeader();
    bool needNextFile();
    void prepareRecordList();
    bool readFileHeader();
    void readJournalData(char* target, const std::streamsize size);
    void removeEmptyFiles(EmptyFilePool* emptyFilePoolPtr);

    static void readJournalFileHeader(const std::string& journalFileName,
                                      ::file_hdr_t& fileHeaderRef,
                                      std::string& queueName);
};

}}}

#endif // QPID_LINEARSTORE_JOURNAL_RECOVERYSTATE_H_
