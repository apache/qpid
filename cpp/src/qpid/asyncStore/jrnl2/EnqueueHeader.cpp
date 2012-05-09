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
 * \file EnqueueHeader.cpp
 */

#include "EnqueueHeader.h"

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

EnqueueHeader::EnqueueHeader() :
        EventHeader()
{}

EnqueueHeader::EnqueueHeader(const uint32_t magic,
                             const uint8_t version,
                             const uint64_t recordId,
                             const uint64_t xidSize,
                             const uint64_t dataSize,
                             const bool overwriteIndicator,
                             const bool transient,
                             const bool external) :
        EventHeader(magic, version, recordId, xidSize, dataSize, overwriteIndicator)
{
    setTransientFlag(transient);
    setExternalFlag(external);
}

EnqueueHeader::EnqueueHeader(const EnqueueHeader& eh) :
        EventHeader(eh)
{}

EnqueueHeader::~EnqueueHeader()
{}

bool
EnqueueHeader::getTransientFlag() const
{
    return m_flags & ENQ_HDR_TRANSIENT_MASK;
}

void
EnqueueHeader::setTransientFlag(const bool transient)
{
    m_flags = transient ?
              m_flags | ENQ_HDR_TRANSIENT_MASK :
              m_flags & (~ENQ_HDR_TRANSIENT_MASK);
}

bool
EnqueueHeader::getExternalFlag() const
{
    return m_flags & ENQ_HDR_EXTERNAL_MASK;
}

void
EnqueueHeader::setExternalFlag(const bool external)
{
    m_flags = external ?
              m_flags | ENQ_HDR_EXTERNAL_MASK :
              m_flags & (~ENQ_HDR_EXTERNAL_MASK);
}

//static
uint64_t
EnqueueHeader::getHeaderSize()
{
    return sizeof(EnqueueHeader);
}

}}} // namespace qpid::asyncStore::jrnl2
