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

#include "qpid/sys/TransportFactory.h"

#include "qpid/Plugin.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/NameGenerator.h"
#include "qpid/framing/AMQP_HighestVersion.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/rdma/RdmaIO.h"
#include "qpid/sys/rdma/rdma_exception.h"
#include "qpid/sys/OutputControl.h"
#include "qpid/sys/SecuritySettings.h"

#include <boost/bind.hpp>
#include <boost/lexical_cast.hpp>
#include <memory>

#include <netdb.h>

using std::auto_ptr;
using std::string;
using std::stringstream;

namespace qpid {
namespace sys {

class RdmaIOHandler : public OutputControl {
    std::string identifier;
    ConnectionCodec::Factory* factory;
    ConnectionCodec* codec;
    bool readError;

    sys::Mutex pollingLock;
    bool polling;

    Rdma::AsynchIO* aio;
    Rdma::Connection::intrusive_ptr connection;

    void write(const framing::ProtocolInitiation&);
    void disconnectAction();

  public:
    RdmaIOHandler(Rdma::Connection::intrusive_ptr c, ConnectionCodec::Factory* f);
    ~RdmaIOHandler();
    void init(Rdma::AsynchIO* a);
    void start(Poller::shared_ptr poller);

    // Output side
    void close();
    void abort();
    void connectionEstablished();
    void activateOutput();
    void initProtocolOut();

    // Input side
    void readbuff(Rdma::AsynchIO& aio, Rdma::Buffer* buff);
    void initProtocolIn(Rdma::Buffer* buff);

    // Notifications
    void full(Rdma::AsynchIO& aio);
    void idle(Rdma::AsynchIO& aio);
    void error(Rdma::AsynchIO& aio);
    void disconnected();
    void drained();
};

RdmaIOHandler::RdmaIOHandler(Rdma::Connection::intrusive_ptr c, qpid::sys::ConnectionCodec::Factory* f) :
    identifier(broker::QPID_NAME_PREFIX+c->getFullName()),
    factory(f),
    codec(0),
    readError(false),
    polling(false),
    connection(c)
{
}

RdmaIOHandler::~RdmaIOHandler() {
    if (codec)
        codec->closed();
    delete codec;
    delete aio;
}

void RdmaIOHandler::init(Rdma::AsynchIO* a) {
    aio = a;
}

void RdmaIOHandler::start(Poller::shared_ptr poller) {
    Mutex::ScopedLock l(pollingLock);
    assert(!polling);

    polling = true;

    aio->start(poller);
}

void RdmaIOHandler::write(const framing::ProtocolInitiation& data)
{
    QPID_LOG(debug, "Rdma: SENT [" << identifier << "]: INIT(" << data << ")");
    Rdma::Buffer* buff = aio->getSendBuffer();
    assert(buff);
    framing::Buffer out(buff->bytes(), buff->byteCount());
    data.encode(out);
    buff->dataCount(data.encodedSize());
    aio->queueWrite(buff);
}

void RdmaIOHandler::close() {
    aio->drainWriteQueue(boost::bind(&RdmaIOHandler::drained, this));
}

// TODO: Dummy implementation, need to fill this in for heartbeat timeout to work
void RdmaIOHandler::abort() {
}

// TODO: Dummy implementation, need to fill this in for connection establishment timeout to work
void RdmaIOHandler::connectionEstablished() {
}

void RdmaIOHandler::activateOutput() {
    aio->notifyPendingWrite();
}

void RdmaIOHandler::idle(Rdma::AsynchIO&) {
    // TODO: Shouldn't need this test as idle() should only ever be called when
    // the connection is writable anyway
    if ( !aio->writable() ) {
        return;
    }
    if (codec == 0) return;
    if (!codec->canEncode()) {
        return;
    }
    Rdma::Buffer* buff = aio->getSendBuffer();
    if (buff) {
        size_t encoded=codec->encode(buff->bytes(), buff->byteCount());
        buff->dataCount(encoded);
        aio->queueWrite(buff);
        if (codec->isClosed()) {
            close();
        }
    }
}

void RdmaIOHandler::initProtocolOut() {
    // We mustn't have already started the conversation
    // but we must be able to send
    assert( codec == 0 );
    assert( aio->writable() );
    codec = factory->create(*this, identifier, SecuritySettings());
    write(framing::ProtocolInitiation(codec->getVersion()));
}

void RdmaIOHandler::error(Rdma::AsynchIO&) {
    disconnected();
}

namespace {
    void stopped(RdmaIOHandler* async) {
        delete async;
    }
}

void RdmaIOHandler::disconnectAction() {
    {
    Mutex::ScopedLock l(pollingLock);
    // If we're closed already then we'll get to drained() anyway
    if (!polling) return;
    polling = false;
    }
    aio->stop(boost::bind(&stopped, this));
}

void RdmaIOHandler::disconnected() {
    aio->requestCallback(boost::bind(&RdmaIOHandler::disconnectAction, this));
}

void RdmaIOHandler::drained() {
    // We know we've drained the write queue now, but we don't have to do anything
    // because we can rely on the client to disconnect to trigger the connection
    // cleanup.
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
    try {
        if (codec) {
            (void) codec->decode(buff->bytes(), buff->dataCount());
        }else{
            // Need to start protocol processing
            initProtocolIn(buff);
        }
    }catch(const std::exception& e){
        QPID_LOG(error, e.what());
        readError = true;
        close();
    }
}

void RdmaIOHandler::initProtocolIn(Rdma::Buffer* buff) {
    framing::Buffer in(buff->bytes(), buff->dataCount());
    framing::ProtocolInitiation protocolInit;
    if (protocolInit.decode(in)) {
        QPID_LOG(debug, "Rdma: RECV [" << identifier << "]: INIT(" << protocolInit << ")");

        codec = factory->create(protocolInit.getVersion(), *this, identifier, SecuritySettings());

        // If we failed to create the codec then we don't understand the offered protocol version
        if (!codec) {
            // send valid version header & close connection.
            write(framing::ProtocolInitiation(framing::highestProtocolVersion));
            readError = true;
            close();
        }
    }
}

class RdmaIOProtocolFactory : public TransportAcceptor, public TransportConnector {
    auto_ptr<Rdma::Listener> listener;
    const uint16_t listeningPort;

