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

#include "Acceptor.h"

#include "Socket.h"
#include "AsynchIO.h"
#include "Mutex.h"
#include "Thread.h"

#include "qpid/sys/ConnectionOutputHandler.h"
#include "qpid/framing/AMQP_HighestVersion.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/log/Statement.h"

#include <boost/bind.hpp>
#include <boost/assert.hpp>
#include <queue>
#include <vector>
#include <memory>
#include <ostream>

namespace qpid {
namespace sys {

class AsynchIOAcceptor : public Acceptor {
    Poller::shared_ptr poller;
    Socket listener;
    int numIOThreads;
    const uint16_t listeningPort;

  public:
    AsynchIOAcceptor(int16_t port, int backlog, int threads);
    ~AsynchIOAcceptor() {}
    void run(ConnectionCodec::Factory*);
    void connect(const std::string& host, int16_t port, ConnectionCodec::Factory*);

    void shutdown();
        
    uint16_t getPort() const;
    std::string getHost() const;

  private:
    void accepted(Poller::shared_ptr, const Socket&, ConnectionCodec::Factory*);
};

Acceptor::shared_ptr Acceptor::create(int16_t port, int backlog, int threads)
{
    return Acceptor::shared_ptr(new AsynchIOAcceptor(port, backlog, threads));
}

AsynchIOAcceptor::AsynchIOAcceptor(int16_t port, int backlog, int threads) :
    poller(new Poller),
    numIOThreads(threads),
    listeningPort(listener.listen(port, backlog))
{}

// Buffer definition
struct Buff : public AsynchIO::BufferBase {
    Buff() :
        AsynchIO::BufferBase(new char[65536], 65536)
    {}
    ~Buff()
    { delete [] bytes;}
};

class AsynchIOHandler : public OutputControl {
    AsynchIO* aio;
    ConnectionCodec::Factory* factory;
    ConnectionCodec* codec;
    bool readError;
    std::string identifier;
    bool isClient;

    void write(const framing::ProtocolInitiation&);

  public:
    AsynchIOHandler() :
        aio(0),
        factory(0),
        codec(0),
        readError(false),
        isClient(false)
    {}
	
    ~AsynchIOHandler() {
        if (codec)
            codec->closed();
        delete codec;
    }

    void setClient() { isClient = true; }
    
    void init(AsynchIO* a, ConnectionCodec::Factory* f) {
        aio = a;
        factory = f;
        identifier = aio->getSocket().getPeerAddress();

    }

    // Output side
    void close();
    void activateOutput();

    // Input side
    void readbuff(AsynchIO& aio, AsynchIO::BufferBase* buff);
    void eof(AsynchIO& aio);
    void disconnect(AsynchIO& aio);
	
