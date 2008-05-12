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

#include "SessionManager.h"
#include "SessionState.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/log/Statement.h"
#include "qpid/log/Helpers.h"
#include "qpid/memory.h"

#include <boost/bind.hpp>
#include <boost/range.hpp>

#include <algorithm>
#include <functional>
#include <ostream>

namespace qpid {
namespace broker {

using boost::intrusive_ptr;
using namespace sys;
using namespace framing;

SessionManager::SessionManager(uint32_t a) : ack(a) {}

SessionManager::~SessionManager() {}

// FIXME aconway 2008-02-01: pass handler*, allow open  unattached.
std::auto_ptr<SessionState>  SessionManager::open(
    SessionHandler& h, uint32_t timeout_, std::string _name)
{
    Mutex::ScopedLock l(lock);
    std::auto_ptr<SessionState> session(
        new SessionState(this, &h, timeout_, ack, _name));
    active.insert(session->getId());
    for_each(observers.begin(), observers.end(),
             boost::bind(&Observer::opened, _1,boost::ref(*session)));
    return session;
}

void  SessionManager::suspend(std::auto_ptr<SessionState> session) {
    Mutex::ScopedLock l(lock);
    active.erase(session->getId());
    session->suspend();
    session->expiry = AbsTime(now(),session->getTimeout()*TIME_SEC);
    if (session->mgmtObject.get() != 0)
        session->mgmtObject->set_expireTime ((uint64_t) Duration (session->expiry));
    suspended.push_back(session.release()); // In expiry order
    eraseExpired();
}

std::auto_ptr<SessionState>  SessionManager::resume(const Uuid& id)
{
    Mutex::ScopedLock l(lock);
    eraseExpired();
    if (active.find(id) != active.end()) 
        throw SessionBusyException(
            QPID_MSG("Session already active: " << id));
    Suspended::iterator i = std::find_if(
        suspended.begin(), suspended.end(),
        boost::bind(std::equal_to<Uuid>(), id, boost::bind(&SessionState::getId, _1))
    );
    if (i == suspended.end())
        throw InvalidArgumentException(
            QPID_MSG("No suspended session with id=" << id));
    active.insert(id);
    std::auto_ptr<SessionState> state(suspended.release(i).release());
    return state;
}

void SessionManager::erase(const framing::Uuid& id)
{
    Mutex::ScopedLock l(lock);
    active.erase(id);
}

void SessionManager::eraseExpired() {
    // Called with lock held.
    if (!suspended.empty()) {
        Suspended::iterator keep = std::lower_bound(
            suspended.begin(), suspended.end(), now(),
            boost::bind(std::less<AbsTime>(), boost::bind(&SessionState::expiry, _1), _2));
        if (suspended.begin() != keep) {
            QPID_LOG(debug, "Expiring sessions: " << log::formatList(suspended.begin(), keep));
            suspended.erase(suspended.begin(), keep);
        }
    }
}

void SessionManager::add(const intrusive_ptr<Observer>& o) {
    observers.push_back(o);
}

}} // namespace qpid::broker
