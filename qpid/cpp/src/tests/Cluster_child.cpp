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

// Child process for the Cluster test suite multi-process tests.

#include "Cluster.h"
#include "test_tools.h"

using namespace std;
using namespace qpid;
using namespace qpid::cluster;
using namespace qpid::framing;
using namespace qpid::sys;
using namespace qpid::log;

static const ProtocolVersion VER;

/** Chlid part of Cluster::clusterTwo test */
void clusterTwo() {
    TestCluster cluster("clusterTwo", "amqp:child:2");
    BOOST_REQUIRE(cluster.received.waitFor(1)); // Frame from parent.
    BOOST_CHECK(cluster.received[0].isIncoming);
    BOOST_CHECK_TYPEID_EQUAL(ChannelPingBody, *cluster.received[0].frame.getBody());
    BOOST_CHECK_EQUAL(2u, cluster.size()); // Me and parent

    AMQFrame frame(VER, 1, new ChannelOkBody(VER));
    SessionFrame sf(cluster.received[0].uuid, frame, false);
    cluster.handle(sf);
    BOOST_REQUIRE(cluster.received.waitFor(2));
    BOOST_CHECK(!cluster.received[1].isIncoming);
    BOOST_CHECK_TYPEID_EQUAL(ChannelOkBody, *cluster.received[1].frame.getBody());
} 

int test_main(int, char**) {
    clusterTwo();
    return 0;
}

