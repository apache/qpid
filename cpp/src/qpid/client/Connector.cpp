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
#include "qpid/sys/AsynchIO.h"
#include "qpid/sys/Dispatcher.h"
#include "qpid/sys/Poller.h"
#include "qpid/Msg.h"

#include <iostream>
#include <map>
#include <boost/bind.hpp>
#include <boost/format.hpp>
#include <boost/weak_ptr.hpp>

namespace qpid {
namespace client {

using namespace qpid::sys;
using namespace qpid::framing;
using boost::format;
using boost::str;

// Stuff for the registry of protocol connectors (maybe should be moved to its own file)
namespace {
    typedef std::map<std::string, Connector::Factory*> ProtocolRegistry;
    
    ProtocolRegistry& theProtocolRegistry() {
        static ProtocolRegistry protocolRegistry;
        
        return protocolRegistry;
    } 
}

Connector* Connector::create(const std::string& proto, framing::ProtocolVersion v, const ConnectionSettings& s, ConnectionImpl* c)
{
    ProtocolRegistry::const_iterator i = theProtocolRegistry().find(proto);
    if (i==theProtocolRegistry().end()) {
        throw Exception(QPID_MSG("Unknown protocol: " << proto));
    }
    return (i->second)(v, s, c);
}

void Connector::registerFactory(const std::string& proto, Factory* connectorFactory)
{
    ProtocolRegistry::const_iterator i = theProtocolRegistry().find(proto);
    if (i!=theProtocolRegistry().end()) {
        QPID_LOG(error, "Tried to register protocol: " << proto << " more than once");
    }
    theProtocolRegistry()[proto] = connectorFactory;
}

class TCPConnector : public Connector, private sys::Runnable
{
    struct Buff;

    /** Batch up frames for writing to aio. */
    class Writer : public framing::FrameHandler {
        typedef sys::AsynchIOBufferBase BufferBase;
        typedef std::vector<framing::AMQFrame> Frames;

        const uint16_t maxFrameSize;
        sys::Mutex lock;
        sys::AsynchIO* aio;
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
        void init(std::string id, sys::AsynchIO*);
        void handle(framing::AMQFrame&);
        void write(sys::AsynchIO&);
    };
    
    const uint16_t maxFrameSize;
    framing::ProtocolVersion version;
    bool initiated;

    sys::Mutex closedLock;    
    bool closed;
    bool joined;

    sys::ShutdownHandler* shutdownHandler;
    framing::InputHandler* input;
    framing::InitiationHandler* initialiser;
    framing::OutputHandler* output;

    Writer writer;
    
    sys::Thread receiver;

    sys::Socket socket;

    sys::AsynchIO* aio;
    boost::shared_ptr<sys::Poller> poller;

    ~TCPConnector();

    void run();
    void handleClosed();
    bool closeInternal();
    
    bool readbuff(qpid::sys::AsynchIO&, qpid::sys::AsynchIOBufferBase*);
    void writebuff(qpid::sys::AsynchIO&);
    void writeDataBlock(const framing::AMQDataBlock& data);
    void eof(qpid::sys::AsynchIO&);

    std::string identifier;

    boost::weak_ptr<ConnectionImpl> impl;
    
    void connect(const std::string& host, int port);
    void init();
    void close();
    void send(framing::AMQFrame& frame);

    void setInputHandler(framing::InputHandler* handler);
    void setShutdownHandler(sys::ShutdownHandler* handler);
    sys::ShutdownHandler* getShutdownHandler() const;
    framing::OutputHandler* getOutputHandler();
    const std::string& getIdentifier() const;

public:
    TCPConnector(framing::ProtocolVersion pVersion,
              const ConnectionSettings&, 
              ConnectionImpl*);
};

// Static constructor which registers connector here
namespace {
    Connector* create(framing::ProtocolVersion v, const ConnectionSettings& s, ConnectionImpl* c) {
        return new TCPConnector(v, s, c);
    }

