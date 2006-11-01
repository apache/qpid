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
#ifndef _Thread_
#define _Thread_

#include "apr-1/apr_thread_proc.h"
#include "qpid/concurrent/Thread.h"
#include "qpid/concurrent/Runnable.h"
#include "qpid/concurrent/Thread.h"

namespace qpid {
namespace concurrent {

    class Thread
    {
	const Runnable* runnable;
	apr_pool_t* pool;
	apr_thread_t* runner;

    public:
	Thread(apr_pool_t* pool, Runnable* runnable);
	virtual ~Thread();
	virtual void start();
	virtual void join();
	virtual void interrupt();
        static unsigned int currentThread();
    };

}
}


#endif
