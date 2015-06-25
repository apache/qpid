#ifndef QPID_STORE_MSCLFS_LOG_H
#define QPID_STORE_MSCLFS_LOG_H

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

#include <string>
#include <windows.h>
#include <clfsw32.h>
#include <qpid/sys/IntegerTypes.h>

namespace qpid {
namespace store {
namespace ms_clfs {

/**
 * @class Log
 *
 * Represents a CLFS-housed log.
 */
class Log {

protected:
    HANDLE handle;
    ULONGLONG containerSize;
    std::string logPath;
    PVOID marshal;

    // Give subclasses a chance to initialize a new log. Called after a new
    // log is created, initial set of containers is added, and marshalling
    // area is allocated.
    virtual void initialize() {}

public:
    struct TuningParameters {
        size_t containerSize;
        unsigned short containers;
        unsigned short shrinkPct;
        uint32_t maxWriteBuffers;
    };

    Log() : handle(INVALID_HANDLE_VALUE), containerSize(0), marshal(0) {}
    virtual ~Log();

    void open(const std::string& path, const TuningParameters& params);

    virtual uint32_t marshallingBufferSize();

    CLFS_LSN write(void* entry, uint32_t length, CLFS_LSN* prev = 0);

    // Get the current base LSN of the log.
    CLFS_LSN getBase();

    // Move the log tail to the indicated LSN.
    void moveTail(const CLFS_LSN& oldest);
};

}}}  // namespace qpid::store::ms_clfs

#endif /* QPID_STORE_MSCLFS_LOG_H */
