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

#ifndef _TCPConnector_
#define _TCPConnector_

#include "Connector.h"
#include "qpid/client/Bounds.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/sys/AsynchIO.h"
#include "qpid/sys/Codec.h"
#include "qpid/sys/IntegerTypes.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/SecurityLayer.h"
#include "qpid/sys/Socket.h"
#include "qpid/sys/Thread.h"

#include <boost/shared_ptr.hpp>
#include <boost/scoped_ptr.hpp>
#include <deque>
#include <string>

namespace qpid {

namespace framing {
    class InitiationHandler;
}

namespace client {

class TCPConnector : public Connector, public sys::Codec
{
    typedef std::deque<framing::AMQFrame> Frames;

    const uint16_t maxFrameSize;

    sys::Mutex lock;
    Frames frames; // Outgoing frame queue
    size_t lastEof; // Position after last EOF in frames
    uint64_t currentSize;
    Bounds* bounds;

    framing::ProtocolVersion version;
    bool initiated;
    bool closed;

    sys::ShutdownHandler* shutdownHandler;
    framing::InputHandler* input;

    boost::scoped_ptr<sys::Socket> socket;

    sys::AsynchConnector* connector;
    sys::AsynchIO* aio;
    std::string identifier;
    boost::shared_ptr<sys::Poller> poller;
    std::auto_ptr<qpid::sys::SecurityLayer> securityLayer;

    virtual void connected(const sys::Socket&);
    void writeDataBlock(const framing::AMQDataBlock& data);

    void close();
    void handle(framing::AMQFrame& frame);
    void abort();
    void connectAborted();

    void setInputHandler(framing::InputHandler* handler);
    void setShutdownHandler(sys::ShutdownHandler* handler);
    const std::string& getIdentifier() const;
    void activateSecurityLayer(std::auto_ptr<qpid::sys::SecurityLayer>);
    const qpid::sys::SecuritySettings* getSecuritySettings() { return 0; }

    size_t decode(const char* buffer, size_t size);
    size_t encode(char* buffer, size_t size);
    bool canEncode();

protected:
    virtual ~TCPConnector();
    void connect(const std::string& host, const std::string& port);
    void start(sys::AsynchIO* aio_);
    void initAmqp();
    virtual void connectFailed(const std::string& msg);
    void readbuff(qpid::sys::AsynchIO&, qpid::sys::AsynchIOBufferBase*);
    void writebuff(qpid::sys::AsynchIO&);
    void eof(qpid::sys::AsynchIO&);
    void disconnected(qpid::sys::AsynchIO&);
    void socketClosed(qpid::sys::AsynchIO&, const qpid::sys::Socket&);

public:
    TCPConnector(boost::shared_ptr<sys::Poller>,
              framing::ProtocolVersion pVersion,
              const ConnectionSettings&,
              ConnectionImpl*);
};

}}   // namespace qpid::client

#endif  /* _TCPConnector_ */
