#ifndef _apr_Thread_h
#define _apr_Thread_h

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

#include <apr-1/apr_thread_proc.h>
#include <qpid/sys/Runnable.h>

namespace qpid {
namespace sys {

class Thread
{

  public:
    Thread();
    explicit Thread(qpid::sys::Runnable*);
    void join();
    static Thread current();

  private:
    Thread(apr_thread_t*);
    
    apr_thread_t* thread;
};

}}

#endif  /*!_apr_Thread_h*/
