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

    class Connector : public qpid::framing::OutputHandler, 
                      private qpid::sys::Runnable
    {
        const bool debug;
	const int receive_buffer_size;
	const int send_buffer_size;
	qpid::framing::ProtocolVersion version;

	volatile bool closed;

        int64_t lastIn;
        int64_t lastOut;
        int64_t timeout;
        u_int32_t idleIn;
        u_int32_t idleOut;

        qpid::sys::TimeoutHandler* timeoutHandler;
        qpid::sys::ShutdownHandler* shutdownHandler;
	qpid::framing::InputHandler* input;
	qpid::framing::InitiationHandler* initialiser;
	qpid::framing::OutputHandler* output;
	
	qpid::framing::Buffer inbuf;
	qpid::framing::Buffer outbuf;

        qpid::sys::Mutex writeLock;
	qpid::sys::Thread receiver;

	qpid::sys::Socket socket;
        
        void checkIdle(ssize_t status);
	void writeBlock(qpid::framing::AMQDataBlock* data);
	void writeToSocket(char* data, size_t available);
        void setSocketTimeout();

	void run();
	void handleClosed();
        bool markClosed();

    public:
	Connector(const qpid::framing::ProtocolVersion& pVersion, bool debug = false, u_int32_t buffer_size = 1024);
	virtual ~Connector();
	virtual void connect(const std::string& host, int port, bool tcpNoDelay=false);
	virtual void init(qpid::framing::ProtocolInitiation* header);
	virtual void close();
	virtual void setInputHandler(qpid::framing::InputHandler* handler);
	virtual void setTimeoutHandler(qpid::sys::TimeoutHandler* handler);
	virtual void setShutdownHandler(qpid::sys::ShutdownHandler* handler);
	virtual qpid::framing::OutputHandler* getOutputHandler();
	virtual void send(qpid::framing::AMQFrame* frame);
        virtual void setReadTimeout(u_int16_t timeout);
        virtual void setWriteTimeout(u_int16_t timeout);
    };

}
}


#endif
