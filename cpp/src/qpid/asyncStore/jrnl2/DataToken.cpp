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
 * \file DataToken.cpp
 */

#include "DataToken.h"

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

DataToken::DataToken() :
        m_dataOpState(),
        m_recordId(s_recordIdCounter.next()),
        m_externalRecordIdFlag(false)
{}

DataToken::DataToken(const recordId_t rid) :
        m_dataOpState(),
        m_recordId(rid),
        m_externalRecordIdFlag(true)
{}

DataToken::~DataToken() {}

const DataOpState&
DataToken::getDataOpState() const {
    return m_dataOpState;
}

DataOpState&
DataToken::getDataOpState() {
    return m_dataOpState;
}

const DataWrComplState&
DataToken::getDataWrComplState() const {
    return m_dataWrComplState;
}

DataWrComplState&
DataToken::getDataWrComplState() {
    return m_dataWrComplState;
}

bool
DataToken::isTransient() const {
    return m_transientFlag;
}

bool
DataToken::isExternal() const {
    return m_externalFlag;
}

const std::string&
DataToken::getExternalLocation() const {
    return m_externalLocation;
}

recordId_t
DataToken::getRecordId() const {
    return m_recordId;
}

bool
DataToken::isRecordIdExternal() const {
    return m_externalRecordIdFlag;
}

recordId_t
DataToken::getDequeueRecordId() const {
    return m_dequeueRecordId;
}

void
DataToken::setRecordId(const recordId_t rid) {
    m_recordId = rid;
    m_externalRecordIdFlag = true;
}

void
DataToken::setDequeueRecordId(const recordId_t drid) {
    m_dequeueRecordId = drid;
}

void
DataToken::toStream(std::ostream& os) const {
    /// \todo TODO: Implementation required
    os << "status string";
}

// private static
RecordIdCounter_t DataToken::s_recordIdCounter;

}}} // namespace qpid::asyncStore::jrnl2
