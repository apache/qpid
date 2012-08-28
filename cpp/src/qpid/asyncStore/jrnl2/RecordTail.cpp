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
 * \file RecordTail.cpp
 */


#include "qpid/asyncStore/jrnl2/RecordTail.h"

#include "qpid/asyncStore/jrnl2/RecordHeader.h"

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

RecordTail::RecordTail() :
    m_xMagic(0xffffffff),
    m_checkSum(0),
    m_recordId(0)
{}

RecordTail::RecordTail(const uint32_t xMagic,
                       const uint32_t checkSum,
                       const uint64_t recordId) :
    m_xMagic(xMagic),
    m_checkSum(checkSum),
    m_recordId(recordId)
{}

RecordTail::RecordTail(const RecordHeader& rh) :
    m_xMagic(~rh.m_magic),
    m_checkSum(rh.getCheckSum()),
    m_recordId(rh.m_recordId)
{}

RecordTail::RecordTail(const RecordTail& rt) :
    m_xMagic(rt.m_xMagic),
    m_checkSum(rt.m_checkSum),
    m_recordId(rt.m_recordId)
{}

void
RecordTail::copy(const RecordTail& rt) {
    m_xMagic = rt.m_xMagic;
    m_checkSum = rt.m_checkSum;
    m_recordId = rt.m_recordId;
}

void
RecordTail::reset() {
    m_xMagic = 0xffffffff;
    m_checkSum = 0;
    m_recordId = 0;
}

//static
uint64_t
RecordTail::getSize() {
    return sizeof(RecordTail);
}

}}} // namespace qpid::asyncStore::jrnl2
