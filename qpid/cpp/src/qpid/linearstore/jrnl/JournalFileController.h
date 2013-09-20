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

#ifndef QPID_LINEARSTORE_JOURNALFILECONTROLLER_H_
#define QPID_LINEARSTORE_JOURNALFILECONTROLLER_H_

#include <deque>
#include "qpid/linearstore/jrnl/smutex.h"

struct file_hdr_t;
namespace qpid {
namespace qls_jrnl {
class EmptyFilePool;
class JournalFile;
//typedef struct file_hdr_t file_hdr_t;

class JournalFileController
{
protected:
    typedef std::deque<const JournalFile*> JournalFileList_t;
    typedef JournalFileList_t::iterator JournalFileListItr_t;

    const std::string dir;
    EmptyFilePool* efpp;
    uint64_t fileSeqCounter;
    JournalFileList_t journalFileList;
    smutex journalFileListMutex;

public:
    JournalFileController(const std::string& dir,
                          EmptyFilePool* efpp);
    virtual ~JournalFileController();

    void pullEmptyFileFromEfp(const uint64_t recId, const uint64_t firstRecOffs, const std::string& queueName);
    void purgeFilesToEfp();
    void finalize();
    void setFileSeqNum(const uint64_t fileSeqNum);

protected:
    std::string readFileHeader(file_hdr_t* fhdr, const std::string& fileName);
    void writeFileHeader(const file_hdr_t* fhdr, const std::string& queueName, const std::string& fileName);
    void resetFileHeader(const std::string& fileName);
    void initialzeFileHeader(const std::string& fileName, const uint64_t recId, const uint64_t firstRecOffs,
                             const uint64_t fileSeqNum, const std::string& queueName);
    uint64_t getNextFileSeqNum();
};

}} // namespace qpid::qls_jrnl

#endif // QPID_LINEARSTORE_JOURNALFILECONTROLLER_H_
