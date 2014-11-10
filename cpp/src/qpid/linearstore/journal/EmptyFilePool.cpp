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

namespace qpid {
namespace linearstore {
namespace journal {

// static
std::string EmptyFilePool::s_inuseFileDirectory_ = "in_use";

// static
std::string EmptyFilePool::s_returnedFileDirectory_ = "returned";

EmptyFilePool::EmptyFilePool(const std::string& efpDirectory,
                             const EmptyFilePoolPartition* partitionPtr,
                             const bool overwriteBeforeReturnFlag,
                             const bool truncateFlag,
                             JournalLog& journalLogRef) :
                efpDirectory_(efpDirectory),
                efpDataSize_kib_(dataSizeFromDirName_kib(efpDirectory, partitionPtr->getPartitionNumber())),
                partitionPtr_(partitionPtr),
                overwriteBeforeReturnFlag_(overwriteBeforeReturnFlag),
                truncateFlag_(truncateFlag),
                journalLogRef_(journalLogRef)
{}

EmptyFilePool::~EmptyFilePool() {}

void EmptyFilePool::initialize() {
//std::cout << "*** Initializing EFP " << efpDataSize_kib_ << "k in partition " << partitionPtr_->getPartitionNumber() << "; efpDirectory=" << efpDirectory_ << std::endl; // DEBUG
    std::vector<std::string> dirList;

    // Process empty files in main dir
    jdir::read_dir(efpDirectory_, dirList, false, true, false, false);
    for (std::vector<std::string>::iterator i = dirList.begin(); i != dirList.end(); ++i) {
        size_t dotPos = i->rfind(".");
        if (dotPos != std::string::npos) {
            if (i->substr(dotPos).compare(".jrnl") == 0 && i->length() == 41) {
                std::string emptyFileName(efpDirectory_ + "/" + (*i));
                if (validateEmptyFile(emptyFileName)) {
                    pushEmptyFile(emptyFileName);
                }
            }
        }
    }

    // Create 'in_use' and 'returned' subdirs if they don't already exist
    // Retern files to EFP in 'in_use' and 'returned' subdirs if they do exist
    initializeSubDirectory(efpDirectory_ + "/" + s_inuseFileDirectory_);
    initializeSubDirectory(efpDirectory_ + "/" + s_returnedFileDirectory_);
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
    std::string newFileName = efpDirectory_ + "/" + s_inuseFileDirectory_ + emptyFileName.substr(emptyFileName.rfind('/'));
    std::string symlinkName = destDirectory + emptyFileName.substr(emptyFileName.rfind('/')); // NOTE: substr() includes leading '/'
    if (moveFile(emptyFileName, newFileName)) {
        // Try again with new UUID for file name
        newFileName = efpDirectory_ + "/" + s_inuseFileDirectory_ + "/" + getEfpFileName();
        if (moveFile(emptyFileName, newFileName)) {
//std::cerr << "*** DEBUG: pushEmptyFile " << emptyFileName << "from  EmptyFilePool::takeEmptyFile()" << std::endl; // DEBUG
            pushEmptyFile(emptyFileName);
            std::ostringstream oss;
            oss << "file=\"" << emptyFileName << "\" dest=\"" <<  newFileName << "\"" << FORMAT_SYSERR(errno);
            throw jexception(jerrno::JERR_JDIR_FMOVE, oss.str(), "EmptyFilePool", "takeEmptyFile");
        }
    }
    if (createSymLink(newFileName, symlinkName)) {
        std::ostringstream oss;
        oss << "file=\"" << emptyFileName << "\" dest=\"" <<  newFileName << "\" symlink=\"" << symlinkName << "\"" << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_EFP_SYMLINK, oss.str(), "EmptyFilePool", "takeEmptyFile");
    }
    return symlinkName;
}

void EmptyFilePool::returnEmptyFileSymlink(const std::string& emptyFileSymlink) {
    if (isFile(emptyFileSymlink)) {
        returnEmptyFile(emptyFileSymlink);
    } else if(isSymlink(emptyFileSymlink)) {
        returnEmptyFile(deleteSymlink(emptyFileSymlink));
    } else {
        std::ostringstream oss;
        oss << "File \"" << emptyFileSymlink << "\" is neither a file nor a symlink";
        throw jexception(jerrno::JERR_EFP_BADFILETYPE, oss.str(), "EmptyFilePool", "returnEmptyFileSymlink");
    }
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

std::string EmptyFilePool::createEmptyFile() {
    std::string efpfn = getEfpFileName();
    if (!overwriteFileContents(efpfn)) {
        // TODO: handle failure to prepare new file here
    }
    return efpfn;
}

std::string EmptyFilePool::getEfpFileName() {
    qpid::types::Uuid uuid(true);
    std::ostringstream oss;
    oss << efpDirectory_ << "/" << uuid << QLS_JRNL_FILE_EXTENSION;
    return oss.str();
}

void EmptyFilePool::initializeSubDirectory(const std::string& fqDirName) {
    std::vector<std::string> dirList;
    if (jdir::exists(fqDirName)) {
        if (truncateFlag_) {
            jdir::read_dir(fqDirName, dirList, false, true, false, false);
            for (std::vector<std::string>::iterator i = dirList.begin(); i != dirList.end(); ++i) {
                size_t dotPos = i->rfind(".");
                if (i->substr(dotPos).compare(".jrnl") == 0 && i->length() == 41) {
                    returnEmptyFile(fqDirName + "/" + (*i));
                } else {
                    std::ostringstream oss;
                    oss << "File \'" << *i << "\' was not a journal file and was not returned to EFP.";
                    journalLogRef_.log(JournalLog::LOG_WARN, oss.str());
                }
            }
        }
    } else {
        jdir::create_dir(fqDirName);
    }
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
//std::cout << "*** WARNING: EFP " << efpDirectory_ << " is empty - created new journal file " << fqFileName.substr(fqFileName.rfind('/') + 1) << " on the fly" << std::endl; // DEBUG
    } else {
//std::cerr << "*** ERROR: Unable to open file \"" << fqFileName << "\"" << std::endl; // DEBUG
    }
    return false;
}

std::string EmptyFilePool::popEmptyFile() {
    std::string emptyFileName;
    bool listEmptyFlag;
    {
        slock l(emptyFileListMutex_);
        listEmptyFlag = emptyFileList_.empty();
        if (!listEmptyFlag) {
            emptyFileName = emptyFileList_.front();
            emptyFileList_.pop_front();
        }
    }
    // If the list is empty, create a new file and return the file name.
    if (listEmptyFlag) {
        emptyFileName = createEmptyFile();
    }
    return emptyFileName;
}

void EmptyFilePool::pushEmptyFile(const std::string fqFileName) {
    slock l(emptyFileListMutex_);
    emptyFileList_.push_back(fqFileName);
}

void EmptyFilePool::returnEmptyFile(const std::string& emptyFileName) {
    std::string returnedFileName = efpDirectory_ + "/" + s_returnedFileDirectory_ + emptyFileName.substr(emptyFileName.rfind('/')); // NOTE: substr() includes leading '/'
    if (moveFile(emptyFileName, returnedFileName)) {
        ::unlink(emptyFileName.c_str());
//std::cerr << "*** WARNING: Unable to move file " << emptyFileName << " to " << returnedFileName << "; deleted." << std::endl; // DEBUG
    }

    // TODO: On a separate thread, process returned files by overwriting headers and, optionally, their contents and
    // returning them to the EFP directory
    resetEmptyFileHeader(returnedFileName);
    if (overwriteBeforeReturnFlag_) {
        overwriteFileContents(returnedFileName);
    }
    std::string sanitizedEmptyFileName = efpDirectory_ + returnedFileName.substr(returnedFileName.rfind('/')); // NOTE: substr() includes leading '/'
    if (moveFile(returnedFileName, sanitizedEmptyFileName)) {
        ::unlink(returnedFileName.c_str());
//std::cerr << "*** WARNING: Unable to move file " << returnedFileName << " to " << sanitizedEmptyFileName << "; deleted." << std::endl; // DEBUG
    } else {
        pushEmptyFile(sanitizedEmptyFileName);
    }
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
//std::cerr << "*** ERROR: Unable to write file header of file \"" << fqFileName << "\": tried to write " << buffsize << " bytes; wrote " << bytesWritten << " bytes." << std::endl; // DEBUG
            }
        } else {
//std::cerr << "*** ERROR: Unable to read file header of file \"" << fqFileName << "\": tried to read " << sizeof(::file_hdr_t) << " bytes; read " << bytesRead << " bytes." << std::endl; // DEBUG
        }
        fs.close();
    } else {
//std::cerr << "*** ERROR: Unable to open file \"" << fqFileName << "\" for reading" << std::endl; // DEBUG
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
int EmptyFilePool::moveFile(const std::string& from,
                                 const std::string& to) {
    if (::rename(from.c_str(), to.c_str())) {
        if (errno == EEXIST) return errno; // File name exists
        std::ostringstream oss;
        oss << "file=\"" << from << "\" dest=\"" <<  to << "\"" << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_JDIR_FMOVE, oss.str(), "EmptyFilePool", "returnEmptyFile");
    }
    return 0;
}

