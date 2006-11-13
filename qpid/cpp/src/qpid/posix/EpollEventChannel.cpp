/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include <qpid/sys/EventChannel.h>
#include <sys/epoll.h>
#include "EpollEventChannel.h"

namespace qpid {
namespace sys {

EventChannel::shared_ptr EventChannel::create()
{
    return EventChannel::shared_ptr(new EpollEventChannel());
}

EpollEventChannel::EpollEventChannel()
{
    // TODO aconway 2006-11-13: How to choose size parameter?
    static const size_t estimatedFdsForEpoll = 1000;
    epollFd = epoll_create(estimatedFdsForEpoll);
}

void 
EpollEventChannel::post(ReadEvent& /*event*/)
{
}

void
EpollEventChannel::post(WriteEvent& /*event*/)
{
}

void
EpollEventChannel::post(AcceptEvent& /*event*/)
{
}

void
EpollEventChannel::post(NotifyEvent& /*event*/)
{
}

inline void
EpollEventChannel::post(Event& /*event*/)
{
}

Event*
EpollEventChannel::getEvent()
{
    return 0;
}

void
EpollEventChannel::dispose(void* /*buffer*/, size_t)
{
}

}}
