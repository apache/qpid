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
#ifndef _APRThreadFactory_
#define _APRThreadFactory_

#include "apr_thread_proc.h"

#include "APRThread.h"
#include "Thread.h"
#include "ThreadFactory.h"
#include "Runnable.h"

namespace qpid {
namespace concurrent {

    class APRThreadFactory : public virtual ThreadFactory
    {
	apr_pool_t* pool;
    public:
	APRThreadFactory();
	virtual ~APRThreadFactory();
	virtual Thread* create(Runnable* runnable);
    };

}
}


#endif