    // Notifications
    void nobuffs(AsynchIO& aio);
    void idle(AsynchIO& aio);
    void closedSocket(AsynchIO& aio, const Socket& s);
};

void AsynchIOAcceptor::accepted(Poller::shared_ptr poller, const Socket& s, ConnectionCodec::Factory* f) {
    AsynchIOHandler* async = new AsynchIOHandler; 
    AsynchIO* aio = new AsynchIO(s,
                                 boost::bind(&AsynchIOHandler::readbuff, async, _1, _2),
                                 boost::bind(&AsynchIOHandler::eof, async, _1),
                                 boost::bind(&AsynchIOHandler::disconnect, async, _1),
                                 boost::bind(&AsynchIOHandler::closedSocket, async, _1, _2),
                                 boost::bind(&AsynchIOHandler::nobuffs, async, _1),
                                 boost::bind(&AsynchIOHandler::idle, async, _1));
    async->init(aio, f);
    // Give connection some buffers to use
    for (int i = 0; i < 4; i++) {
        aio->queueReadBuffer(new Buff);
    }
    aio->start(poller);
}


uint16_t AsynchIOAcceptor::getPort() const {
    return listeningPort; // Immutable no need for lock.
}

std::string AsynchIOAcceptor::getHost() const {
    return listener.getSockname();
}

void AsynchIOAcceptor::run(ConnectionCodec::Factory* fact) {
    Dispatcher d(poller);
    AsynchAcceptor
        acceptor(listener,
                 boost::bind(&AsynchIOAcceptor::accepted, this, poller, _1, fact));
    acceptor.start(poller);
	
    std::vector<Thread> t(numIOThreads-1);

    // Run n-1 io threads
    for (int i=0; i<numIOThreads-1; ++i)
        t[i] = Thread(d);

    // Run final thread
    d.run();
	
    // Now wait for n-1 io threads to exit
    for (int i=0; i<numIOThreads-1; ++i) {
        t[i].join();
    }
}
    
void AsynchIOAcceptor::connect(
    const std::string& host, int16_t port, ConnectionCodec::Factory* f)
{
    Socket* socket = new Socket();//Should be deleted by handle when socket closes
    socket->connect(host, port);
    AsynchIOHandler* async = new AsynchIOHandler; 
    async->setClient();
    AsynchIO* aio = new AsynchIO(*socket,
                                 boost::bind(&AsynchIOHandler::readbuff, async, _1, _2),
                                 boost::bind(&AsynchIOHandler::eof, async, _1),
                                 boost::bind(&AsynchIOHandler::disconnect, async, _1),
                                 boost::bind(&AsynchIOHandler::closedSocket, async, _1, _2),
                                 boost::bind(&AsynchIOHandler::nobuffs, async, _1),
                                 boost::bind(&AsynchIOHandler::idle, async, _1));
    async->init(aio, f);
    // Give connection some buffers to use
    for (int i = 0; i < 4; i++) {
        aio->queueReadBuffer(new Buff);
    }
    aio->start(poller);
}


void AsynchIOAcceptor::shutdown() {
    // NB: this function must be async-signal safe, it must not
    // call any function that is not async-signal safe.
    poller->shutdown();
}


void AsynchIOHandler::write(const framing::ProtocolInitiation& data)
{
    QPID_LOG(debug, "SENT [" << identifier << "] INIT(" << data << ")");
    AsynchIO::BufferBase* buff = aio->getQueuedBuffer();
    if (!buff)
        buff = new Buff;
    framing::Buffer out(buff->bytes, buff->byteCount);
    data.encode(out);
    buff->dataCount = data.size();
    aio->queueWrite(buff);
}

void AsynchIOHandler::activateOutput() {
    aio->notifyPendingWrite();
}

// Input side
void AsynchIOHandler::readbuff(AsynchIO& , AsynchIO::BufferBase* buff) {
    if (readError) {
        return;
    }
    size_t decoded = 0;
    if (codec) {                // Already initiated
        try {
            decoded = codec->decode(buff->bytes+buff->dataStart, buff->dataCount);
        }catch(const std::exception& e){
            QPID_LOG(error, e.what());
            readError = true;
            aio->queueWriteClose();
        }
    }else{
        framing::Buffer in(buff->bytes+buff->dataStart, buff->dataCount);
        framing::ProtocolInitiation protocolInit;
        if (protocolInit.decode(in)) {
            decoded = in.getPosition();
            QPID_LOG(debug, "RECV [" << identifier << "] INIT(" << protocolInit << ")");
            try {
                codec = factory->create(protocolInit.getVersion(), *this, identifier);
                if (!codec) {
                    //TODO: may still want to revise this...
                    //send valid version header & close connection.
                    write(framing::ProtocolInitiation(framing::highestProtocolVersion));
                    readError = true;
                    aio->queueWriteClose();                
                }
            } catch (const std::exception& e) {
                QPID_LOG(error, e.what());
                readError = true;
                aio->queueWriteClose();
            }
        }
    }
    // TODO: unreading needs to go away, and when we can cope
    // with multiple sub-buffers in the general buffer scheme, it will
    if (decoded != size_t(buff->dataCount)) {
        // Adjust buffer for used bytes and then "unread them"
        buff->dataStart += decoded;
        buff->dataCount -= decoded;
        aio->unread(buff);
    } else {
        // Give whole buffer back to aio subsystem
        aio->queueReadBuffer(buff);
    }
}

void AsynchIOHandler::eof(AsynchIO&) {
    QPID_LOG(debug, "DISCONNECTED [" << identifier << "]");
    if (codec) codec->closed();
    aio->queueWriteClose();
}

void AsynchIOHandler::closedSocket(AsynchIO&, const Socket& s) {
    // If we closed with data still to send log a warning 
    if (!aio->writeQueueEmpty()) {
        QPID_LOG(warning, "CLOSING [" << identifier << "] unsent data (probably due to client disconnect)");
    }
    delete &s;
    aio->queueForDeletion();
    delete this;
}

void AsynchIOHandler::disconnect(AsynchIO& a) {
    // treat the same as eof
    eof(a);
}

// Notifications
void AsynchIOHandler::nobuffs(AsynchIO&) {
}

void AsynchIOHandler::idle(AsynchIO&){
    if (isClient && codec == 0) {
        codec = factory->create(*this, identifier);
        write(framing::ProtocolInitiation(codec->getVersion()));
        return;
    }
    if (codec == 0) return;
    if (codec->canEncode()) {
        // Try and get a queued buffer if not then construct new one
        AsynchIO::BufferBase* buff = aio->getQueuedBuffer();
        if (!buff) buff = new Buff;
        size_t encoded=codec->encode(buff->bytes, buff->byteCount);
        buff->dataCount = encoded;
        aio->queueWrite(buff);
    }
    if (codec->isClosed())
        aio->queueWriteClose();
}

}} // namespace qpid::sys
