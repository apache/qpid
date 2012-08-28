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
 * \file JournalRunState.cpp
 */

#include "qpid/asyncStore/jrnl2/JournalRunState.h"

#include "qpid/asyncStore/jrnl2/JournalError.h"

#include <sstream>

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

JournalRunState::JournalRunState() :
        State<journalState_t>()
{}

JournalRunState::JournalRunState(const JournalRunState& s) :
        State<journalState_t>(s)
{}

JournalRunState::JournalRunState(const journalState_t s) :
        State<journalState_t>(s)
{}

JournalRunState::~JournalRunState() {}

void
JournalRunState::initialize() {
    set(JS_INITIALIZING);
}

void
JournalRunState::recoverPhase1() {
    set(JS_RECOVERING_PHASE_1);
}

void
JournalRunState::recoverPhase2() {
    set(JS_RECOVERING_PHASE_2);
}

void
JournalRunState::run() {
    set(JS_RUNNING);
}

void
JournalRunState::stop() {
    set(JS_STOPPING);
}

void
JournalRunState::stopped() {
    set(JS_STOPPED);
}

const char*
JournalRunState::getAsStr() const {
    return s_toStr(m_state);
}

// static
const char*
JournalRunState::s_toStr(const journalState_t s) {
    switch (s) {
    case JS_NONE:
        return "JS_NONE";
    case JS_RECOVERING_PHASE_1:
        return "JS_RECOVERING_PHASE_1";
    case JS_RECOVERING_PHASE_2:
        return "JS_RECOVERING_PHASE_2";
    case JS_INITIALIZING:
        return "JS_INITIALIZING";
    case JS_RUNNING:
        return "JS_RUNNING";
    case JS_STOPPING:
        return "JS_STOPPING";
    case JS_STOPPED:
        return "JS_STOPPED";
   default:
        std::ostringstream oss;
        oss << "<unknown state (" << "s" << ")>";
        return oss.str().c_str();
    }
}

// private
void
JournalRunState::set(const journalState_t s) {
    // State transition logic: set stateError to true if an invalid transition is attempted
    bool stateTransitionError = false;
    switch(m_state) {
    case JS_NONE:
        if (s != JS_RECOVERING_PHASE_1 && s != JS_INITIALIZING) stateTransitionError = true;
        break;
    case JS_RECOVERING_PHASE_1:
        if (s != JS_RECOVERING_PHASE_2) stateTransitionError = true;
        break;
    case JS_RECOVERING_PHASE_2:
    case JS_INITIALIZING:
        if (s != JS_RUNNING) stateTransitionError = true;
        break;
    case JS_RUNNING:
        if (s != JS_STOPPING) stateTransitionError = true;
        break;
    case JS_STOPPING:
        if (s != JS_STOPPED) stateTransitionError = true;
        break;
    case JS_STOPPED: // Cannot move out of this state except with reset()
        stateTransitionError = true;
        break;
    }
    if (stateTransitionError) {
        THROW_STATE_EXCEPTION(JournalError::JERR_JRNLRUNSTATE, s_toStr(s), s_toStr(m_state),
                "JournalState", "set");
    }
    m_state = s;
}

}}} // namespace qpid::asyncStore::jrnl2
