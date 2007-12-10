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

#include "qpid/sys/Thread.h"
#include "qpid/broker/Broker.h"
#include "qpid/client/Connection.h"
#include "qpid/client/ConnectionImpl.h"
#include "qpid/client/Session_0_10.h"
#include "qpid/client/SubscriptionManager.h"

/**
 * A fixture to create an in-process broker and connect to it for tests.
 */
struct BrokerFixture {
    typedef qpid::broker::Broker Broker;
    typedef boost::shared_ptr<Broker> BrokerPtr;

    struct OpenConnection : public qpid::client::Connection {
        OpenConnection(int port) { open("localhost", port); }
    };
    
    BrokerPtr broker;
    qpid::sys::Thread brokerThread;
    OpenConnection connection;
    qpid::client::Session_0_10 session;
    qpid::client::SubscriptionManager subs;
    qpid::client::LocalQueue lq;
        
    BrokerPtr newBroker() {
        Broker::Options opts;
        opts.port=0;
        opts.workerThreads=1;
        BrokerPtr b=Broker::create(opts);
        // TODO aconway 2007-12-05: Without the following line
        // the test can hang in the connection ctor. This is
        // a race condition that should be fixed.
        b->getPort(); 
        return b;
    };

    BrokerFixture() : broker(newBroker()),
                      brokerThread(*broker),
                      connection(broker->getPort()),
                      session(connection.newSession()),
                      subs(session)
    {}

    ~BrokerFixture() {
        connection.close();
        broker->shutdown();
        brokerThread.join();
    }

    /** Open a connection to the local broker */
    void open(qpid::client::Connection& c) {
        c.open("localhost", broker->getPort());
    }
};

#endif  /*!TESTS_BROKERFIXTURE_H*/