    struct StaticInit {
        StaticInit() {
            Connector::registerFactory("tcp", &create);
        };
    } init;
}

TCPConnector::TCPConnector(ProtocolVersion ver,
                     const ConnectionSettings& settings,
                     ConnectionImpl* cimpl)
    : maxFrameSize(settings.maxFrameSize),
      version(ver), 
      initiated(false),
      closed(true),
      joined(true),
      shutdownHandler(0),
      writer(maxFrameSize, cimpl),
      aio(0),
      impl(cimpl->shared_from_this())
{
    QPID_LOG(debug, "TCPConnector created for " << version.toString());
    settings.configureSocket(socket);
}

TCPConnector::~TCPConnector() {
    close();
}

void TCPConnector::connect(const std::string& host, int port){
    Mutex::ScopedLock l(closedLock);
    assert(closed);
    try {
        socket.connect(host, port);
    } catch (const std::exception& /*e*/) {
        socket.close();
        throw;
    }

    identifier = str(format("[%1% %2%]") % socket.getLocalPort() % socket.getPeerAddress());
    closed = false;
    poller = Poller::shared_ptr(new Poller);
    aio = AsynchIO::create(socket,
                       boost::bind(&TCPConnector::readbuff, this, _1, _2),
                       boost::bind(&TCPConnector::eof, this, _1),
                       boost::bind(&TCPConnector::eof, this, _1),
                       0, // closed
                       0, // nobuffs
                       boost::bind(&TCPConnector::writebuff, this, _1));
    writer.init(identifier, aio);
}

void TCPConnector::init(){
    Mutex::ScopedLock l(closedLock);
    assert(joined);
    ProtocolInitiation init(version);
    writeDataBlock(init);
    joined = false;
    receiver = Thread(this);
}

bool TCPConnector::closeInternal() {
    Mutex::ScopedLock l(closedLock);
    bool ret = !closed;
    if (!closed) {
        closed = true;
        poller->shutdown();
    }
    if (!joined && receiver.id() != Thread::current().id()) {
        joined = true;
        Mutex::ScopedUnlock u(closedLock);
        receiver.join();
    }
    return ret;
}
        
void TCPConnector::close() {
    closeInternal();
}

void TCPConnector::setInputHandler(InputHandler* handler){
    input = handler;
}

void TCPConnector::setShutdownHandler(ShutdownHandler* handler){
    shutdownHandler = handler;
}

OutputHandler* TCPConnector::getOutputHandler() {
    return this; 
}

sys::ShutdownHandler* TCPConnector::getShutdownHandler() const {
    return shutdownHandler;
}

const std::string& TCPConnector::getIdentifier() const { 
    return identifier;
}

void TCPConnector::send(AMQFrame& frame) {
    writer.handle(frame);
}

void TCPConnector::handleClosed() {
    if (closeInternal() && shutdownHandler)
        shutdownHandler->shutdown();
}

struct TCPConnector::Buff : public AsynchIO::BufferBase {
    Buff(size_t size) : AsynchIO::BufferBase(new char[size], size) {}    
    ~Buff() { delete [] bytes;}
};

TCPConnector::Writer::Writer(uint16_t s, Bounds* b) : maxFrameSize(s), aio(0), buffer(0), lastEof(0), bounds(b)
{
}

TCPConnector::Writer::~Writer() { delete buffer; }

void TCPConnector::Writer::init(std::string id, sys::AsynchIO* a) {
    Mutex::ScopedLock l(lock);
    identifier = id;
    aio = a;
    newBuffer();
}
void TCPConnector::Writer::handle(framing::AMQFrame& frame) { 
    Mutex::ScopedLock l(lock);
    frames.push_back(frame);
    //only try to write if this is the end of a frameset or if we
    //already have a buffers worth of data
    if (frame.getEof() || (bounds && bounds->getCurrentSize() >= maxFrameSize)) {
        lastEof = frames.size();
        aio->notifyPendingWrite();
    }
    QPID_LOG(trace, "SENT " << identifier << ": " << frame);
}

void TCPConnector::Writer::writeOne() {
    assert(buffer);
    framesEncoded = 0;

    buffer->dataStart = 0;
    buffer->dataCount = encode.getPosition();
    aio->queueWrite(buffer);
    newBuffer();
}

void TCPConnector::Writer::newBuffer() {
    buffer = aio->getQueuedBuffer();
    if (!buffer) buffer = new Buff(maxFrameSize);
    encode = framing::Buffer(buffer->bytes, buffer->byteCount);
    framesEncoded = 0;
}

// Called in IO thread.
void TCPConnector::Writer::write(sys::AsynchIO&) {
    Mutex::ScopedLock l(lock);
    assert(buffer);
    size_t bytesWritten(0);
    for (size_t i = 0; i < lastEof; ++i) {
        AMQFrame& frame = frames[i];
        uint32_t size = frame.encodedSize();
        if (size > encode.available()) writeOne();
        assert(size <= encode.available());
        frame.encode(encode);
        ++framesEncoded;
        bytesWritten += size;
    }
    frames.erase(frames.begin(), frames.begin()+lastEof);
    lastEof = 0;
    if (bounds) bounds->reduce(bytesWritten);
    if (encode.getPosition() > 0) writeOne();
}

bool TCPConnector::readbuff(AsynchIO& aio, AsynchIO::BufferBase* buff) {
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
    // TODO: unreading needs to go away, and when we can cope
    // with multiple sub-buffers in the general buffer scheme, it will
    if (in.available() != 0) {
        // Adjust buffer for used bytes and then "unread them"
        buff->dataStart += buff->dataCount-in.available();
        buff->dataCount = in.available();
        aio.unread(buff);
    } else {
        // Give whole buffer back to aio subsystem
        aio.queueReadBuffer(buff);
    }
    return true;
}

void TCPConnector::writebuff(AsynchIO& aio_) {
    writer.write(aio_);
}

void TCPConnector::writeDataBlock(const AMQDataBlock& data) {
    AsynchIO::BufferBase* buff = new Buff(maxFrameSize);
    framing::Buffer out(buff->bytes, buff->byteCount);
    data.encode(out);
    buff->dataCount = data.encodedSize();
    aio->queueWrite(buff);
}

void TCPConnector::eof(AsynchIO&) {
    handleClosed();
}

// TODO: astitcher 20070908 This version of the code can never time out, so the idle processing
// will never be called
void TCPConnector::run(){
    // Keep the connection impl in memory until run() completes.
    boost::shared_ptr<ConnectionImpl> protect = impl.lock();
    assert(protect);
    try {
        Dispatcher d(poller);
	
        for (int i = 0; i < 32; i++) {
            aio->queueReadBuffer(new Buff(maxFrameSize));
        }
	
        aio->start(poller);
        d.run();
        aio->queueForDeletion();
        socket.close();
    } catch (const std::exception& e) {
        QPID_LOG(error, QPID_MSG("FAIL " << identifier << ": " << e.what()));
        handleClosed();
    }
}


}} // namespace qpid::client
