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
 * \file DataWrComplState.cpp
 */

#include "qpid/asyncStore/jrnl2/DataWrComplState.h"

#include "qpid/asyncStore/jrnl2/JournalError.h"

#include <sstream> // std::ostringstream

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

DataWrComplState::DataWrComplState() :
        State<wrComplState_t>()
{}

DataWrComplState::DataWrComplState(const wrComplState_t s) :
        State<wrComplState_t>(s)
{}

DataWrComplState::DataWrComplState(const DataWrComplState& s) :
        State<wrComplState_t>(s)
{}

DataWrComplState::~DataWrComplState() {}

void
DataWrComplState::complete() {
    set(WR_COMPLETE);
}

void
DataWrComplState::partComplete() {
    set(WR_PART);
}

const char*
DataWrComplState::getAsStr() const {
    return s_toStr(m_state);
}

// static
const char*
DataWrComplState::s_toStr(const wrComplState_t s) {
    switch (s) {
    case WR_NONE:
        return "WR_NONE";
    case WR_PART:
        return "WR_PART";
    case WR_COMPLETE:
        return "WR_COMPLETE";
   default:
        std::ostringstream oss;
        oss << "<unknown state (" << "s" << ")>";
        return oss.str().c_str();
    }
}

// protected
void
DataWrComplState::set(const wrComplState_t s) {
    // State transition logic: set stateError to true if an invalid transition is attempted
    bool stateTransitionError = false;
    switch(m_state) {
    case WR_NONE:
        if (s != WR_PART && s != WR_COMPLETE) stateTransitionError = true;
        break;
    case WR_PART:
        if (s != WR_COMPLETE) stateTransitionError = true;
        break;
    case WR_COMPLETE: // Cannot move out of this state except with reset()
        stateTransitionError = true;
        break;
    }
    if (stateTransitionError) {
        THROW_STATE_EXCEPTION(JournalError::JERR_MSGWRCMPLSTATE, s_toStr(s), s_toStr(m_state), "DataWrComplState",
                "set");
    }
    m_state = s;
}

}}} // namespace qpid::asyncStore::jrnl2
