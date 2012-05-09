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
 * \file RecordHeader.cpp
 */

#include "RecordHeader.h"

#include "Configuration.h"

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

RecordHeader::RecordHeader() :
        m_magic(0),
        m_version(0),
        m_bigEndianFlag(0),
        m_flags(0),
        m_recordId(0)
{}

RecordHeader::RecordHeader(const uint32_t magic,
                           const uint8_t version,
                           const uint64_t recordId,
                           const bool overwriteIndicator) :
        m_magic(magic),
        m_version(version),
        m_bigEndianFlag(Configuration::s_endianValue),
        m_flags(overwriteIndicator ? HDR_OVERWRITE_INDICATOR_MASK : 0),
        m_recordId(recordId)
{}

RecordHeader::RecordHeader(const RecordHeader& rh) :
        m_magic(rh.m_magic),
        m_version(rh.m_version),
        m_bigEndianFlag(rh.m_bigEndianFlag),
        m_flags(rh.m_flags),
        m_recordId(rh.m_recordId)
{}

RecordHeader::~RecordHeader()
{}

void
RecordHeader::copy(const RecordHeader& rh)
{
    m_magic = rh.m_magic;
    m_version = rh.m_version;
    m_bigEndianFlag = rh.m_bigEndianFlag;
    m_flags = rh.m_flags;
    m_recordId = rh.m_recordId;
}

void
RecordHeader::reset()
{
    m_magic = 0;
    m_version = 0;
    m_bigEndianFlag = 0;
    m_flags = 0;
    m_recordId = 0;
}

bool
RecordHeader::getOverwriteIndicator() const
{
    return m_flags & HDR_OVERWRITE_INDICATOR_MASK;
}

void
RecordHeader::setOverwriteIndicator(const bool owi)
{
    m_flags = owi ?
              m_flags | HDR_OVERWRITE_INDICATOR_MASK :
              m_flags & (~HDR_OVERWRITE_INDICATOR_MASK);
}

//static
uint64_t
RecordHeader::getHeaderSize()
{
    return static_cast<uint64_t>(sizeof(RecordHeader));
}

uint32_t
RecordHeader::getCheckSum(uint32_t initialValue) const
{
    uint32_t cs = initialValue;
    for (unsigned char* p = (unsigned char*)this;
                        p < (unsigned char*)this + getHeaderSize() + getBodySize();
                        p++) {
        cs ^= (uint32_t)(*p);
        bool carry = cs & uint32_t(0x80000000);
        cs <<= 1;
        if (carry) cs++;
    }
    return cs;
}

}}} // namespace qpid::asyncStore::jrnl2
