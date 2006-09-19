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
#ifndef _BlockingAPRSessionContext_
#define _BlockingAPRSessionContext_

#include <queue>
#include <vector>

#include "apr_network_io.h"
#include "apr_time.h"

#include "AMQFrame.h"
#include "APRMonitor.h"
#include "Buffer.h"
#include "Runnable.h"
#include "SessionContext.h"
#include "SessionHandler.h"
#include "SessionHandlerFactory.h"
#include "ShutdownHandler.h"
#include "Thread.h"
#include "ThreadFactory.h"

namespace qpid {
namespace io {

    class BlockingAPRAcceptor;

    class BlockingAPRSessionContext : public virtual SessionContext
    {
        class Reader : public virtual qpid::concurrent::Runnable{
            BlockingAPRSessionContext* parent;
        public:
            inline Reader(BlockingAPRSessionContext* p) : parent(p){}
            inline virtual void run(){ parent->read(); }
            inline virtual ~Reader(){}
        };

        class Writer : public virtual qpid::concurrent::Runnable{
            BlockingAPRSessionContext* parent;
        public:
            inline Writer(BlockingAPRSessionContext* p) : parent(p){}
            inline virtual void run(){ parent->write(); }
            inline virtual ~Writer(){}
        };        

        apr_socket_t* socket;
        const bool debug;
        SessionHandler* handler;
        BlockingAPRAcceptor* acceptor;
        std::queue<qpid::framing::AMQFrame*> outframes;
        qpid::framing::Buffer inbuf;
        qpid::framing::Buffer outbuf;
        qpid::concurrent::APRMonitor outlock;
        Reader* reader;
        Writer* writer;
        qpid::concurrent::Thread* rThread;
        qpid::concurrent::Thread* wThread;

        volatile bool closed;

        void read();
        void write();    
    public:
        BlockingAPRSessionContext(apr_socket_t* socket, 
                                  qpid::concurrent::ThreadFactory* factory, 
                                  BlockingAPRAcceptor* acceptor, 
                                  bool debug = false);
        ~BlockingAPRSessionContext();
        virtual void send(qpid::framing::AMQFrame* frame);
        virtual void close();
        void shutdown();
        void init(SessionHandler* handler);
    };

}
}


#endif
