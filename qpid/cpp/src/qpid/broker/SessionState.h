#ifndef QPID_BROKER_SESSION_H
#define QPID_BROKER_SESSION_H

/*
 *
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
 *
 */

#include "qpid/framing/Uuid.h"

namespace qpid {
namespace broker {

/**
 * State of a session.
 */
class SessionState
{
  public:
    enum State { CLOSED, ACTIVE, SUSPENDED };

    /** Initially in CLOSED state */
    SessionState() : id(false), state(CLOSED), timeout(0) {}

    /** Make CLOSED session ACTIVE, assigns a new UUID.
     * #@param timeout in seconds
     */
    void open(u_int32_t timeout_) {
        state=ACTIVE;  id.generate(); timeout=timeout_;
    }

    /** Close a session. */
    void close() { state=CLOSED; id.clear(); timeout=0; }

    State getState() const { return state; }
    const framing::Uuid& getId() const { return id; }
    uint32_t getTimeout() const { return timeout; }

    bool isOpen() { return state == ACTIVE; }
    bool isClosed() { return state == CLOSED; }
    bool isSuspended() { return state == SUSPENDED; }
    
  private:
  friend class SuspendedSessions;
    framing::Uuid id;
    State state;
    uint32_t timeout;
};

}} // namespace qpid::broker



#endif  /*!QPID_BROKER_SESSION_H*/
