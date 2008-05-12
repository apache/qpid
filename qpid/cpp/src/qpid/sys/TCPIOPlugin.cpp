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
#include "AsynchIOHandler.h"
#include "AsynchIO.h"

#include "qpid/Plugin.h"
#include "qpid/broker/Broker.h"
#include "qpid/log/Statement.h"

#include <boost/bind.hpp>
#include <memory>

namespace qpid {
namespace sys {

class AsynchIOProtocolFactory : public ProtocolFactory {
    Socket listener;
    const uint16_t listeningPort;
    std::auto_ptr<AsynchAcceptor> acceptor;

  public:
    AsynchIOProtocolFactory(int16_t port, int backlog);
    void accept(Poller::shared_ptr, ConnectionCodec::Factory*);
    void connect(Poller::shared_ptr, const std::string& host, int16_t port,
                 ConnectionCodec::Factory*, ProtocolAccess*);

    uint16_t getPort() const;
    std::string getHost() const;

  private:
    void established(Poller::shared_ptr, const Socket&, ConnectionCodec::Factory*,
                     bool isClient, ProtocolAccess*);
};

// Static instance to initialise plugin
static class TCPIOPlugin : public Plugin {
    void earlyInitialize(Target&) {
    }
    
    void initialize(Target& target) {
        broker::Broker* broker = dynamic_cast<broker::Broker*>(&target);
        // Only provide to a Broker
        if (broker) {
            const broker::Broker::Options& opts = broker->getOptions();
            ProtocolFactory::shared_ptr protocol(new AsynchIOProtocolFactory(opts.port, opts.connectionBacklog));
            QPID_LOG(info, "Listening on TCP port " << protocol->getPort());
            broker->registerProtocolFactory(protocol);
        }
    }
} tcpPlugin;

AsynchIOProtocolFactory::AsynchIOProtocolFactory(int16_t port, int backlog) :
    listeningPort(listener.listen(port, backlog))
{}

void AsynchIOProtocolFactory::established(Poller::shared_ptr poller, const Socket& s,
                                          ConnectionCodec::Factory* f, bool isClient,
                                          ProtocolAccess* a) {
    AsynchIOHandler* async = new AsynchIOHandler(s.getPeerAddress(), f, a);
    AsynchIO* aio;

    if (isClient)
        async->setClient();
    if (a == 0)
        aio = new AsynchIO(s,
                           boost::bind(&AsynchIOHandler::readbuff, async, _1, _2),
                           boost::bind(&AsynchIOHandler::eof, async, _1),
                           boost::bind(&AsynchIOHandler::disconnect, async, _1),
                           boost::bind(&AsynchIOHandler::closedSocket, async, _1, _2),
                           boost::bind(&AsynchIOHandler::nobuffs, async, _1),
                           boost::bind(&AsynchIOHandler::idle, async, _1));
    else {
        aio = new AsynchIO(s,
                           boost::bind(&AsynchIOHandler::readbuff, async, _1, _2),
                           boost::bind(&ProtocolAccess::closedEof, a, async),
                           boost::bind(&AsynchIOHandler::disconnect, async, _1),
                           boost::bind(&AsynchIOHandler::closedSocket, async, _1, _2),
                           boost::bind(&AsynchIOHandler::nobuffs, async, _1),
                           boost::bind(&AsynchIOHandler::idle, async, _1));
        a->setAio(aio);
    }

    async->init(aio, 4);
    aio->start(poller);
}

uint16_t AsynchIOProtocolFactory::getPort() const {
    return listeningPort; // Immutable no need for lock.
}

std::string AsynchIOProtocolFactory::getHost() const {
    return listener.getSockname();
}

void AsynchIOProtocolFactory::accept(Poller::shared_ptr poller,
                                     ConnectionCodec::Factory* fact) {
    acceptor.reset(
        new AsynchAcceptor(listener,
                           boost::bind(&AsynchIOProtocolFactory::established, this, poller, _1, fact, false,
                                       (ProtocolAccess*) 0)));
    acceptor->start(poller);
}

void AsynchIOProtocolFactory::connect(
    Poller::shared_ptr poller,
    const std::string& host, int16_t port,
    ConnectionCodec::Factory* fact,
    ProtocolAccess* access)
{
    // Note that the following logic does not cause a memory leak.
    // The allocated Socket is freed either by the AsynchConnector
    // upon connection failure or by the AsynchIO upon connection
    // shutdown.  The allocated AsynchConnector frees itself when it
    // is no longer needed.

    Socket* socket = new Socket();
    new AsynchConnector (*socket, poller, host, port,
                         boost::bind(&AsynchIOProtocolFactory::established, this, poller, _1, fact, true, access),
                         boost::bind(&ProtocolAccess::closed, access, _1, _2));
}

}} // namespace qpid::sys
