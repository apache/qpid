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
#ifndef _BlockingAPRAcceptor_
#define _BlockingAPRAcceptor_

#include <vector>
#include "apr_network_io.h"
#include "apr_poll.h"
#include "apr_time.h"

#include "Acceptor.h"
#include "APRMonitor.h"
#include "BlockingAPRSessionContext.h"
#include "Runnable.h"
#include "SessionContext.h"
#include "SessionHandlerFactory.h"
#include "Thread.h"
#include "ThreadFactory.h"
#include "ThreadPool.h"

namespace qpid {
namespace io {

    class BlockingAPRAcceptor : public virtual Acceptor
    {
        typedef std::vector<BlockingAPRSessionContext*>::iterator iterator;

        const bool debug;
        apr_pool_t* apr_pool;
        qpid::concurrent::ThreadFactory* threadFactory;
        std::vector<BlockingAPRSessionContext*> sessions;
	apr_socket_t* socket;
        const int connectionBacklog;
        volatile bool running;

    public:
	BlockingAPRAcceptor(bool debug = false, int connectionBacklog = 10);
        virtual void bind(int port, SessionHandlerFactory* factory);
	virtual ~BlockingAPRAcceptor();
        void closed(BlockingAPRSessionContext* session);
    };

}
}


#endif
