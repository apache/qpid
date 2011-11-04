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
#include "Ticker.h"
#include <boost/bind.hpp>

namespace qpid {
namespace cluster {

Ticker::Tickable::~Tickable() {}

Ticker::Ticker(sys::Duration tick, sys::Timer& timer_,
               boost::shared_ptr<sys::Poller> poller)
    : sys::TimerTask(tick, "Cluster ticker"),  timer(timer_),
      condition(boost::bind(&Ticker::dispatch, this, _1), poller)
{
    timer.add(this);
}

void Ticker::add(boost::intrusive_ptr<Tickable> t) {
    sys::Mutex::ScopedLock l(lock);
    tickables.push_back(t);
}

void Ticker::remove(boost::intrusive_ptr<Tickable> t) {
    sys::Mutex::ScopedLock l(lock);
    Tickables::iterator i = std::find(tickables.begin(), tickables.end(), t);
    if (i != tickables.end()) tickables.erase(i);
}

// Called by timer thread, sets condition
void Ticker::fire() {
    condition.set();
    setupNextFire();
    timer.add(this);
}

// Called only in condition IO thread.
void Ticker::dispatch(sys::PollableCondition& cond) {
    assert(&cond == &condition);
    {
        sys::Mutex::ScopedLock l(lock);
        working = tickables;
    }
    // This is safe outside the lock see comment in Ticker.h
    for(Tickables::iterator i = working.begin(); i!= working.end(); ++i)
        (*i)->tick();
    condition.clear();          // Ready for next tick.
}

}} // namespace qpid::cluster
