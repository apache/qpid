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
#include "qpid/sys/Codec.h"
#include "qpid/sys/Time.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/sys/AsynchIO.h"
#include "qpid/sys/Dispatcher.h"
#include "qpid/sys/Poller.h"
#include "qpid/sys/SecurityLayer.h"
#include "qpid/Msg.h"

#include <iostream>
#include <map>
#include <deque>
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

class TCPConnector : public Connector, public sys::Codec, private sys::Runnable
{
    typedef std::deque<framing::AMQFrame> Frames;
    struct Buff;

    const uint16_t maxFrameSize;

    sys::Mutex lock;
    Frames frames; // Outgoing frame queue
    size_t lastEof; // Position after last EOF in frames
    uint64_t currentSize;
    Bounds* bounds;
    
    framing::ProtocolVersion version;
    bool initiated;

    sys::Mutex closedLock;    
    bool closed;
    bool joined;

    sys::ShutdownHandler* shutdownHandler;
    framing::InputHandler* input;
    framing::InitiationHandler* initialiser;
    framing::OutputHandler* output;

    sys::Thread receiver;

    sys::Socket socket;

    sys::AsynchIO* aio;
    std::string identifier;
    boost::shared_ptr<sys::Poller> poller;
    std::auto_ptr<qpid::sys::SecurityLayer> securityLayer;

    ~TCPConnector();

    void run();
    void handleClosed();
    bool closeInternal();
    
    bool readbuff(qpid::sys::AsynchIO&, qpid::sys::AsynchIOBufferBase*);
    void writebuff(qpid::sys::AsynchIO&);
    void writeDataBlock(const framing::AMQDataBlock& data);
    void eof(qpid::sys::AsynchIO&);

    boost::weak_ptr<ConnectionImpl> impl;
    
    void connect(const std::string& host, int port);
    void init();
    void close();
    void send(framing::AMQFrame& frame);
    void abort();

    void setInputHandler(framing::InputHandler* handler);
    void setShutdownHandler(sys::ShutdownHandler* handler);
    sys::ShutdownHandler* getShutdownHandler() const;
    framing::OutputHandler* getOutputHandler();
    const std::string& getIdentifier() const;
    void activateSecurityLayer(std::auto_ptr<qpid::sys::SecurityLayer>);

    size_t decode(const char* buffer, size_t size);
    size_t encode(const char* buffer, size_t size);
    bool canEncode();
    

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
      lastEof(0),
      currentSize(0),
      bounds(cimpl),
      version(ver), 
      initiated(false),
      closed(true),
      joined(true),
      shutdownHandler(0),
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

void TCPConnector::abort() {
    aio->requestCallback(boost::bind(&TCPConnector::eof, this, _1));
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

void TCPConnector::handleClosed() {
    if (closeInternal() && shutdownHandler)
        shutdownHandler->shutdown();
}

struct TCPConnector::Buff : public AsynchIO::BufferBase {
    Buff(size_t size) : AsynchIO::BufferBase(new char[size], size) {}    
    ~Buff() { delete [] bytes;}
};

void TCPConnector::writebuff(AsynchIO& /*aio*/) 
{
    Codec* codec = securityLayer.get() ? (Codec*) securityLayer.get() : (Codec*) this;
    if (codec->canEncode()) {
        std::auto_ptr<AsynchIO::BufferBase> buffer = std::auto_ptr<AsynchIO::BufferBase>(aio->getQueuedBuffer());
        if (!buffer.get()) buffer = std::auto_ptr<AsynchIO::BufferBase>(new Buff(maxFrameSize));
        
        size_t encoded = codec->encode(buffer->bytes, buffer->byteCount);

        buffer->dataStart = 0;
        buffer->dataCount = encoded;
        aio->queueWrite(buffer.release());
    }
}

// Called in IO thread.
bool TCPConnector::canEncode()
{
    Mutex::ScopedLock l(lock);
    //have at least one full frameset or a whole buffers worth of data
    return lastEof || currentSize >= maxFrameSize;
}

// Called in IO thread.
size_t TCPConnector::encode(const char* buffer, size_t size)
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

bool TCPConnector::readbuff(AsynchIO& aio, AsynchIO::BufferBase* buff) 
{
    Codec* codec = securityLayer.get() ? (Codec*) securityLayer.get() : (Codec*) this;
    int32_t decoded = codec->decode(buff->bytes+buff->dataStart, buff->dataCount);
    // TODO: unreading needs to go away, and when we can cope
    // with multiple sub-buffers in the general buffer scheme, it will
    if (decoded < buff->dataCount) {
        // Adjust buffer for used bytes and then "unread them"
        buff->dataStart += decoded;
        buff->dataCount -= decoded;
        aio.unread(buff);
    } else {
        // Give whole buffer back to aio subsystem
        aio.queueReadBuffer(buff);
    }
    return true;
}

size_t TCPConnector::decode(const char* buffer, size_t size) 
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

void TCPConnector::run() {
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

void TCPConnector::activateSecurityLayer(std::auto_ptr<qpid::sys::SecurityLayer> sl)
{
    securityLayer = sl;
    securityLayer->init(this);
}


}} // namespace qpid::client
