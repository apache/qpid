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

/**@file Tests for partial failure in a cluster.
 * Partial failure means some nodes experience a failure while others do not.
 * In this case the failed nodes must shut down.
 */

#include "test_tools.h"
#include "unit_test.h"
#include "ClusterFixture.h"
#include "qpid/client/FailoverManager.h"
#include <boost/assign.hpp>
#include <boost/algorithm/string.hpp>
#include <boost/bind.hpp>

namespace qpid {
namespace tests {

QPID_AUTO_TEST_SUITE(ClusterFailoverTestSuite)

using namespace std;
using namespace qpid;
using namespace qpid::cluster;
using namespace qpid::framing;
using namespace qpid::client;
using namespace qpid::client::arg;
using namespace boost::assign;
using broker::Broker;
using boost::shared_ptr;

// Timeout for tests that wait for messages
const sys::Duration TIMEOUT=sys::TIME_SEC/4;

ClusterFixture::Args getArgs(bool durable=std::getenv("STORE_LIB"))
{
    ClusterFixture::Args args;
    args += "--auth", "no", "--no-module-dir",
        "--load-module", getLibPath("CLUSTER_LIB");
    if (durable)
        args += "--load-module", getLibPath("STORE_LIB"), "TMP_DATA_DIR";
    else
        args += "--no-data-dir";
    return args;
}

// Test re-connecting with same session name after a failure.
QPID_AUTO_TEST_CASE(testReconnectSameSessionName) {
    ClusterFixture cluster(2, getArgs(), -1);
    // Specify a timeout to make sure it is ignored, session resume is
    // not implemented so sessions belonging to dead brokers should
    // not be kept.
    Client c0(cluster[0], "foo", 5);
    BOOST_CHECK_EQUAL(2u, knownBrokerPorts(c0.connection, 2).size()); // wait for both.
    c0.session.queueDeclare("q");
    c0.session.messageTransfer(arg::content=Message("sendme", "q"));
    BOOST_CHECK_EQUAL(c0.subs.get("q").getData(), "sendme");
    cluster.killWithSilencer(0, c0.connection, 9);
    Client c1(cluster[1], "foo", 5);
    c1.session.queueQuery();    // Try to use the session.
}

QPID_AUTO_TEST_CASE(testReconnectExclusiveQueue) {
    // Regresion test. Session timeouts should be ignored
    // by the broker as session resume is not implemented.
    ClusterFixture cluster(2, getArgs(), -1);
    Client c0(cluster[0], "foo", 5);
    c0.session.queueDeclare("exq", arg::exclusive=true);
    SubscriptionSettings settings;
    settings.exclusive = true;
    settings.autoAck = 0;
    Subscription s0 = c0.subs.subscribe(c0.lq, "exq", settings, "exsub");
    c0.session.messageTransfer(arg::content=Message("sendme", "exq"));
    BOOST_CHECK_EQUAL(c0.lq.get().getData(), "sendme");

    // Regression: core dump on exit if unacked messages were left in
    // a session with a timeout.
    cluster.killWithSilencer(0, c0.connection);

    // Regression: session timeouts prevented re-connecting to
    // exclusive queue.
    Client c1(cluster[1]);
    c1.session.queueDeclare("exq", arg::exclusive=true);
    Subscription s1 = c1.subs.subscribe(c1.lq, "exq", settings, "exsub");
    s1.cancel();

    // Regression: session timeouts prevented new member joining
    // cluster with exclusive queues.
    cluster.add();
    Client c2(cluster[2]);
    c2.session.queueQuery();
}


QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests
