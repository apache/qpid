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

#include "qpid/client/Connection.h"
#include "qpid/client/Session.h"
#include "qpid/client/FailoverListener.h"
#include "qpid/cluster/Cluster.h"
#include "qpid/cluster/Cpg.h"
#include "qpid/cluster/DumpClient.h"
#include "qpid/framing/AMQBody.h"
#include "qpid/framing/Uuid.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/log/Logger.h"

#include <boost/bind.hpp>
#include <boost/ptr_container/ptr_vector.hpp>

#include <string>
#include <iostream>
#include <iterator>
#include <vector>
#include <set>
#include <algorithm>
#include <iterator>

namespace qpid {
namespace cluster {
Cluster& getGlobalCluster(); // Defined in qpid/cluster/ClusterPlugin.cpp
}} // namespace qpid::cluster


namespace std {                 // ostream operators in std:: namespace
template <class T>
ostream& operator<<(ostream& o, const std::set<T>& s) { return seqPrint(o, s); }
}


QPID_AUTO_TEST_SUITE(cluster)

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

/** Parse broker & cluster options */
Broker::Options parseOpts(size_t argc, const char* argv[]) {
    Broker::Options opts;
    Plugin::addOptions(opts); // Pick up cluster options.
    opts.parse(argc, argv, "", true); // Allow-unknown for --load-module
    return opts;
}

/** Cluster fixture is a vector of ports for the replicas.
 * Replica 0 is in the current process, all others are forked as children.
 */
struct ClusterFixture : public vector<uint16_t>  {
    string name;
    std::auto_ptr<BrokerFixture> broker0;
    boost::ptr_vector<ForkedBroker> forkedBrokers;
    bool init0;

    ClusterFixture(size_t n, bool init0=true);
    void add(size_t n) { for (size_t i=0; i < n; ++i) add(); }
    void add();
    void add0(bool force);
    void setup();

    void kill(size_t n) {
        if (n) forkedBrokers[n-1].kill();
        else broker0->broker->shutdown();
    }

    void waitFor(size_t n) {
        for (size_t retry = 1000; retry && getGlobalCluster().getUrls().size() != n; --retry)
            ::usleep(1000);
    }
};

ClusterFixture::ClusterFixture(size_t n, bool init0_) : name(Uuid(true).str()), init0(init0_) {
    add(n);
    if (!init0) return;  // Defer initialization of broker0
    // Wait for all n members to join the cluster
    waitFor(n);
    BOOST_REQUIRE_EQUAL(n, getGlobalCluster().getUrls().size());
}

void ClusterFixture::add() {
    std::ostringstream os;
    os << "fork" << size();
    std::string prefix = os.str();

    if (size())  {              // Not the first broker, fork.

        const char* argv[] = {
            "qpidd " __FILE__ ,
            "--load-module=../.libs/cluster.so",
            "--cluster-name", name.c_str(), 
            "--auth=no", "--no-data-dir",
            "--log-prefix", prefix.c_str(),
        };
        size_t argc = sizeof(argv)/sizeof(argv[0]);


        forkedBrokers.push_back(new ForkedBroker(argc, argv));
        push_back(forkedBrokers.back().getPort());
    }
    else {      
        add0(init0);            // First broker, run in this process.
    }
}

void ClusterFixture::add0(bool init) {
    if (!init) {
        push_back(0);
        return;
    }
    const char* argv[] = {
        "qpidd " __FILE__ ,
        "--load-module=../.libs/cluster.so",
        "--cluster-name", name.c_str(), 
        "--auth=no", "--no-data-dir"
    };
    size_t argc = sizeof(argv)/sizeof(argv[0]);

    qpid::log::Logger::instance().setPrefix("main");
    broker0.reset(new BrokerFixture(parseOpts(argc, argv)));
    if (size()) front() = broker0->getPort(); else push_back(broker0->getPort());
}

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

template <class C> set<uint16_t> makeSet(const C& c) {
    set<uint16_t> s;
    std::copy(c.begin(), c.end(), std::inserter(s, s.begin()));
    return s;
}

std::set<uint16_t> portsFromFailoverArray(const framing::Array& urlArray) {
    std::set<uint16_t> ports;
    for (framing::Array::ValueVector::const_iterator i = urlArray.begin(); i < urlArray.end(); ++i ) {
        Url url((*i)->get<std::string>());
        BOOST_REQUIRE(url.size() > 0);
        BOOST_REQUIRE(url[0].get<TcpAddress>());
        ports.insert(url[0].get<TcpAddress>()->port);
    }
    return ports;
}

std::set<uint16_t> portsFromFailoverMessage(const Message& m) {
    framing::Array urlArray;
    m.getHeaders().getArray("amq.failover", urlArray);
    return portsFromFailoverArray(urlArray);
}

QPID_AUTO_TEST_CASE(FailoverExchange) {
    ClusterFixture cluster(2);
    Client c0(cluster[0], "c0");
    c0.session.queueDeclare("q");
    c0.session.exchangeBind(arg::queue="q", arg::exchange="amq.failover");

    Message m;
    BOOST_CHECK_EQUAL(1u, c0.subs.get(m, "q", TIME_SEC));
    BOOST_CHECK_EQUAL(makeSet(cluster), portsFromFailoverMessage(m));

    cluster.add();
    BOOST_CHECK_EQUAL(1u, c0.subs.get(m, "q", TIME_SEC));
    BOOST_CHECK_EQUAL(makeSet(cluster),portsFromFailoverMessage(m));
}

