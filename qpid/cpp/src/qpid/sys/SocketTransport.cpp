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
// Turn off unintialised warnings as errors when compiling under Red Enterprise Linux 6
// as an unitialised variable warning is unavoidable there.
#if __GNUC__ == 4 && __GNUC_MINOR__ == 4
#pragma GCC diagnostic warning "-Wuninitialized"
#endif

#include "qpid/sys/SocketTransport.h"

#include "qpid/broker/NameGenerator.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/AsynchIOHandler.h"
#include "qpid/sys/AsynchIO.h"
#include "qpid/sys/Socket.h"
#include "qpid/sys/SocketAddress.h"
#include "qpid/sys/SystemInfo.h"

#include <boost/bind.hpp>
#include <boost/lexical_cast.hpp>

namespace qpid {
namespace sys {

namespace {
    void establishedCommon(
        AsynchIOHandler* async,
        boost::shared_ptr<Poller> poller, const SocketTransportOptions& opts, Timer* timer,
        const Socket& s)
    {
        if (opts.tcpNoDelay) {
            s.setTcpNoDelay();
            QPID_LOG(debug, "Set TCP_NODELAY on connection to " << s.getPeerAddress());
        }

        AsynchIO* aio = AsynchIO::create
        (s,
         boost::bind(&AsynchIOHandler::readbuff, async, _1, _2),
         boost::bind(&AsynchIOHandler::eof, async, _1),
         boost::bind(&AsynchIOHandler::disconnect, async, _1),
         boost::bind(&AsynchIOHandler::closedSocket, async, _1, _2),
         boost::bind(&AsynchIOHandler::nobuffs, async, _1),
         boost::bind(&AsynchIOHandler::idle, async, _1));

        async->init(aio, *timer, opts.maxNegotiateTime);
        aio->start(poller);
    }

    void establishedIncoming(
        boost::shared_ptr<Poller> poller, const SocketTransportOptions& opts, Timer* timer,
        const Socket& s, ConnectionCodec::Factory* f)
    {
        AsynchIOHandler* async = new AsynchIOHandler(broker::QPID_NAME_PREFIX+s.getFullAddress(), f, false, opts.nodict);
        establishedCommon(async, poller, opts, timer, s);
    }

    void establishedOutgoing(
        boost::shared_ptr<Poller> poller, const SocketTransportOptions& opts, Timer* timer,
        const Socket& s, ConnectionCodec::Factory* f, const std::string& name)
    {
        AsynchIOHandler* async = new AsynchIOHandler(name, f, true, opts.nodict);
        establishedCommon(async, poller, opts, timer, s);
    }

    void connectFailed(
        const Socket& s, int ec, const std::string& emsg,
        SocketConnector::ConnectFailedCallback failedCb)
    {
        failedCb(ec, emsg);
        s.close();
        delete &s;
    }

