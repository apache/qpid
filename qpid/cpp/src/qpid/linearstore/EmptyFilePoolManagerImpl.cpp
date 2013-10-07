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

#include "EmptyFilePoolManagerImpl.h"

#include "QpidLog.h"
#include "qpid/linearstore/jrnl/EmptyFilePoolTypes.h"

namespace qpid {
namespace linearstore {

EmptyFilePoolManagerImpl::EmptyFilePoolManagerImpl(const std::string& qlsStorePath) :
            qpid::qls_jrnl::EmptyFilePoolManager(qlsStorePath)
{}

EmptyFilePoolManagerImpl::~EmptyFilePoolManagerImpl() {}

void EmptyFilePoolManagerImpl::findEfpPartitions() {
    qpid::qls_jrnl::EmptyFilePoolManager::findEfpPartitions();
    QLS_LOG(info, "EFP Manager initialization complete");
    std::vector<qpid::qls_jrnl::EmptyFilePoolPartition*> partitionList;
    std::vector<qpid::qls_jrnl::EmptyFilePool*> filePoolList;
    getEfpPartitions(partitionList);
    if (partitionList.size() == 0) {
        QLS_LOG(error, "NO EFP PARTITIONS FOUND! No queue creation is possible.")
    } else {
        QLS_LOG(info, "> EFP Partitions found: " << partitionList.size());
        for (std::vector<qpid::qls_jrnl::EmptyFilePoolPartition*>::const_iterator i=partitionList.begin(); i!= partitionList.end(); ++i) {
            filePoolList.clear();
            (*i)->getEmptyFilePools(filePoolList);
            QLS_LOG(info, "  * Partition " << (*i)->partitionNumber() << " containing " << filePoolList.size() << " pool" <<
                          (filePoolList.size()>1 ? "s" : "") << " at \'" << (*i)->partitionDirectory() << "\'");
            for (std::vector<qpid::qls_jrnl::EmptyFilePool*>::const_iterator j=filePoolList.begin(); j!=filePoolList.end(); ++j) {
                QLS_LOG(info, "    - EFP \'" << (*j)->dataSize_kib() << "k\' containing " << (*j)->numEmptyFiles() <<
                              " files of size " << (*j)->dataSize_kib() << " KiB totaling " << (*j)->cumFileSize_kib() << " KiB");
            }
        }
    }
}

}} /* namespace qpid::linearstore */
