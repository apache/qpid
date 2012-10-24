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
#include "qpid/sys/ssl/check.h"
#include "qpid/sys/ssl/util.h"
#include "qpid/sys/ssl/SslHandler.h"
#include "qpid/sys/AsynchIOHandler.h"
#include "qpid/sys/AsynchIO.h"
#include "qpid/sys/ssl/SslIo.h"
#include "qpid/sys/ssl/SslSocket.h"
#include "qpid/sys/SocketAddress.h"
#include "qpid/broker/Broker.h"
#include "qpid/log/Statement.h"

#include <boost/bind.hpp>
#include <memory>


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

template <class T>
class SslProtocolFactoryTmpl : public ProtocolFactory {
  private:

    Timer& brokerTimer;
    uint32_t maxNegotiateTime;
    const bool tcpNoDelay;
    T listener;
    const uint16_t listeningPort;
    std::auto_ptr<SslAcceptor> acceptor;
    bool nodict;

  public:
    SslProtocolFactoryTmpl(const std::string& host, const std::string& port,
                           const SslServerOptions&,
                           int backlog, bool nodelay,
                           Timer& timer, uint32_t maxTime);
    void accept(Poller::shared_ptr, ConnectionCodec::Factory*);
    void connect(Poller::shared_ptr, const std::string& host, const std::string& port,
                 ConnectionCodec::Factory*,
                 boost::function2<void, int, std::string> failed);

    uint16_t getPort() const;

  private:
    void established(Poller::shared_ptr, const Socket&, ConnectionCodec::Factory*,
                     bool isClient);
};

typedef SslProtocolFactoryTmpl<SslSocket> SslProtocolFactory;
typedef SslProtocolFactoryTmpl<SslMuxSocket> SslMuxProtocolFactory;


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

                    ProtocolFactory::shared_ptr protocol(options.multiplex ?
                        static_cast<ProtocolFactory*>(new SslMuxProtocolFactory("", boost::lexical_cast<std::string>(options.port),
                                                                                options,
                                                                                opts.connectionBacklog,
                                                                                opts.tcpNoDelay,
                                                                                broker->getTimer(), opts.maxNegotiateTime)) :
                        static_cast<ProtocolFactory*>(new SslProtocolFactory("", boost::lexical_cast<std::string>(options.port),
                                                                             options,
                                                                             opts.connectionBacklog,
                                                                             opts.tcpNoDelay,
                                                                             broker->getTimer(), opts.maxNegotiateTime)));
                    QPID_LOG(notice, "Listening for " <<
                                     (options.multiplex ? "SSL or TCP" : "SSL") <<
                                     " connections on TCP port " <<
                                     protocol->getPort());
                    broker->registerProtocolFactory("ssl", protocol);
                } catch (const std::exception& e) {
                    QPID_LOG(error, "Failed to initialise SSL plugin: " << e.what());
                }
            }
        }
    }
} sslPlugin;

template <class T>
SslProtocolFactoryTmpl<T>::SslProtocolFactoryTmpl(const std::string& host, const std::string& port,
                                                  const SslServerOptions& options,
                                                  int backlog, bool nodelay,
                                                  Timer& timer, uint32_t maxTime) :
    brokerTimer(timer),
    maxNegotiateTime(maxTime),
    tcpNoDelay(nodelay),
    listener(options.certName, options.clientAuth),
    listeningPort(listener.listen(SocketAddress(host, port), backlog)),
    nodict(options.nodict)
{}

void SslEstablished(Poller::shared_ptr poller, const qpid::sys::SslSocket& s,
                    ConnectionCodec::Factory* f, bool isClient,
                    Timer& timer, uint32_t maxTime, bool tcpNoDelay, bool nodict) {
    qpid::sys::ssl::SslHandler* async = new qpid::sys::ssl::SslHandler(s.getFullAddress(), f, nodict);

    if (tcpNoDelay) {
        s.setTcpNoDelay();
        QPID_LOG(info, "Set TCP_NODELAY on connection to " << s.getPeerAddress());
    }

    if (isClient) {
        async->setClient();
    }

    qpid::sys::ssl::SslIO* aio = new qpid::sys::ssl::SslIO(s,
                                 boost::bind(&qpid::sys::ssl::SslHandler::readbuff, async, _1, _2),
                                 boost::bind(&qpid::sys::ssl::SslHandler::eof, async, _1),
                                 boost::bind(&qpid::sys::ssl::SslHandler::disconnect, async, _1),
                                 boost::bind(&qpid::sys::ssl::SslHandler::closedSocket, async, _1, _2),
                                 boost::bind(&qpid::sys::ssl::SslHandler::nobuffs, async, _1),
                                 boost::bind(&qpid::sys::ssl::SslHandler::idle, async, _1));

    async->init(aio,timer, maxTime);
    aio->start(poller);
}

template <>
void SslProtocolFactory::established(Poller::shared_ptr poller, const Socket& s,
                                     ConnectionCodec::Factory* f, bool isClient) {
    const SslSocket *sslSock = dynamic_cast<const SslSocket*>(&s);

    SslEstablished(poller, *sslSock, f, isClient, brokerTimer, maxNegotiateTime, tcpNoDelay, nodict);
}

template <class T>
uint16_t SslProtocolFactoryTmpl<T>::getPort() const {
    return listeningPort; // Immutable no need for lock.
}

template <class T>
void SslProtocolFactoryTmpl<T>::accept(Poller::shared_ptr poller,
                                       ConnectionCodec::Factory* fact) {
    acceptor.reset(
        new SslAcceptor(listener,
                        boost::bind(&SslProtocolFactoryTmpl<T>::established,
                                    this, poller, _1, fact, false)));
    acceptor->start(poller);
}

template <>
void SslMuxProtocolFactory::established(Poller::shared_ptr poller, const Socket& s,
                                        ConnectionCodec::Factory* f, bool isClient) {
    const SslSocket *sslSock = dynamic_cast<const SslSocket*>(&s);

    if (sslSock) {
        SslEstablished(poller, *sslSock, f, isClient, brokerTimer, maxNegotiateTime, tcpNoDelay, nodict);
        return;
    }

    AsynchIOHandler* async = new AsynchIOHandler(s.getFullAddress(), f, false);

    if (tcpNoDelay) {
        s.setTcpNoDelay();
        QPID_LOG(info, "Set TCP_NODELAY on connection to " << s.getPeerAddress());
    }

    if (isClient) {
        async->setClient();
    }
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

template <class T>
void SslProtocolFactoryTmpl<T>::connect(
    Poller::shared_ptr poller,
    const std::string& host, const std::string& port,
    ConnectionCodec::Factory* fact,
    ConnectFailedCallback failed)
{
    // Note that the following logic does not cause a memory leak.
    // The allocated Socket is freed either by the SslConnector
    // upon connection failure or by the SslIoHandle upon connection
    // shutdown.  The allocated SslConnector frees itself when it
    // is no longer needed.

    qpid::sys::ssl::SslSocket* socket = new qpid::sys::ssl::SslSocket();
    new SslConnector(*socket, poller, host, port,
                     boost::bind(&SslProtocolFactoryTmpl<T>::established, this, poller, _1, fact, true),
                     failed);
}

}} // namespace qpid::sys
