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


static const ProtocolVersion VER;

/** Chlid part of Cluster::clusterTwo test */
void clusterTwo() {
    Cluster cluster("Test", "amqp::2");
    TestClusterHandler handler(cluster);
    BOOST_REQUIRE(handler.waitFrames(1));
    BOOST_CHECK_TYPEID_EQUAL(ChannelOkBody, *handler[0].getBody());
    AMQFrame frame(VER, 1, new BasicGetOkBody(VER));
    cluster.handle(frame);
    BOOST_REQUIRE(handler.waitFrames(2));
    BOOST_CHECK_TYPEID_EQUAL(BasicGetOkBody, *handler[1].getBody());
} 

int test_main(int, char**) {
    clusterTwo();
    return 0;
}

