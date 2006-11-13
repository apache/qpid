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

namespace qpid {
namespace sys {

/** Epoll-based implementation of the event channel */
class EpollEventChannel : public EventChannel
{
  public:

    EpollEventChannel();
    ~EpollEventChannel();

    virtual void post(ReadEvent& event);
    virtual void post(WriteEvent& event);
    virtual void post(AcceptEvent& event);
    virtual void post(NotifyEvent& event);

    inline void post(Event& event);

    virtual Event* getEvent();

    virtual void dispose(void* buffer, size_t size);

  private:
    int epollFd;
    
};

}}
