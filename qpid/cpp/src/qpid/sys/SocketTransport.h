#ifndef QPID_SYS_SOCKETTRANSPORT_H
#define QPID_SYS_SOCKETTRANSPORT_H

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

#include "qpid/sys/IntegerTypes.h"
#include "qpid/sys/ConnectionCodec.h"
#include <boost/ptr_container/ptr_vector.hpp>
#include <boost/function.hpp>

namespace qpid {
namespace sys {

class AsynchAcceptor;
class Poller;
class Timer;
class Socket;
typedef boost::function0<Socket*> SocketFactory;
typedef boost::function3<void, boost::shared_ptr<Poller>, const Socket&, ConnectionCodec::Factory*> EstablishedCallback;

struct SocketTransportOptions {
    bool tcpNoDelay;
    bool nodict;
    uint32_t maxNegotiateTime;

    SocketTransportOptions(bool t, bool d, uint32_t m) :
        tcpNoDelay(t),
        nodict(d),
        maxNegotiateTime(m)
    {}
};

class SocketAcceptor : public TransportAcceptor {
    boost::ptr_vector<Socket> listeners;
    boost::ptr_vector<AsynchAcceptor> acceptors;
    Timer& timer;
    SocketTransportOptions options;
    const EstablishedCallback established;

public:
    SocketAcceptor(bool tcpNoDelay, bool nodict, uint32_t maxNegotiateTime, Timer& timer);
    SocketAcceptor(bool tcpNoDelay, bool nodict, uint32_t maxNegotiateTime, Timer& timer, const EstablishedCallback& established);

    // Create sockets from list of interfaces and listen to them
    uint16_t listen(const std::vector<std::string>& interfaces, uint16_t port, int backlog, const SocketFactory& factory);

    // Import sockets that are already being listened to
    void addListener(Socket* socket);

    void accept(boost::shared_ptr<Poller> poller, ConnectionCodec::Factory* f);
};

class SocketConnector : public TransportConnector {
    Timer& timer;
    const SocketFactory factory;
    SocketTransportOptions options;

public:
    SocketConnector(bool tcpNoDelay, bool nodict, uint32_t maxNegotiateTime, Timer& timer, const SocketFactory& factory);

    void connect(boost::shared_ptr<Poller> poller,
                 const std::string& name,
                 const std::string& host, const std::string& port,
                 ConnectionCodec::Factory* f,
                 ConnectFailedCallback failed);
};

}}

#endif
