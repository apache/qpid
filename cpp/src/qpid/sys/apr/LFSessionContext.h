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
#ifndef _LFSessionContext_
#define _LFSessionContext_

#include <queue>

#include <apr_network_io.h>
#include <apr_poll.h>
#include <apr_time.h>

#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/Buffer.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/ConnectionOutputHandler.h"
#include "qpid/sys/ConnectionInputHandler.h"

#include "APRSocket.h"
#include "LFProcessor.h"

namespace qpid {
namespace sys {


class LFSessionContext : public virtual qpid::sys::ConnectionOutputHandler
{
    const bool debug;
    APRSocket socket;
    bool initiated;
        
    qpid::framing::Buffer in;
    qpid::framing::Buffer out;
        
    qpid::sys::ConnectionInputHandler* handler;
    LFProcessor* const processor;

    apr_pollfd_t fd;

    std::queue<qpid::framing::AMQFrame> framesToWrite;
    qpid::sys::Mutex writeLock;
        
    bool processing;
    bool closing;

  public:
    LFSessionContext(apr_pool_t* pool, apr_socket_t* socket, 
                     LFProcessor* const processor, 
                     bool debug = false);
    virtual ~LFSessionContext();
    virtual void send(framing::AMQFrame& frame);
    virtual void close();        
    void read();
    void write();
    void init(qpid::sys::ConnectionInputHandler* handler);
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
