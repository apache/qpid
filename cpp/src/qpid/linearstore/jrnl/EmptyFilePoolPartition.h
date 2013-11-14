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

#ifndef QPID_LINEARSTORE_JOURNAL_EMPTYFILEPOOLPARTITION_H_
#define QPID_LINEARSTORE_JOURNAL_EMPTYFILEPOOLPARTITION_H_

namespace qpid {
namespace linearstore {
namespace journal {
    class EmptyFilePoolPartition;
}}}

#include "qpid/linearstore/jrnl/EmptyFilePool.h"
#include "qpid/linearstore/jrnl/EmptyFilePoolTypes.h"
#include "qpid/linearstore/jrnl/smutex.h"
#include <string>
#include <map>
#include <vector>

namespace qpid {
namespace linearstore {
namespace journal {
class JournalLog;

class EmptyFilePoolPartition
{
public:
    static const std::string s_efpTopLevelDir_;
protected:
    typedef std::map<efpDataSize_kib_t, EmptyFilePool*> efpMap_t;
    typedef efpMap_t::iterator efpMapItr_t;
    typedef efpMap_t::const_iterator efpMapConstItr_t;

    const efpPartitionNumber_t partitionNum_;
    const std::string partitionDir_;
    JournalLog& journalLogRef_;
    efpMap_t efpMap_;
    smutex efpMapMutex_;

public:
    EmptyFilePoolPartition(const efpPartitionNumber_t partitionNum,
                           const std::string& partitionDir,
                           JournalLog& journalLogRef);
    virtual ~EmptyFilePoolPartition();

    void findEmptyFilePools();
    EmptyFilePool* getEmptyFilePool(const efpDataSize_kib_t efpDataSize_kib);
    void getEmptyFilePools(std::vector<EmptyFilePool*>& efpList);
    void getEmptyFilePoolSizes_kib(std::vector<efpDataSize_kib_t>& efpDataSizesList) const;
    std::string getPartitionDirectory() const;
    efpPartitionNumber_t getPartitionNumber() const;

    static std::string getPartionDirectoryName(const efpPartitionNumber_t partitionNumber);
    static efpPartitionNumber_t getPartitionNumber(const std::string& name);

protected:
    void validatePartitionDir();
};

}}}

#endif /* QPID_LINEARSTORE_JOURNAL_EMPTYFILEPOOLPARTITION_H_ */