  public:
    RdmaIOProtocolFactory(int16_t port, int backlog);
    void accept(Poller::shared_ptr, ConnectionCodec::Factory*);
    void connect(Poller::shared_ptr, const std::string& name, const string& host, const std::string& port, ConnectionCodec::Factory*, ConnectFailedCallback);

    uint16_t getPort() const;

  private:
    bool request(Rdma::Connection::intrusive_ptr, const Rdma::ConnectionParams&, ConnectionCodec::Factory*);
    void established(Poller::shared_ptr, Rdma::Connection::intrusive_ptr);
    void connected(Poller::shared_ptr, Rdma::Connection::intrusive_ptr, const Rdma::ConnectionParams&, ConnectionCodec::Factory*);
    void connectionError(Rdma::Connection::intrusive_ptr, Rdma::ErrorType);
    void disconnected(Rdma::Connection::intrusive_ptr);
    void rejected(Rdma::Connection::intrusive_ptr, const Rdma::ConnectionParams&, ConnectFailedCallback);
};

// Static instance to initialise plugin
static class RdmaIOPlugin : public Plugin {
    void earlyInitialize(Target&) {
    }

    void initialize(Target& target) {
        // Check whether we actually have any rdma devices
        if ( Rdma::deviceCount() == 0 ) {
            QPID_LOG(info, "Rdma: Disabled: no rdma devices found");
            return;
        }

        broker::Broker* broker = dynamic_cast<broker::Broker*>(&target);
        // Only provide to a Broker
        if (broker) {
            boost::shared_ptr<RdmaIOProtocolFactory> protocol(new RdmaIOProtocolFactory(broker->getPortOption(), broker->getConnectionBacklog()));
            uint16_t port = protocol->getPort();
            QPID_LOG(notice, "Rdma: Listening on RDMA port " << port);
            broker->registerTransport("rdma", protocol, protocol, port);
        }
    }
} rdmaPlugin;

RdmaIOProtocolFactory::RdmaIOProtocolFactory(int16_t port, int /*backlog*/) :
    listeningPort(port)
{}

void RdmaIOProtocolFactory::established(Poller::shared_ptr poller, Rdma::Connection::intrusive_ptr ci) {
    RdmaIOHandler* async = ci->getContext<RdmaIOHandler>();
    async->start(poller);
}

bool RdmaIOProtocolFactory::request(Rdma::Connection::intrusive_ptr ci, const Rdma::ConnectionParams& cp,
        ConnectionCodec::Factory* f) {
    try {
        if (cp.rdmaProtocolVersion == 0) {
            QPID_LOG(warning, "Rdma: connection from protocol version 0 client");
        }
        RdmaIOHandler* async = new RdmaIOHandler(ci, f);
        Rdma::AsynchIO* aio =
            new Rdma::AsynchIO(ci->getQueuePair(),
                cp.rdmaProtocolVersion,
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
        QPID_LOG(error, "Rdma: Cannot accept new connection (Rdma exception): " << e.what());
    } catch (const std::exception& e) {
        QPID_LOG(error, "Rdma: Cannot accept new connection (unknown exception): " << e.what());
    }

    // If we get here we caught an exception so reject connection
    return false;
}

void RdmaIOProtocolFactory::connectionError(Rdma::Connection::intrusive_ptr, Rdma::ErrorType) {
}

void RdmaIOProtocolFactory::disconnected(Rdma::Connection::intrusive_ptr ci) {
    // If we've got a connection already tear it down, otherwise ignore
    RdmaIOHandler* async =  ci->getContext<RdmaIOHandler>();
    if (async) {
        // Make sure we don't disconnect more than once
        ci->removeContext();
        async->disconnected();
    }
}

uint16_t RdmaIOProtocolFactory::getPort() const {
    return listeningPort; // Immutable no need for lock.
}

void RdmaIOProtocolFactory::accept(Poller::shared_ptr poller, ConnectionCodec::Factory* fact) {
    listener.reset(
        new Rdma::Listener(
            Rdma::ConnectionParams(65536, Rdma::DEFAULT_WR_ENTRIES),
            boost::bind(&RdmaIOProtocolFactory::established, this, poller, _1),
            boost::bind(&RdmaIOProtocolFactory::connectionError, this, _1, _2),
            boost::bind(&RdmaIOProtocolFactory::disconnected, this, _1),
            boost::bind(&RdmaIOProtocolFactory::request, this, _1, _2, fact)));

    SocketAddress sa("",boost::lexical_cast<std::string>(listeningPort));
    listener->start(poller, sa);
}

// Only used for outgoing connections (in federation)
void RdmaIOProtocolFactory::rejected(Rdma::Connection::intrusive_ptr, const Rdma::ConnectionParams&, ConnectFailedCallback failed) {
    failed(-1, "Connection rejected");
}

// Do the same as connection request and established but mark a client too
void RdmaIOProtocolFactory::connected(Poller::shared_ptr poller, Rdma::Connection::intrusive_ptr ci, const Rdma::ConnectionParams& cp,
        ConnectionCodec::Factory* f) {
    (void) request(ci, cp, f);
    established(poller, ci);
    RdmaIOHandler* async =  ci->getContext<RdmaIOHandler>();
    async->initProtocolOut();
}

void RdmaIOProtocolFactory::connect(
    Poller::shared_ptr poller,
    const std::string& /*name*/,
    const std::string& host, const std::string& port,
    ConnectionCodec::Factory* f,
    ConnectFailedCallback failed)
{
    Rdma::Connector* c =
        new Rdma::Connector(
            Rdma::ConnectionParams(8000, Rdma::DEFAULT_WR_ENTRIES),
            boost::bind(&RdmaIOProtocolFactory::connected, this, poller, _1, _2, f),
            boost::bind(&RdmaIOProtocolFactory::connectionError, this, _1, _2),
            boost::bind(&RdmaIOProtocolFactory::disconnected, this, _1),
            boost::bind(&RdmaIOProtocolFactory::rejected, this, _1, _2, failed));

    SocketAddress sa(host, port);
    c->start(poller, sa);
}

}} // namespace qpid::sys
