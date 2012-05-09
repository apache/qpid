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
 * \file RunState.cpp
 */

#include "RunState.h"

#include "qpid/Exception.h"

#include <sstream>

namespace qpid {
namespace asyncStore {

RunState::RunState() :
        qpid::asyncStore::jrnl2::State<RunState_t>()
{}

RunState::RunState(const RunState_t s) :
        qpid::asyncStore::jrnl2::State<RunState_t>(s)
{}

RunState::RunState(const RunState& s) :
        qpid::asyncStore::jrnl2::State<RunState_t>(s)
{}

RunState::~RunState()
{}

void
RunState::setInitializing()
{
    set(RS_INITIALIZING);
}

void
RunState::setRestoring()
{
    set(RS_RESTORING);
}

void
RunState::setRunning()
{
    set(RS_RUNNING);
}

void
RunState::setStopping()
{
    set(RS_STOPPING);
}

void
RunState::setStopped()
{
    set(RS_STOPPED);
}

const char*
RunState::getAsStr() const
{
    return s_toStr(m_state);
}

//static
const char*
RunState::s_toStr(const RunState_t s)
{
    switch (s) {
    case RS_NONE:
        return "WR_NONE";
    case RS_INITIALIZING:
        return "RS_INITIALIZING";
    case RS_RESTORING:
        return "RS_RESTORING";
    case RS_RUNNING:
        return "RS_RUNNING";
    case RS_STOPPING:
        return "RS_STOPPING";
    case RS_STOPPED:
        return "RS_STOPPED";
   default:
        std::ostringstream oss;
        oss << "<unknown state (" << "s" << ")>";
        return oss.str().c_str();
    }
}

void
RunState::set(const RunState_t s)
{
    // State transition logic: set stateError to true if an invalid transition is attempted
    bool stateTransitionError = false;
    switch (m_state) {
    case RS_NONE:
        if (s != RS_INITIALIZING && s != RS_RESTORING) {
            stateTransitionError = true;
        }
        break;
    case RS_INITIALIZING:
    case RS_RESTORING:
        if (s != RS_RUNNING) {
            stateTransitionError = true;
        }
        break;
    case RS_RUNNING:
        if (s != RS_STOPPING) {
            stateTransitionError = true;
        }
        break;
    case RS_STOPPING:
        if (s != RS_STOPPED) {
            stateTransitionError = true;
        }
        break;
    case RS_STOPPED: // Cannot move out of this state except with reset()
        stateTransitionError = true;
        break;
    }

    if (stateTransitionError) {
        std::ostringstream oss;
        oss << "RunState::set() is in state " << m_state << " and cannot be moved to state " << s << ".";
        throw qpid::Exception(oss.str());
    }
    m_state = s;
}

}} // namespace qpid::asyncStore
