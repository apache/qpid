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
 * \file FileHeader.cpp
 */

#include "FileHeader.h"

#include "JournalError.h"

#include <cerrno>
#include <cstring>
#include <ctime>
#include <sstream>

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

FileHeader::FileHeader() :
        RecordHeader(),
        m_physicalFileId(0),
        m_logicalFileId(0),
        m_firstRecordOffset(0),
        m_timestampSeconds(0),
        m_timestampNanoSeconds(0),
        m_reserved(0)
{}

FileHeader::FileHeader(const uint32_t magic,
                       const uint8_t version,
                       const uint64_t recordId,
                       const bool overwriteIndicator,
                       const uint16_t physicalFileId,
                       const uint16_t logicalFileId,
                       const uint64_t firstRecordOffset,
                       const bool setTimestampFlag) :
        RecordHeader(magic, version, recordId, overwriteIndicator),
        m_physicalFileId(physicalFileId),
        m_logicalFileId(logicalFileId),
        m_firstRecordOffset(firstRecordOffset),
        m_timestampSeconds(0),
        m_timestampNanoSeconds(0),
        m_reserved(0)
{
    if (setTimestampFlag) setTimestamp();
}

FileHeader::FileHeader(const FileHeader& fh) :
        RecordHeader(fh),
        m_physicalFileId(fh.m_physicalFileId),
        m_logicalFileId(fh.m_logicalFileId),
        m_firstRecordOffset(fh.m_firstRecordOffset),
        m_timestampSeconds(fh.m_timestampSeconds),
        m_timestampNanoSeconds(fh.m_timestampNanoSeconds),
        m_reserved(fh.m_reserved)
{}

FileHeader::~FileHeader()
{}

void
FileHeader::copy(const FileHeader& fh)
{
    RecordHeader::copy(fh);
    m_physicalFileId = fh.m_physicalFileId;
    m_logicalFileId = fh.m_logicalFileId;
    m_firstRecordOffset = fh.m_firstRecordOffset;
    m_timestampSeconds = fh.m_timestampSeconds;
    m_timestampNanoSeconds = fh.m_timestampNanoSeconds;
    m_reserved = fh.m_reserved;
}

void
FileHeader::reset()
{
    RecordHeader::reset();
    m_physicalFileId = 0;
    m_logicalFileId = 0;
    m_firstRecordOffset = 0;
    m_timestampSeconds = 0;
    m_timestampNanoSeconds = 0;
    m_reserved = 0;
}

//static
uint64_t
FileHeader::getHeaderSize()
{
    return sizeof(FileHeader);
}

uint64_t
FileHeader::getBodySize() const
{
    return 0;
}

uint64_t
FileHeader::getRecordSize() const
{
    return getHeaderSize();
}

void
FileHeader::setTimestamp()
{
    /// \todo TODO: Standardize on method for getting time that does not require a context switch.
    timespec ts;
    if (::clock_gettime(CLOCK_REALTIME, &ts)) {
        std::ostringstream oss;
        oss << FORMAT_SYSERR(errno);
        throw JournalError(JournalError::JERR_RTCLOCK, oss.str(), "FileHeader", "setTimestamp");
    }
    setTimestamp(ts);
}

void
FileHeader::setTimestamp(const timespec& ts)
{
    m_timestampSeconds = static_cast<uint64_t>(ts.tv_sec);
    m_timestampNanoSeconds = static_cast<uint32_t>(ts.tv_nsec);
}

}}} // namespace qpid::asyncStore::jrnl2
