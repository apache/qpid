#ifndef TESTS_BROKERFIXTURE_H
#define TESTS_BROKERFIXTURE_H

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

#include "qpid/broker/Broker.h"
#include "qpid/broker/BrokerOptions.h"
#include "qpid/client/Connection.h"
#include "qpid/client/ConnectionImpl.h"
#include "qpid/client/Session.h"
#include "qpid/client/SubscriptionManager.h"
#include "qpid/client/LocalQueue.h"
#include "qpid/log/Logger.h"
#include "qpid/log/Options.h"
#include "qpid/sys/Thread.h"
#include <boost/noncopyable.hpp>

namespace qpid {
namespace tests {

/**
 * A fixture with an in-process broker.
 */
struct  BrokerFixture : private boost::noncopyable {
    typedef qpid::broker::Broker Broker;
    typedef boost::intrusive_ptr<Broker> BrokerPtr;
    typedef qpid::broker::BrokerOptions BrokerOptions;
    typedef std::vector<std::string> Args;

    BrokerPtr broker;
    BrokerOptions opts;
    uint16_t port;
    qpid::sys::Thread brokerThread;

    BrokerFixture(const Args& args=Args(), const BrokerOptions& opts0=BrokerOptions(),
                  bool isExternalPort_=false, uint16_t externalPort_=0) :
        opts(opts0)
    {
        init(args, isExternalPort_, externalPort_);
    }

    BrokerFixture(const BrokerOptions& opts0,
                  bool isExternalPort_=false, uint16_t externalPort_=0) :
        opts(opts0)
    {
        init(Args(), isExternalPort_, externalPort_);
    }

    void shutdownBroker() {
        if (broker) {
            broker->shutdown();
            brokerThread.join();
            broker = BrokerPtr();
        }
    }

    ~BrokerFixture() {  shutdownBroker(); }

    /** Open a connection to the broker. */
    void open(qpid::client::Connection& c) {
        c.open("localhost", getPort());
    }

    uint16_t getPort() { return port; }

  private:
    void init(const Args& args, bool isExternalPort=false, uint16_t externalPort=0)
    {
        // Keep the tests quiet unless logging env. vars have been set by user.
        if (!::getenv("QPID_LOG_ENABLE") && !::getenv("QPID_TRACE")) {
            qpid::log::Options logOpts;
            logOpts.selectors.clear();
            logOpts.deselectors.clear();
            logOpts.selectors.push_back("error+");
            qpid::log::Logger::instance().configure(logOpts);
        }
        // Default options, may be over-ridden when we parse args.
        opts.port=0;
        opts.listenInterfaces.push_back("127.0.0.1");
        opts.workerThreads=1;
        opts.dataDir="";
        opts.auth=false;

        // Argument parsing
        if (args.size() > 0) {
            std::vector<const char*> argv(args.size());
            for (size_t i = 0; i<args.size(); ++i)
                argv[i] = args[i].c_str();
            Plugin::addOptions(opts);
            opts.parse(argv.size(), &argv[0]);
        }
        broker = Broker::create(opts);
        // TODO aconway 2007-12-05: At one point BrokerFixture
        // tests could hang in Connection ctor if the following
        // line is removed. This may not be an issue anymore.
        broker->accept();
        if (isExternalPort) port = externalPort;
        else port = broker->getPort(qpid::broker::Broker::TCP_TRANSPORT);
        brokerThread = qpid::sys::Thread(*broker);
    };
};

/** Connection that opens in its constructor */
struct LocalConnection : public qpid::client::Connection {
    LocalConnection(uint16_t port) { open("localhost", port); }
    LocalConnection(const qpid::client::ConnectionSettings& s) { open(s); }
    ~LocalConnection() { close(); }
};

/** Convenience class to create and open a connection and session
 * and some related useful objects.
 */
template <class ConnectionType=LocalConnection, class SessionType=qpid::client::Session>
struct ClientT {
    ConnectionType connection;
    SessionType session;
    qpid::client::SubscriptionManager subs;
    qpid::client::LocalQueue lq;
    std::string name;

    ClientT(uint16_t port, const std::string& name_=std::string(), int timeout=0)
        : connection(port), session(connection.newSession(name_,timeout)), subs(session), name(name_) {}
    ClientT(const qpid::client::ConnectionSettings& settings, const std::string& name_=std::string(), int timeout=0)
        : connection(settings), session(connection.newSession(name_, timeout)), subs(session), name(name_) {}

    ~ClientT() { close(); }
    void close() { session.close(); connection.close(); }
};

typedef ClientT<> Client;

/**
 * A BrokerFixture and ready-connected BrokerFixture::Client all in one.
 */
template <class ConnectionType, class SessionType=qpid::client::Session>
struct  SessionFixtureT : BrokerFixture, ClientT<ConnectionType,SessionType> {

    SessionFixtureT(const BrokerOptions& opts=BrokerOptions()) :
        BrokerFixture(BrokerFixture::Args(), opts),
        ClientT<ConnectionType,SessionType>(getPort())
    {}

};

typedef SessionFixtureT<LocalConnection> SessionFixture;

}} // namespace qpid::tests

#endif  /*!TESTS_BROKERFIXTURE_H*/
