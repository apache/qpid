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
 * \file DequeueHeader.cpp
 */

#include "DequeueHeader.h"

#include "RecordTail.h"

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

DequeueHeader::DequeueHeader() :
        RecordHeader(),
        m_dequeuedRecordId(0),
        m_xidSize(0)
{}

DequeueHeader::DequeueHeader(const uint32_t magic,
                             const uint8_t version,
                             const uint64_t recordId,
                             const uint64_t dequeuedRecordId,
                             const uint64_t xidSize,
                             const bool overwriteIndicator,
                             const bool tplCommitOnTxnComplFlag) :
        RecordHeader(magic, version, recordId, overwriteIndicator),
        m_dequeuedRecordId(dequeuedRecordId),
        m_xidSize(xidSize)
{
    setTplCommitOnTxnComplFlag(tplCommitOnTxnComplFlag);
}

DequeueHeader::DequeueHeader(const DequeueHeader& dh) :
        RecordHeader(dh),
        m_dequeuedRecordId(dh.m_dequeuedRecordId),
        m_xidSize(dh.m_xidSize)
{}

DequeueHeader::~DequeueHeader()
{}

void
DequeueHeader::copy(const DequeueHeader& dh)
{
    RecordHeader::copy(dh);
    m_dequeuedRecordId = dh.m_dequeuedRecordId;
    m_xidSize = dh.m_xidSize;
}

void
DequeueHeader::reset()
{
    RecordHeader::reset();
    m_dequeuedRecordId = 0;
    m_xidSize = 0;
}

bool
DequeueHeader::getTplCommitOnTxnComplFlag() const
{
    return m_flags & DEQ_HDR_TPL_COMMIT_ON_TXN_COMPL_MASK;
}

void
DequeueHeader::setTplCommitOnTxnComplFlag(const bool commitOnTxnCompl)
{
    m_flags = commitOnTxnCompl ?
              m_flags | DEQ_HDR_TPL_COMMIT_ON_TXN_COMPL_MASK :
              m_flags & (~DEQ_HDR_TPL_COMMIT_ON_TXN_COMPL_MASK);
}

//static
uint64_t
DequeueHeader::getHeaderSize()
{
    return static_cast<uint64_t>(sizeof(DequeueHeader));
}

uint64_t
DequeueHeader::getBodySize() const
{
    return m_xidSize;
}

uint64_t
DequeueHeader::getRecordSize() const
{
    return getHeaderSize() + (getBodySize() > 0LL ?
                              getBodySize() + RecordTail::getSize() :
                              0);
}

}}} // namespace qpid::asyncStore::jrnl2
