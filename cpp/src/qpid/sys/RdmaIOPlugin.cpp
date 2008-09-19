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

#include "ProtocolFactory.h"

#include "qpid/Plugin.h"
#include "qpid/broker/Broker.h"
#include "qpid/framing/AMQP_HighestVersion.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/rdma/RdmaIO.h"
#include "qpid/sys/OutputControl.h"

#include <boost/bind.hpp>
#include <memory>

#include <netdb.h>

using std::auto_ptr;
using std::string;
using std::stringstream;

namespace qpid {
namespace sys {

class RdmaIOHandler : public OutputControl {
    Rdma::Connection::intrusive_ptr connection;
    std::string identifier;
    Rdma::AsynchIO* aio;
    ConnectionCodec::Factory* factory;
    ConnectionCodec* codec;
    bool readError;
    bool isClient;

    void write(const framing::ProtocolInitiation&);

  public:
    RdmaIOHandler(Rdma::Connection::intrusive_ptr& c, ConnectionCodec::Factory* f);
    ~RdmaIOHandler();
    void init(Rdma::AsynchIO* a);
    void start(Poller::shared_ptr poller) {aio->start(poller);}

    void setClient() { isClient = true; }

    // Output side
    void close();
    void activateOutput();

    // Input side
    void readbuff(Rdma::AsynchIO& aio, Rdma::Buffer* buff);
    
    // Notifications
    void full(Rdma::AsynchIO& aio);
    void idle(Rdma::AsynchIO& aio);
    void error(Rdma::AsynchIO& aio);    
};

RdmaIOHandler::RdmaIOHandler(Rdma::Connection::intrusive_ptr& c, qpid::sys::ConnectionCodec::Factory* f) :
    connection(c),
    identifier(c->getPeerName()),
    factory(f),
    codec(0),
    readError(false),
    isClient(false)
{
}

void RdmaIOHandler::init(Rdma::AsynchIO* a) {
    aio = a;
}

RdmaIOHandler::~RdmaIOHandler() {
    if (codec)
        codec->closed();
    delete codec;
    
    aio->deferDelete();
}

void RdmaIOHandler::write(const framing::ProtocolInitiation& data)
{
    QPID_LOG(debug, "Rdma: SENT [" << identifier << "] INIT(" << data << ")");
    Rdma::Buffer* buff = aio->getBuffer();
    framing::Buffer out(buff->bytes, buff->byteCount);
    data.encode(out);
    buff->dataCount = data.size();
    aio->queueWrite(buff);
}

void RdmaIOHandler::close() {
    aio->queueWriteClose();
}

void RdmaIOHandler::activateOutput() {
    aio->notifyPendingWrite();
}

void RdmaIOHandler::idle(Rdma::AsynchIO&) {
    // TODO: Shouldn't need this test as idle() should only ever be called when
    // the connection is writable anyway
    if ( !(aio->writable() && aio->bufferAvailable()) ) {
        return;
    }
    if (isClient && codec == 0) {
        codec = factory->create(*this, identifier);
        write(framing::ProtocolInitiation(codec->getVersion()));
        return;
    }
    if (codec == 0) return;
    if (codec->canEncode()) {
        // Try and get a queued buffer if not then construct new one
        Rdma::Buffer* buff = aio->getBuffer();
        size_t encoded=codec->encode(buff->bytes, buff->byteCount);
        buff->dataCount = encoded;
        aio->queueWrite(buff);
    }
    if (codec->isClosed())
        aio->queueWriteClose();
}

void RdmaIOHandler::error(Rdma::AsynchIO&) {
    close();
}

void RdmaIOHandler::full(Rdma::AsynchIO&) {
    QPID_LOG(debug, "Rdma: buffer full [" << identifier << "]");
}

// The logic here is subtly different from TCP as RDMA is message oriented
// so we define that an RDMA message is a frame - in this case there is no putting back
// of any message remainder - there shouldn't be any. And what we read here can't be
// smaller than a frame
void RdmaIOHandler::readbuff(Rdma::AsynchIO&, Rdma::Buffer* buff) {
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
            QPID_LOG(debug, "Rdma: RECV [" << identifier << "] INIT(" << protocolInit << ")");
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
}

class RdmaIOProtocolFactory : public ProtocolFactory {
    auto_ptr<Rdma::Listener> listener;
    const uint16_t listeningPort;

  public:
    RdmaIOProtocolFactory(int16_t port, int backlog);
    void accept(Poller::shared_ptr, ConnectionCodec::Factory*);
    void connect(Poller::shared_ptr, const string& host, int16_t port, ConnectionCodec::Factory*, ConnectFailedCallback);

    uint16_t getPort() const;
    string getHost() const;

  private:
    bool request(Rdma::Connection::intrusive_ptr&, const Rdma::ConnectionParams&, ConnectionCodec::Factory*);
    void established(Poller::shared_ptr, Rdma::Connection::intrusive_ptr&);
    void connected(Poller::shared_ptr, Rdma::Connection::intrusive_ptr&, const Rdma::ConnectionParams&, ConnectionCodec::Factory*);
    void connectionError(Rdma::Connection::intrusive_ptr&, Rdma::ErrorType);
    void disconnected(Rdma::Connection::intrusive_ptr&);
    void rejected(Rdma::Connection::intrusive_ptr&, const Rdma::ConnectionParams&);
};

// Static instance to initialise plugin
static class RdmaIOPlugin : public Plugin {
    void earlyInitialize(Target&) {
    }
    
