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
#ifndef _LFAcceptor_
#define _LFAcceptor_

#include <vector>
#include "apr_network_io.h"
#include "apr_poll.h"
#include "apr_time.h"

#include "Acceptor.h"
#include "APRMonitor.h"
#include "APRThreadFactory.h"
#include "APRThreadPool.h"
#include "LFProcessor.h"
#include "LFSessionContext.h"
#include "Runnable.h"
#include "SessionContext.h"
#include "SessionHandlerFactory.h"
#include "Thread.h"

namespace qpid {
namespace io {

    class LFAcceptor : public virtual Acceptor
    {
        class APRPool{
        public:
            apr_pool_t* pool;
            APRPool();
            ~APRPool();
        };

        APRPool aprPool;
        LFProcessor processor;

        const int max_connections_per_processor;
        const bool debug;
        const int connectionBacklog;

        volatile bool running;

    public:
	LFAcceptor(bool debug = false, 
                   int connectionBacklog = 10, 
                   int worker_threads = 5, 
                   int max_connections_per_processor = 500);
        virtual void bind(int port, SessionHandlerFactory* factory);
	virtual ~LFAcceptor();
    };

}
}


#endif