//static
int EmptyFilePool::createSymLink(const std::string& fqFileName,
                                 const std::string& fqLinkName) {
    if(::symlink(fqFileName.c_str(), fqLinkName.c_str())) {
        if (errno == EEXIST) return errno; // File name exists
        std::ostringstream oss;
        oss << "file=\"" << fqFileName << "\" symlink=\"" <<  fqLinkName << "\"" << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_EFP_SYMLINK, oss.str(), "EmptyFilePool", "createSymLink");
    }
    return 0;
}

//static
std::string EmptyFilePool::deleteSymlink(const std::string& fqLinkName) {
    char buff[1024];
    ssize_t len = ::readlink(fqLinkName.c_str(), buff, 1024);
    if (len < 0) {
        std::ostringstream oss;
        oss << "symlink=\"" << fqLinkName << "\"" << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_EFP_SYMLINK, oss.str(), "EmptyFilePool", "deleteSymlink");
    }
    ::unlink(fqLinkName.c_str());
    return std::string(buff, len);
}

//static
bool EmptyFilePool::isFile(const std::string& fqName) {
    struct stat buff;
    if (::lstat(fqName.c_str(), &buff)) {
        std::ostringstream oss;
        oss << "lstat file=\"" << fqName << "\"" << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_EFP_LSTAT, oss.str(), "EmptyFilePool", "isFile");
    }
    return S_ISREG(buff.st_mode);
}

//static
bool EmptyFilePool::isSymlink(const std::string& fqName) {
    struct stat buff;
    if (::lstat(fqName.c_str(), &buff)) {
        std::ostringstream oss;
        oss << "lstat file=\"" << fqName << "\"" << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_EFP_LSTAT, oss.str(), "EmptyFilePool", "isSymlink");
    }
    return S_ISLNK(buff.st_mode);

}

}}}
