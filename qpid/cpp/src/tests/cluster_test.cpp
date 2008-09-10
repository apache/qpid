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

#include "test_tools.h"
#include "unit_test.h"
#include "ForkedBroker.h"
#include "BrokerFixture.h"

#include "qpid/cluster/Cpg.h"
#include "qpid/cluster/Cluster.h"
#include "qpid/cluster/DumpClient.h"
#include "qpid/framing/AMQBody.h"
#include "qpid/client/Connection.h"
#include "qpid/client/Session.h"
#include "qpid/framing/Uuid.h"
#include "qpid/log/Logger.h"

#include <boost/bind.hpp>
#include <boost/ptr_container/ptr_vector.hpp>

#include <string>
#include <iostream>
#include <iterator>
#include <vector>
#include <algorithm>

namespace qpid {
namespace cluster {
boost::intrusive_ptr<Cluster> getGlobalCluster(); // Defined in qpid/cluster/ClusterPlugin.cpp
}} // namespace qpid::cluster


QPID_AUTO_TEST_SUITE(CpgTestSuite)

using namespace std;
using namespace qpid;
using namespace qpid::cluster;
using namespace qpid::framing;
using namespace qpid::client;
using qpid::sys::TIME_SEC;
using qpid::broker::Broker;
using boost::ptr_vector;
using qpid::cluster::Cluster;
using qpid::cluster::getGlobalCluster;

/** Cluster fixture is a vector of ports for the replicas.
 * Replica 0 is in the current process, all others are forked as children.
 */
struct ClusterFixture : public vector<uint16_t>  {
    string name;
    Broker::Options opts;
    std::auto_ptr<BrokerFixture> broker0;
    boost::ptr_vector<ForkedBroker> forkedBrokers;

    ClusterFixture(size_t n);
    void add(size_t n) { for (size_t i=0; i < n; ++i) add(); }
    void add();
    void setup();
    void kill(size_t n) {
        if (n) forkedBrokers[n-1].kill();
        else broker0->broker->shutdown();
    }
};

ClusterFixture::ClusterFixture(size_t n) : name(Uuid(true).str()) {
    add(n);
    // Wait for all n members to join the cluster
    int retry=20;            // TODO aconway 2008-07-16: nasty sleeps, clean this up.
    while (retry && getGlobalCluster()->size() != n) {
        ::sleep(1);
        --retry;
    }
    BOOST_REQUIRE_EQUAL(n, getGlobalCluster()->size());
}

void ClusterFixture::add() {
    std::ostringstream os;
    os << "fork" << size();
    std::string prefix = os.str();

    const char* argv[] = {
        "qpidd " __FILE__ ,
        "--load-module=../.libs/libqpidcluster.so",
        "--cluster-name", name.c_str(), 
        "--auth=no", "--no-data-dir",
        "--log-prefix", prefix.c_str(),
    };
    size_t argc = sizeof(argv)/sizeof(argv[0]);

    if (size())  {              // Not the first broker, fork.
        forkedBrokers.push_back(new ForkedBroker(argc, argv));
        push_back(forkedBrokers.back().getPort());
    }
    else {                      // First broker, run in this process.
        Broker::Options opts;
        qpid::log::Logger::instance().setPrefix("main");
        Plugin::addOptions(opts); // Pick up cluster options.
        opts.parse(argc, argv, "", true); // Allow-unknown for --load-module
        broker0.reset(new BrokerFixture(opts));
        push_back(broker0->getPort());
    }
}

// For debugging: op << for CPG types.

ostream& operator<<(ostream& o, const cpg_name* n) {
    return o << qpid::cluster::Cpg::str(*n);
}

ostream& operator<<(ostream& o, const cpg_address& a) {
    return o << "(" << a.nodeid <<","<<a.pid<<","<<a.reason<<")";
}

template <class T>
ostream& operator<<(ostream& o, const pair<T*, int>& array) {
    o << "{ ";
    ostream_iterator<cpg_address> i(o, " ");
    copy(array.first, array.first+array.second, i);
    o << "}";
    return o;
}

