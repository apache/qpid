/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "qpid/client/Connection.h"
#include "qpid/shared_ptr.h"

#include "unit_test.h"

#include <fstream>
#include <vector>
#include <functional>


QPID_AUTO_TEST_SUITE(cluster_clientTestSuite)

using namespace std;
using namespace qpid;
using namespace qpid::client;

struct ClusterConnections : public vector<shared_ptr<Connection> > {
    ClusterConnections() {
        ifstream portfile("cluster.ports");
        BOOST_REQUIRE(portfile.good());
        portfile >> ws;
        while (portfile.good()) {
            uint16_t port;
            portfile >> port >> ws;
            push_back(make_shared_ptr(new Connection(port)));
            back()->open("localhost", port);
        }
        BOOST_REQUIRE(size() > 1);
    }

    ~ClusterConnections() {
        for (iterator i = begin(); i != end(); ++i ){
            (*i)->close();
        }
    }
};

BOOST_AUTO_TEST_CASE(testWiringReplication) {
    // Declare on one broker, use on others.
    ClusterConnections cluster;
    BOOST_REQUIRE(cluster.size() > 1);

    Exchange fooEx("FooEx", Exchange::TOPIC_EXCHANGE);
    Queue fooQ("FooQ");
    
    Channel broker0;
    cluster[0]->openChannel(broker0);
    broker0.declareExchange(fooEx);
    broker0.declareQueue(fooQ);
    broker0.bind(fooEx, fooQ, "FooKey");
    broker0.close();
    
    for (size_t i = 1; i < cluster.size(); ++i) {
        Channel ch;
        cluster[i]->openChannel(ch);
        ch.publish(Message("hello"), fooEx, "FooKey");
        Message m;
        BOOST_REQUIRE(ch.get(m, fooQ));
        BOOST_REQUIRE_EQUAL(m.getData(), "hello");
        ch.close();
    }
}

QPID_AUTO_TEST_SUITE_END()
