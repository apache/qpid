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
 * \file DataOpState.cpp
 */

#include "DataOpState.h"

#include "JournalError.h"

#include <sstream> // std::ostringstream

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

DataOpState::DataOpState() :
        State<opState_t>()
{}

DataOpState::DataOpState(const opState_t s) :
        State<opState_t>(s)
{}

DataOpState::DataOpState(const DataOpState& s) :
        State<opState_t>(s)
{}

DataOpState::~DataOpState()
{}

void
DataOpState::enqueue()
{
    set(OP_ENQUEUE);
}

void
DataOpState::dequeue()
{
    set(OP_DEQUEUE);
}

const char*
DataOpState::getAsStr() const
{
    return s_toStr(m_state);
}

// static
const char*
DataOpState::s_toStr(const opState_t s)
{
    switch (s) {
    case OP_NONE:
        return "OP_NONE";
    case OP_ENQUEUE:
        return "OP_ENQUEUE";
    case OP_DEQUEUE:
        return "OP_DEQUEUE";
   default:
        std::ostringstream oss;
        oss << "<unknown state (" << "s" << ")>";
        return oss.str().c_str();
    }
}

// protected
void
DataOpState::set(const opState_t s)
{
    // State transition logic: set stateError to true if an invalid transition is attempted
    bool stateTransitionError = false;
    switch(m_state) {
    case OP_NONE:
        if (s != OP_ENQUEUE) stateTransitionError = true;
        break;
    case OP_ENQUEUE:
        if (s != OP_DEQUEUE) stateTransitionError = true;
        break;
    case OP_DEQUEUE: // Cannot move out of this state except with reset()
        stateTransitionError = true;
        break;
    }
    if (stateTransitionError) {
        THROW_STATE_EXCEPTION(JournalError::JERR_MSGOPSTATE, s_toStr(s), s_toStr(m_state), "DataOpState", "set");
    }
    m_state = s;
}

}}} // namespace qpid::asyncStore::jrnl2
