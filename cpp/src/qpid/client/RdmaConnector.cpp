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
#include "qpid/sys/rdma/rdma_exception.h"
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
    typedef std::deque<framing::AMQFrame> Frames;

    const uint16_t maxFrameSize;
    sys::Mutex lock;
    Frames frames;
    size_t lastEof; // Position after last EOF in frames
    uint64_t currentSize;
    Bounds* bounds;

    framing::ProtocolVersion version;
    bool initiated;

    sys::Mutex dataConnectedLock;
    bool dataConnected;

    sys::ShutdownHandler* shutdownHandler;
    framing::InputHandler* input;
    framing::InitiationHandler* initialiser;
    framing::FrameHandler* output;

    Rdma::AsynchIO* aio;
    Rdma::Connector* acon;
    sys::Poller::shared_ptr poller;
    std::auto_ptr<qpid::sys::SecurityLayer> securityLayer;

    ~RdmaConnector();

    // Callbacks
    void connected(sys::Poller::shared_ptr, Rdma::Connection::intrusive_ptr, const Rdma::ConnectionParams&);
    void connectionError(sys::Poller::shared_ptr, Rdma::Connection::intrusive_ptr, Rdma::ErrorType);
    void disconnected();
    void rejected(sys::Poller::shared_ptr, Rdma::Connection::intrusive_ptr, const Rdma::ConnectionParams&);

    void readbuff(Rdma::AsynchIO&, Rdma::Buffer*);
    void writebuff(Rdma::AsynchIO&);
    void writeDataBlock(const framing::AMQDataBlock& data);
    void dataError(Rdma::AsynchIO&);
    void drained();
    void connectionStopped(Rdma::Connector* acon, Rdma::AsynchIO* aio);
    void dataStopped(Rdma::AsynchIO* aio);

    std::string identifier;

    void connect(const std::string& host, const std::string& port);
    void close();
    void handle(framing::AMQFrame& frame);
    void abort() {} // TODO: need to fix this for heartbeat timeouts to work

    void setInputHandler(framing::InputHandler* handler);
    void setShutdownHandler(sys::ShutdownHandler* handler);
    const std::string& getIdentifier() const;
    void activateSecurityLayer(std::auto_ptr<qpid::sys::SecurityLayer>);
    const qpid::sys::SecuritySettings* getSecuritySettings() { return 0; }

    size_t decode(const char* buffer, size_t size);
    size_t encode(char* buffer, size_t size);
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
      dataConnected(false),
      shutdownHandler(0),
      aio(0),
      acon(0),
      poller(p)
{
    QPID_LOG(debug, "RdmaConnector created for " << version);
}

namespace {
    void deleteAsynchIO(Rdma::AsynchIO& aio) {
        delete &aio;
    }

    void deleteConnector(Rdma::ConnectionManager& con) {
        delete &con;
    }
}

RdmaConnector::~RdmaConnector() {
    QPID_LOG(debug, "~RdmaConnector " << identifier);
    if (aio) {
        aio->stop(deleteAsynchIO);
    }
    if (acon) {
        acon->stop(deleteConnector);
    }
}

void RdmaConnector::connect(const std::string& host, const std::string& port){
    Mutex::ScopedLock l(dataConnectedLock);
    assert(!dataConnected);

    acon = new Rdma::Connector(
        Rdma::ConnectionParams(maxFrameSize, Rdma::DEFAULT_WR_ENTRIES),
        boost::bind(&RdmaConnector::connected, this, poller, _1, _2),
        boost::bind(&RdmaConnector::connectionError, this, poller, _1, _2),
        boost::bind(&RdmaConnector::disconnected, this),
        boost::bind(&RdmaConnector::rejected, this, poller, _1, _2));

    SocketAddress sa(host, port);
    acon->start(poller, sa);
}

// The following only gets run when connected
void RdmaConnector::connected(Poller::shared_ptr poller, Rdma::Connection::intrusive_ptr ci, const Rdma::ConnectionParams& cp) {
    try {
        Mutex::ScopedLock l(dataConnectedLock);
        assert(!dataConnected);
        Rdma::QueuePair::intrusive_ptr q = ci->getQueuePair();

        aio = new Rdma::AsynchIO(ci->getQueuePair(),
            cp.rdmaProtocolVersion,
            cp.maxRecvBufferSize, cp.initialXmitCredit , Rdma::DEFAULT_WR_ENTRIES,
            boost::bind(&RdmaConnector::readbuff, this, _1, _2),
            boost::bind(&RdmaConnector::writebuff, this, _1),
            0, // write buffers full
            boost::bind(&RdmaConnector::dataError, this, _1));

        identifier = str(format("[%1% %2%]") % ci->getLocalName() % ci->getPeerName());
        ProtocolInitiation init(version);
        writeDataBlock(init);

        aio->start(poller);

        dataConnected = true;

        return;
    } catch (const Rdma::Exception& e) {
        QPID_LOG(error, "Rdma: Cannot create new connection (Rdma exception): " << e.what());
    } catch (const std::exception& e) {
        QPID_LOG(error, "Rdma: Cannot create new connection (unknown exception): " << e.what());
    }
    dataConnected = false;
    connectionStopped(acon, aio);
}

void RdmaConnector::connectionError(sys::Poller::shared_ptr, Rdma::Connection::intrusive_ptr, Rdma::ErrorType) {
    QPID_LOG(debug, "Connection Error " << identifier);
    connectionStopped(acon, aio);
}

