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
    /** Create a new, active session. */
    SessionState(uint32_t timeoutSeconds)
        : id(true), active(true), timeout(timeoutSeconds) {}

    const framing::Uuid& getId() const { return id; }
    uint32_t getTimeout() const { return timeout; }

    /** Call SuspendedSessions::resume to re-activate a suspended session. */ 
    bool isActive() const { return active; }

  private:
  friend class SuspendedSessions;
    framing::Uuid id;
    bool active;
    uint32_t timeout;
};

}} // namespace qpid::broker



#endif  /*!QPID_BROKER_SESSION_H*/
