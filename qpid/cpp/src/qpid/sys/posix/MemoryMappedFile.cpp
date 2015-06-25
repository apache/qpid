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
#include "qpid/sys/MemoryMappedFile.h"
#include "qpid/Exception.h"
#include "qpid/Msg.h"
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#include <fcntl.h>

namespace qpid {
namespace sys {
namespace {
const std::string PAGEFILE_PREFIX("pf_");
const std::string PATH_SEPARATOR("/");
const std::string ESCAPE("%");
const std::string VALID("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-.");
std::string getFileName(const std::string& name, const std::string& dir)
{
    std::stringstream filename;
    if (dir.size())  filename << dir << PATH_SEPARATOR << PAGEFILE_PREFIX;
    size_t start = 0;
    while (true) {
        size_t i = name.find_first_not_of(VALID, start);
        if (i == std::string::npos) {
            filename << name.substr(start);
            return filename.str();
        } else {
            if (i > start) filename << name.substr(start, i-start);
            filename << ESCAPE << (int) name.at(i);
            start = i+1;
        }
    }

}
}

class MemoryMappedFilePrivate
{
    friend class MemoryMappedFile;
    std::string path;
    int fd;
    MemoryMappedFilePrivate() : fd(0) {}
};
MemoryMappedFile::MemoryMappedFile() : state(new MemoryMappedFilePrivate) {}
MemoryMappedFile::~MemoryMappedFile() { delete state; }

void MemoryMappedFile::open(const std::string& name, const std::string& directory)
{
    // Ensure directory exists
    if ( ::mkdir(directory.c_str(), S_IRWXU | S_IRGRP | S_IXGRP )!=0 && errno!=EEXIST ) {
        throw qpid::Exception(QPID_MSG("Failed to create memory mapped file directory " << directory << ": " << qpid::sys::strError(errno)));
    }

    state->path = getFileName(name, directory);

    int flags = O_CREAT | O_TRUNC | O_RDWR;
    int fd = ::open(state->path.c_str(), flags, S_IRUSR | S_IWUSR);
    if (fd == -1) throw qpid::Exception(QPID_MSG("Failed to open memory mapped file " << state->path << ": " << qpid::sys::strError(errno) << " [flags=" << flags << "]"));
    state->fd = fd;
}

void MemoryMappedFile::close()
{
    ::close(state->fd);
    ::unlink(state->path.c_str());
}

size_t MemoryMappedFile::getPageSize()
{
    return ::sysconf(_SC_PAGE_SIZE);
}

char* MemoryMappedFile::map(size_t offset, size_t size)
{
    int protection = PROT_READ | PROT_WRITE;
    char* region = (char*) ::mmap(0, size, protection, MAP_SHARED, state->fd, offset);
    if (region == MAP_FAILED) {
        throw qpid::Exception(QPID_MSG("Failed to map page into memory: " << qpid::sys::strError(errno)));
    }
    return region;

}

void MemoryMappedFile::unmap(char* region, size_t size)
{
    ::munmap(region, size);
}

void MemoryMappedFile::flush(char* region, size_t size)
{
    ::msync(region, size, MS_ASYNC);
}

void MemoryMappedFile::expand(size_t offset)
{
    if ((::lseek(state->fd, offset - 1, SEEK_SET) == -1) || (::write(state->fd, "", 1) == -1)) {
        throw qpid::Exception(QPID_MSG("Failed to expand paged queue file: " << qpid::sys::strError(errno)));
    }
}

bool MemoryMappedFile::isSupported()
{
    return true;
}

}} // namespace qpid::sys
