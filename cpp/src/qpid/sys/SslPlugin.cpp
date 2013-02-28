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

#include "qpid/Plugin.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/NameGenerator.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/AsynchIOHandler.h"
#include "qpid/sys/AsynchIO.h"
#include "qpid/sys/ssl/util.h"
#include "qpid/sys/ssl/SslSocket.h"
#include "qpid/sys/SocketAddress.h"
#include "qpid/sys/SystemInfo.h"
#include "qpid/sys/Poller.h"

#include <boost/bind.hpp>
#include <boost/ptr_container/ptr_vector.hpp>

namespace qpid {
namespace sys {

class Timer;

using namespace qpid::sys::ssl;

struct SslServerOptions : ssl::SslOptions
{
    uint16_t port;
    bool clientAuth;
    bool nodict;
    bool multiplex;

    SslServerOptions() : port(5671),
                         clientAuth(false),
                         nodict(false),
                         multiplex(false)
    {
        addOptions()
            ("ssl-port", optValue(port, "PORT"), "Port on which to listen for SSL connections")
            ("ssl-require-client-authentication", optValue(clientAuth), 
             "Forces clients to authenticate in order to establish an SSL connection")
            ("ssl-sasl-no-dict", optValue(nodict), 
             "Disables SASL mechanisms that are vulnerable to passive dictionary-based password attacks");
    }
};

class SslProtocolFactory : public ProtocolFactory {
    boost::ptr_vector<Socket> listeners;
    boost::ptr_vector<AsynchAcceptor> acceptors;
    Timer& brokerTimer;
    uint32_t maxNegotiateTime;
    uint16_t listeningPort;
    const bool tcpNoDelay;
    bool nodict;

  public:
    SslProtocolFactory(const qpid::broker::Broker::Options& opts, const SslServerOptions& options,
                       Timer& timer);
    void accept(Poller::shared_ptr, ConnectionCodec::Factory*);
    void connect(Poller::shared_ptr, const std::string& name, const std::string& host, const std::string& port,
                 ConnectionCodec::Factory*,
                 ConnectFailedCallback);

    uint16_t getPort() const;

  private:
    void establishedIncoming(Poller::shared_ptr, const Socket&, ConnectionCodec::Factory*);
    void establishedOutgoing(Poller::shared_ptr, const Socket&, ConnectionCodec::Factory*, const std::string&);
    void establishedCommon(AsynchIOHandler*, Poller::shared_ptr , const Socket&);
    void connectFailed(const Socket&, int, const std::string&, ConnectFailedCallback);
};


// Static instance to initialise plugin
static struct SslPlugin : public Plugin {
    SslServerOptions options;
    bool nssInitialized;

    Options* getOptions() { return &options; }

    SslPlugin() : nssInitialized(false) {}
    ~SslPlugin() { if (nssInitialized) ssl::shutdownNSS(); }

    void earlyInitialize(Target& target) {
        broker::Broker* broker = dynamic_cast<broker::Broker*>(&target);
        if (broker && !options.certDbPath.empty()) {
            const broker::Broker::Options& opts = broker->getOptions();

            if (opts.port == options.port && // AMQP & AMQPS ports are the same
                opts.port != 0) {
                // The presence of this option is used to signal to the TCP
                // plugin not to start listening on the shared port. The actual
                // value cannot be configured through the command line or config
                // file (other than by setting the ports to the same value)
                // because we are only adding it after option parsing.
                options.multiplex = true;
                options.addOptions()("ssl-multiplex", optValue(options.multiplex), "Allow SSL and non-SSL connections on the same port");
            }
        }
    }

