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

#include "Cluster.h"
#include "test_tools.h"

#include "qpid/framing/ChannelPingBody.h"
#include "qpid/framing/ChannelOkBody.h"
#include "qpid/cluster/ClassifierHandler.h"

#define BOOST_AUTO_TEST_MAIN    // Must come before #include<boost/test/*>
#include <boost/test/auto_unit_test.hpp>

#include <sys/wait.h>

static const ProtocolVersion VER;

using namespace qpid::log;

/** Verify membership in a cluster with one member. */
BOOST_AUTO_TEST_CASE(testClusterOne) {
    TestCluster cluster("clusterOne", "amqp:one:1");
    AMQFrame frame(VER, 1, new ChannelPingBody(VER));
    cluster.getSendChains().in->handle(frame);
    BOOST_REQUIRE(cluster.received.waitFor(1));

    SessionFrame& sf=cluster.received[0];
    BOOST_CHECK(sf.isIncoming);
    BOOST_CHECK_TYPEID_EQUAL(ChannelPingBody, *sf.frame.getBody());
    
    BOOST_CHECK_EQUAL(1u, cluster.size());
    Cluster::MemberList members = cluster.getMembers();
    BOOST_CHECK_EQUAL(1u, members.size());
    shared_ptr<const Cluster::Member> me=members.front();
    BOOST_REQUIRE_EQUAL(me->url, "amqp:one:1");
}

/** Fork a process to test a cluster with two members */
BOOST_AUTO_TEST_CASE(testClusterTwo) {
    pid_t pid=fork();
    BOOST_REQUIRE(pid >= 0);
    if (pid) {              // Parent, see Cluster_child.cpp for child.
        TestCluster cluster("clusterTwo", "amqp::1");
        BOOST_REQUIRE(cluster.waitFor(2)); // Myself and child.

        // Exchange frames with child.
        AMQFrame frame(VER, 1, new ChannelPingBody(VER));
        cluster.getSendChains().in->handle(frame);
        BOOST_REQUIRE(cluster.received.waitFor(1));
        SessionFrame& sf=cluster.received[0];
        BOOST_CHECK(sf.isIncoming);
        BOOST_CHECK_TYPEID_EQUAL(ChannelPingBody, *sf.frame.getBody());
        BOOST_REQUIRE(cluster.received.waitFor(2));
        BOOST_CHECK_TYPEID_EQUAL(ChannelOkBody, *cluster.received[1].frame.getBody());

        // Wait for child to exit.
        int status;
        BOOST_CHECK_EQUAL(::wait(&status), pid);
        BOOST_CHECK_EQUAL(0, status);
        BOOST_CHECK(cluster.waitFor(1));
        BOOST_CHECK_EQUAL(1u, cluster.size());
    }
    else {                      // Child
        BOOST_REQUIRE(execl("./Cluster_child", "./Cluster_child", NULL));
    }
}

struct CountHandler : public FrameHandler {
    CountHandler() : count(0) {}
    void handle(AMQFrame&) { count++; }
    size_t count;
};
    
/** Test the ClassifierHandler */
BOOST_AUTO_TEST_CASE(testClassifierHandlerWiring) {
    AMQFrame queueDecl(VER, 0, new QueueDeclareBody(VER));
    AMQFrame messageTrans(VER, 0, new MessageTransferBody(VER));
    shared_ptr<CountHandler> wiring(new CountHandler());
    shared_ptr<CountHandler> other(new CountHandler());
    
    ClassifierHandler classify(wiring, other);

    classify.handle(queueDecl);
    BOOST_CHECK_EQUAL(1u, wiring->count);
    BOOST_CHECK_EQUAL(0u, other->count);
    
    classify.handle(messageTrans);
    BOOST_CHECK_EQUAL(1u, wiring->count);
    BOOST_CHECK_EQUAL(1u, other->count);
}
    