QPID_AUTO_TEST_CASE(testDumpClient) {
    BrokerFixture donor, receiver;
    {
        Client c(donor.getPort());
        FieldTable args;
        args.setString("x", "y");
        c.session.queueDeclare("qa", arg::arguments=args);
        c.session.queueDeclare("qb", arg::alternateExchange="amq.direct");

        c.session.exchangeDeclare(arg::exchange="exd", arg::type="direct", arg::arguments=args);
        c.session.exchangeBind(arg::exchange="exd", arg::queue="qa", arg::bindingKey="foo");
        c.session.messageTransfer(arg::destination="exd", arg::content=TransferContent("one", "foo"));

        c.session.exchangeDeclare("ext", arg::type="topic");
        c.session.exchangeBind(arg::exchange="ext", arg::queue="qb", arg::bindingKey="bar");
        c.subs.subscribe(c.lq, "qa", FlowControl::messageCredit(0));
        c.session.messageTransfer(arg::destination="ext", arg::content=TransferContent("one", "bar"));
        c.session.messageTransfer(arg::destination="ext", arg::content=TransferContent("two", "bar"));

        c.session.close();
        c.connection.close();
    }
    qpid::cluster::DumpClient dump(Url::getIpAddressesUrl(receiver.getPort()));
    dump.dump(*donor.broker);
    {
        Client r(receiver.getPort());
        // Verify exchanges
        ExchangeQueryResult ex=r.session.exchangeQuery("exd");
        BOOST_CHECK_EQUAL(ex.getType(), "direct");
        BOOST_CHECK_EQUAL(ex.getDurable(), false);
        BOOST_CHECK_EQUAL(ex.getNotFound(), false);
        BOOST_CHECK_EQUAL(ex.getArguments().getString("x"), "y");

        ex = r.session.exchangeQuery("ext");
        BOOST_CHECK_EQUAL(ex.getType(), "topic");
        BOOST_CHECK_EQUAL(ex.getNotFound(), false);

        // Verify queues
        QueueQueryResult qq = r.session.queueQuery("qa");
        BOOST_CHECK_EQUAL(qq.getQueue(), "qa");
        BOOST_CHECK_EQUAL(qq.getAlternateExchange(), "");
        BOOST_CHECK_EQUAL(qq.getArguments().getString("x"), "y");
        BOOST_CHECK_EQUAL(qq.getMessageCount(), 1);

        qq = r.session.queueQuery("qb");
        BOOST_CHECK_EQUAL(qq.getQueue(), "qb");
        BOOST_CHECK_EQUAL(qq.getAlternateExchange(), "amq.direct");
        BOOST_CHECK_EQUAL(qq.getMessageCount(), 2);

        // Verify messages
        Message m;
        BOOST_CHECK(r.subs.get(m, "qa", TIME_SEC));
        BOOST_CHECK_EQUAL(m.getData(), "one");
        BOOST_CHECK_EQUAL(m.getDeliveryProperties().getExchange(), "exd");
        BOOST_CHECK_EQUAL(m.getDeliveryProperties().getRoutingKey(), "foo");

        BOOST_CHECK(r.subs.get(m, "qb", TIME_SEC));
        BOOST_CHECK_EQUAL(m.getData(), "one");
        BOOST_CHECK_EQUAL(m.getDeliveryProperties().getExchange(), "ext");
        BOOST_CHECK_EQUAL(m.getDeliveryProperties().getRoutingKey(), "bar");

        BOOST_CHECK(r.subs.get(m, "qb", TIME_SEC));
        BOOST_CHECK_EQUAL(m.getData(), "two");
        BOOST_CHECK_EQUAL(m.getDeliveryProperties().getExchange(), "ext");
        BOOST_CHECK_EQUAL(m.getDeliveryProperties().getRoutingKey(), "bar");

        // Verify bindings
        r.session.messageTransfer(arg::destination="exd", arg::content=TransferContent("xxx", "foo"));
        BOOST_CHECK(r.subs.get(m, "qa"));
        BOOST_CHECK_EQUAL(m.getData(), "xxx");
        
        r.session.messageTransfer(arg::destination="ext", arg::content=TransferContent("yyy", "bar"));
        BOOST_CHECK(r.subs.get(m, "qb"));
        BOOST_CHECK_EQUAL(m.getData(), "yyy");

        r.session.close();
        r.connection.close();
    }
}

QPID_AUTO_TEST_CASE(testForkedBroker) {
    // Verify the ForkedBroker works as expected.
    const char* argv[] = { "", "--auth=no", "--no-data-dir", "--log-prefix=testForkedBroker" };
    ForkedBroker broker(sizeof(argv)/sizeof(argv[0]), argv);
    Client c(broker.getPort());
    BOOST_CHECK_EQUAL("direct", c.session.exchangeQuery("amq.direct").getType()); 
}

