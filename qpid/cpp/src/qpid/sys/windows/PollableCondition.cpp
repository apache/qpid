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

#include "qpid/sys/PollableCondition.h"
#include "qpid/sys/IOHandle.h"
#include "qpid/sys/windows/AsynchIoResult.h"
#include "qpid/sys/windows/IoHandlePrivate.h"

#include <boost/bind.hpp>
#include <windows.h>

namespace qpid {
namespace sys {

// PollableConditionPrivate will reuse the IocpPoller's ability to queue
// a completion to the IOCP and have it dispatched to the completer callback
// noted in the IOHandlePrivate when the request is queued. The
// AsynchCallbackRequest object is not really used - we already have the
// desired callback for the user of PollableCondition.
class PollableConditionPrivate : private IOHandle {
    friend class PollableCondition;

private:
    PollableConditionPrivate(const sys::PollableCondition::Callback& cb,
                             sys::PollableCondition& parent,
                             const boost::shared_ptr<sys::Poller>& poller);
    ~PollableConditionPrivate();

  void poke();
  void dispatch(windows::AsynchIoResult *result);

private:
    PollableCondition::Callback cb;
    PollableCondition& parent;
    boost::shared_ptr<sys::Poller> poller;
    LONG isSet;
    LONG isDispatching;
};

PollableConditionPrivate::PollableConditionPrivate(const sys::PollableCondition::Callback& cb,
                                                   sys::PollableCondition& parent,
                                                   const boost::shared_ptr<sys::Poller>& poller)
  : IOHandle(INVALID_SOCKET, boost::bind(&PollableConditionPrivate::dispatch, this, _1)),
    cb(cb), parent(parent), poller(poller), isSet(0), isDispatching(0)
{
}

PollableConditionPrivate::~PollableConditionPrivate()
{
}

void PollableConditionPrivate::poke()
{
    // monitorHandle will queue a completion for the IOCP; when it's handled, a
    // poller thread will call back to dispatch() below.
    PollerHandle ph(*this);
    poller->monitorHandle(ph, Poller::INPUT);
}

void PollableConditionPrivate::dispatch(windows::AsynchIoResult *result)
{
    delete result;       // Poller::monitorHandle() allocates this
    // If isDispatching is already set, just return. Else, enter.
    if (::InterlockedCompareExchange(&isDispatching, 1, 0) == 1)
        return;
    cb(parent);
    LONG oops = ::InterlockedDecrement(&isDispatching);   // Result must be 0
    assert(!oops);
    if (isSet)
        poke();
}

  /* PollableCondition */

PollableCondition::PollableCondition(const Callback& cb,
                                     const boost::shared_ptr<sys::Poller>& poller)
  : impl(new PollableConditionPrivate(cb, *this, poller))
{
}

PollableCondition::~PollableCondition()
{
    delete impl;
}

void PollableCondition::set() {
    // Add one to the set count and poke it to provoke a callback
    ::InterlockedIncrement(&impl->isSet);
    impl->poke();
}

void PollableCondition::clear() {
    ::InterlockedExchange(&impl->isSet, 0);
}

}} // namespace qpid::sys
