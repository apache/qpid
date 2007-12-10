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
#ifndef _Connector_
#define _Connector_


#include "qpid/framing/InputHandler.h"
#include "qpid/framing/OutputHandler.h"
#include "qpid/framing/InitiationHandler.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/sys/ShutdownHandler.h"
#include "qpid/sys/TimeoutHandler.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Socket.h"
#include "qpid/sys/Time.h"
#include "qpid/sys/AsynchIO.h"

#include <queue>

namespace qpid {
	
namespace client {

class Connector : public framing::OutputHandler, 
                  private sys::Runnable
{
    const bool debug;
    const int receive_buffer_size;
    const int send_buffer_size;
    framing::ProtocolVersion version;

    bool closed;
    sys::Mutex closedLock;

    sys::AbsTime lastIn;
    sys::AbsTime lastOut;
    sys::Duration timeout;
    sys::Duration idleIn;
    sys::Duration idleOut;

    sys::TimeoutHandler* timeoutHandler;
    sys::ShutdownHandler* shutdownHandler;
    framing::InputHandler* input;
    framing::InitiationHandler* initialiser;
    framing::OutputHandler* output;

    sys::Mutex writeLock;
    std::queue<framing::AMQFrame> writeFrameQueue;
    
    sys::Thread receiver;

    sys::Socket socket;

    sys::AsynchIO* aio;
    sys::Poller::shared_ptr poller;

    void checkIdle(ssize_t status);
    void setSocketTimeout();

    void run();
    void handleClosed();
    bool closeInternal();
    
    void readbuff(qpid::sys::AsynchIO&, qpid::sys::AsynchIO::BufferBase*);
    void writebuff(qpid::sys::AsynchIO&);
    void writeDataBlock(const framing::AMQDataBlock& data);
    void eof(qpid::sys::AsynchIO&);
    
  friend class Channel;

  public:
    Connector(framing::ProtocolVersion pVersion,
              bool debug = false, uint32_t buffer_size = 1024);
    virtual ~Connector();
    virtual void connect(const std::string& host, int port);
    virtual void init();
    virtual void close();
    virtual void setInputHandler(framing::InputHandler* handler);
    virtual void setTimeoutHandler(sys::TimeoutHandler* handler);
    virtual void setShutdownHandler(sys::ShutdownHandler* handler);
    virtual sys::ShutdownHandler* getShutdownHandler() { return shutdownHandler; }
    virtual framing::OutputHandler* getOutputHandler();
    virtual void send(framing::AMQFrame& frame);
    virtual void setReadTimeout(uint16_t timeout);
    virtual void setWriteTimeout(uint16_t timeout);
};

}}


#endif
