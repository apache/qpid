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

#include "SocketProxy.h"
#include "qpid/sys/Thread.h"
#include "qpid/broker/Broker.h"
#include "qpid/client/Connection.h"
#include "qpid/client/ConnectionImpl.h"
#include "qpid/client/Session.h"
#include "qpid/client/SubscriptionManager.h"

/**
 * A fixture with an in-process broker.
 */
struct  BrokerFixture {
    typedef qpid::broker::Broker Broker;
    typedef boost::shared_ptr<Broker> BrokerPtr;

    BrokerPtr broker;
    qpid::sys::Thread brokerThread;

    BrokerFixture() {
        Broker::Options opts;
        opts.port=0;
        // Management doesn't play well with multiple in-process brokers.
        opts.enableMgmt=false;  
        opts.workerThreads=1;
        opts.dataDir="";
        opts.auth=false;
        broker = Broker::create(opts);
        // TODO aconway 2007-12-05: At one point BrokerFixture
        // tests could hang in Connection ctor if the following
        // line is removed. This may not be an issue anymore.
        broker->getPort();
        brokerThread = qpid::sys::Thread(*broker);
    };

    ~BrokerFixture() {
        broker->shutdown();
        brokerThread.join();
    }

    /** Open a connection to the broker. */
    void open(qpid::client::Connection& c) {
        c.open("localhost", broker->getPort());
    }
};

struct LocalConnection : public qpid::client::Connection {
    LocalConnection(uint16_t port) { open("localhost", port); }
};

/** A local client connection via a socket proxy. */
struct ProxyConnection : public qpid::client::Connection {
    SocketProxy proxy;
    ProxyConnection(int brokerPort) : proxy(brokerPort) {
        open("localhost", proxy.getPort());
    }
    ~ProxyConnection() { close(); }
};

/**
 * A BrokerFixture with open Connection, Session and
 * SubscriptionManager and LocalQueue for convenience.
 */
template <class ConnectionType>
struct  SessionFixtureT : BrokerFixture {
    ConnectionType connection;
    qpid::client::Session session;
    qpid::client::SubscriptionManager subs;
    qpid::client::LocalQueue lq;

    SessionFixtureT() : connection(broker->getPort()),
                        session(connection.newSession("SessionFixture")),
                        subs(session)
    {}

    ~SessionFixtureT() {
        connection.close();
    }
};

typedef SessionFixtureT<LocalConnection> SessionFixture;
typedef SessionFixtureT<ProxyConnection> ProxySessionFixture;


#endif  /*!TESTS_BROKERFIXTURE_H*/
