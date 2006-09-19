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
#ifndef _LFSessionContext_
#define _LFSessionContext_

#include <queue>

#include "apr_network_io.h"
#include "apr_poll.h"
#include "apr_time.h"

#include "AMQFrame.h"
#include "APRMonitor.h"
#include "APRSocket.h"
#include "Buffer.h"
#include "IOSession.h"
#include "LFProcessor.h"
#include "SessionContext.h"
#include "SessionHandler.h"

namespace qpid {
namespace io {


    class LFSessionContext : public virtual SessionContext, public virtual IOSession
    {
        const bool debug;
        APRSocket socket;
        bool initiated;
        
        qpid::framing::Buffer in;
        qpid::framing::Buffer out;
        
        SessionHandler* handler;
        LFProcessor* const processor;

        apr_pollfd_t fd;

        std::queue<qpid::framing::AMQFrame*> framesToWrite;
        qpid::concurrent::APRMonitor writeLock;
        
        bool processing;
        bool closing;

        //these are just for debug, as a crude way of detecting concurrent access
        volatile unsigned int reading;
        volatile unsigned int writing;

        static qpid::concurrent::APRMonitor logLock;
        void log(const std::string& desc, qpid::framing::AMQFrame* const frame);

    public:
        LFSessionContext(apr_pool_t* pool, apr_socket_t* socket, 
                         LFProcessor* const processor, 
                         bool debug = false);
        ~LFSessionContext();
        virtual void send(qpid::framing::AMQFrame* frame);
        virtual void close();        
        virtual void read();
        virtual void write();
        void init(SessionHandler* handler);
        void startProcessing();
        void stopProcessing();
        void handleClose();        
        void shutdown();        
        inline apr_pollfd_t* const getFd(){ return &fd; }
        inline bool isClosed(){ return !socket.isOpen(); }
    };

}
}


#endif
