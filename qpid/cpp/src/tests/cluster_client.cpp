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

#include "unit_test.h"
#include "BrokerFixture.h"
#include "qpid/client/Session.h"

#include <fstream>
#include <vector>
#include <functional>

QPID_AUTO_TEST_SUITE(cluster_clientTestSuite)

using namespace qpid;
using namespace qpid::client;
using namespace qpid::framing;
using namespace qpid::client::arg;
using framing::TransferContent;
using std::vector;
using std::string;
using std::ifstream;
using std::ws;

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

    Session broker0 = cluster[0]->newSession(ASYNC);
    broker0.exchangeDeclare(exchange="ex");
    broker0.queueDeclare(queue="q");
    broker0.queueBind(exchange="ex", queue="q", routingKey="key");
    broker0.close();
    
    for (size_t i = 1; i < cluster.size(); ++i) {
        Session s = cluster[i]->newSession(ASYNC);
        s.messageTransfer(content=TransferContent("data", "key", "ex"));
        s.messageSubscribe(queue="q", destination="q");
        s.messageFlow(destination="q", unit=0, value=1);//messages
        FrameSet::shared_ptr msg = s.get();
        BOOST_CHECK(msg->isA<MessageTransferBody>());
        BOOST_CHECK_EQUAL(string("data"), msg->getContent());
        s.getExecution().completed(msg->getId(), true, true);
        cluster[i]->close();
    }    
}

QPID_AUTO_TEST_SUITE_END()
