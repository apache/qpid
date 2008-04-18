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
#include "AsynchIOHandler.h"
#include "AsynchIO.h"

#include "qpid/Plugin.h"
#include "qpid/broker/Broker.h"
#include "qpid/log/Statement.h"

#include <boost/bind.hpp>
#include <memory>

namespace qpid {
namespace sys {

class AsynchIOAcceptor : public Acceptor {
    Socket listener;
    const uint16_t listeningPort;
    std::auto_ptr<AsynchAcceptor> acceptor;

  public:
    AsynchIOAcceptor(int16_t port, int backlog);
    void run(Poller::shared_ptr, ConnectionCodec::Factory*);
    void connect(Poller::shared_ptr, const std::string& host, int16_t port, ConnectionCodec::Factory*);

    uint16_t getPort() const;
    std::string getHost() const;

  private:
    void accepted(Poller::shared_ptr, const Socket&, ConnectionCodec::Factory*);
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
            Acceptor::shared_ptr acceptor(new AsynchIOAcceptor(opts.port, opts.connectionBacklog));
            QPID_LOG(info, "Listening on TCP port " << acceptor->getPort());
            broker->registerAccepter(acceptor);
        }
    }
} acceptor;

AsynchIOAcceptor::AsynchIOAcceptor(int16_t port, int backlog) :
    listeningPort(listener.listen(port, backlog))
{}

void AsynchIOAcceptor::accepted(Poller::shared_ptr poller, const Socket& s, ConnectionCodec::Factory* f) {
    AsynchIOHandler* async = new AsynchIOHandler(s.getPeerAddress(), f);
    AsynchIO* aio = new AsynchIO(s,
                                 boost::bind(&AsynchIOHandler::readbuff, async, _1, _2),
                                 boost::bind(&AsynchIOHandler::eof, async, _1),
                                 boost::bind(&AsynchIOHandler::disconnect, async, _1),
                                 boost::bind(&AsynchIOHandler::closedSocket, async, _1, _2),
                                 boost::bind(&AsynchIOHandler::nobuffs, async, _1),
                                 boost::bind(&AsynchIOHandler::idle, async, _1));
    async->init(aio, 4);
    aio->start(poller);
}


uint16_t AsynchIOAcceptor::getPort() const {
    return listeningPort; // Immutable no need for lock.
}

std::string AsynchIOAcceptor::getHost() const {
    return listener.getSockname();
}

void AsynchIOAcceptor::run(Poller::shared_ptr poller, ConnectionCodec::Factory* fact) {
    acceptor.reset(
        new AsynchAcceptor(listener,
                           boost::bind(&AsynchIOAcceptor::accepted, this, poller, _1, fact)));
    acceptor->start(poller);
}
    
void AsynchIOAcceptor::connect(
    Poller::shared_ptr poller,
    const std::string& host, int16_t port,
    ConnectionCodec::Factory* f)
{
    Socket* socket = new Socket();//Should be deleted by handle when socket closes
    socket->connect(host, port);
    AsynchIOHandler* async = new AsynchIOHandler(socket->getPeerAddress(), f);
    async->setClient();
    AsynchIO* aio = new AsynchIO(*socket,
                                 boost::bind(&AsynchIOHandler::readbuff, async, _1, _2),
                                 boost::bind(&AsynchIOHandler::eof, async, _1),
                                 boost::bind(&AsynchIOHandler::disconnect, async, _1),
                                 boost::bind(&AsynchIOHandler::closedSocket, async, _1, _2),
                                 boost::bind(&AsynchIOHandler::nobuffs, async, _1),
                                 boost::bind(&AsynchIOHandler::idle, async, _1));
    async->init(aio, 4);
    aio->start(poller);
}

}} // namespace qpid::sys
