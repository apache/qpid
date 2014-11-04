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

#ifndef QPID_LINEARSTORE_JOURNAL_EMPTYFILEPOOL_H_
#define QPID_LINEARSTORE_JOURNAL_EMPTYFILEPOOL_H_

namespace qpid {
namespace linearstore {
namespace journal {
    class EmptyFilePool;
}}}

#include <deque>
#include "qpid/linearstore/journal/EmptyFilePoolTypes.h"
#include "qpid/linearstore/journal/smutex.h"

namespace qpid {
namespace linearstore {
namespace journal {
class EmptyFilePoolPartition;
class jdir;
class JournalFile;
class JournalLog;

class EmptyFilePool
{
protected:
    typedef std::deque<std::string> emptyFileList_t;
    typedef emptyFileList_t::const_iterator emptyFileListConstItr_t;

    static std::string s_inuseFileDirectory_;
    static std::string s_returnedFileDirectory_;

    const std::string efpDirectory_;
    const efpDataSize_kib_t efpDataSize_kib_;
    const EmptyFilePoolPartition* partitionPtr_;
    const bool overwriteBeforeReturnFlag_;
    const bool truncateFlag_;
    JournalLog& journalLogRef_;

private:
    emptyFileList_t emptyFileList_;
    smutex emptyFileListMutex_;

public:
    EmptyFilePool(const std::string& efpDirectory,
                  const EmptyFilePoolPartition* partitionPtr,
                  const bool overwriteBeforeReturnFlag,
                  const bool truncateFlag,
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
    void returnEmptyFileSymlink(const std::string& emptyFileSymlink);

    static std::string dirNameFromDataSize(const efpDataSize_kib_t efpDataSize_kib);
    static efpDataSize_kib_t dataSizeFromDirName_kib(const std::string& dirName,
                                                     const efpPartitionNumber_t partitionNumber);

protected:
    std::string createEmptyFile();
    std::string getEfpFileName();
    void initializeSubDirectory(const std::string& fqDirName);
    bool overwriteFileContents(const std::string& fqFileName);
    std::string popEmptyFile();
    void pushEmptyFile(const std::string fqFileName);
    void returnEmptyFile(const std::string& emptyFileName);
    void resetEmptyFileHeader(const std::string& fqFileName);
    bool validateEmptyFile(const std::string& emptyFileName) const;

    static int moveFile(const std::string& fromFqPath,
                        const std::string& toFqPath);
    static int createSymLink(const std::string& fqFileName,
                             const std::string& fqLinkName);
    static std::string deleteSymlink(const std::string& fqLinkName);
};

}}}

#endif /* QPID_LINEARSTORE_JOURNAL_EMPTYFILEPOOL_H_ */