    void initialize(Target& target) {
        QPID_LOG(trace, "Initialising SSL plugin");
        broker::Broker* broker = dynamic_cast<broker::Broker*>(&target);
        // Only provide to a Broker
        if (broker) {
            if (options.certDbPath.empty()) {
                QPID_LOG(notice, "SSL plugin not enabled, you must set --ssl-cert-db to enable it.");
            } else {
                try {
                    ssl::initNSS(options, true);
                    nssInitialized = true;

                    const broker::Broker::Options& opts = broker->getOptions();

                    ProtocolFactory::shared_ptr protocol(
                        static_cast<ProtocolFactory*>(new SslProtocolFactory(opts, options, broker->getTimer())));

                    if (protocol->getPort()!=0 ) {
                        QPID_LOG(notice, "Listening for " <<
                                        (options.multiplex ? "SSL or TCP" : "SSL") <<
                                        " connections on TCP/TCP6 port " <<
                                        protocol->getPort());
                    }

                    broker->registerProtocolFactory("ssl", protocol);
                } catch (const std::exception& e) {
                    QPID_LOG(error, "Failed to initialise SSL plugin: " << e.what());
                }
            }
        }
    }
} sslPlugin;

namespace {
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

SslProtocolFactory::SslProtocolFactory(const qpid::broker::Broker::Options& opts, const SslServerOptions& options,
                                       Timer& timer) :
    brokerTimer(timer),
    maxNegotiateTime(opts.maxNegotiateTime),
    tcpNoDelay(opts.tcpNoDelay),
    nodict(options.nodict)
{
    std::vector<std::string> addresses = expandInterfaces(opts.listenInterfaces);
    if (addresses.empty()) {
        // We specified some interfaces, but couldn't find addresses for them
        QPID_LOG(warning, "SSL: No specified network interfaces found: Not Listening");
        listeningPort = 0;
    }

    for (unsigned i = 0; i<addresses.size(); ++i) {
        QPID_LOG(debug, "Using interface: " << addresses[i]);
        SocketAddress sa(addresses[i], boost::lexical_cast<std::string>(options.port));

        // We must have at least one resolved address
        QPID_LOG(info, "Listening to: " << sa.asString())
        Socket* s = options.multiplex ?
            new SslMuxSocket(options.certName, options.clientAuth) :
            new SslSocket(options.certName, options.clientAuth);
        uint16_t lport = s->listen(sa, opts.connectionBacklog);
        QPID_LOG(debug, "Listened to: " << lport);
        listeners.push_back(s);

        listeningPort = lport;

        // Try any other resolved addresses
        while (sa.nextAddress()) {
            // Hack to ensure that all listening connections are on the same port
            sa.setAddrInfoPort(listeningPort);
            QPID_LOG(info, "Listening to: " << sa.asString())
            Socket* s = options.multiplex ?
                new SslMuxSocket(options.certName, options.clientAuth) :
                new SslSocket(options.certName, options.clientAuth);
            uint16_t lport = s->listen(sa, opts.connectionBacklog);
            QPID_LOG(debug, "Listened to: " << lport);
            listeners.push_back(s);
        }
    }
}

void SslProtocolFactory::establishedIncoming(Poller::shared_ptr poller, const Socket& s,
                                          ConnectionCodec::Factory* f) {
    AsynchIOHandler* async = new AsynchIOHandler(broker::QPID_NAME_PREFIX+s.getFullAddress(), f, false, false);
    establishedCommon(async, poller, s);
}

void SslProtocolFactory::establishedOutgoing(Poller::shared_ptr poller, const Socket& s,
                                             ConnectionCodec::Factory* f, const std::string& name) {
    AsynchIOHandler* async = new AsynchIOHandler(name, f, true, false);
    establishedCommon(async, poller, s);
}

void SslProtocolFactory::establishedCommon(AsynchIOHandler* async, Poller::shared_ptr poller, const Socket& s) {
    if (tcpNoDelay) {
        s.setTcpNoDelay();
        QPID_LOG(info, "Set TCP_NODELAY on connection to " << s.getPeerAddress());
    }

    AsynchIO* aio = AsynchIO::create(
        s,
        boost::bind(&AsynchIOHandler::readbuff, async, _1, _2),
        boost::bind(&AsynchIOHandler::eof, async, _1),
        boost::bind(&AsynchIOHandler::disconnect, async, _1),
        boost::bind(&AsynchIOHandler::closedSocket, async, _1, _2),
        boost::bind(&AsynchIOHandler::nobuffs, async, _1),
        boost::bind(&AsynchIOHandler::idle, async, _1));

    async->init(aio, brokerTimer, maxNegotiateTime);
    aio->start(poller);
}

uint16_t SslProtocolFactory::getPort() const {
    return listeningPort; // Immutable no need for lock.
}

void SslProtocolFactory::accept(Poller::shared_ptr poller,
                                ConnectionCodec::Factory* fact) {
    for (unsigned i = 0; i<listeners.size(); ++i) {
        acceptors.push_back(
            AsynchAcceptor::create(listeners[i],
                            boost::bind(&SslProtocolFactory::establishedIncoming, this, poller, _1, fact)));
        acceptors[i].start(poller);
    }
}

void SslProtocolFactory::connectFailed(
    const Socket& s, int ec, const std::string& emsg,
    ConnectFailedCallback failedCb)
{
    failedCb(ec, emsg);
    s.close();
    delete &s;
}

void SslProtocolFactory::connect(
    Poller::shared_ptr poller,
    const std::string& name,
    const std::string& host, const std::string& port,
    ConnectionCodec::Factory* fact,
    ConnectFailedCallback failed)
{
    // Note that the following logic does not cause a memory leak.
    // The allocated Socket is freed either by the SslConnector
    // upon connection failure or by the SslIoHandle upon connection
    // shutdown.  The allocated SslConnector frees itself when it
    // is no longer needed.

    Socket* socket = new qpid::sys::ssl::SslSocket();
    try {
    AsynchConnector* c = AsynchConnector::create(
        *socket,
        host,
        port,
        boost::bind(&SslProtocolFactory::establishedOutgoing,
                    this, poller, _1, fact, name),
        boost::bind(&SslProtocolFactory::connectFailed,
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
