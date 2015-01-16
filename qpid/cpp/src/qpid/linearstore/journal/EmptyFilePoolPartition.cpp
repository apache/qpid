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
#include "qpid/linearstore/journal/JournalLog.h"
#include "qpid/linearstore/journal/slock.h"
#include <unistd.h>

namespace qpid {
namespace linearstore {
namespace journal {

// static
const std::string EmptyFilePoolPartition::s_efpTopLevelDir_("efp"); // Sets the top-level efp dir within a partition

EmptyFilePoolPartition::EmptyFilePoolPartition(const efpPartitionNumber_t partitionNum,
                                               const std::string& partitionDir,
                                               const bool overwriteBeforeReturnFlag,
                                               const bool truncateFlag,
                                               JournalLog& journalLogRef) :
                partitionNum_(partitionNum),
                partitionDir_(partitionDir),
                overwriteBeforeReturnFlag_(overwriteBeforeReturnFlag),
                truncateFlag_(truncateFlag),
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
//std::cout << "*** EmptyFilePoolPartition::findEmptyFilePools(): Reading " << partitionDir_ << std::endl; // DEBUG
    std::string efpDir(partitionDir_ + "/" + s_efpTopLevelDir_);
    if (jdir::is_dir(efpDir)) {
        std::vector<std::string> dirList;
        jdir::read_dir(efpDir, dirList, true, false, false, true);
        for (std::vector<std::string>::iterator i = dirList.begin(); i != dirList.end(); ++i) {
            createEmptyFilePool(*i);
        }
    } else {
        std::ostringstream oss;
        oss << "Partition \"" << partitionDir_ << "\" does not contain top level EFP dir \"" << s_efpTopLevelDir_ << "\"";
        journalLogRef_.log(JournalLog::LOG_WARN, oss.str());
    }
}

EmptyFilePool* EmptyFilePoolPartition::getEmptyFilePool(const efpDataSize_kib_t efpDataSize_kib, const bool createIfNonExistent) {
    {
        slock l(efpMapMutex_);
        efpMapItr_t i = efpMap_.find(efpDataSize_kib);
        if (i != efpMap_.end())
            return i->second;
    }
    if (createIfNonExistent) {
        return createEmptyFilePool(efpDataSize_kib);
    }
    return 0;
}

void EmptyFilePoolPartition::getEmptyFilePools(std::vector<EmptyFilePool*>& efpList) {
    slock l(efpMapMutex_);
    for (efpMapItr_t i=efpMap_.begin(); i!=efpMap_.end(); ++i) {
        efpList.push_back(i->second);
    }
}

void EmptyFilePoolPartition::getEmptyFilePoolSizes_kib(std::vector<efpDataSize_kib_t>& efpDataSizesList_kib) const {
    slock l(efpMapMutex_);
    for (efpMapConstItr_t i=efpMap_.begin(); i!=efpMap_.end(); ++i) {
        efpDataSizesList_kib.push_back(i->first);
    }
}

std::string EmptyFilePoolPartition::getPartitionDirectory() const {
    return partitionDir_;
}

efpPartitionNumber_t EmptyFilePoolPartition::getPartitionNumber() const {
    return partitionNum_;
}

std::string EmptyFilePoolPartition::toString(const uint16_t indent) const {
    std::string indentStr(indent, ' ');
    std::stringstream oss;
    oss << "EFP Partition " <<  partitionNum_ << ":" << std::endl;
    oss << indentStr  << "EFP Partition Analysis (partition " << partitionNum_ << " at \"" << partitionDir_ << "\"):" << std::endl;
    if (efpMap_.empty()) {
        oss << indentStr << "<Partition empty, no EFPs found>" << std::endl;
    } else {
        oss << indentStr << std::setw(12) << "efp_size_kib"
                         << std::setw(12) << "num_files"
                         << std::setw(18) << "tot_capacity_kib" << std::endl;
        oss << indentStr << std::setw(12) << "------------"
                         << std::setw(12) << "----------"
                         << std::setw(18) << "----------------" << std::endl;
        {
            slock l(efpMapMutex_);
            for (efpMapConstItr_t i=efpMap_.begin(); i!= efpMap_.end(); ++i) {
                oss << indentStr << std::setw(12) << i->first
                                 << std::setw(12) << i->second->numEmptyFiles()
                                 << std::setw(18) << i->second->cumFileSize_kib() << std::endl;
            }
        }
    }
    return oss.str();
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
        long pn = ::strtol(name.c_str() + 1, 0, 10);
        if (pn == 0 && errno) {
            return 0;
        } else {
            return (efpPartitionNumber_t)pn;
        }
    }
    return 0;
}

// --- protected functions ---

EmptyFilePool* EmptyFilePoolPartition::createEmptyFilePool(const efpDataSize_kib_t efpDataSize_kib) {
    std::string fqEfpDirectoryName(partitionDir_ + "/" + EmptyFilePoolPartition::s_efpTopLevelDir_ + "/" + EmptyFilePool::dirNameFromDataSize(efpDataSize_kib));
    return createEmptyFilePool(fqEfpDirectoryName);
}

EmptyFilePool* EmptyFilePoolPartition::createEmptyFilePool(const std::string fqEfpDirectoryName) {
    EmptyFilePool* efpp = 0;
    try {
        efpp = new EmptyFilePool(fqEfpDirectoryName, this, overwriteBeforeReturnFlag_, truncateFlag_, journalLogRef_);
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
        std::ostringstream oss;
        oss << "EmptyFilePool create failed: " << e.what();
        journalLogRef_.log(JournalLog::LOG_WARN, oss.str());
    }
    if (efpp != 0) {
        efpp->initialize();
    }
    return efpp;
}

void EmptyFilePoolPartition::validatePartitionDir() {
    std::ostringstream ss;
    if (!jdir::is_dir(partitionDir_)) {
        ss << "Invalid partition directory: \'" << partitionDir_ << "\' is not a directory";
        throw jexception(jerrno::JERR_EFP_BADPARTITIONDIR, ss.str(), "EmptyFilePoolPartition", "validatePartitionDir");
    }

    // TODO: other validity checks here
}

}}}
