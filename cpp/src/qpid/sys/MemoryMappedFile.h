#ifndef QPID_SYS_MEMORYMAPPEDFILE_H
#define QPID_SYS_MEMORYMAPPEDFILE_H

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
#include "qpid/CommonImportExport.h"
#include <string>

namespace qpid {
namespace sys {

class MemoryMappedFilePrivate;
/**
 * Abstraction of memory mapping functionality
 */
class MemoryMappedFile {
  public:
    QPID_COMMON_EXTERN MemoryMappedFile();
    QPID_COMMON_EXTERN ~MemoryMappedFile();
    /**
     * Opens a file that can be mapped by region into memory
     */
    QPID_COMMON_EXTERN void open(const std::string& name, const std::string& directory);
    /**
     * Closes and removes the file that can be mapped by region into memory
     */
    QPID_COMMON_EXTERN void close();
    /**
     * Returns the page size
     */
    QPID_COMMON_EXTERN size_t getPageSize();
    /**
     * Load a portion of the file into memory
     */
    QPID_COMMON_EXTERN char* map(size_t offset, size_t size);
    /**
     * Evict a portion of the file from memory
     */
    QPID_COMMON_EXTERN void unmap(char* region, size_t size);
    /**
     * Flush any changes to a previously mapped region of the file
     * back to disk
     */
    QPID_COMMON_EXTERN void flush(char* region, size_t size);
    /**
     * Expand the capacity of the file
     */
    QPID_COMMON_EXTERN void expand(size_t offset);
    /**
     * Returns true if memory mapping is supported, false otherwise
     */
    QPID_COMMON_EXTERN static bool isSupported();
  private:
    MemoryMappedFilePrivate* state;

    MemoryMappedFile(const MemoryMappedFile&);
    MemoryMappedFile& operator=(const MemoryMappedFile&);
};
}} // namespace qpid::sys

#endif  /*!QPID_SYS_MEMORYMAPPEDFILE_H*/
