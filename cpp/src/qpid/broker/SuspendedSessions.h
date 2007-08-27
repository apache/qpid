#ifndef QPID_BROKER_SUSPENDEDSESSIONS_H
#define QPID_BROKER_SUSPENDEDSESSIONS_H

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

#include "qpid/broker/SessionState.h"
#include "qpid/sys/Time.h"
#include "qpid/sys/Mutex.h"

#include <map>

namespace qpid {
namespace broker {

/** Collection of suspended sessions.
 * Thread safe.
 */
class SuspendedSessions {
    typedef std::multimap<sys::AbsTime,SessionState> Map;

    sys::Mutex lock;
    Map suspended;
        
  public:
    /** Suspend a session, start it's timeout counter.*/
    void suspend(SessionState& session);
        
    /** Resume a suspended session.
     *@throw Exception if timed out or non-existant.
     */
    SessionState resume(const framing::Uuid& id);
};
    


}} // namespace qpid::broker



#endif  /*!QPID_BROKER_SUSPENDEDSESSIONS_H*/
