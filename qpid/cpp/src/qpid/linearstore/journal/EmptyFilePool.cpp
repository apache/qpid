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

#include <fstream>
#include "qpid/linearstore/journal/EmptyFilePoolPartition.h"
#include "qpid/linearstore/journal/jcfg.h"
#include "qpid/linearstore/journal/jdir.h"
#include "qpid/linearstore/journal/JournalLog.h"
#include "qpid/linearstore/journal/slock.h"
#include "qpid/linearstore/journal/utils/file_hdr.h"
#include "qpid/types/Uuid.h"
#include <sys/stat.h>
#include <unistd.h>
#include <vector>

//#include <iostream> // DEBUG

namespace qpid {
namespace linearstore {
namespace journal {

EmptyFilePool::EmptyFilePool(const std::string& efpDirectory,
                             const EmptyFilePoolPartition* partitionPtr,
                             JournalLog& journalLogRef) :
                efpDirectory_(efpDirectory),
                efpDataSize_kib_(dataSizeFromDirName_kib(efpDirectory, partitionPtr->getPartitionNumber())),
                partitionPtr_(partitionPtr),
                journalLogRef_(journalLogRef)
{}

EmptyFilePool::~EmptyFilePool() {}

void EmptyFilePool::initialize() {
    std::vector<std::string> dirList;
    jdir::read_dir(efpDirectory_, dirList, false, true, false, false);
    for (std::vector<std::string>::iterator i = dirList.begin(); i != dirList.end(); ++i) {
        size_t dotPos = i->rfind(".");
        if (dotPos != std::string::npos) {
            if (i->substr(dotPos).compare(".jrnl") == 0 && i->length() == 41) {
                std::string emptyFile(efpDirectory_ + "/" + (*i));
                if (validateEmptyFile(emptyFile)) {
                    pushEmptyFile(emptyFile);
                }
            }
        }
    }
}

efpDataSize_kib_t EmptyFilePool::dataSize_kib() const {
    return efpDataSize_kib_;
}

efpFileSize_kib_t EmptyFilePool::fileSize_kib() const {
    return efpDataSize_kib_ + (QLS_JRNL_FHDR_RES_SIZE_SBLKS * QLS_SBLK_SIZE_KIB);
}

efpDataSize_sblks_t EmptyFilePool::dataSize_sblks() const {
    return efpDataSize_kib_ / QLS_SBLK_SIZE_KIB;
}

efpFileSize_sblks_t EmptyFilePool::fileSize_sblks() const {
    return (efpDataSize_kib_ / QLS_SBLK_SIZE_KIB) + QLS_JRNL_FHDR_RES_SIZE_SBLKS;
}

efpFileCount_t EmptyFilePool::numEmptyFiles() const {
    slock l(emptyFileListMutex_);
    return efpFileCount_t(emptyFileList_.size());
}

efpDataSize_kib_t EmptyFilePool::cumFileSize_kib() const {
    slock l(emptyFileListMutex_);
    return efpDataSize_kib_t(emptyFileList_.size()) * efpDataSize_kib_;
}

efpPartitionNumber_t EmptyFilePool::getPartitionNumber() const {
    return partitionPtr_->getPartitionNumber();
}

const EmptyFilePoolPartition* EmptyFilePool::getPartition() const {
    return partitionPtr_;
}

const efpIdentity_t EmptyFilePool::getIdentity() const {
    return efpIdentity_t(partitionPtr_->getPartitionNumber(), efpDataSize_kib_);
}

std::string EmptyFilePool::takeEmptyFile(const std::string& destDirectory) {
    std::string emptyFileName = popEmptyFile();
    std::string newFileName = destDirectory + emptyFileName.substr(emptyFileName.rfind('/')); // NOTE: substr() includes leading '/'
    if (moveEmptyFile(emptyFileName.c_str(), newFileName.c_str())) {
        // Try again with new UUID for file name
        newFileName = destDirectory + "/" + getEfpFileName();
        if (moveEmptyFile(emptyFileName.c_str(), newFileName.c_str())) {
            pushEmptyFile(emptyFileName);
            std::ostringstream oss;
            oss << "file=\"" << emptyFileName << "\" dest=\"" <<  newFileName << "\"" << FORMAT_SYSERR(errno);
            throw jexception(jerrno::JERR_JDIR_FMOVE, oss.str(), "EmptyFilePool", "takeEmptyFile");
        }
    }
    return newFileName;
}

void EmptyFilePool::returnEmptyFile(const std::string& fqSrcFile) {
    std::string emptyFileName(efpDirectory_ + fqSrcFile.substr(fqSrcFile.rfind('/'))); // NOTE: substr() includes leading '/'
    if (moveEmptyFile(fqSrcFile.c_str(), emptyFileName.c_str())) {
        // Try again with new UUID for file name
        emptyFileName = efpDirectory_ + "/" + getEfpFileName();
        if (moveEmptyFile(fqSrcFile.c_str(), emptyFileName.c_str())) {
            // Failed twice in a row - delete file
            ::unlink(fqSrcFile.c_str());
            return;
        }
    }
    resetEmptyFileHeader(emptyFileName);
    if (partitionPtr_->getOverwriteBeforeReturnFlag()) {
        overwriteFileContents(emptyFileName);
    }
    pushEmptyFile(emptyFileName);
}

//static
std::string EmptyFilePool::dirNameFromDataSize(const efpDataSize_kib_t efpDataSize_kib) {
    std::ostringstream oss;
    oss << efpDataSize_kib << "k";
    return oss.str();
}


// static
efpDataSize_kib_t EmptyFilePool::dataSizeFromDirName_kib(const std::string& dirName,
                                                        const efpPartitionNumber_t partitionNumber) {
    // Check for dirName format 'NNNk', where NNN is a number, convert NNN into an integer. NNN cannot be 0.
    std::string n(dirName.substr(dirName.rfind('/')+1));
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
    efpDataSize_kib_t s = ::atol(n.c_str());
    if (!valid || s == 0 || s % QLS_SBLK_SIZE_KIB != 0) {
        std::ostringstream oss;
        oss << "Partition: " << partitionNumber << "; EFP directory: \'" << n << "\'";
        throw jexception(jerrno::JERR_EFP_BADEFPDIRNAME, oss.str(), "EmptyFilePool", "fileSizeKbFromDirName");
    }
    return s;
}

// --- protected functions ---

void EmptyFilePool::createEmptyFile() {
    std::string efpfn = getEfpFileName();
    if (overwriteFileContents(efpfn)) {
        pushEmptyFile(efpfn);
    }
}

std::string EmptyFilePool::getEfpFileName() {
    qpid::types::Uuid uuid(true);
    std::ostringstream oss;
    oss << efpDirectory_ << "/" << uuid << QLS_JRNL_FILE_EXTENSION;
    return oss.str();
}

bool EmptyFilePool::overwriteFileContents(const std::string& fqFileName) {
    ::file_hdr_t fh;
    ::file_hdr_create(&fh, QLS_FILE_MAGIC, QLS_JRNL_VERSION, QLS_JRNL_FHDR_RES_SIZE_SBLKS, partitionPtr_->getPartitionNumber(), efpDataSize_kib_);
    std::ofstream ofs(fqFileName.c_str(), std::ofstream::out | std::ofstream::binary);
    if (ofs.good()) {
        ofs.write((char*)&fh, sizeof(::file_hdr_t));
        uint64_t rem = ((efpDataSize_kib_ + (QLS_JRNL_FHDR_RES_SIZE_SBLKS * QLS_SBLK_SIZE_KIB)) * 1024) - sizeof(::file_hdr_t);
        while (rem--)
            ofs.put('\0');
        ofs.close();
        return true;
//std::cout << "WARNING: EFP " << efpDirectory << " is empty - created new journal file " << efpfn.substr(efpfn.rfind('/') + 1) << " on the fly" << std::endl; // DEBUG
    } else {
//std::cerr << "ERROR: Unable to open file \"" << efpfn << "\"" << std::endl; // DEBUG
    }
    return false;
}

std::string EmptyFilePool::popEmptyFile() {
    std::string emptyFileName;
    bool isEmpty = false;
    {
        slock l(emptyFileListMutex_);
        isEmpty = emptyFileList_.empty();
    }
    if (isEmpty) {
        createEmptyFile();
    }
    {
        slock l(emptyFileListMutex_);
        emptyFileName = emptyFileList_.front();
        emptyFileList_.pop_front();
    }
    return emptyFileName;
}

void EmptyFilePool::pushEmptyFile(const std::string fqFileName) {
    slock l(emptyFileListMutex_);
    emptyFileList_.push_back(fqFileName);
}

void EmptyFilePool::resetEmptyFileHeader(const std::string& fqFileName) {
    std::fstream fs(fqFileName.c_str(), std::fstream::in | std::fstream::out | std::fstream::binary);
    if (fs.good()) {
        const std::streamsize buffsize = QLS_JRNL_FHDR_RES_SIZE_SBLKS * QLS_SBLK_SIZE_BYTES;
        char buff[buffsize];
        fs.read((char*)buff, buffsize);
        std::streampos bytesRead = fs.tellg();
        if (std::streamoff(bytesRead) == buffsize) {
            ::file_hdr_reset((::file_hdr_t*)buff);
            // set rest of buffer to 0
            ::memset(buff + sizeof(::file_hdr_t), 0, MAX_FILE_HDR_LEN - sizeof(::file_hdr_t));
            fs.seekp(0, std::fstream::beg);
            fs.write(buff, buffsize);
            std::streampos bytesWritten = fs.tellp();
            if (std::streamoff(bytesWritten) != buffsize) {
//std::cerr << "ERROR: Unable to write file header of file \"" << fqFileName_ << "\": tried to write " << buffsize << " bytes; wrote " << bytesWritten << " bytes." << std::endl;
            }
        } else {
//std::cerr << "ERROR: Unable to read file header of file \"" << fqFileName_ << "\": tried to read " << sizeof(::file_hdr_t) << " bytes; read " << bytesRead << " bytes." << std::endl;
        }
        fs.close();
    } else {
//std::cerr << "ERROR: Unable to open file \"" << fqFileName_ << "\" for reading" << std::endl; // DEBUG
    }
}

bool EmptyFilePool::validateEmptyFile(const std::string& emptyFileName) const {
    std::ostringstream oss;
    struct stat s;
    if (::stat(emptyFileName.c_str(), &s))
    {
        oss << "stat: file=\"" << emptyFileName << "\"" << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_JDIR_STAT, oss.str(), "EmptyFilePool", "validateEmptyFile");
    }

