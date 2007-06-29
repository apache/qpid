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

#define BOOST_AUTO_TEST_MAIN    // Must come before #include<boost/test/*>
#include <boost/test/auto_unit_test.hpp>
#include "test_tools.h"
#include "Cluster.h"
#include "qpid/framing/ChannelOkBody.h"
#include "qpid/framing/BasicGetOkBody.h"


static const ProtocolVersion VER;

/** Verify membership ind a cluster with one member. */
BOOST_AUTO_TEST_CASE(clusterOne) {
    Cluster cluster("Test", "amqp:one:1");
    TestClusterHandler handler(cluster);
    AMQFrame frame(VER, 1, new ChannelOkBody(VER));
    cluster.handle(frame);
    BOOST_REQUIRE(handler.waitFrames(1));
    BOOST_CHECK_EQUAL(1u, cluster.size());
    Cluster::MemberList members = cluster.getMembers();
    BOOST_CHECK_EQUAL(1u, members.size());
    BOOST_REQUIRE_EQUAL(members.front()->url, "amqp:one:1");
    BOOST_CHECK_EQUAL(1u, handler.size());
    BOOST_CHECK_TYPEID_EQUAL(ChannelOkBody, *handler[0].getBody());
}

/** Fork a process to verify membership in a cluster with two members */
BOOST_AUTO_TEST_CASE(clusterTwo) {
    pid_t pid=fork();
    BOOST_REQUIRE(pid >= 0);
    if (pid) {                  // Parent see Cluster_child.cpp for child.
        Cluster cluster("Test", "amqp::1");
        TestClusterHandler handler(cluster);
        BOOST_REQUIRE(handler.waitMembers(2));

        // Exchange frames with child.
        AMQFrame frame(VER, 1, new ChannelOkBody(VER));
        cluster.handle(frame);
        BOOST_REQUIRE(handler.waitFrames(2));
        BOOST_CHECK_TYPEID_EQUAL(ChannelOkBody, *handler[0].getBody());
        BOOST_CHECK_TYPEID_EQUAL(BasicGetOkBody, *handler[1].getBody());

        // Wait for child to exit.
        int status;
        BOOST_CHECK_EQUAL(::wait(&status), pid);
        BOOST_CHECK_EQUAL(0, status);
        BOOST_CHECK(handler.waitMembers(1));
        BOOST_CHECK_EQUAL(1u, cluster.size());
    }
    else {                      // Child
        BOOST_REQUIRE(execl("Cluster_child", "Cluster_child", NULL));
    }
}
