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
 * \file RunState.h
 */

#ifndef qpid_asyncStore_RunState_h_
#define qpid_asyncStore_RunState_h_

#include "qpid/asyncStore/jrnl2/State.h"

namespace qpid {
namespace asyncStore {

/**
 *                  RS_NONE
 *                    / \
 * setInitializing() /   \ setRestoring()
 *                  /     \
 *                 V       V
 *     RS_INITIALIZING   RS_RESTORING
 *                 \       /
 *     setRunning() \     / setRunning()
 *                   \   /
 *                    V V
 *                 RS_RUNNING
 *                     |
 *                     | setStopping()
 *                     V
 *                RS_STOPPING
 *                     |
 *                     | setStopped()
 *                     V
 *                 RS_STOPPED
 */
typedef enum {
    RS_NONE = 0,
    RS_INITIALIZING,
    RS_RESTORING,
    RS_RUNNING,
    RS_STOPPING,
    RS_STOPPED
} RunState_t;

class RunState: public qpid::asyncStore::jrnl2::State<RunState_t>
{
public:
    RunState();
    RunState(const RunState_t s);
    RunState(const RunState& s);
    virtual ~RunState();
    void setInitializing();
    void setRestoring();
    void setRunning();
    void setStopping();
    void setStopped();
    virtual const char* getAsStr() const;
    static const char* s_toStr(const RunState_t s);
private:
    virtual void set(const RunState_t s);

};

}} // namespace qpid::asyncStore

#endif // qpid_asyncStore_RunState_h_
