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
#ifndef _LMonitor_
#define _LMonitor_

/* Native Linux Monitor - Based of Kernel patch 19/20 */

#include "Monitor.h"

namespace qpid {
namespace concurrent {

    class LMonitor : public virtual Monitor 
    {

    public:
	LMonitor();
	virtual ~LMonitor();
	virtual void wait();
	virtual void notify();
	virtual void notifyAll();
	virtual void acquire();
	virtual void release();
    };
}
}


#endif
