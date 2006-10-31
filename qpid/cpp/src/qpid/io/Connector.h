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
#ifndef _Connector_
#define _Connector_

#include "apr-1/apr_network_io.h"
#include "apr-1/apr_time.h"

#include "qpid/framing/InputHandler.h"
#include "qpid/framing/OutputHandler.h"
#include "qpid/framing/InitiationHandler.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/io/ShutdownHandler.h"
#include "qpid/io/TimeoutHandler.h"
#include "qpid/concurrent/Thread.h"
#include "qpid/concurrent/ThreadFactory.h"
#include "qpid/io/Connector.h"
#include "qpid/concurrent/Monitor.h"

namespace qpid {
namespace io {

    class Connector : public virtual qpid::framing::OutputHandler, 
	private virtual qpid::concurrent::Runnable
    {
        const bool debug;
	const int receive_buffer_size;
	const int send_buffer_size;

	bool closed;

        apr_time_t lastIn;
        apr_time_t lastOut;
        apr_interval_time_t timeout;
        u_int32_t idleIn;
        u_int32_t idleOut;

        TimeoutHandler* timeoutHandler;
        ShutdownHandler* shutdownHandler;
	qpid::framing::InputHandler* input;
	qpid::framing::InitiationHandler* initialiser;
	qpid::framing::OutputHandler* output;
	
	qpid::framing::Buffer inbuf;
	qpid::framing::Buffer outbuf;

        qpid::concurrent::Monitor* writeLock;
	qpid::concurrent::ThreadFactory* threadFactory;
	qpid::concurrent::Thread* receiver;

	apr_pool_t* pool;
	apr_socket_t* socket;

        void checkIdle(apr_status_t status);
	void writeBlock(qpid::framing::AMQDataBlock* data);
	void writeToSocket(char* data, size_t available);
        void setSocketTimeout();

	void run();

    public:
	Connector(bool debug = false, u_int32_t buffer_size = 1024);
	virtual ~Connector();
	virtual void connect(const std::string& host, int port);
	virtual void init(qpid::framing::ProtocolInitiation* header);
	virtual void close();
	virtual void setInputHandler(qpid::framing::InputHandler* handler);
	virtual void setTimeoutHandler(TimeoutHandler* handler);
	virtual void setShutdownHandler(ShutdownHandler* handler);
	virtual qpid::framing::OutputHandler* getOutputHandler();
	virtual void send(qpid::framing::AMQFrame* frame);
        virtual void setReadTimeout(u_int16_t timeout);
        virtual void setWriteTimeout(u_int16_t timeout);
    };

}
}


#endif