    // Size matches pool
    efpDataSize_kib_t expectedSize = (QLS_SBLK_SIZE_KIB + efpDataSize_kib_) * 1024;
    if ((efpDataSize_kib_t)s.st_size != expectedSize) {
        oss << "ERROR: File " << emptyFileName << ": Incorrect size: Expected=" << expectedSize
            << "; actual=" << s.st_size;
        journalLogRef_.log(JournalLog::LOG_ERROR, oss.str());
        return false;
    }

    // Open file and read header
    std::fstream fs(emptyFileName.c_str(), std::fstream::in | std::fstream::out | std::fstream::binary);
    if (!fs) {
        oss << "ERROR: File " << emptyFileName << ": Unable to open for reading";
        journalLogRef_.log(JournalLog::LOG_ERROR, oss.str());
        return false;
    }
    const std::streamsize buffsize = QLS_JRNL_FHDR_RES_SIZE_SBLKS * QLS_SBLK_SIZE_BYTES;
    char buff[buffsize];
    fs.read((char*)buff, buffsize);
    std::streampos bytesRead = fs.tellg();
    if (std::streamoff(bytesRead) != buffsize) {
        oss << "ERROR: Unable to read file header of file \"" << emptyFileName << "\": tried to read "
            << buffsize << " bytes; read " << bytesRead << " bytes";
        journalLogRef_.log(JournalLog::LOG_ERROR, oss.str());
        fs.close();
        return false;
    }

