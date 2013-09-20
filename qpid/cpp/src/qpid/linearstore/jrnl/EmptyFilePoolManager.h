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
#include "qpid/linearstore/jrnl/EmptyFilePoolPartition.h"
#include "qpid/linearstore/jrnl/smutex.h"
#include <string>

namespace qpid {
namespace qls_jrnl {

class EmptyFilePoolManager
{
protected:
    typedef std::map<efpPartitionNumber_t, EmptyFilePoolPartition*> partitionMap_t;
    typedef partitionMap_t::iterator partitionMapItr_t;
    typedef partitionMap_t::const_iterator partitionMapConstItr_t;

    std::string qlsStorePath;
    partitionMap_t partitionMap;
    smutex partitionMapMutex;

public:
    EmptyFilePoolManager(const std::string& qlsStorePath_);
    virtual ~EmptyFilePoolManager();
    void findEfpPartitions();

    uint16_t getNumEfpPartitions() const;
    EmptyFilePoolPartition* getEfpPartition(const efpPartitionNumber_t partitionNumber);
    void getEfpPartitionNumbers(std::vector<efpPartitionNumber_t>& partitionNumberList, const efpFileSizeKib_t efpFileSizeKb = 0) const;
    void getEfpPartitions(std::vector<EmptyFilePoolPartition*>& partitionList, const efpFileSizeKib_t efpFileSizeKb = 0);

    void getEfpFileSizes(std::vector<efpFileSizeKib_t>& efpFileSizeList, const efpPartitionNumber_t efpPartitionNumber = 0) const;
    void getEmptyFilePools(std::vector<EmptyFilePool*>& emptyFilePoolList, const efpPartitionNumber_t efpPartitionNumber = 0);

    EmptyFilePool* getEmptyFilePool(const efpPartitionNumber_t partitionNumber, const efpFileSizeKib_t efpFileSizeKb);
    EmptyFilePool* getEmptyFilePool(const efpIdentity_t efpIdentity);
};

}} // namespace qpid::qls_jrnl

#endif /* QPID_QLS_JRNL_EMPTYFILEPOOLMANAGER_H_ */
