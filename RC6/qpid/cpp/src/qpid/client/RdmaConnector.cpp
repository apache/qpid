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
#include "Connector.h"

#include "Bounds.h"
#include "ConnectionImpl.h"
#include "ConnectionSettings.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Time.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/sys/rdma/RdmaIO.h"
#include "qpid/sys/Dispatcher.h"
#include "qpid/sys/Poller.h"
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

class RdmaConnector : public Connector, private sys::Runnable
{
    struct Buff;

    /** Batch up frames for writing to aio. */
    class Writer : public framing::FrameHandler {
        typedef Rdma::Buffer BufferBase;
        typedef std::deque<framing::AMQFrame> Frames;

        const uint16_t maxFrameSize;
        sys::Mutex lock;
        Rdma::AsynchIO* aio;
        BufferBase* buffer;
        Frames frames;
        size_t lastEof; // Position after last EOF in frames
        framing::Buffer encode;
        size_t framesEncoded;
        std::string identifier;
        Bounds* bounds;        
        
        void writeOne();
        void newBuffer();

      public:
        
        Writer(uint16_t maxFrameSize, Bounds*);
        ~Writer();
        void init(std::string id, Rdma::AsynchIO*);
        void handle(framing::AMQFrame&);
        void write(Rdma::AsynchIO&);
    };
    
    const uint16_t maxFrameSize;
    framing::ProtocolVersion version;
    bool initiated;

    sys::Mutex pollingLock;    
    bool polling;
    bool joined;

    sys::ShutdownHandler* shutdownHandler;
    framing::InputHandler* input;
    framing::InitiationHandler* initialiser;
    framing::OutputHandler* output;

    Writer writer;
    
    sys::Thread receiver;

    Rdma::AsynchIO* aio;
    sys::Poller::shared_ptr poller;

    ~RdmaConnector();

    void run();
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

    void setInputHandler(framing::InputHandler* handler);
    void setShutdownHandler(sys::ShutdownHandler* handler);
    sys::ShutdownHandler* getShutdownHandler() const;
    framing::OutputHandler* getOutputHandler();
    const std::string& getIdentifier() const;

public:
    RdmaConnector(framing::ProtocolVersion pVersion,
              const ConnectionSettings&, 
              ConnectionImpl*);
};

// Static constructor which registers connector here
namespace {
    Connector* create(framing::ProtocolVersion v, const ConnectionSettings& s, ConnectionImpl* c) {
        return new RdmaConnector(v, s, c);
    }

    struct StaticInit {
        StaticInit() {
            Connector::registerFactory("rdma", &create);
            Connector::registerFactory("ib", &create);
        };
    } init;
}


RdmaConnector::RdmaConnector(ProtocolVersion ver,
                     const ConnectionSettings& settings,
                     ConnectionImpl* cimpl)
    : maxFrameSize(settings.maxFrameSize),
      version(ver), 
      initiated(false),
      polling(false),
      joined(true),
      shutdownHandler(0),
      writer(maxFrameSize, cimpl),
      aio(0),
      impl(cimpl)
{
    QPID_LOG(debug, "RdmaConnector created for " << version);
}

RdmaConnector::~RdmaConnector() {
    close();
}

void RdmaConnector::connect(const std::string& host, int port){
    Mutex::ScopedLock l(pollingLock);
    assert(!polling);
    assert(joined);
    poller = Poller::shared_ptr(new Poller);

    // This stuff needs to abstracted out of here to a platform specific file
    ::addrinfo *res;
    ::addrinfo hints;
    hints.ai_flags = 0;
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_protocol = 0;
    int n = ::getaddrinfo(host.c_str(), boost::lexical_cast<std::string>(port).c_str(), &hints, &res);
    if (n<0) {
        throw Exception(QPID_MSG("Cannot resolve " << host << ": " << ::gai_strerror(n)));
    }

    Rdma::Connector* c = new Rdma::Connector(
        *res->ai_addr,
        Rdma::ConnectionParams(maxFrameSize, Rdma::DEFAULT_WR_ENTRIES),
        boost::bind(&RdmaConnector::connected, this, poller, _1, _2),
        boost::bind(&RdmaConnector::connectionError, this, poller, _1, _2),
        boost::bind(&RdmaConnector::disconnected, this, poller, _1),
        boost::bind(&RdmaConnector::rejected, this, poller, _1, _2));
    c->start(poller);

    polling = true;
    joined = false;
    receiver = Thread(this);
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
    aio->start(poller);

    identifier = str(format("[%1% %2%]") % ci->getLocalName() % ci->getPeerName());
    writer.init(identifier, aio);
    ProtocolInitiation init(version);
    writeDataBlock(init);
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
    bool ret;
    {
    Mutex::ScopedLock l(pollingLock);
    ret = polling;
    if (polling) {
        polling = false;
        poller->shutdown();
    }
    if (joined || receiver.id() == Thread::current().id()) {
        return ret;
    }
    joined = true;
    }

    receiver.join();
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
    writer.handle(frame);
}

