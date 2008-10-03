#ifndef _sys_Thread_h
#define _sys_Thread_h

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
#include <boost/shared_ptr.hpp>

#ifdef _WIN32
#  define QPID_TSS __declspec(thread)
#elif defined (gcc)
#  define QPID_TSS __thread
#else
#  define QPID_TSS
#endif

namespace qpid {
namespace sys {

class Runnable;
class ThreadPrivate;

class Thread
{
    boost::shared_ptr<ThreadPrivate> impl;

  public:
    Thread();
    explicit Thread(qpid::sys::Runnable*);
    explicit Thread(qpid::sys::Runnable&);
    
    void join();

    unsigned long id();
        
    static Thread current();

    /** ID of current thread for logging.
     * Workaround for broken Thread::current() in APR
     */
    static unsigned long logId() { return current().id(); }
};

}}
#endif  /*!_sys_Thread_h*/
