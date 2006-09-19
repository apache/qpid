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
#ifndef _ThreadFactoryImpl_
#define _ThreadFactoryImpl_


#ifdef _USE_APR_IO_
#include "APRThreadFactory.h"
#else
#include "LThreadFactory.h"
#endif


namespace qpid {
namespace concurrent {


#ifdef _USE_APR_IO_
    class ThreadFactoryImpl : public virtual APRThreadFactory
    {
    public:
	ThreadFactoryImpl(): APRThreadFactory() {};
	virtual ~ThreadFactoryImpl() {};
    };
#else
    class ThreadFactoryImpl : public virtual LThreadFactory
    {
    public:
	ThreadFactoryImpl(): LThreadFactory() {};
	virtual ~ThreadFactoryImpl() {};
    };
#endif
}
}


#endif
