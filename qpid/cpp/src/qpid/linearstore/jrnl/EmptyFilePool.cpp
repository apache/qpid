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

#include "EmptyFilePool.h"

#include <cctype>
#include <fstream>
#include "qpid/linearstore/jrnl/jcfg.h"
#include "qpid/linearstore/jrnl/jdir.h"
#include "qpid/linearstore/jrnl/JournalFile.h"
#include "qpid/linearstore/jrnl/slock.h"
#include "qpid/linearstore/jrnl/utils/file_hdr.h"
#include <sys/stat.h>
#include <uuid/uuid.h>
#include <vector>

#include <iostream> // DEBUG

namespace qpid {
namespace qls_jrnl {

EmptyFilePool::EmptyFilePool(const std::string& efpDirectory_,
                             const EmptyFilePoolPartition* partitionPtr_) :
                efpDirectory(efpDirectory_),
                efpFileSizeKib(fileSizeKbFromDirName(efpDirectory_, partitionPtr_->partitionNumber())),
                partitionPtr(partitionPtr_)
{}

EmptyFilePool::~EmptyFilePool() {}

void
EmptyFilePool::initialize() {
    //std::cout << "Reading " << efpDirectory << std::endl; // DEBUG
    std::vector<std::string> dirList;
    jdir::read_dir(efpDirectory, dirList, false, true, false);
    for (std::vector<std::string>::iterator i = dirList.begin(); i != dirList.end(); ++i) {
        size_t dotPos = i->rfind(".");
        if (dotPos != std::string::npos) {
            if (i->substr(dotPos).compare(".jrnl") == 0 && i->length() == 41) {
                std::string emptyFile(efpDirectory + "/" + (*i));
                if (validateEmptyFile(emptyFile)) {
                    pushEmptyFile(emptyFile);
                }
            }
        }
    }
    //std::cout << "Found " << emptyFileList.size() << " files" << std::endl; // DEBUG
}

efpFileSizeKib_t
EmptyFilePool::fileSizeKib() const {
    return efpFileSizeKib;
}

efpFileCount_t
EmptyFilePool::numEmptyFiles() const {
    slock l(emptyFileListMutex);
    return efpFileCount_t(emptyFileList.size());
}

efpFileSizeKib_t
EmptyFilePool::cumFileSizeKib() const {
    slock l(emptyFileListMutex);
    return efpFileSizeKib_t(emptyFileList.size()) * efpFileSizeKib;
}

efpPartitionNumber_t
EmptyFilePool::getPartitionNumber() const {
    return partitionPtr->partitionNumber();
}

const EmptyFilePoolPartition*
EmptyFilePool::getPartition() const {
    return partitionPtr;
}

const efpIdentity_t
EmptyFilePool::getIdentity() const {
    return efpIdentity_t(partitionPtr->partitionNumber(), efpFileSizeKib);
}

std::string
EmptyFilePool::takeEmptyFile(const std::string& destDirectory) {
    std::string emptyFileName = popEmptyFile();
    std::string newFileName = destDirectory + emptyFileName.substr(emptyFileName.rfind('/')); // NOTE: substr() includes leading '/'
    if (::rename(emptyFileName.c_str(), newFileName.c_str())) {
        pushEmptyFile(emptyFileName);
        std::ostringstream oss;
        oss << "file=\"" << emptyFileName << "\" dest=\"" <<  newFileName << "\"" << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_JDIR_FMOVE, oss.str(), "EmptyFilePool", "takeEmptyFile");
    }
    return newFileName;
}

bool
EmptyFilePool::returnEmptyFile(const JournalFile* srcFile) {
    std::string emptyFileName(efpDirectory + srcFile->fileName());
    // TODO: reset file here
    if (::rename(srcFile->fqFileName().c_str(), emptyFileName.c_str())) {
        std::ostringstream oss;
        oss << "file=\"" << srcFile << "\" dest=\"" <<  emptyFileName << "\"" << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_JDIR_FMOVE, oss.str(), "EmptyFilePool", "returnEmptyFile");
    }
    pushEmptyFile(emptyFileName);
    return true;
}

// protected

void
EmptyFilePool::pushEmptyFile(const std::string fqFileName_) {
    slock l(emptyFileListMutex);
    emptyFileList.push_back(fqFileName_);
}

std::string
EmptyFilePool::popEmptyFile() {
    std::string emptyFileName;
    bool isEmpty = false;
    {
        slock l(emptyFileListMutex);
        isEmpty = emptyFileList.empty();
    }
    if (isEmpty) {
        createEmptyFile();
    }
    {
        slock l(emptyFileListMutex);
        emptyFileName = emptyFileList.front();
        emptyFileList.pop_front();
    }
    return emptyFileName;
}