    void initialize(Target& target) {
        broker::Broker* broker = dynamic_cast<broker::Broker*>(&target);
        // Only provide to a Broker
        if (broker) {
            const broker::Broker::Options& opts = broker->getOptions();
            ProtocolFactory::shared_ptr protocol(new RdmaIOProtocolFactory(opts.port, opts.connectionBacklog));
            QPID_LOG(info, "Listening on RDMA port " << protocol->getPort());
            broker->registerProtocolFactory(protocol);
        }
    }
} rdmaPlugin;

RdmaIOProtocolFactory::RdmaIOProtocolFactory(int16_t port, int /*backlog*/) :
    listeningPort(port)
{}

void RdmaIOProtocolFactory::established(Poller::shared_ptr poller, Rdma::Connection::intrusive_ptr& ci) {
    RdmaIOHandler* async = ci->getContext<RdmaIOHandler>();
    async->start(poller);
}

bool RdmaIOProtocolFactory::request(Rdma::Connection::intrusive_ptr& ci, const Rdma::ConnectionParams& cp,
        ConnectionCodec::Factory* f) {
    try {
        RdmaIOHandler* async = new RdmaIOHandler(ci, f);
        Rdma::AsynchIO* aio =
            new Rdma::AsynchIO(ci->getQueuePair(),
                cp.maxRecvBufferSize, cp.initialXmitCredit, Rdma::DEFAULT_WR_ENTRIES,
                boost::bind(&RdmaIOHandler::readbuff, async, _1, _2),
                boost::bind(&RdmaIOHandler::idle, async, _1),
                0, // boost::bind(&RdmaIOHandler::full, async, _1),
                boost::bind(&RdmaIOHandler::error, async, _1));
        async->init(aio);
    
        // Record aio so we can get it back from a connection
        ci->addContext(async);
        return true;
    } catch (const Rdma::Exception& e) {
        QPID_LOG(error, "Rdma: Cannot accept new connection (Rdma excepion): " << e.what());
    } catch (const std::exception& e) {
        QPID_LOG(error, "Rdma: Cannot accept new connection (unknown exception): " << e.what());
    }

    // If we get here we caught an exception so reject connection
    return false;
}

void RdmaIOProtocolFactory::connectionError(Rdma::Connection::intrusive_ptr&, Rdma::ErrorType) {
}

void RdmaIOProtocolFactory::disconnected(Rdma::Connection::intrusive_ptr& ci) {
    // If we've got a connection already tear it down, otherwise ignore
    RdmaIOHandler* async =  ci->getContext<RdmaIOHandler>();
    if (async) {
        async->close();
    }
    delete async;
}

uint16_t RdmaIOProtocolFactory::getPort() const {
    return listeningPort; // Immutable no need for lock.
}

string RdmaIOProtocolFactory::getHost() const {
    //return listener.getSockname();
    return "";
}

void RdmaIOProtocolFactory::accept(Poller::shared_ptr poller, ConnectionCodec::Factory* fact) {
    ::sockaddr_in sin;

    sin.sin_family = AF_INET;
    sin.sin_port = htons(listeningPort);
    sin.sin_addr.s_addr = INADDR_ANY;

    listener.reset(
        new Rdma::Listener((const sockaddr&)(sin), 
            Rdma::ConnectionParams(65536, Rdma::DEFAULT_WR_ENTRIES),
            boost::bind(&RdmaIOProtocolFactory::established, this, poller, _1),
            boost::bind(&RdmaIOProtocolFactory::connectionError, this, _1, _2),
            boost::bind(&RdmaIOProtocolFactory::disconnected, this, _1),
            boost::bind(&RdmaIOProtocolFactory::request, this, _1, _2, fact)));
                           
    listener->start(poller);
}

// Only used for outgoing connections (in federation)
void RdmaIOProtocolFactory::rejected(Rdma::Connection::intrusive_ptr&, const Rdma::ConnectionParams&) {
}

// Do the same as connection request and established but mark a client too
void RdmaIOProtocolFactory::connected(Poller::shared_ptr poller, Rdma::Connection::intrusive_ptr& ci, const Rdma::ConnectionParams& cp,
        ConnectionCodec::Factory* f) {
    (void) request(ci, cp, f);
    RdmaIOHandler* async =  ci->getContext<RdmaIOHandler>();
    async->setClient();
    established(poller, ci);
}
 
void RdmaIOProtocolFactory::connect(
    Poller::shared_ptr poller,
    const std::string& host, int16_t p,
    ConnectionCodec::Factory* f,
    ConnectFailedCallback)
{
    ::addrinfo *res;
    ::addrinfo hints = {};
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;
    stringstream ss; ss << p;
    string port = ss.str();
    int n = ::getaddrinfo(host.c_str(), port.c_str(), &hints, &res);
    if (n<0) {
        throw Exception(QPID_MSG("Rdma: Cannot resolve " << host << ": " << ::gai_strerror(n)));
    }

    Rdma::Connector c(
            *res->ai_addr,
            Rdma::ConnectionParams(8000, Rdma::DEFAULT_WR_ENTRIES),
            boost::bind(&RdmaIOProtocolFactory::connected, this, poller, _1, _2, f),
            boost::bind(&RdmaIOProtocolFactory::connectionError, this, _1, _2),
            boost::bind(&RdmaIOProtocolFactory::disconnected, this, _1),
            boost::bind(&RdmaIOProtocolFactory::rejected, this, _1, _2));
}

}} // namespace qpid::sys
