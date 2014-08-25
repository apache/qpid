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

#include "qpid/linearstore/journal/EmptyFilePoolPartition.h"

#include <iomanip>
#include "qpid/linearstore/journal/EmptyFilePool.h"
#include "qpid/linearstore/journal/jdir.h"
#include "qpid/linearstore/journal/slock.h"

//#include <iostream> // DEBUG

namespace qpid {
namespace linearstore {
namespace journal {

// static
const std::string EmptyFilePoolPartition::s_efpTopLevelDir_("efp"); // Sets the top-level efp dir within a partition

EmptyFilePoolPartition::EmptyFilePoolPartition(const efpPartitionNumber_t partitionNum,
                                               const std::string& partitionDir,
                                               const bool overwriteBeforeReturnFlag,
                                               JournalLog& journalLogRef) :
                partitionNum_(partitionNum),
                partitionDir_(partitionDir),
                overwriteBeforeReturnFlag_(overwriteBeforeReturnFlag),
                journalLogRef_(journalLogRef)
{
    validatePartitionDir();
}

EmptyFilePoolPartition::~EmptyFilePoolPartition() {
    slock l(efpMapMutex_);
    for (efpMapItr_t i = efpMap_.begin(); i != efpMap_.end(); ++i) {
        delete i->second;
    }
    efpMap_.clear();
}

void
EmptyFilePoolPartition::findEmptyFilePools() {
//std::cout << "Reading " << partitionDir << std::endl; // DEBUG
    std::vector<std::string> dirList;
    jdir::read_dir(partitionDir_, dirList, true, false, false, false);
    bool foundEfpDir = false;
    for (std::vector<std::string>::iterator i = dirList.begin(); i != dirList.end(); ++i) {
        if (i->compare(s_efpTopLevelDir_) == 0) {
            foundEfpDir = true;
            break;
        }
    }
    if (foundEfpDir) {
        std::string efpDir(partitionDir_ + "/" + s_efpTopLevelDir_);
//std::cout << "Reading " << efpDir << std::endl; // DEBUG
        dirList.clear();
        jdir::read_dir(efpDir, dirList, true, false, false, true);
        for (std::vector<std::string>::iterator i = dirList.begin(); i != dirList.end(); ++i) {
            EmptyFilePool* efpp = 0;
            try {
                efpp = new EmptyFilePool(*i, this, journalLogRef_);
                {
                    slock l(efpMapMutex_);
                    efpMap_[efpp->dataSize_kib()] = efpp;
                }
            }
            catch (const std::exception& e) {
                if (efpp != 0) {
                    delete efpp;
                    efpp = 0;
                }
                //std::cerr << "WARNING: " << e.what() << std::endl;
            }
            if (efpp != 0)
                efpp->initialize();
        }
    }
}

EmptyFilePool* EmptyFilePoolPartition::getEmptyFilePool(const efpDataSize_kib_t efpDataSize_kib) {
    efpMapItr_t i = efpMap_.find(efpDataSize_kib);
    if (i == efpMap_.end())
        return 0;
    return i->second;
}

void EmptyFilePoolPartition::getEmptyFilePools(std::vector<EmptyFilePool*>& efpList) {
    for (efpMapItr_t i=efpMap_.begin(); i!=efpMap_.end(); ++i) {
        efpList.push_back(i->second);
    }
}

void EmptyFilePoolPartition::getEmptyFilePoolSizes_kib(std::vector<efpDataSize_kib_t>& efpDataSizesList_kib) const {
    for (efpMapConstItr_t i=efpMap_.begin(); i!=efpMap_.end(); ++i) {
        efpDataSizesList_kib.push_back(i->first);
    }
}

bool EmptyFilePoolPartition::getOverwriteBeforeReturnFlag() const {
    return overwriteBeforeReturnFlag_;
}

std::string EmptyFilePoolPartition::getPartitionDirectory() const {
    return partitionDir_;
}

efpPartitionNumber_t EmptyFilePoolPartition::getPartitionNumber() const {
    return partitionNum_;
}

// static
std::string EmptyFilePoolPartition::getPartionDirectoryName(const efpPartitionNumber_t partitionNumber) {
    std::ostringstream oss;
    oss << "p" << std::setfill('0') << std::setw(3) << partitionNumber;
    return oss.str();
}

//static
efpPartitionNumber_t EmptyFilePoolPartition::getPartitionNumber(const std::string& name) {
    if (name.length() == 4 && name[0] == 'p' && ::isdigit(name[1]) && ::isdigit(name[2]) && ::isdigit(name[3])) {
        long pn = ::strtol(name.c_str() + 1, 0, 0);
        if (pn == 0 && errno) {
            return 0;
        } else {
            return (efpPartitionNumber_t)pn;
        }
    }
    return 0;
}

// --- protected functions ---

void EmptyFilePoolPartition::validatePartitionDir() {
    if (!jdir::is_dir(partitionDir_)) {
        std::ostringstream ss;
        ss << "Invalid partition directory: \'" << partitionDir_ << "\' is not a directory";
        throw jexception(jerrno::JERR_EFP_BADPARTITIONDIR, ss.str(), "EmptyFilePoolPartition", "validatePartitionDir");
    }
    // TODO: other validity checks here
}

}}}
