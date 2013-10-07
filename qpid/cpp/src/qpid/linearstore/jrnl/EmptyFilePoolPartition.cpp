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

#include "qpid/linearstore/jrnl/EmptyFilePoolPartition.h"

#include <dirent.h>
#include "qpid/linearstore/jrnl/jdir.h"
#include "qpid/linearstore/jrnl/jerrno.h"
#include "qpid/linearstore/jrnl/jexception.h"
#include "qpid/linearstore/jrnl/slock.h"

//#include <iostream> // DEBUG

namespace qpid {
namespace qls_jrnl {

const std::string EmptyFilePoolPartition::efpTopLevelDir("efp"); // Sets the top-level efp dir within a partition

EmptyFilePoolPartition::EmptyFilePoolPartition(const efpPartitionNumber_t partitionNum_, const std::string& partitionDir_) :
                partitionNum(partitionNum_),
                partitionDir(partitionDir_)
{
    validatePartitionDir();
}

EmptyFilePoolPartition::~EmptyFilePoolPartition() {
    slock l(efpMapMutex);
    for (efpMapItr_t i = efpMap.begin(); i != efpMap.end(); ++i) {
        delete i->second;
    }
    efpMap.clear();
}

void
EmptyFilePoolPartition::validatePartitionDir() {
    if (!jdir::is_dir(partitionDir)) {
        std::ostringstream ss;
        ss << "Invalid partition directory: \'" << partitionDir << "\' is not a directory";
        throw jexception(jerrno::JERR_EFP_BADPARTITIONDIR, ss.str(), "EmptyFilePoolPartition", "validatePartitionDir");
    }
    // TODO: other validity checks here
}

void
EmptyFilePoolPartition::findEmptyFilePools() {
    //std::cout << "Reading " << partitionDir << std::endl; // DEBUG
    std::vector<std::string> dirList;
    jdir::read_dir(partitionDir, dirList, true, false, false, false);
    bool foundEfpDir = false;
    for (std::vector<std::string>::iterator i = dirList.begin(); i != dirList.end(); ++i) {
        if (i->compare(efpTopLevelDir) == 0) {
            foundEfpDir = true;
            break;
        }
    }
    if (foundEfpDir) {
        std::string efpDir(partitionDir + "/" + efpTopLevelDir);
        //std::cout << "Reading " << efpDir << std::endl; // DEBUG
        dirList.clear();
        jdir::read_dir(efpDir, dirList, true, false, false, true);
        for (std::vector<std::string>::iterator i = dirList.begin(); i != dirList.end(); ++i) {
            EmptyFilePool* efpp = 0;
            try {
                efpp = new EmptyFilePool(*i, this);
                {
                    slock l(efpMapMutex);
                    efpMap[efpp->dataSize_kib()] = efpp;
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

efpPartitionNumber_t
EmptyFilePoolPartition::partitionNumber() const {
    return partitionNum;
}

std::string
EmptyFilePoolPartition::partitionDirectory() const {
    return partitionDir;
}

EmptyFilePool*
EmptyFilePoolPartition::getEmptyFilePool(const efpDataSize_kib_t efpFileSizeKb) {
    efpMapItr_t i = efpMap.find(efpFileSizeKb);
    if (i == efpMap.end())
        return 0;
    return i->second;
}

void
EmptyFilePoolPartition::getEmptyFilePoolSizesKb(std::vector<efpDataSize_kib_t>& efpFileSizesKbList) const {
    for (efpMapConstItr_t i=efpMap.begin(); i!=efpMap.end(); ++i) {
        efpFileSizesKbList.push_back(i->first);
    }
}

void
EmptyFilePoolPartition::getEmptyFilePools(std::vector<EmptyFilePool*>& efpList) {
    for (efpMapItr_t i=efpMap.begin(); i!=efpMap.end(); ++i) {
        efpList.push_back(i->second);
    }
}

}} // namespace qpid::qls_jrnl
