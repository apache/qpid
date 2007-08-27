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
#include "SuspendedSessions.h"
#include <boost/bind.hpp>

namespace qpid {
namespace broker {

using namespace framing;
using namespace sys;
using namespace boost;
typedef Mutex::ScopedLock Lock;

void SuspendedSessions::suspend(SessionState& s) {
    Lock l(lock);
    assert(s.state == SessionState::ACTIVE);
    if (s.timeout == 0) 
        s.state = SessionState::CLOSED;
    else {
        AbsTime expires(now(), Duration(s.timeout*TIME_SEC));
        suspended.insert(std::make_pair(expires, s));
        s.state = SessionState::SUSPENDED;
    }
}

SessionState SuspendedSessions::resume(const Uuid& id)
{
    Lock l(lock);
    Map::iterator notExpired = suspended.lower_bound(now());
    suspended.erase(suspended.begin(), notExpired);
    Map::iterator i = suspended.begin();
    while (i != suspended.end() && i->second.getId() != id)
        ++i;
    if (i == suspended.end())
        throw Exception(QPID_MSG("Session timed out or invalid ID: " << id));
    return i->second;
}

}} // namespace qpid::broker



