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
#include "qpid/client/Connector.h"

#include "qpid/client/Bounds.h"
#include "qpid/client/ConnectionImpl.h"
#include "qpid/client/ConnectionSettings.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Time.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/InitiationHandler.h"
#include "qpid/sys/rdma/RdmaIO.h"
#include "qpid/sys/Dispatcher.h"
#include "qpid/sys/Poller.h"
#include "qpid/sys/SecurityLayer.h"
#include "qpid/Msg.h"

#include <iostream>
#include <boost/bind.hpp>
#include <boost/format.hpp>
#include <boost/lexical_cast.hpp>

// This stuff needs to abstracted out of here to a platform specific file
#include <netdb.h>

namespace qpid {
namespace client {

using namespace qpid::sys;
using namespace qpid::framing;
using boost::format;
using boost::str;

class RdmaConnector : public Connector, public sys::Codec
{
    struct Buff;

    typedef Rdma::Buffer BufferBase;
    typedef std::deque<framing::AMQFrame> Frames;

    const uint16_t maxFrameSize;
    sys::Mutex lock;
    Frames frames;
    size_t lastEof; // Position after last EOF in frames
    uint64_t currentSize;
    Bounds* bounds;

    framing::ProtocolVersion version;
    bool initiated;

    sys::Mutex pollingLock;
    bool polling;

    sys::ShutdownHandler* shutdownHandler;
    framing::InputHandler* input;
    framing::InitiationHandler* initialiser;
    framing::OutputHandler* output;

    Rdma::AsynchIO* aio;
    sys::Poller::shared_ptr poller;
    std::auto_ptr<qpid::sys::SecurityLayer> securityLayer;

    ~RdmaConnector();

    void handleClosed();
    bool closeInternal();

    // Callbacks
    void connected(sys::Poller::shared_ptr, Rdma::Connection::intrusive_ptr&, const Rdma::ConnectionParams&);
    void connectionError(sys::Poller::shared_ptr, Rdma::Connection::intrusive_ptr&, Rdma::ErrorType);
    void disconnected(sys::Poller::shared_ptr, Rdma::Connection::intrusive_ptr&);
    void rejected(sys::Poller::shared_ptr, Rdma::Connection::intrusive_ptr&, const Rdma::ConnectionParams&);

    void readbuff(Rdma::AsynchIO&, Rdma::Buffer*);
    void writebuff(Rdma::AsynchIO&);
    void writeDataBlock(const framing::AMQDataBlock& data);
    void eof(Rdma::AsynchIO&);

    std::string identifier;

    ConnectionImpl* impl;

    void connect(const std::string& host, int port);
    void close();
    void send(framing::AMQFrame& frame);
    void abort() {} // TODO: need to fix this for heartbeat timeouts to work

    void setInputHandler(framing::InputHandler* handler);
    void setShutdownHandler(sys::ShutdownHandler* handler);
    sys::ShutdownHandler* getShutdownHandler() const;
    framing::OutputHandler* getOutputHandler();
    const std::string& getIdentifier() const;
    void activateSecurityLayer(std::auto_ptr<qpid::sys::SecurityLayer>);
    unsigned int getSSF() { return 0; }

    size_t decode(const char* buffer, size_t size);
    size_t encode(const char* buffer, size_t size);
    bool canEncode();

public:
    RdmaConnector(Poller::shared_ptr,
              framing::ProtocolVersion pVersion,
              const ConnectionSettings&, 
              ConnectionImpl*);
};

// Static constructor which registers connector here
namespace {
    Connector* create(Poller::shared_ptr p, framing::ProtocolVersion v, const ConnectionSettings& s, ConnectionImpl* c) {
        return new RdmaConnector(p, v, s, c);
    }

