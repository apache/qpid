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

#include "qpid/framing/SessionOpenBody.h"
#include "qpid/framing/SessionAttachedBody.h"
#include "qpid/framing/all_method_bodies.h"
#include "qpid/cluster/ClassifierHandler.h"

#include "unit_test.h"

#include <sys/wait.h>

QPID_AUTO_TEST_SUITE(ClusterTestSuite)

static const ProtocolVersion VER;

/** Verify membership in a cluster with one member. */
BOOST_AUTO_TEST_CASE(testClusterOne) {
    TestCluster cluster("clusterOne", "amqp:one:1");
    AMQFrame send(in_place<SessionOpenBody>(VER));
    send.setChannel(1);
    cluster.handle(send);
    AMQFrame received = cluster.received.pop();
    BOOST_CHECK_TYPEID_EQUAL(SessionOpenBody, *received.getBody());
    BOOST_CHECK_EQUAL(1u, cluster.size());
    Cluster::MemberList members = cluster.getMembers();
    BOOST_CHECK_EQUAL(1u, members.size());
    Cluster::Member me=members.front();
    BOOST_REQUIRE_EQUAL(me.url, "amqp:one:1");
}

/** Fork a process to test a cluster with two members */
BOOST_AUTO_TEST_CASE(testClusterTwo) {
    bool nofork=getenv("NOFORK") != 0;
    pid_t pid=0;
    if (!nofork) {
        pid = fork();
        BOOST_REQUIRE(pid >= 0);
    }
    if (pid || nofork) {        // Parent
        BOOST_MESSAGE("Parent start");
        TestCluster cluster("clusterTwo", "amqp:parent:1");
        BOOST_REQUIRE(cluster.waitFor(2)); // Myself and child.

        // Exchange frames with child.
        AMQFrame send(SessionOpenBody(VER));
        send.setChannel(1);
        cluster.handle(send);
        AMQFrame received = cluster.received.pop();
        BOOST_CHECK_TYPEID_EQUAL(SessionOpenBody, *received.getBody());
        
        received=cluster.received.pop();
        BOOST_CHECK_TYPEID_EQUAL(SessionAttachedBody, *received.getBody());

        if (!nofork) {
            // Wait for child to exit.
            int status;
            BOOST_CHECK_EQUAL(::wait(&status), pid);
            BOOST_CHECK_EQUAL(0, status);
            BOOST_CHECK(cluster.waitFor(1));
            BOOST_CHECK_EQUAL(1u, cluster.size());
        }
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
    AMQFrame queueDecl(in_place<QueueDeclareBody>(VER));
    AMQFrame messageTrans(in_place<MessageTransferBody>(VER));
    CountHandler wiring;
    CountHandler other;
    
    ClassifierHandler classify(wiring, other);

    classify.handle(queueDecl);
    BOOST_CHECK_EQUAL(1u, wiring.count);
    BOOST_CHECK_EQUAL(0u, other.count);
    
    classify.handle(messageTrans);
    BOOST_CHECK_EQUAL(1u, wiring.count);
    BOOST_CHECK_EQUAL(1u, other.count);
}

QPID_AUTO_TEST_SUITE_END()