    // Check file header
    ::file_hdr_t* fhp = (::file_hdr_t*)buff;
    const bool jrnlMagicError = fhp->_rhdr._magic != QLS_FILE_MAGIC;
    const bool jrnlVersionError = fhp->_rhdr._version != QLS_JRNL_VERSION;
    const bool jrnlPartitionError = fhp->_efp_partition != partitionPtr_->getPartitionNumber();
    const bool jrnlFileSizeError = fhp->_data_size_kib != efpDataSize_kib_;
    if (jrnlMagicError || jrnlVersionError || jrnlPartitionError || jrnlFileSizeError)
    {
        oss << "ERROR: File " << emptyFileName << ": Invalid file header - mismatched header fields: " <<
                        (jrnlMagicError ? "magic " : "") <<
                        (jrnlVersionError ? "version " : "") <<
                        (jrnlPartitionError ? "partition" : "") <<
                        (jrnlFileSizeError ? "file-size" : "");
        journalLogRef_.log(JournalLog::LOG_ERROR, oss.str());
        fs.close();
        return false;
    }

    // Check file header is reset
    if (!::is_file_hdr_reset(fhp)) {
        ::file_hdr_reset(fhp);
        ::memset(buff + sizeof(::file_hdr_t), 0, MAX_FILE_HDR_LEN - sizeof(::file_hdr_t)); // set rest of buffer to 0
        fs.seekp(0, std::fstream::beg);
        fs.write(buff, buffsize);
        std::streampos bytesWritten = fs.tellp();
        if (std::streamoff(bytesWritten) != buffsize) {
            oss << "ERROR: Unable to write file header of file \"" << emptyFileName << "\": tried to write "
                << buffsize << " bytes; wrote " << bytesWritten << " bytes";
            journalLogRef_.log(JournalLog::LOG_ERROR, oss.str());
            fs.close();
            return false;
        }
        oss << "WARNING: File " << emptyFileName << ": File header not reset";
        journalLogRef_.log(JournalLog::LOG_WARN, oss.str());
    }

    // Close file
    fs.close();
    return true;
}

// static
int EmptyFilePool::moveEmptyFile(const std::string& from,
                                 const std::string& to) {
    if (::rename(from.c_str(), to.c_str())) {
        if (errno == EEXIST) return errno; // File name exists
        std::ostringstream oss;
        oss << "file=\"" << from << "\" dest=\"" <<  to << "\"" << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_JDIR_FMOVE, oss.str(), "EmptyFilePool", "returnEmptyFile");
    }
    return 0;
}

}}}