    struct StaticInit {
        StaticInit() {
            Connector::registerFactory("rdma", &create);
            Connector::registerFactory("ib", &create);
        };
    } init;
}


RdmaConnector::RdmaConnector(Poller::shared_ptr p,
                     ProtocolVersion ver,
                     const ConnectionSettings& settings,
                     ConnectionImpl* cimpl)
    : maxFrameSize(settings.maxFrameSize),
      lastEof(0),
      currentSize(0),
      bounds(cimpl),
      version(ver), 
      initiated(false),
      polling(false),
      shutdownHandler(0),
      aio(0),
      poller(p),
      impl(cimpl)
{
    QPID_LOG(debug, "RdmaConnector created for " << version);
}

RdmaConnector::~RdmaConnector() {
    close();
    if (aio) aio->deferDelete();
}

void RdmaConnector::connect(const std::string& host, int port){
    Mutex::ScopedLock l(pollingLock);
    assert(!polling);

    Rdma::Connector* c = new Rdma::Connector(
        Rdma::ConnectionParams(maxFrameSize, Rdma::DEFAULT_WR_ENTRIES),
        boost::bind(&RdmaConnector::connected, this, poller, _1, _2),
        boost::bind(&RdmaConnector::connectionError, this, poller, _1, _2),
        boost::bind(&RdmaConnector::disconnected, this, poller, _1),
        boost::bind(&RdmaConnector::rejected, this, poller, _1, _2));

    polling = true;

    SocketAddress sa(host, boost::lexical_cast<std::string>(port));
    c->start(poller, sa);
}

// The following only gets run when connected
void RdmaConnector::connected(Poller::shared_ptr poller, Rdma::Connection::intrusive_ptr& ci, const Rdma::ConnectionParams& cp) {
    Rdma::QueuePair::intrusive_ptr q = ci->getQueuePair();

    aio = new Rdma::AsynchIO(ci->getQueuePair(),
        cp.maxRecvBufferSize, cp.initialXmitCredit , Rdma::DEFAULT_WR_ENTRIES,
        boost::bind(&RdmaConnector::readbuff, this, _1, _2),
        boost::bind(&RdmaConnector::writebuff, this, _1),
        0, // write buffers full
        boost::bind(&RdmaConnector::eof, this, _1)); // data error - just close connection

    identifier = str(format("[%1% %2%]") % ci->getLocalName() % ci->getPeerName());
    ProtocolInitiation init(version);
    writeDataBlock(init);

    aio->start(poller);
}

void RdmaConnector::connectionError(sys::Poller::shared_ptr, Rdma::Connection::intrusive_ptr&, Rdma::ErrorType) {
    QPID_LOG(trace, "Connection Error " << identifier);
    eof(*aio);
}

void RdmaConnector::disconnected(sys::Poller::shared_ptr, Rdma::Connection::intrusive_ptr&) {
    eof(*aio);
}

void RdmaConnector::rejected(sys::Poller::shared_ptr, Rdma::Connection::intrusive_ptr&, const Rdma::ConnectionParams& cp) {
    QPID_LOG(trace, "Connection Rejected " << identifier << ": " << cp.maxRecvBufferSize);
    eof(*aio);
}

bool RdmaConnector::closeInternal() {
    Mutex::ScopedLock l(pollingLock);
    bool ret = polling;
    polling = false;
    if (ret) {
        if (aio) aio->queueWriteClose();
    }
    return ret;
}

void RdmaConnector::close() {
    closeInternal();
}

void RdmaConnector::setInputHandler(InputHandler* handler){
    input = handler;
}

void RdmaConnector::setShutdownHandler(ShutdownHandler* handler){
    shutdownHandler = handler;
}

OutputHandler* RdmaConnector::getOutputHandler(){ 
    return this; 
}

sys::ShutdownHandler* RdmaConnector::getShutdownHandler() const {
    return shutdownHandler;
}

const std::string& RdmaConnector::getIdentifier() const { 
    return identifier;
}

void RdmaConnector::send(AMQFrame& frame) {
    bool notifyWrite = false;
    {
        Mutex::ScopedLock l(lock);
	frames.push_back(frame);
	//only ask to write if this is the end of a frameset or if we
	//already have a buffers worth of data
	currentSize += frame.encodedSize();
	if (frame.getEof()) {
	    lastEof = frames.size();
	    notifyWrite = true;
	} else {
	    notifyWrite = (currentSize >= maxFrameSize);
	}
    }
    if (notifyWrite) aio->notifyPendingWrite();
}

void RdmaConnector::handleClosed() {
    if (closeInternal() && shutdownHandler)
        shutdownHandler->shutdown();
}

// Called in IO thread. (write idle routine)
// This is NOT only called in response to previously calling notifyPendingWrite
void RdmaConnector::writebuff(Rdma::AsynchIO&) {
    Codec* codec = securityLayer.get() ? (Codec*) securityLayer.get() : (Codec*) this;
    if (codec->canEncode()) {
        std::auto_ptr<BufferBase> buffer = std::auto_ptr<BufferBase>(aio->getBuffer());
        size_t encoded = codec->encode(buffer->bytes, buffer->byteCount);

        buffer->dataStart = 0;
        buffer->dataCount = encoded;
        aio->queueWrite(buffer.release());
    }
}

bool RdmaConnector::canEncode()
{
    Mutex::ScopedLock l(lock);
    //have at least one full frameset or a whole buffers worth of data
    return aio->writable() && aio->bufferAvailable() && (lastEof || currentSize >= maxFrameSize);
}

size_t RdmaConnector::encode(const char* buffer, size_t size)
{
    framing::Buffer out(const_cast<char*>(buffer), size);
    size_t bytesWritten(0);
    {
        Mutex::ScopedLock l(lock);
        while (!frames.empty() && out.available() >= frames.front().encodedSize() ) {
            frames.front().encode(out);
            QPID_LOG(trace, "SENT " << identifier << ": " << frames.front());
            frames.pop_front();
            if (lastEof) --lastEof;
        }
        bytesWritten = size - out.available();
        currentSize -= bytesWritten;
    }
    if (bounds) bounds->reduce(bytesWritten);
    return bytesWritten;
}

void RdmaConnector::readbuff(Rdma::AsynchIO&, Rdma::Buffer* buff) {
    Codec* codec = securityLayer.get() ? (Codec*) securityLayer.get() : (Codec*) this;
    codec->decode(buff->bytes+buff->dataStart, buff->dataCount);
}

size_t RdmaConnector::decode(const char* buffer, size_t size) 
{
    framing::Buffer in(const_cast<char*>(buffer), size);
    if (!initiated) {
        framing::ProtocolInitiation protocolInit;
        if (protocolInit.decode(in)) {
            //TODO: check the version is correct
            QPID_LOG(debug, "RECV " << identifier << " INIT(" << protocolInit << ")");
        }
        initiated = true;
    }
    AMQFrame frame;
    while(frame.decode(in)){
        QPID_LOG(trace, "RECV " << identifier << ": " << frame);
        input->received(frame);
    }
    return size - in.available();
}

void RdmaConnector::writeDataBlock(const AMQDataBlock& data) {
    Rdma::Buffer* buff = aio->getBuffer();
    framing::Buffer out(buff->bytes, buff->byteCount);
    data.encode(out);
    buff->dataCount = data.encodedSize();
    aio->queueWrite(buff);
}

void RdmaConnector::eof(Rdma::AsynchIO&) {
    handleClosed();
}

void RdmaConnector::activateSecurityLayer(std::auto_ptr<qpid::sys::SecurityLayer> sl)
{
    securityLayer = sl;
    securityLayer->init(this);
}

}} // namespace qpid::client
