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
#ifndef _Monitor_
#define _Monitor_

#include "amqp_types.h"

namespace qpid {
namespace concurrent {

class Monitor
{
  public:
    virtual ~Monitor(){}
    virtual void wait() = 0;
    virtual void wait(u_int64_t time) = 0;
    virtual void notify() = 0;
    virtual void notifyAll() = 0;
    virtual void acquire() = 0;
    virtual void release() = 0;
};

/**
 * Scoped locker for a monitor.
 */
class Locker
{
  public:
    Locker(Monitor&  lock_) : lock(lock_) { lock.acquire(); }
    ~Locker() { lock.release(); }

  private:
    Monitor& lock;

    // private and unimplemented to prevent copying
    Locker(const Locker&);
    void operator=(const Locker&);
};

}
}


#endif