// Bizarrely we seem to get rejected events *after* we've already got a connected event for some peer disconnects
// so we need to check whether the data connection is started or not in here
void RdmaConnector::rejected(sys::Poller::shared_ptr, Rdma::Connection::intrusive_ptr, const Rdma::ConnectionParams& cp) {
    QPID_LOG(debug, "Connection Rejected " << identifier << ": " << cp.maxRecvBufferSize);
    if (dataConnected) {
        disconnected();
    } else {
        connectionStopped(acon, aio);
    }
}

void RdmaConnector::disconnected() {
    QPID_LOG(debug, "Connection disconnected " << identifier);
    {
    Mutex::ScopedLock l(dataConnectedLock);
    // If we're closed already then we'll get to drained() anyway
    if (!dataConnected) return;
    dataConnected = false;
    }
    // Make sure that all the disconnected actions take place on the data "thread"
    aio->requestCallback(boost::bind(&RdmaConnector::drained, this));
}

void RdmaConnector::dataError(Rdma::AsynchIO&) {
    QPID_LOG(debug, "Data Error " << identifier);
    {
    Mutex::ScopedLock l(dataConnectedLock);
    // If we're closed already then we'll get to drained() anyway
    if (!dataConnected) return;
    dataConnected = false;
    }
    drained();
}

void RdmaConnector::close() {
    QPID_LOG(debug, "RdmaConnector::close " << identifier);
    {
    Mutex::ScopedLock l(dataConnectedLock);
    if (!dataConnected) return;
    dataConnected = false;
    }
    aio->drainWriteQueue(boost::bind(&RdmaConnector::drained, this));
}

void RdmaConnector::drained() {
    QPID_LOG(debug, "RdmaConnector::drained " << identifier);
    assert(!dataConnected);
    assert(aio);
    Rdma::AsynchIO* a = aio;
    aio = 0;
    a->stop(boost::bind(&RdmaConnector::dataStopped, this, a));
}

void RdmaConnector::dataStopped(Rdma::AsynchIO* a) {
    QPID_LOG(debug, "RdmaConnector::dataStopped " << identifier);
    assert(!dataConnected);
    assert(acon);
    Rdma::Connector* c = acon;
    acon = 0;
    c->stop(boost::bind(&RdmaConnector::connectionStopped, this, c, a));
}

void RdmaConnector::connectionStopped(Rdma::Connector* c, Rdma::AsynchIO* a) {
    QPID_LOG(debug, "RdmaConnector::connectionStopped " << identifier);
    assert(!dataConnected);
    aio = 0;
    acon = 0;
    delete a;
    delete c;
    if (shutdownHandler) {
        ShutdownHandler* s = shutdownHandler;
        shutdownHandler = 0;
        s->shutdown();
    }
}

void RdmaConnector::setInputHandler(InputHandler* handler){
    input = handler;
}

void RdmaConnector::setShutdownHandler(ShutdownHandler* handler){
    shutdownHandler = handler;
}

const std::string& RdmaConnector::getIdentifier() const { 
    return identifier;
}

void RdmaConnector::handle(AMQFrame& frame) {
    // It is possible that we are called to write after we are already shutting down
    Mutex::ScopedLock l(dataConnectedLock);
    if (!dataConnected) return;

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

// Called in IO thread. (write idle routine)
// This is NOT only called in response to previously calling notifyPendingWrite
void RdmaConnector::writebuff(Rdma::AsynchIO&) {
    // It's possible to be disconnected and be writable
    Mutex::ScopedLock l(dataConnectedLock);
    if (!dataConnected) {
        return;
    }
    Codec* codec = securityLayer.get() ? (Codec*) securityLayer.get() : (Codec*) this;
    if (!codec->canEncode()) {
        return;
    }
    Rdma::Buffer* buffer = aio->getSendBuffer();
    if (buffer) {
        size_t encoded = codec->encode(buffer->bytes(), buffer->byteCount());
        buffer->dataCount(encoded);
        aio->queueWrite(buffer);
    }
}

bool RdmaConnector::canEncode()
{
    Mutex::ScopedLock l(lock);
    //have at least one full frameset or a whole buffers worth of data
    return aio->writable() && (lastEof || currentSize >= maxFrameSize);
}

size_t RdmaConnector::encode(char* buffer, size_t size)
{
    framing::Buffer out(buffer, size);
    size_t bytesWritten(0);
    {
        Mutex::ScopedLock l(lock);
        while (!frames.empty() && out.available() >= frames.front().encodedSize() ) {
            frames.front().encode(out);
            QPID_LOG(trace, "SENT [" << identifier << "]: " << frames.front());
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
    codec->decode(buff->bytes(), buff->dataCount());
}

size_t RdmaConnector::decode(const char* buffer, size_t size) 
{
    framing::Buffer in(const_cast<char*>(buffer), size);
    try {
        if (checkProtocolHeader(in, version)) {
            AMQFrame frame;
            while(frame.decode(in)){
                QPID_LOG(trace, "RECV [" << identifier << "]: " << frame);
                input->received(frame);
            }
        }
    } catch (const ProtocolVersionError& e) {
        QPID_LOG(info, "Closing connection due to " << e.what());
        close();
    }
    return size - in.available();
}

void RdmaConnector::writeDataBlock(const AMQDataBlock& data) {
    Rdma::Buffer* buff = aio->getSendBuffer();
    framing::Buffer out(buff->bytes(), buff->byteCount());
    data.encode(out);
    buff->dataCount(data.encodedSize());
    aio->queueWrite(buff);
}

void RdmaConnector::activateSecurityLayer(std::auto_ptr<qpid::sys::SecurityLayer> sl)
{
    securityLayer = sl;
    securityLayer->init(this);
}

}} // namespace qpid::client