QPID_AUTO_TEST_CASE(testSingletonCluster) {
    // Test against a singleton cluster, verify basic operation.
    ClusterFixture cluster(1);
    Client c(cluster[0]);
    BOOST_CHECK(c.session.queueQuery("q").getQueue().empty());
    BOOST_CHECK(c.session.exchangeQuery("ex").getNotFound());
}

QPID_AUTO_TEST_CASE(testWiringReplication) {
    ClusterFixture cluster(3);
    Client c0(cluster[0]);
    BOOST_CHECK(c0.session.queueQuery("q").getQueue().empty());
    BOOST_CHECK(c0.session.exchangeQuery("ex").getType().empty()); 
    c0.session.queueDeclare("q");
    c0.session.exchangeDeclare("ex", arg::type="direct");
    c0.session.close();
    c0.connection.close();
    // Verify all brokers get wiring update.
    for (size_t i = 0; i < cluster.size(); ++i) {
        BOOST_MESSAGE("i == "<< i);
        Client c(cluster[i]);
        BOOST_CHECK_EQUAL("q", c.session.queueQuery("q").getQueue());
        BOOST_CHECK_EQUAL("direct", c.session.exchangeQuery("ex").getType());
    }    
}

QPID_AUTO_TEST_CASE(testMessageEnqueue) {
    // Enqueue on one broker, dequeue on another.
    ClusterFixture cluster(2);
    Client c0(cluster[0]);
    c0.session.queueDeclare("q");
    c0.session.messageTransfer(arg::content=TransferContent("foo", "q"));
    c0.session.messageTransfer(arg::content=TransferContent("bar", "q"));
    c0.session.close();
    Client c1(cluster[1]);
    Message msg;
    BOOST_CHECK(c1.subs.get(msg, "q", qpid::sys::TIME_SEC));
    BOOST_CHECK_EQUAL(string("foo"), msg.getData());
    BOOST_CHECK(c1.subs.get(msg, "q", qpid::sys::TIME_SEC));
    BOOST_CHECK_EQUAL(string("bar"), msg.getData());
}

QPID_AUTO_TEST_CASE(testMessageDequeue) {
    // Enqueue on one broker, dequeue on two others.
    ClusterFixture cluster (3);
    Client c0(cluster[0]);
    c0.session.queueDeclare("q");
    c0.session.messageTransfer(arg::content=TransferContent("foo", "q"));
    c0.session.messageTransfer(arg::content=TransferContent("bar", "q"));

    Message msg;

    // Dequeue on 2 others, ensure correct order.
    Client c1(cluster[1]);
    BOOST_CHECK(c1.subs.get(msg, "q"));
    BOOST_CHECK_EQUAL("foo", msg.getData());
    
    Client c2(cluster[2]);
    BOOST_CHECK(c1.subs.get(msg, "q"));
    BOOST_CHECK_EQUAL("bar", msg.getData());

    // Queue should be empty on all cluster members.
    BOOST_CHECK_EQUAL(0u, c0.session.queueQuery("q").getMessageCount());
    BOOST_CHECK_EQUAL(0u, c1.session.queueQuery("q").getMessageCount());
    BOOST_CHECK_EQUAL(0u, c2.session.queueQuery("q").getMessageCount());
}

QPID_AUTO_TEST_CASE(testDequeueWaitingSubscription) {
    ClusterFixture cluster(3);
    // First start a subscription.
    Client c0(cluster[0]);
    c0.session.queueDeclare("q");
    c0.subs.subscribe(c0.lq, "q", FlowControl::messageCredit(2));
    // Now send messages
    Client c1(cluster[1]);
    c1.session.messageTransfer(arg::content=TransferContent("foo", "q"));
    c1.session.messageTransfer(arg::content=TransferContent("bar", "q"));

    // Check they arrived
    Message m;
    BOOST_CHECK(c0.lq.get(m, sys::TIME_SEC));
    BOOST_CHECK_EQUAL("foo", m.getData());
    BOOST_CHECK(c0.lq.get(m, sys::TIME_SEC));
    BOOST_CHECK_EQUAL("bar", m.getData());

    // Queue should be empty on all cluster members.
    Client c2(cluster[2]);
    BOOST_CHECK_EQUAL(0u, c0.session.queueQuery("q").getMessageCount());
    BOOST_CHECK_EQUAL(0u, c1.session.queueQuery("q").getMessageCount());
    BOOST_CHECK_EQUAL(0u, c2.session.queueQuery("q").getMessageCount());
}

QPID_AUTO_TEST_SUITE_END()
