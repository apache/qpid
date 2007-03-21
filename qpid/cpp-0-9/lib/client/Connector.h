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


#include <framing/InputHandler.h>
#include <framing/OutputHandler.h>
#include <framing/InitiationHandler.h>
#include <framing/ProtocolInitiation.h>
#include <ProtocolVersion.h>
#include <sys/ShutdownHandler.h>
#include <sys/TimeoutHandler.h>
#include <sys/Thread.h>
#include <sys/Monitor.h>
#include <sys/Socket.h>

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

    int64_t lastIn;
    int64_t lastOut;
    int64_t timeout;
    uint32_t idleIn;
    uint32_t idleOut;

    sys::TimeoutHandler* timeoutHandler;
    sys::ShutdownHandler* shutdownHandler;
    framing::InputHandler* input;
    framing::InitiationHandler* initialiser;
    framing::OutputHandler* output;
	
    framing::Buffer inbuf;
    framing::Buffer outbuf;

    sys::Mutex writeLock;
    sys::Thread receiver;

    sys::Socket socket;

    void checkIdle(ssize_t status);
    void writeBlock(framing::AMQDataBlock* data);
    void writeToSocket(char* data, size_t available);
    void setSocketTimeout();

    void run();
    void handleClosed();

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
    virtual framing::OutputHandler* getOutputHandler();
    virtual void send(framing::AMQFrame* frame);
    virtual void setReadTimeout(uint16_t timeout);
    virtual void setWriteTimeout(uint16_t timeout);
};

}}


#endif