void RdmaConnector::handleClosed() {
    if (closeInternal() && shutdownHandler)
        shutdownHandler->shutdown();
}

RdmaConnector::Writer::Writer(uint16_t s, Bounds* b) :
    maxFrameSize(s),
    aio(0),
    buffer(0), 
    lastEof(0), 
    bounds(b)
{
}

RdmaConnector::Writer::~Writer() {
    if (aio)
        aio->returnBuffer(buffer);
}

void RdmaConnector::Writer::init(std::string id, Rdma::AsynchIO* a) {
    Mutex::ScopedLock l(lock);
    identifier = id;
    aio = a;
    assert(aio->bufferAvailable());
    newBuffer();
}
void RdmaConnector::Writer::handle(framing::AMQFrame& frame) { 
    Mutex::ScopedLock l(lock);
    frames.push_back(frame);
    // Don't bother to send anything unless we're at the end of a frameset (assembly in 0-10 terminology)
    if (frame.getEof()) {
        lastEof = frames.size();
        QPID_LOG(debug, "Requesting write: lastEof=" << lastEof);
        aio->notifyPendingWrite();
    }
    QPID_LOG(trace, "SENT " << identifier << ": " << frame);
}

void RdmaConnector::Writer::writeOne() {
    assert(buffer);
    QPID_LOG(trace, "Write buffer " << encode.getPosition()
             << " bytes " << framesEncoded << " frames ");    
    framesEncoded = 0;

    buffer->dataStart = 0;
    buffer->dataCount = encode.getPosition();
    aio->queueWrite(buffer);
    newBuffer();
}

void RdmaConnector::Writer::newBuffer() {
    buffer = aio->getBuffer();
    encode = framing::Buffer(buffer->bytes, buffer->byteCount);
    framesEncoded = 0;
}

// Called in IO thread. (write idle routine)
// This is NOT only called in response to previously calling notifyPendingWrite
void RdmaConnector::Writer::write(Rdma::AsynchIO&) {
    Mutex::ScopedLock l(lock);
    assert(buffer);
    // If nothing to do return immediately
    if (lastEof==0)
        return;
    size_t bytesWritten = 0;
    while (aio->writable() && aio->bufferAvailable() && !frames.empty()) {
        const AMQFrame* frame = &frames.front();        
        uint32_t size = frame->encodedSize();
        while (size <= encode.available()) {
            frame->encode(encode);
            frames.pop_front();
            ++framesEncoded;
            bytesWritten += size;
            if (frames.empty())
                break;
            frame = &frames.front();        
            size = frame->encodedSize();
        }
        lastEof -= framesEncoded;
        writeOne();
    }
    if (bounds) bounds->reduce(bytesWritten);
}

void RdmaConnector::readbuff(Rdma::AsynchIO&, Rdma::Buffer* buff) {
    framing::Buffer in(buff->bytes+buff->dataStart, buff->dataCount);

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
}

void RdmaConnector::writebuff(Rdma::AsynchIO& aio_) {
    writer.write(aio_);
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

// TODO: astitcher 20070908 This version of the code can never time out, so the idle processing
// will never be called
void RdmaConnector::run(){
    // Keep the connection impl in memory until run() completes.
    //GRS: currently the ConnectionImpls destructor is where the Io thread is joined
    //boost::shared_ptr<ConnectionImpl> protect = impl->shared_from_this();
    //assert(protect);
    try {
        Dispatcher d(poller);
	
        //aio->start(poller);
        d.run();
        //aio->queueForDeletion();
    } catch (const std::exception& e) {
        {
        // We're no longer polling
        Mutex::ScopedLock l(pollingLock);
        polling = false;
        }
        QPID_LOG(error, e.what());
        handleClosed();
    }
}


}} // namespace qpid::client
