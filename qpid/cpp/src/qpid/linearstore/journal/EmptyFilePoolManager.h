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

#ifndef QPID_QLS_JRNL_EMPTYFILEPOOLMANAGER_H_
#define QPID_QLS_JRNL_EMPTYFILEPOOLMANAGER_H_

#include <map>
#include "qpid/linearstore/journal/EmptyFilePoolTypes.h"
#include "qpid/linearstore/journal/smutex.h"
#include <vector>

namespace qpid {
namespace linearstore {
namespace journal {

class EmptyFilePool;
class EmptyFilePoolPartition;
class JournalLog;

class EmptyFilePoolManager
{
protected:
    typedef std::map<efpPartitionNumber_t, EmptyFilePoolPartition*> partitionMap_t;
    typedef partitionMap_t::iterator partitionMapItr_t;
    typedef partitionMap_t::const_iterator partitionMapConstItr_t;

    const std::string qlsStorePath_;
    const efpPartitionNumber_t defaultPartitionNumber_;
    const efpDataSize_kib_t defaultEfpDataSize_kib_;
    const bool overwriteBeforeReturnFlag_;
    const bool truncateFlag_;
    JournalLog& journalLogRef_;
    partitionMap_t partitionMap_;
    smutex partitionMapMutex_;

public:
    EmptyFilePoolManager(const std::string& qlsStorePath_,
                         const efpPartitionNumber_t defaultPartitionNumber,
                         const efpDataSize_kib_t defaultEfpDataSize_kib,
                         const bool overwriteBeforeReturnFlag,
                         const bool truncateFlag,
                         JournalLog& journalLogRef_);
    virtual ~EmptyFilePoolManager();

    void findEfpPartitions();
    void getEfpFileSizes(std::vector<efpDataSize_kib_t>& efpFileSizeList,
                         const efpPartitionNumber_t efpPartitionNumber = 0) const;
    EmptyFilePoolPartition* getEfpPartition(const efpPartitionNumber_t partitionNumber);
    void getEfpPartitionNumbers(std::vector<efpPartitionNumber_t>& partitionNumberList,
                                const efpDataSize_kib_t efpDataSize_kib = 0) const;
    void getEfpPartitions(std::vector<EmptyFilePoolPartition*>& partitionList,
                          const efpDataSize_kib_t efpDataSize_kib = 0);
    EmptyFilePool* getEmptyFilePool(const efpIdentity_t efpIdentity);
    EmptyFilePool* getEmptyFilePool(const efpPartitionNumber_t partitionNumber,
                                    const efpDataSize_kib_t efpDataSize_kib);
    void getEmptyFilePools(std::vector<EmptyFilePool*>& emptyFilePoolList,
                           const efpPartitionNumber_t efpPartitionNumber = 0);
    uint16_t getNumEfpPartitions() const;
protected:
    EmptyFilePoolPartition* insertPartition(const efpPartitionNumber_t pn, const std::string& fullPartitionPath);
};

}}}

#endif /* QPID_QLS_JRNL_EMPTYFILEPOOLMANAGER_H_ */
