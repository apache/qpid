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

#include "EmptyFilePoolManager.h"

#include <dirent.h>
#include "qpid/linearstore/jrnl/EmptyFilePoolPartition.h"
#include "qpid/linearstore/jrnl/jdir.h"
#include "qpid/linearstore/jrnl/slock.h"
#include <vector>

// DEBUG
//#include <iostream>

namespace qpid {
namespace qls_jrnl {

EmptyFilePoolManager::EmptyFilePoolManager(const std::string& qlsStorePath_) :
                qlsStorePath(qlsStorePath_)
{}

EmptyFilePoolManager::~EmptyFilePoolManager() {
    slock l(partitionMapMutex);
    for (partitionMapItr_t i = partitionMap.begin(); i != partitionMap.end(); ++i) {
        delete i->second;
    }
    partitionMap.clear();
}

void
EmptyFilePoolManager::findEfpPartitions() {
    //std::cout << "*** Reading " << qlsStorePath << std::endl; // DEBUG
    std::vector<std::string> dirList;
    jdir::read_dir(qlsStorePath, dirList, true, false, true, false);
    for (std::vector<std::string>::iterator i = dirList.begin(); i != dirList.end(); ++i) {
        if ((*i)[0] == 'p' && i->length() == 4) { // Filter: look only at names pNNN
            efpPartitionNumber_t pn = ::atoi(i->c_str() + 1);
            std::string fullDirPath(qlsStorePath + "/" + (*i));
            EmptyFilePoolPartition* efppp = 0;
            try {
                efppp = new EmptyFilePoolPartition(pn, fullDirPath);
                {
                    slock l(partitionMapMutex);
                    partitionMap[pn] = efppp;
                }
            } catch (const std::exception& e) {
                if (efppp != 0) {
                    delete efppp;
                    efppp = 0;
                }
                //std::cerr << "Unable to initialize partition " << pn << " (\'" << fullDirPath << "\'): " << e.what() << std::endl;
            }
            if (efppp != 0)
                efppp->findEmptyFilePools();
        }
    }
}

uint16_t
EmptyFilePoolManager::getNumEfpPartitions() const {
    return partitionMap.size();
}

EmptyFilePoolPartition*
EmptyFilePoolManager::getEfpPartition(const efpPartitionNumber_t partitionNumber) {
    partitionMapItr_t i = partitionMap.find(partitionNumber);
    if (i == partitionMap.end())
        return 0;
    else
        return i->second;
}

void
EmptyFilePoolManager::getEfpPartitionNumbers(std::vector<efpPartitionNumber_t>& partitionNumberList,
                                             const efpDataSize_kib_t efpFileSizeKb) const {
    slock l(partitionMapMutex);
    for (partitionMapConstItr_t i=partitionMap.begin(); i!=partitionMap.end(); ++i) {
        if (efpFileSizeKb == 0) {
            partitionNumberList.push_back(i->first);
        } else {
            std::vector<efpDataSize_kib_t> efpFileSizeList;
            i->second->getEmptyFilePoolSizesKb(efpFileSizeList);
            for (std::vector<efpDataSize_kib_t>::iterator j=efpFileSizeList.begin(); j!=efpFileSizeList.end(); ++j) {
                if (*j == efpFileSizeKb) {
                    partitionNumberList.push_back(i->first);
                    break;
                }
            }
        }
    }
}

void
EmptyFilePoolManager::getEfpPartitions(std::vector<EmptyFilePoolPartition*>& partitionList,
                                       const efpDataSize_kib_t efpFileSizeKb) {
    slock l(partitionMapMutex);
    for (partitionMapConstItr_t i=partitionMap.begin(); i!=partitionMap.end(); ++i) {
        if (efpFileSizeKb == 0) {
            partitionList.push_back(i->second);
        } else {
            std::vector<efpDataSize_kib_t> efpFileSizeList;
            i->second->getEmptyFilePoolSizesKb(efpFileSizeList);
            for (std::vector<efpDataSize_kib_t>::iterator j=efpFileSizeList.begin(); j!=efpFileSizeList.end(); ++j) {
                if (*j == efpFileSizeKb) {
                    partitionList.push_back(i->second);
                    break;
                }
            }
        }
    }
}

void
EmptyFilePoolManager::getEfpFileSizes(std::vector<efpDataSize_kib_t>& efpFileSizeList,
                                      const efpPartitionNumber_t efpPartitionNumber) const {
    if (efpPartitionNumber == 0) {
        for (partitionMapConstItr_t i=partitionMap.begin(); i!=partitionMap.end(); ++i) {
            i->second->getEmptyFilePoolSizesKb(efpFileSizeList);
        }
    } else {
        partitionMapConstItr_t i = partitionMap.find(efpPartitionNumber);
        if (i != partitionMap.end()) {
            i->second->getEmptyFilePoolSizesKb(efpFileSizeList);
        }
    }
}

void
EmptyFilePoolManager::getEmptyFilePools(std::vector<EmptyFilePool*>& emptyFilePoolList,
                                        const efpPartitionNumber_t efpPartitionNumber) {
    if (efpPartitionNumber == 0) {
        for (partitionMapConstItr_t i=partitionMap.begin(); i!=partitionMap.end(); ++i) {
            i->second->getEmptyFilePools(emptyFilePoolList);
        }
    } else {
        partitionMapConstItr_t i = partitionMap.find(efpPartitionNumber);
        if (i != partitionMap.end()) {
            i->second->getEmptyFilePools(emptyFilePoolList);
        }
    }
}

EmptyFilePool*
EmptyFilePoolManager::getEmptyFilePool(const efpPartitionNumber_t partitionNumber,
                                       const efpDataSize_kib_t efpFileSizeKib) {
    EmptyFilePoolPartition* efppp = getEfpPartition(partitionNumber);
    if (efppp != 0)
        return efppp->getEmptyFilePool(efpFileSizeKib);
    return 0;
}

EmptyFilePool*
EmptyFilePoolManager::getEmptyFilePool(const efpIdentity_t efpIdentity) {
    return getEmptyFilePool(efpIdentity.first, efpIdentity.second);
}

}} // namespace qpid::qls_jrnl
