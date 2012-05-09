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
 * \file EventHeader.cpp
 */

#include "EventHeader.h"

#include "RecordTail.h"

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

EventHeader::EventHeader() :
        RecordHeader(),
        m_xidSize(0),
        m_dataSize(0)
{}

EventHeader::EventHeader(const uint32_t magic,
                         const uint8_t version,
                         const uint64_t recordId,
                         const uint64_t xidSize,
                         const uint64_t dataSize,
                         const bool overwriteIndicator) :
        RecordHeader(magic, version, recordId, overwriteIndicator),
        m_xidSize(xidSize),
        m_dataSize(dataSize)
{}

EventHeader::EventHeader(const EventHeader& eh) :
        RecordHeader(eh),
        m_xidSize(eh.m_xidSize),
        m_dataSize(eh.m_dataSize)
{}

EventHeader::~EventHeader()
{}

void
EventHeader::copy(const EventHeader& e)
{
    RecordHeader::copy(e);
    m_xidSize = e.m_xidSize;
    m_dataSize = e.m_dataSize;
}

void
EventHeader::reset()
{
    RecordHeader::reset();
    m_xidSize = 0;
    m_dataSize = 0;
}

//static
uint64_t
EventHeader::getHeaderSize()
{
    return sizeof(EventHeader);
}

uint64_t
EventHeader::getBodySize() const
{
    return m_xidSize + m_dataSize;
}

uint64_t
EventHeader::getRecordSize() const
{
    return getHeaderSize() + (getBodySize() ?
           getBodySize() + RecordTail::getSize() :
           0);
}

}}} // namespace qpid::asyncStore::jrnl2
