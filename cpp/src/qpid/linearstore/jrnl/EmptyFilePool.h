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

#ifndef QPID_QLS_JRNL_EMPTYFILEPOOL_H_
#define QPID_QLS_JRNL_EMPTYFILEPOOL_H_

namespace qpid {
namespace qls_jrnl {

    class EmptyFilePool;

}} // namespace qpid::qls_jrnl

#include <deque>
#include "qpid/linearstore/jrnl/EmptyFilePoolTypes.h"
#include "qpid/linearstore/jrnl/smutex.h"
#include <string>

namespace qpid {
namespace qls_jrnl {
class EmptyFilePoolPartition;
class jdir;
class JournalFile;
class JournalLog;

class EmptyFilePool
{
protected:
    typedef std::deque<std::string> emptyFileList_t;
    typedef emptyFileList_t::iterator emptyFileListItr_t;

    const std::string efpDirectory_;
    const efpDataSize_kib_t efpDataSize_kib_;
    const EmptyFilePoolPartition* partitionPtr_;
    JournalLog& journalLogRef_;

private:
    emptyFileList_t emptyFileList_;
    smutex emptyFileListMutex_;

public:
    EmptyFilePool(const std::string& efpDirectory,
                  const EmptyFilePoolPartition* partitionPtr,
                  JournalLog& journalLogRef);
    virtual ~EmptyFilePool();

    void initialize();
    efpDataSize_kib_t dataSize_kib() const;
    efpFileSize_kib_t fileSize_kib() const;
    efpDataSize_sblks_t dataSize_sblks() const;
    efpFileSize_sblks_t fileSize_sblks() const;
    efpFileCount_t numEmptyFiles() const;
    efpDataSize_kib_t cumFileSize_kib() const;
    efpPartitionNumber_t getPartitionNumber() const;
    const EmptyFilePoolPartition* getPartition() const;
    const efpIdentity_t getIdentity() const;

    std::string takeEmptyFile(const std::string& destDirectory);
    void returnEmptyFile(const std::string& srcFile);

    static std::string dirNameFromDataSize(const efpDataSize_kib_t efpDataSize_kib);
    static efpDataSize_kib_t dataSizeFromDirName_kib(const std::string& dirName,
                                                     const efpPartitionNumber_t partitionNumber);

protected:
    void createEmptyFile();
    std::string getEfpFileName();
    std::string popEmptyFile();
    void pushEmptyFile(const std::string fqFileName);
    void resetEmptyFileHeader(const std::string& fqFileName);
    bool validateEmptyFile(const std::string& emptyFileName) const;

    static int moveEmptyFile(const std::string& fromFqPath,
                             const std::string& toFqPath);
};

}} // namespace qpid::qls_jrnl

#endif /* QPID_QLS_JRNL_EMPTYFILEPOOL_H_ */
