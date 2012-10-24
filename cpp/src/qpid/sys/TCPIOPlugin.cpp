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

#include "qpid/sys/ProtocolFactory.h"
#include "qpid/sys/AsynchIOHandler.h"
#include "qpid/sys/AsynchIO.h"

#include "qpid/Plugin.h"
#include "qpid/sys/Socket.h"
#include "qpid/sys/SocketAddress.h"
#include "qpid/sys/Poller.h"
#include "qpid/broker/Broker.h"
#include "qpid/log/Statement.h"

#include <boost/bind.hpp>
#include <boost/ptr_container/ptr_vector.hpp>

namespace qpid {
namespace sys {

class Timer;

class AsynchIOProtocolFactory : public ProtocolFactory {
    boost::ptr_vector<Socket> listeners;
    boost::ptr_vector<AsynchAcceptor> acceptors;
    Timer& brokerTimer;
    uint32_t maxNegotiateTime;
    uint16_t listeningPort;
    const bool tcpNoDelay;

  public:
    AsynchIOProtocolFactory(const std::string& host, const std::string& port,
                            int backlog, bool nodelay,
                            Timer& timer, uint32_t maxTime,
                            bool shouldListen);
    void accept(Poller::shared_ptr, ConnectionCodec::Factory*);
    void connect(Poller::shared_ptr, const std::string& host, const std::string& port,
                 ConnectionCodec::Factory*,
                 ConnectFailedCallback);

    uint16_t getPort() const;

  private:
    void established(Poller::shared_ptr, const Socket&, ConnectionCodec::Factory*,
                     bool isClient);
    void connectFailed(const Socket&, int, const std::string&, ConnectFailedCallback);
};

static bool sslMultiplexEnabled(void)
{
    Options o;
    Plugin::addOptions(o);

    if (o.find_nothrow("ssl-multiplex", false)) {
        // This option is added by the SSL plugin when the SSL port
        // is configured to be the same as the main port.
        QPID_LOG(notice, "SSL multiplexing enabled");
        return true;
    }
    return false;
}

// Static instance to initialise plugin
static class TCPIOPlugin : public Plugin {
    void earlyInitialize(Target&) {
    }

    void initialize(Target& target) {
        broker::Broker* broker = dynamic_cast<broker::Broker*>(&target);
        // Only provide to a Broker
        if (broker) {
            const broker::Broker::Options& opts = broker->getOptions();

            // Check for SSL on the same port
            bool shouldListen = !sslMultiplexEnabled();

            ProtocolFactory::shared_ptr protocolt(
                new AsynchIOProtocolFactory(
                    "", boost::lexical_cast<std::string>(opts.port),
                    opts.connectionBacklog,
                    opts.tcpNoDelay,
                    broker->getTimer(), opts.maxNegotiateTime,
                    shouldListen));

            if (shouldListen) {
                QPID_LOG(notice, "Listening on TCP/TCP6 port " << protocolt->getPort());
            }

            broker->registerProtocolFactory("tcp", protocolt);
        }
    }
} tcpPlugin;

AsynchIOProtocolFactory::AsynchIOProtocolFactory(const std::string& host, const std::string& port,
                                                 int backlog, bool nodelay,
                                                 Timer& timer, uint32_t maxTime,
                                                 bool shouldListen) :
    brokerTimer(timer),
    maxNegotiateTime(maxTime),
    tcpNoDelay(nodelay)
{
    if (!shouldListen) {
        listeningPort = boost::lexical_cast<uint16_t>(port);
        return;
    }

    SocketAddress sa(host, port);

    // We must have at least one resolved address
    QPID_LOG(info, "Listening to: " << sa.asString())
    Socket* s = createSocket();
    uint16_t lport = s->listen(sa, backlog);
    QPID_LOG(debug, "Listened to: " << lport);
    listeners.push_back(s);

    listeningPort = lport;

    // Try any other resolved addresses
    while (sa.nextAddress()) {
        // Hack to ensure that all listening connections are on the same port
        sa.setAddrInfoPort(listeningPort);
        QPID_LOG(info, "Listening to: " << sa.asString())
        Socket* s = createSocket();
        uint16_t lport = s->listen(sa, backlog);
        QPID_LOG(debug, "Listened to: " << lport);
        listeners.push_back(s);
    }

}

void AsynchIOProtocolFactory::established(Poller::shared_ptr poller, const Socket& s,
                                          ConnectionCodec::Factory* f, bool isClient) {
    AsynchIOHandler* async = new AsynchIOHandler(s.getFullAddress(), f, false);

    if (tcpNoDelay) {
        s.setTcpNoDelay();
        QPID_LOG(info, "Set TCP_NODELAY on connection to " << s.getPeerAddress());
    }

    if (isClient)
        async->setClient();
    AsynchIO* aio = AsynchIO::create
      (s,
       boost::bind(&AsynchIOHandler::readbuff, async, _1, _2),
       boost::bind(&AsynchIOHandler::eof, async, _1),
       boost::bind(&AsynchIOHandler::disconnect, async, _1),
       boost::bind(&AsynchIOHandler::closedSocket, async, _1, _2),
       boost::bind(&AsynchIOHandler::nobuffs, async, _1),
       boost::bind(&AsynchIOHandler::idle, async, _1));

    async->init(aio, brokerTimer, maxNegotiateTime);
    aio->start(poller);
}

uint16_t AsynchIOProtocolFactory::getPort() const {
    return listeningPort; // Immutable no need for lock.
}

void AsynchIOProtocolFactory::accept(Poller::shared_ptr poller,
                                     ConnectionCodec::Factory* fact) {
    for (unsigned i = 0; i<listeners.size(); ++i) {
        acceptors.push_back(
            AsynchAcceptor::create(listeners[i],
                            boost::bind(&AsynchIOProtocolFactory::established, this, poller, _1, fact, false)));
        acceptors[i].start(poller);
    }
}

void AsynchIOProtocolFactory::connectFailed(
    const Socket& s, int ec, const std::string& emsg,
    ConnectFailedCallback failedCb)
{
    failedCb(ec, emsg);
    s.close();
    delete &s;
}

void AsynchIOProtocolFactory::connect(
    Poller::shared_ptr poller,
    const std::string& host, const std::string& port,
    ConnectionCodec::Factory* fact,
    ConnectFailedCallback failed)
{
    // Note that the following logic does not cause a memory leak.
    // The allocated Socket is freed either by the AsynchConnector
    // upon connection failure or by the AsynchIO upon connection
    // shutdown.  The allocated AsynchConnector frees itself when it
    // is no longer needed.
    Socket* socket = createSocket();
    try {
    AsynchConnector* c = AsynchConnector::create(
        *socket,
        host,
        port,
        boost::bind(&AsynchIOProtocolFactory::established,
                    this, poller, _1, fact, true),
        boost::bind(&AsynchIOProtocolFactory::connectFailed,
                    this, _1, _2, _3, failed));
    c->start(poller);
    } catch (std::exception&) {
        // TODO: Design question - should we do the error callback and also throw?
        int errCode = socket->getError();
        connectFailed(*socket, errCode, strError(errCode), failed);
        throw;
    }
}

}} // namespace qpid::sys