    // Expand list of Interfaces and addresses to a list of addresses
    std::vector<std::string> expandInterfaces(const std::vector<std::string>& interfaces) {
        std::vector<std::string> addresses;
        // If there are no specific interfaces listed use a single "" to listen on every interface
        if (interfaces.empty()) {
            addresses.push_back("");
            return addresses;
        }
        for (unsigned i = 0; i < interfaces.size(); ++i) {
            const std::string& interface = interfaces[i];
            if (!(SystemInfo::getInterfaceAddresses(interface, addresses))) {
                // We don't have an interface of that name -
                // Check for IPv6 ('[' ']') brackets and remove them
                // then pass to be looked up directly
                if (interface[0]=='[' && interface[interface.size()-1]==']') {
                    addresses.push_back(interface.substr(1, interface.size()-2));
                } else {
                    addresses.push_back(interface);
                }
            }
        }
        return addresses;
    }
}

SocketAcceptor::SocketAcceptor(bool tcpNoDelay, bool nodict, uint32_t maxNegotiateTime, Timer& timer0) :
    timer(timer0),
    options(tcpNoDelay, nodict, maxNegotiateTime),
    established(boost::bind(&establishedIncoming, _1, options, &timer, _2, _3))
{}

SocketAcceptor::SocketAcceptor(bool tcpNoDelay, bool nodict, uint32_t maxNegotiateTime, Timer& timer0, const EstablishedCallback& established0) :
    timer(timer0),
    options(tcpNoDelay, nodict, maxNegotiateTime),
    established(established0)
{}

void SocketAcceptor::addListener(Socket* socket)
{
    listeners.push_back(socket);
}

uint16_t SocketAcceptor::listen(const std::vector<std::string>& interfaces, uint16_t port, int backlog, const SocketFactory& factory)
{
    std::vector<std::string> addresses = expandInterfaces(interfaces);
    std::string sport(boost::lexical_cast<std::string>(port));

    if (addresses.empty()) {
        // We specified some interfaces, but couldn't find addresses for them
        QPID_LOG(warning, "TCP/TCP6: No specified network interfaces found: Not Listening");
        return 0;
    }

    int listeningPort = 0;
    for (unsigned i = 0; i<addresses.size(); ++i) {
        QPID_LOG(debug, "Using interface: " << addresses[i]);
        SocketAddress sa(addresses[i], sport);

        do {
        try {
            // If we were told to figure out the port then only allow listening to one address
            if (port==0 && listeningPort!=0) {
                // Print warning if the user specified more than one interface
                QPID_LOG(warning, "Specified port=0: Only listened to: " << sa.asString());
                return listeningPort;
            }

            QPID_LOG(info, "Listening to: " << sa.asString());
            std::auto_ptr<Socket> s(factory());
            uint16_t lport = s->listen(sa, backlog);
            QPID_LOG(debug, "Listened to: " << lport);
            addListener(s.release());

            if (listeningPort==0) listeningPort = lport;
        } catch (std::exception& e) {
            QPID_LOG(warning, "Couldn't listen to: " << sa.asString() << ": " << e.what());
        }
        } while (sa.nextAddress());
    }
    if (listeningPort==0) {
        throw Exception("Couldn't find any network address to listen to");
    }
    return listeningPort;
}

void SocketAcceptor::accept(boost::shared_ptr<Poller> poller, ConnectionCodec::Factory* f)
{
    for (unsigned i = 0; i<listeners.size(); ++i) {
        acceptors.push_back(
            AsynchAcceptor::create(listeners[i], boost::bind(established, poller, _1, f)));
        acceptors[i].start(poller);
    }
}

SocketConnector::SocketConnector(bool tcpNoDelay, bool nodict, uint32_t maxNegotiateTime, Timer& timer0, const SocketFactory& factory0) :
    timer(timer0),
    factory(factory0),
    options(tcpNoDelay, nodict, maxNegotiateTime)
{}

void SocketConnector::connect(
    boost::shared_ptr<Poller> poller,
    const std::string& name,
    const std::string& host, const std::string& port,
    ConnectionCodec::Factory* fact,
    ConnectFailedCallback failed)
{
    // Note that the following logic does not cause a memory leak.
    // The allocated Socket is freed either by the AsynchConnector
    // upon connection failure or by the AsynchIO upon connection
    // shutdown.  The allocated AsynchConnector frees itself when it
    // is no longer needed.
    Socket* socket = factory();
    try {
        AsynchConnector* c = AsynchConnector::create(
            *socket,
            host,
            port,
            boost::bind(&establishedOutgoing, poller, options, &timer, _1, fact, name),
            boost::bind(&connectFailed, _1, _2, _3, failed));
        c->start(poller);
    } catch (std::exception&) {
        // TODO: Design question - should we do the error callback and also throw?
        int errCode = socket->getError();
        connectFailed(*socket, errCode, strError(errCode), failed);
        throw;
    }
}

}} // namespace qpid::sys
