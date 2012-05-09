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
 * \file TransactionHeader.cpp
 */

#include "RecordTail.h"
#include "TransactionHeader.h"

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

TransactionHeader::TransactionHeader() :
        RecordHeader(),
        m_xidSize(0)
{}

TransactionHeader::TransactionHeader(const uint32_t magic,
                                     const uint8_t version,
                                     const uint64_t recordId,
                                     const uint64_t xidSize,
                                     const bool overwriteIndicator) :
        RecordHeader(magic, version, recordId, overwriteIndicator),
        m_xidSize(xidSize)
{}

TransactionHeader::TransactionHeader(const TransactionHeader& th) :
        RecordHeader(th),
        m_xidSize(th.m_xidSize)
{}

TransactionHeader::~TransactionHeader()
{}

void
TransactionHeader::copy(const TransactionHeader& th)
{
    RecordHeader::copy(th);
    m_xidSize = th.m_xidSize;
}

void
TransactionHeader::reset()
{
    RecordHeader::reset();
    m_xidSize = 0;
}

//static
uint64_t
TransactionHeader::getHeaderSize()
{
    return sizeof(TransactionHeader);
}

uint64_t
TransactionHeader::getBodySize() const
{
    return m_xidSize;
}

uint64_t
TransactionHeader::getRecordSize() const
{
    // By definition, TransactionRecords must always have an xid, hence a record
    // tail as well. No check on body size required in this case.
    return getHeaderSize() + getBodySize() + RecordTail::getSize();
}

}}} // namespace qpid::asyncStore::jrnl2
