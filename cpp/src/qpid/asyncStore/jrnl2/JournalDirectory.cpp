/*
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
 */

/**
 * \file JournalDirectory.cpp
 */
#include "qpid/asyncStore/jrnl2/JournalDirectory.h"

#include "qpid/asyncStore/jrnl2/JournalError.h"

#include <cstring>
#include <dirent.h>
#include <errno.h>
#include <sstream>
#include <unistd.h>

#include <sys/stat.h>

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

JournalDirectory::JournalDirectory(const std::string& fqName) :
        m_fqName(fqName),
        m_verified(false)
{}

const
std::string JournalDirectory::getFqName() const {
    return m_fqName;
}

void
JournalDirectory::setFqName(const std::string newFqName,
                            const bool createNew,
                            const bool destroyExisting) {
    if (m_fqName.compare(newFqName) != 0) {
        if (destroyExisting) {
            destroy();
        }
        m_fqName = newFqName;
        if (createNew) {
            create();
        }
    }
}

// static
bool
JournalDirectory::s_exists(const std::string& fqName,
                           const bool checkIsWritable) {
    struct stat buff;
    if (::lstat(fqName.c_str(), &buff)) {
        if (errno == ENOENT) // No such dir or file
            return false;
        HANDLE_SYS_ERROR(fqName, JournalError::JERR_STAT, "s_exists");
    }
    CHK_ERROR(!S_ISDIR(buff.st_mode), fqName, JournalError::JERR_NOTADIR, "s_exists");
    if (checkIsWritable) {
        return (buff.st_mode & (S_IWUSR | S_IWGRP | S_IWOTH));
    }
    return true;
}

// static
void
JournalDirectory::s_create(const std::string& fqName) {
    std::size_t fdp = fqName.find_last_of('/');
    if (fdp != std::string::npos) {
        std::string parent_dir = fqName.substr(0, fdp);
        if (!s_exists(parent_dir)) {
            s_create(parent_dir);
        }
    }
    if (::mkdir(fqName.c_str(), S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH)) {
        if (errno != EEXIST) { // Dir exists, ignore
            HANDLE_SYS_ERROR(fqName, JournalError::JERR_MKDIR, "s_create");
        }
    }
}

void
JournalDirectory::create() {
    s_create(m_fqName);
    m_verified = true;
}

//static
void
JournalDirectory::s_clear(const std::string& fqName,
                          const bool recursiveDelete) {
    s_destroy(fqName, recursiveDelete, true);
}

void
JournalDirectory::clear(const bool recursiveDelete) {
    s_clear(m_fqName, recursiveDelete);
}

// static
void
JournalDirectory::s_destroy(const std::string& fqName,
                            const bool recursiveDelete,
                            const bool childrenOnly) {
    if (s_exists(fqName)) {
        DIR* dir = ::opendir(fqName.c_str());
        if (dir) {
            struct stat buff;
            struct dirent* entry;
            while ((entry = ::readdir(dir)) != 0) {
                // Ignore . and ..
                if (std::strcmp(entry->d_name, ".") != 0 && std::strcmp(entry->d_name, "..") != 0) {
                    std::string fullName(fqName + "/" + entry->d_name);
                    CHK_SYS_ERROR(::lstat(fullName.c_str(), &buff), fullName, JournalError::JERR_STAT, "s_destroy");
                    if (S_ISREG(buff.st_mode) || S_ISLNK(buff.st_mode)) { // This is a file or symlink
                        CHK_SYS_ERROR(::unlink(fullName.c_str()), fullName, JournalError::JERR_UNLINK, "s_destroy");
                    } else if (S_ISDIR(buff.st_mode)) { // This is a directory
                        if (recursiveDelete) {
                            s_destroy(fullName);
                        } else {
                            HANDLE_ERROR(fullName, JournalError::JERR_DIRNOTEMPTY, "s_destroy");
                        }
                    } else {
                        HANDLE_ERROR(fullName, JournalError::JERR_BADFTYPE, "s_destroy");
                    }
                }
            }
            CHK_SYS_ERROR(::closedir(dir), fqName, JournalError::JERR_CLOSEDIR, "s_destroy");
            if (!childrenOnly) {
                CHK_SYS_ERROR(::rmdir(fqName.c_str()), fqName, JournalError::JERR_RMDIR, "s_destroy");
            }
        } else {
            HANDLE_SYS_ERROR(fqName, JournalError::JERR_OPENDIR, "s_destroy");
        }
    }
}

void
JournalDirectory::destroy(const bool recursiveDelete,
                          const bool childrenOnly) {
    if (m_verified) {
        s_destroy(m_fqName, recursiveDelete, childrenOnly);
        m_verified = false;
    }
}

bool
JournalDirectory::isVerified() const {
    return m_verified;
}

}}} // namespace qpid::asyncStore::jrnl2
