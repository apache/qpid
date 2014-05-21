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

#include "qpid/linearstore/journal/EmptyFilePool.h"
#include "qpid/linearstore/journal/EmptyFilePoolPartition.h"
#include "qpid/linearstore/journal/jdir.h"
#include "qpid/linearstore/journal/JournalLog.h"
#include "qpid/linearstore/journal/slock.h"

//#include <iostream> // DEBUG

namespace qpid {
namespace linearstore {
namespace journal {

EmptyFilePoolManager::EmptyFilePoolManager(const std::string& qlsStorePath,
                                           const efpPartitionNumber_t defaultPartitionNumber,
                                           const efpDataSize_kib_t defaultEfpDataSize_kib,
                                           JournalLog& journalLogRef) :
                qlsStorePath_(qlsStorePath),
                defaultPartitionNumber_(defaultPartitionNumber),
                defaultEfpDataSize_kib_(defaultEfpDataSize_kib),
                journalLogRef_(journalLogRef)
{}

EmptyFilePoolManager::~EmptyFilePoolManager() {
    slock l(partitionMapMutex_);
    for (partitionMapItr_t i = partitionMap_.begin(); i != partitionMap_.end(); ++i) {
        delete i->second;
    }
    partitionMap_.clear();
}

void EmptyFilePoolManager::findEfpPartitions() {
//std::cout << "*** Reading " << qlsStorePath_ << std::endl; // DEBUG
    bool foundPartition = false;
    std::vector<std::string> dirList;
    while (!foundPartition) {
        jdir::read_dir(qlsStorePath_, dirList, true, false, true, false);
        for (std::vector<std::string>::iterator i = dirList.begin(); i != dirList.end(); ++i) {
            efpPartitionNumber_t pn = EmptyFilePoolPartition::getPartitionNumber(*i);
            if (pn > 0) { // valid partition name found
                std::string fullDirPath(qlsStorePath_ + "/" + (*i));
                EmptyFilePoolPartition* efppp = 0;
                try {
                    efppp = new EmptyFilePoolPartition(pn, fullDirPath, journalLogRef_);
                    {
                        slock l(partitionMapMutex_);
                        partitionMap_[pn] = efppp;
                    }
                } catch (const std::exception& e) {
                    if (efppp != 0) {
                        delete efppp;
                        efppp = 0;
                    }
//std::cerr << "Unable to initialize partition " << pn << " (\'" << fullDirPath << "\'): " << e.what() << std::endl; // DEBUG
                }
                if (efppp != 0)
                    efppp->findEmptyFilePools();
                foundPartition = true;
            }
        }

        // If no partition was found, create an empty default partition with a warning.
        if (!foundPartition) {
            journalLogRef_.log(JournalLog::LOG_WARN, "No EFP partition found, creating an empty partition.");
            std::ostringstream oss;
            oss << qlsStorePath_ << "/" << EmptyFilePoolPartition::getPartionDirectoryName(defaultPartitionNumber_)
                << "/" << EmptyFilePoolPartition::s_efpTopLevelDir_ << "/" << EmptyFilePool::dirNameFromDataSize(defaultEfpDataSize_kib_);
            jdir::create_dir(oss.str());
        }
    }

    journalLogRef_.log(JournalLog::LOG_NOTICE, "EFP Manager initialization complete");
    std::vector<qpid::linearstore::journal::EmptyFilePoolPartition*> partitionList;
    std::vector<qpid::linearstore::journal::EmptyFilePool*> filePoolList;
    getEfpPartitions(partitionList);
    if (partitionList.size() == 0) {
        journalLogRef_.log(JournalLog::LOG_WARN, "NO EFP PARTITIONS FOUND! No queue creation is possible.");
    } else {
        std::stringstream oss;
        oss << "> EFP Partitions found: " << partitionList.size();
        journalLogRef_.log(JournalLog::LOG_INFO, oss.str());
        for (std::vector<qpid::linearstore::journal::EmptyFilePoolPartition*>::const_iterator i=partitionList.begin(); i!= partitionList.end(); ++i) {
            filePoolList.clear();
            (*i)->getEmptyFilePools(filePoolList);
            std::stringstream oss;
            oss << "  * Partition " << (*i)->getPartitionNumber() << " containing " << filePoolList.size()
                << " pool" << (filePoolList.size()>1 ? "s" : "") << " at \'" << (*i)->getPartitionDirectory() << "\'";
            journalLogRef_.log(JournalLog::LOG_INFO, oss.str());
            for (std::vector<qpid::linearstore::journal::EmptyFilePool*>::const_iterator j=filePoolList.begin(); j!=filePoolList.end(); ++j) {
                std::ostringstream oss;
                oss << "    - EFP \'" << (*j)->dataSize_kib() << "k\' containing " << (*j)->numEmptyFiles() <<
                              " files of size " << (*j)->dataSize_kib() << " KiB totaling " << (*j)->cumFileSize_kib() << " KiB";
            journalLogRef_.log(JournalLog::LOG_INFO, oss.str());
            }
        }
    }
}

void EmptyFilePoolManager::getEfpFileSizes(std::vector<efpDataSize_kib_t>& efpFileSizeList,
                                           const efpPartitionNumber_t efpPartitionNumber) const {
    if (efpPartitionNumber == 0) {
        for (partitionMapConstItr_t i=partitionMap_.begin(); i!=partitionMap_.end(); ++i) {
            i->second->getEmptyFilePoolSizes_kib(efpFileSizeList);
        }
    } else {
        partitionMapConstItr_t i = partitionMap_.find(efpPartitionNumber);
        if (i != partitionMap_.end()) {
            i->second->getEmptyFilePoolSizes_kib(efpFileSizeList);
        }
    }
}

EmptyFilePoolPartition* EmptyFilePoolManager::getEfpPartition(const efpPartitionNumber_t partitionNumber) {
    partitionMapItr_t i = partitionMap_.find(partitionNumber);
    if (i == partitionMap_.end())
        return 0;
    else
        return i->second;
}

void EmptyFilePoolManager::getEfpPartitionNumbers(std::vector<efpPartitionNumber_t>& partitionNumberList,
                                                  const efpDataSize_kib_t efpDataSize_kib) const {
    slock l(partitionMapMutex_);
    for (partitionMapConstItr_t i=partitionMap_.begin(); i!=partitionMap_.end(); ++i) {
        if (efpDataSize_kib == 0) {
            partitionNumberList.push_back(i->first);
        } else {
            std::vector<efpDataSize_kib_t> efpFileSizeList;
            i->second->getEmptyFilePoolSizes_kib(efpFileSizeList);
            for (std::vector<efpDataSize_kib_t>::iterator j=efpFileSizeList.begin(); j!=efpFileSizeList.end(); ++j) {
                if (*j == efpDataSize_kib) {
                    partitionNumberList.push_back(i->first);
                    break;
                }
            }
        }
    }
}

void EmptyFilePoolManager::getEfpPartitions(std::vector<EmptyFilePoolPartition*>& partitionList,
                                            const efpDataSize_kib_t efpDataSize_kib) {
    slock l(partitionMapMutex_);
    for (partitionMapConstItr_t i=partitionMap_.begin(); i!=partitionMap_.end(); ++i) {
        if (efpDataSize_kib == 0) {
            partitionList.push_back(i->second);
        } else {
            std::vector<efpDataSize_kib_t> efpFileSizeList;
            i->second->getEmptyFilePoolSizes_kib(efpFileSizeList);
            for (std::vector<efpDataSize_kib_t>::iterator j=efpFileSizeList.begin(); j!=efpFileSizeList.end(); ++j) {
                if (*j == efpDataSize_kib) {
                    partitionList.push_back(i->second);
                    break;
                }
            }
        }
    }
}

EmptyFilePool* EmptyFilePoolManager::getEmptyFilePool(const efpIdentity_t efpIdentity) {
    return getEmptyFilePool(efpIdentity.pn_, efpIdentity.ds_);
}

EmptyFilePool* EmptyFilePoolManager::getEmptyFilePool(const efpPartitionNumber_t partitionNumber,
                                                      const efpDataSize_kib_t efpDataSize_kib) {
    EmptyFilePoolPartition* efppp = getEfpPartition(partitionNumber > 0 ? partitionNumber : defaultPartitionNumber_);
    if (efppp != 0)
	return efppp->getEmptyFilePool(efpDataSize_kib > 0 ? efpDataSize_kib : defaultEfpDataSize_kib_);
    return 0;
}

void EmptyFilePoolManager::getEmptyFilePools(std::vector<EmptyFilePool*>& emptyFilePoolList,
                                             const efpPartitionNumber_t efpPartitionNumber) {
    if (efpPartitionNumber == 0) {
        for (partitionMapConstItr_t i=partitionMap_.begin(); i!=partitionMap_.end(); ++i) {
            i->second->getEmptyFilePools(emptyFilePoolList);
        }
    } else {
        partitionMapConstItr_t i = partitionMap_.find(efpPartitionNumber);
        if (i != partitionMap_.end()) {
            i->second->getEmptyFilePools(emptyFilePoolList);
        }
    }
}

uint16_t EmptyFilePoolManager::getNumEfpPartitions() const {
    return partitionMap_.size();
}

}}}
