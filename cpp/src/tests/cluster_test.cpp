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
#include "qpid/client/ConnectionAccess.h"
#include "qpid/client/Session.h"
#include "qpid/client/FailoverListener.h"
#include "qpid/cluster/Cluster.h"
#include "qpid/cluster/Cpg.h"
#include "qpid/cluster/DumpClient.h"
#include "qpid/framing/AMQBody.h"
#include "qpid/framing/Uuid.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/enum.h"
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

    /** Kill a forked broker with sig, or shutdown broker0 if n==0. */
    void kill(size_t n, int sig=SIGINT) {
        if (n) forkedBrokers[n-1].kill(sig);
        else broker0->broker->shutdown();
    }

    /** Kill a broker and suppress errors from connection. */
    void killWithSilencer(size_t n, client::Connection& c, int sig=SIGINT) {
        ScopedSuppressLogging sl;
        kill(n,sig);
        try { c.close(); } catch(...) {}
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

template <class T>  std::set<uint16_t> knownBrokerPorts(T& source, int n=-1) {
    vector<Url> urls = source.getKnownBrokers();
    BOOST_MESSAGE("knownBrokerPorts " << n << ": " << urls);
    if (n >= 0) {
        for (size_t retry=10; urls.size() != unsigned(n) && retry != 0; --retry) {
            ::usleep(100000);
            urls = source.getKnownBrokers();
            BOOST_MESSAGE("knownBrokerPorts retry: " << urls);
        }
    }
    set<uint16_t> s;
    for (vector<Url>::const_iterator i = urls.begin(); i != urls.end(); ++i) 
        s.insert((*i)[0].get<TcpAddress>()->port);
    return s;
}

class Sender {
  public:
    Sender(boost::shared_ptr<ConnectionImpl> ci, uint16_t ch) : connection(ci), channel(ch) {}
    void send(const AMQBody& body, bool firstSeg, bool lastSeg, bool firstFrame, bool lastFrame) {
        AMQFrame f(body);
        f.setChannel(channel);
        f.setFirstSegment(firstSeg);
        f.setLastSegment(lastSeg);
        f.setFirstFrame(firstFrame);
        f.setLastFrame(lastFrame);
        connection->handle(f);
    }

  private:
    boost::shared_ptr<ConnectionImpl> connection;
    uint16_t channel;
};

QPID_AUTO_TEST_CASE(testUnacked) {
    // Verify replication of unacknowledged messages.
    ClusterFixture cluster(1);
    Client c0(cluster[0], "c0"); 

    Message m;

    SubscriptionSettings manualAccept(FlowControl::unlimited(), ACCEPT_MODE_EXPLICIT, ACQUIRE_MODE_PRE_ACQUIRED, 0);
    c0.session.queueDeclare("q1");
    c0.session.messageTransfer(arg::content=Message("11","q1"));
    LocalQueue q1;
    c0.subs.subscribe(q1, "q1", manualAccept);
    BOOST_CHECK_EQUAL(q1.get(TIME_SEC).getData(), "11"); // Acquired but not accepted
    BOOST_CHECK_EQUAL(c0.session.queueQuery("q1").getMessageCount(), 0u); // Gone from queue

    SubscriptionSettings manualAcquire(FlowControl::unlimited(), ACCEPT_MODE_EXPLICIT, ACQUIRE_MODE_NOT_ACQUIRED, 0);
    c0.session.queueDeclare("q2");
    c0.session.messageTransfer(arg::content=Message("21","q2"));
    c0.session.messageTransfer(arg::content=Message("22","q2"));

    LocalQueue q2;
    c0.subs.subscribe(q2, "q2", manualAcquire);
    m = q2.get(TIME_SEC);  // Not acquired or accepted, still on queue
    BOOST_CHECK_EQUAL(m.getData(), "21");
    BOOST_CHECK_EQUAL(c0.session.queueQuery("q2").getMessageCount(), 2u); // Not removed
    c0.subs.getSubscription("q2").acquire(m); // Acquire manually
    BOOST_CHECK_EQUAL(c0.session.queueQuery("q2").getMessageCount(), 1u); // Removed
    BOOST_CHECK_EQUAL(q2.get(TIME_SEC).getData(), "22"); // Not acquired or accepted, still on queue
    BOOST_CHECK_EQUAL(c0.session.queueQuery("q2").getMessageCount(), 1u); // 1 not acquired.

    // Add new member while there are unacked messages.
    cluster.add();
    cluster.waitFor(2);
    Client c1(cluster[1], "c1"); 

    // Check queue counts
    BOOST_CHECK_EQUAL(c1.session.queueQuery("q1").getMessageCount(), 0u);
    BOOST_CHECK_EQUAL(c1.session.queueQuery("q2").getMessageCount(), 1u);

    // Unacked messages should be requeued when session is closed.
    c0.session.close();
    BOOST_CHECK_EQUAL(c1.session.queueQuery("q1").getMessageCount(), 1u);
    BOOST_CHECK_EQUAL(c1.session.queueQuery("q2").getMessageCount(), 2u);

    BOOST_CHECK_EQUAL(c1.subs.get("q1", TIME_SEC).getData(), "11");
    BOOST_CHECK_EQUAL(c1.subs.get("q2", TIME_SEC).getData(), "21");
    BOOST_CHECK_EQUAL(c1.subs.get("q2", TIME_SEC).getData(), "22");
}

QPID_AUTO_TEST_CASE_EXPECTED_FAILURES(testDumpTxState, 1) {
    // Verify that we dump transaction state correctly to new members.
    ClusterFixture cluster(1);
    Client c0(cluster[0], "c0");

    // Do work in a transaction.
    c0.session.txSelect();
    c0.session.queueDeclare("q");
    c0.session.messageTransfer(arg::content=Message("1","q"));
    c0.session.messageTransfer(arg::content=Message("2","q"));
    Message m;
    BOOST_CHECK(c0.subs.get(m, "q", TIME_SEC));
    BOOST_CHECK_EQUAL(m.getData(), "1");

    // New member, TX not comitted, c1 should see nothing.
    cluster.add();
    Client c1(cluster[1], "c1");
    BOOST_CHECK_EQUAL(c1.session.queueQuery(arg::queue="q").getMessageCount(), 0u);

    // After commit c1 shoudl see results of tx.
    c0.session.txCommit();
    BOOST_CHECK_EQUAL(c1.session.queueQuery(arg::queue="q").getMessageCount(), 1u);
    BOOST_CHECK(c1.subs.get(m, "q", TIME_SEC));
    BOOST_CHECK_EQUAL(m.getData(), "2");

    // Another transaction with both members active.
    c0.session.messageTransfer(arg::content=Message("3","q"));
    BOOST_CHECK_EQUAL(c1.session.queueQuery(arg::queue="q").getMessageCount(), 0u);
    c0.session.txCommit();
    BOOST_CHECK_EQUAL(c1.session.queueQuery(arg::queue="q").getMessageCount(), 1u);
    BOOST_CHECK(c1.subs.get(m, "q", TIME_SEC));
    BOOST_CHECK_EQUAL(m.getData(), "3");
}

QPID_AUTO_TEST_CASE(testDumpMessageBuilder) {
    // Verify that we dump a partially recieved message to a new member.
    ClusterFixture cluster(1);    
    Client c0(cluster[0], "c0");
    c0.session.queueDeclare("q");
    Sender sender(ConnectionAccess::getImpl(c0.connection), c0.session.getChannel());

    // Send first 2 frames of message.
    MessageTransferBody transfer(
        ProtocolVersion(), std::string(), // default exchange.
        framing::message::ACCEPT_MODE_NONE,
        framing::message::ACQUIRE_MODE_PRE_ACQUIRED);
    sender.send(transfer, true, false, true, true);
    AMQHeaderBody header;
    header.get<DeliveryProperties>(true)->setRoutingKey("q");
    sender.send(header, false, false, true, true);

    // No reliable way to ensure the partial message has arrived
    // before we start the new broker, so we sleep.
    ::usleep(250); 
    cluster.add();

    // Send final 2 frames of message.
    sender.send(AMQContentBody("ab"), false, true, true, false);
    sender.send(AMQContentBody("cd"), false, true, false, true);
    
    // Verify message is enqued correctly on second member.
    Message m;
    Client c1(cluster[1], "c1");
    BOOST_CHECK(c1.subs.get(m, "q", TIME_SEC));
    BOOST_CHECK_EQUAL(m.getData(), "abcd");

    BOOST_CHECK_EQUAL(2u, getGlobalCluster().getUrls().size());
}

QPID_AUTO_TEST_CASE(testConnectionKnownHosts) {
    ClusterFixture cluster(1);
    Client c0(cluster[0], "c0");
    set<uint16_t> kb0 = knownBrokerPorts(c0.connection);
    BOOST_CHECK_EQUAL(kb0.size(), 1u);
    BOOST_CHECK_EQUAL(kb0, makeSet(cluster));

    cluster.add();
    Client c1(cluster[1], "c1");
    set<uint16_t> kb1 = knownBrokerPorts(c1.connection);
    kb0 = knownBrokerPorts(c0.connection, 2);
    BOOST_CHECK_EQUAL(kb1.size(), 2u);
    BOOST_CHECK_EQUAL(kb1, makeSet(cluster));
    BOOST_CHECK_EQUAL(kb1,kb0);

    cluster.add();
    Client c2(cluster[2], "c2");
    set<uint16_t> kb2 = knownBrokerPorts(c2.connection);
    kb1 = knownBrokerPorts(c1.connection, 3);
    kb0 = knownBrokerPorts(c0.connection, 3);
    BOOST_CHECK_EQUAL(kb2.size(), 3u);
    BOOST_CHECK_EQUAL(kb2, makeSet(cluster));
    BOOST_CHECK_EQUAL(kb2,kb0);
    BOOST_CHECK_EQUAL(kb2,kb1);

    cluster.killWithSilencer(1,c1.connection,9);
    cluster.waitFor(2);
    kb0 = knownBrokerPorts(c0.connection, 2);
    kb2 = knownBrokerPorts(c2.connection, 2);
    BOOST_CHECK_EQUAL(kb0.size(), 2u);
    BOOST_CHECK_EQUAL(kb0, kb2);
}

QPID_AUTO_TEST_CASE(DumpConsumers) {
    ClusterFixture cluster(1, false); // Don't init broker 0

    cluster.add();
    Client c1(cluster[1], "c1"); 
    c1.session.queueDeclare("p");
    c1.session.queueDeclare("q");
    c1.subs.subscribe(c1.lq, "q", FlowControl::zero());
    LocalQueue lp;
    c1.subs.subscribe(lp, "p", FlowControl::messageCredit(1));
    c1.session.sync();

    // Start new members
    cluster.add0(true);
    Client c0(cluster[0], "c0"); 
    cluster.add();
    Client c2(cluster[2], "c2"); 

    // Transfer messages
    c1.session.messageTransfer(arg::content=Message("aaa", "q"));
    BOOST_CHECK_EQUAL(c0.session.queueQuery("q").getMessageCount(), 1u);
    BOOST_CHECK_EQUAL(c1.session.queueQuery("q").getMessageCount(), 1u);
    BOOST_CHECK_EQUAL(c2.session.queueQuery("q").getMessageCount(), 1u);

    c1.session.messageTransfer(arg::content=Message("bbb", "p"));
    c1.session.messageTransfer(arg::content=Message("ccc", "p"));
    
    // Activate the subscription, ensure message removed on all queues. 
    c1.subs.setFlowControl("q", FlowControl::unlimited());
    Message m;
    BOOST_CHECK(c1.lq.get(m, TIME_SEC));
    BOOST_CHECK_EQUAL(m.getData(), "aaa");
    BOOST_CHECK_EQUAL(c1.session.queueQuery("q").getMessageCount(), 0u);
    BOOST_CHECK_EQUAL(c1.session.queueQuery("q").getMessageCount(), 0u);
    BOOST_CHECK_EQUAL(c2.session.queueQuery("q").getMessageCount(), 0u);

    // Check second subscription's flow control: getsnn first message, not second.
    BOOST_CHECK(lp.get(m, TIME_SEC));
    BOOST_CHECK_EQUAL(m.getData(), "bbb");
    BOOST_CHECK_EQUAL(c1.session.queueQuery("p").getMessageCount(), 1u);
    BOOST_CHECK_EQUAL(c1.session.queueQuery("p").getMessageCount(), 1u);
    BOOST_CHECK_EQUAL(c2.session.queueQuery("p").getMessageCount(), 1u);

    BOOST_CHECK(c1.subs.get(m, "p", TIME_SEC));
    BOOST_CHECK_EQUAL(m.getData(), "ccc");
    
    // Kill the subscribing member, ensure further messages are not removed.
    cluster.killWithSilencer(1,c1.connection,9);
    cluster.waitFor(2);
    for (int i = 0; i < 10; ++i) {
        c0.session.messageTransfer(arg::content=Message("bbb", "q"));
        BOOST_REQUIRE(c0.subs.get(m, "q", TIME_SEC));
        BOOST_REQUIRE_EQUAL(m.getData(), "bbb");
    }
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