std::set<uint16_t> portsFromFailoverListener(const FailoverListener& fl, size_t n) {
    // Wait till there are n ports in the list.
    vector<Url> kb = fl.getKnownBrokers();
    for (size_t retry=1000; kb.size() != n && retry != 0; --retry) {
        ::usleep(1000);
        kb = fl.getKnownBrokers();
    }
    set<uint16_t> s;
    for (vector<Url>::const_iterator i = kb.begin(); i != kb.end(); ++i) {
        BOOST_MESSAGE("Failover URL: " << *i);      
        BOOST_CHECK(i->size() >= 1);
        BOOST_CHECK((*i)[0].get<TcpAddress>());
        s.insert((*i)[0].get<TcpAddress>()->port);
    }
    return s;
}

QPID_AUTO_TEST_CASE(testFailoverListener) {
    ClusterFixture cluster(1);
    Client c0(cluster[0], "c0");
    FailoverListener fl(c0.connection);

    set<uint16_t> set0=makeSet(cluster);

    BOOST_CHECK_EQUAL(set0, portsFromFailoverListener(fl, 1));
    cluster.add();
    BOOST_CHECK_EQUAL(makeSet(cluster), portsFromFailoverListener(fl, 2));
    cluster.kill(1);
    BOOST_CHECK_EQUAL(set0, portsFromFailoverListener(fl, 1));
}

QPID_AUTO_TEST_CASE(DumpConsumers) {
    ClusterFixture cluster(1); 
    Client c0(cluster[0], "c0"); 
    c0.session.queueDeclare("q");
    c0.subs.subscribe(c0.lq, "q", FlowControl::zero());
    c0.session.sync();

    // Start new members
    cluster.add();
    Client c1(cluster[1], "c1"); 
    cluster.add();
    Client c2(cluster[2], "c2"); 

    // Transfer a message, verify all members see it.
    c0.session.messageTransfer(arg::content=Message("aaa", "q"));
    BOOST_CHECK_EQUAL(c0.session.queueQuery("q").getMessageCount(), 1u);
    BOOST_CHECK_EQUAL(c1.session.queueQuery("q").getMessageCount(), 1u);
    BOOST_CHECK_EQUAL(c2.session.queueQuery("q").getMessageCount(), 1u);

    // Activate the subscription, ensure message removed on all queues. 
    c0.subs.setFlowControl("q", FlowControl::messageCredit(1));
    Message m;
    BOOST_CHECK(c0.lq.get(m, TIME_SEC));
    BOOST_CHECK_EQUAL(m.getData(), "aaa");

    BOOST_CHECK_EQUAL(c0.session.queueQuery("q").getMessageCount(), 0u);
    BOOST_CHECK_EQUAL(c1.session.queueQuery("q").getMessageCount(), 0u);
    BOOST_CHECK_EQUAL(c2.session.queueQuery("q").getMessageCount(), 0u);
}

QPID_AUTO_TEST_CASE(testCatchupSharedState) {
    ClusterFixture cluster(1);
    Client c0(cluster[0], "c0");

    // Create some shared state.
    c0.session.queueDeclare("q");
    c0.session.messageTransfer(arg::content=Message("foo","q"));
    c0.session.messageTransfer(arg::content=Message("bar","q"));
    while (c0.session.queueQuery("q").getMessageCount() != 2)
        ::usleep(1000);         // Wait for message to show up on broker 0.

    // Add a new broker, it should catch up.
    cluster.add();

    // Do some work post-add
    c0.session.queueDeclare("p");
    c0.session.messageTransfer(arg::content=Message("pfoo","p"));

    // Do some work post-join
    cluster.waitFor(2);
    c0.session.messageTransfer(arg::content=Message("pbar","p"));

    // Verify new brokers have state.
    Message m;

    Client c1(cluster[1], "c1");

    BOOST_CHECK(c1.subs.get(m, "q", TIME_SEC));
    BOOST_CHECK_EQUAL(m.getData(), "foo");
    BOOST_CHECK(c1.subs.get(m, "q", TIME_SEC));
    BOOST_CHECK_EQUAL(m.getData(), "bar");
    BOOST_CHECK_EQUAL(c1.session.queueQuery("q").getMessageCount(), 0u);

    // Add another broker, don't wait for join - should be stalled till ready.
    cluster.add();
    Client c2(cluster[2], "c2");
    BOOST_CHECK(c2.subs.get(m, "p", TIME_SEC));
    BOOST_CHECK_EQUAL(m.getData(), "pfoo");
    BOOST_CHECK(c2.subs.get(m, "p", TIME_SEC));
    BOOST_CHECK_EQUAL(m.getData(), "pbar");
    BOOST_CHECK_EQUAL(c2.session.queueQuery("p").getMessageCount(), 0u);
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
    c0.session.messageTransfer(arg::content=Message("foo", "q"));
    c0.session.messageTransfer(arg::content=Message("bar", "q"));
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
    ClusterFixture cluster(3);
    Client c0(cluster[0], "c0");
    c0.session.queueDeclare("q");
    c0.session.messageTransfer(arg::content=Message("foo", "q"));
    c0.session.messageTransfer(arg::content=Message("bar", "q"));

    Message msg;

    // Dequeue on 2 others, ensure correct order.
    Client c1(cluster[1], "c1");
    BOOST_CHECK(c1.subs.get(msg, "q"));
    BOOST_CHECK_EQUAL("foo", msg.getData());
    
    Client c2(cluster[2], "c2");
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
    c1.session.messageTransfer(arg::content=Message("foo", "q"));
    c1.session.messageTransfer(arg::content=Message("bar", "q"));

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