void
EmptyFilePool::createEmptyFile() {
    file_hdr_t fh;
    ::file_hdr_create(&fh, QLS_FILE_MAGIC, QLS_JRNL_VERSION, QLS_JRNL_FHDRSIZESBLKS, partitionPtr->partitionNumber(),
                      efpFileSizeKib);
    std::string efpfn = getEfpFileName();
    std::ofstream ofs(efpfn.c_str(), std::ofstream::out | std::ofstream::binary);
    if (ofs.good()) {
        ofs.write((char*)&fh, sizeof(file_hdr_t));
        uint64_t rem = ((efpFileSizeKib + (QLS_JRNL_FHDRSIZESBLKS * JRNL_SBLK_SIZE_KIB)) * 1024) - sizeof(file_hdr_t);
        while (rem--)
            ofs.put('\0');
        ofs.close();
        pushEmptyFile(efpfn);
        std::cout << "WARNING: EFP " << efpDirectory << " is empty - created new journal file " <<
                     efpfn.substr(efpfn.rfind('/') + 1) << " on the fly" << std::endl;
    } else {
        std::cerr << "ERROR: Unable to open file \"" << efpfn << "\"" << std::endl; // DEBUG
    }
}

bool
EmptyFilePool::validateEmptyFile(const std::string& emptyFileName_) const {
    struct stat s;
    if (::stat(emptyFileName_.c_str(), &s))
    {
        std::ostringstream oss;
        oss << "stat: file=\"" << emptyFileName_ << "\"" << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_JDIR_STAT, oss.str(), "EmptyFilePool", "validateEmptyFile");
    }
    efpFileSizeKib_t expectedSize = (JRNL_SBLK_SIZE_KIB + efpFileSizeKib) * 1024;
    if ((efpFileSizeKib_t)s.st_size != expectedSize) {
        //std::cout << "ERROR: File " << emptyFileName << ": Incorrect size: Expected=" << expectedSize << "; actual=" << s.st_size << std::endl; // DEBUG
        return false;
    }

    std::ifstream ifs(emptyFileName_.c_str(), std::ifstream::in | std::ifstream::binary);
    if (!ifs) {
        //std::cout << "ERROR: File " << emptyFileName << ": Unable to open for reading" << std::endl;
        return false;
    }

    const uint8_t fhFileNameBuffLen = 50;
    char fhFileNameBuff[fhFileNameBuffLen];
    file_hdr_t fh;
    ifs.read((char*)&fh, sizeof(file_hdr_t));
    uint16_t fhFileNameLen = fh._queue_name_len > fhFileNameBuffLen ? fhFileNameBuffLen : fh._queue_name_len;
    ifs.read(fhFileNameBuff, fhFileNameLen);
    std::string fhFileName(fhFileNameBuff, fhFileNameLen);
    ifs.close();

    if (fh._rhdr._magic != QLS_FILE_MAGIC ||
        fh._rhdr._version != QLS_JRNL_VERSION ||
        fh._efp_partition != partitionPtr->partitionNumber() ||
        fh._file_size_kib != efpFileSizeKib ||
        !::is_file_hdr_reset(&fh))
    {
        //std::cout << "ERROR: File " << emptyFileName << ": Invalid file header" << std::endl;
        return false;
    }

    return true;
}

std::string
EmptyFilePool::getEfpFileName() {
    uuid_t uuid;
    ::uuid_generate(uuid); // NOTE: NOT THREAD SAFE
    char uuid_str[37]; // 36 char uuid + trailing \0
    ::uuid_unparse(uuid, uuid_str);
    std::ostringstream oss;
    oss << efpDirectory << "/" << uuid_str << QLS_JRNL_FILE_EXTENSION;
    return oss.str();
}

// protected
// static
efpFileSizeKib_t
EmptyFilePool::fileSizeKbFromDirName(const std::string& dirName_,
                                     const efpPartitionNumber_t partitionNumber_) {
    // Check for dirName format 'NNNk', where NNN is a number, convert NNN into an integer. NNN cannot be 0.
    std::string n(dirName_.substr(dirName_.rfind('/')+1));
    bool valid = true;
    for (uint16_t charNum = 0; charNum < n.length(); ++charNum) {
        if (charNum < n.length()-1) {
            if (!::isdigit((int)n[charNum])) {
                valid = false;
                break;
            }
        } else {
            valid = n[charNum] == 'k';
        }
    }
    efpFileSizeKib_t s = ::atol(n.c_str());
    if (!valid || s == 0 || s % JRNL_SBLK_SIZE_KIB != 0) {
        std::ostringstream oss;
        oss << "Partition: " << partitionNumber_ << "; EFP directory: \'" << n << "\'";
        throw jexception(jerrno::JERR_EFP_BADEFPDIRNAME, oss.str(), "EmptyFilePool", "fileSizeKbFromDirName");
    }
    return s;
}

}} // namespace qpid::qls_jrnl
