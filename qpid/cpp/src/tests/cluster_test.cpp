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
#include "ClusterFixture.h"

#include "qpid/client/Connection.h"
#include "qpid/client/ConnectionSettings.h"
#include "qpid/client/ConnectionAccess.h"
#include "qpid/client/Session.h"
#include "qpid/client/FailoverListener.h"
#include "qpid/client/FailoverManager.h"
#include "qpid/cluster/Cluster.h"
#include "qpid/cluster/Cpg.h"
#include "qpid/cluster/UpdateClient.h"
#include "qpid/framing/AMQBody.h"
#include "qpid/framing/Uuid.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/enum.h"
#include "qpid/log/Logger.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Thread.h"

#include <boost/bind.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/assign.hpp>

#include <string>
#include <iostream>
#include <fstream>
#include <iterator>
#include <vector>
#include <set>
#include <algorithm>
#include <iterator>

namespace std {                 // ostream operators in std:: namespace
template <class T>
ostream& operator<<(ostream& o, const std::set<T>& s) { return seqPrint(o, s); }
}

QPID_AUTO_TEST_SUITE(cluster_test)

using namespace std;
using namespace qpid;
using namespace qpid::cluster;
using namespace qpid::framing;
using namespace qpid::client;
using namespace boost::assign;
using broker::Broker;
using boost::shared_ptr;

// Timeout for tests that wait for messages
const sys::Duration TIMEOUT=sys::TIME_SEC/4;


ostream& operator<<(ostream& o, const cpg_name* n) {
    return o << cluster::Cpg::str(*n);
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
    copy(c.begin(), c.end(), inserter(s, s.begin()));
    return s;
}

template <class T>  set<uint16_t> knownBrokerPorts(T& source, int n=-1) {
    vector<Url> urls = source.getKnownBrokers();
    if (n >= 0 && unsigned(n) != urls.size()) {
        BOOST_MESSAGE("knownBrokerPorts waiting for " << n << ": " << urls);
        // Retry up to 10 secs in .1 second intervals.
        for (size_t retry=100; urls.size() != unsigned(n) && retry != 0; --retry) {
            sys::usleep(1000*100); // 0.1 secs
            urls = source.getKnownBrokers();
        }
    }
    BOOST_MESSAGE("knownBrokerPorts expecting " << n << ": " << urls);
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

int64_t getMsgSequence(const Message& m) {
    return m.getMessageProperties().getApplicationHeaders().getAsInt64("qpid.msg_sequence");
}

Message ttlMessage(const string& data, const string& key, uint64_t ttl) {
    Message m(data, key);
    m.getDeliveryProperties().setTtl(ttl);
    return m;
}

vector<string> browse(Client& c, const string& q, int n) {
    SubscriptionSettings browseSettings(
        FlowControl::unlimited(),
        ACCEPT_MODE_NONE,
        ACQUIRE_MODE_NOT_ACQUIRED,
        0                       // No auto-ack.
    );
    LocalQueue lq;
    c.subs.subscribe(lq, q, browseSettings);
    vector<string> result;
    for (int i = 0; i < n; ++i) {
        Message m;
        if (!lq.get(m, TIMEOUT))
            break;
        result.push_back(m.getData());
    }
    c.subs.getSubscription(q).cancel();
    return result;
}


// FIXME aconway 2009-02-12: need to figure out how to test this properly.
// Current problems:
// - all brokers share the same data-dir (set ACL without data dir?)
// - updater's user name not making it through to updatee for ACL checks.
// 
// QPID_AUTO_TEST_CASE(testAcl) {
//     ofstream policyFile("cluster_test.acl");
//     // FIXME aconway 2009-02-12: guest -> qpidd?
//     policyFile << "acl allow guest@QPID all all" << endl
//                << "acl allow foo@QPID create queue name=foo" << endl
//                << "acl allow bar@QPID create queue name=bar" << endl
//                << "acl deny all create queue" << endl
//                << "acl allow all all" << endl;
//     policyFile.close();
//     ClusterFixture cluster(2,-1, list_of<string>
//                            ("--data-dir=.") ("--auth=no")
//                            ("--acl-file=cluster_test.acl")
//                            ("--cluster-mechanism=PLAIN")
//                            ("--load-module=../.libs/acl.so"));
//     Client c0(cluster[0], "c0");
//     Client c1(cluster[1], "c1");

//     ConnectionSettings settings;
//     settings.port = cluster[0];
//     settings.username = "foo";
//     Client foo(settings, "foo");

//     foo.session.queueDeclare("foo");
//     BOOST_CHECK_EQUAL(c0.session.queueQuery("foo").getQueue(), "foo");
//     BOOST_CHECK_EQUAL(c1.session.queueQuery("foo").getQueue(), "foo");

//     BOOST_CHECK_THROW(foo.session.queueDeclare("bar"), int);
//     BOOST_CHECK_EQUAL(c0.session.queueQuery("bar").getQueue(), "");
//     BOOST_CHECK_EQUAL(c1.session.queueQuery("bar").getQueue(), "");
// }


QPID_AUTO_TEST_CASE(testMessageTimeToLive) {
    // Note: this doesn't actually test for cluster race conditions around TTL,
    // it just verifies that basic TTL functionality works.
    //
    ClusterFixture cluster(2);
    Client c0(cluster[0], "c0");
    Client c1(cluster[1], "c1");
    c0.session.queueDeclare("p");
    c0.session.queueDeclare("q");
    c0.session.messageTransfer(arg::content=ttlMessage("a", "q", 200));
    c0.session.messageTransfer(arg::content=Message("b", "q"));
    c0.session.messageTransfer(arg::content=ttlMessage("x", "p", 10000));
    c0.session.messageTransfer(arg::content=Message("y", "p"));
    cluster.add();
    Client c2(cluster[1], "c2");

    BOOST_CHECK_EQUAL(browse(c0, "p", 2), list_of<string>("x")("y"));
    BOOST_CHECK_EQUAL(browse(c1, "p", 2), list_of<string>("x")("y"));
    BOOST_CHECK_EQUAL(browse(c2, "p", 2), list_of<string>("x")("y"));

    sys::usleep(200*1000); 
    BOOST_CHECK_EQUAL(browse(c0, "q", 1), list_of<string>("b"));
    BOOST_CHECK_EQUAL(browse(c1, "q", 1), list_of<string>("b"));
    BOOST_CHECK_EQUAL(browse(c2, "q", 1), list_of<string>("b"));
}

QPID_AUTO_TEST_CASE(testSequenceOptions) {
    // Make sure the exchange qpid.msg_sequence property is properly replicated.
    ClusterFixture cluster(1);
    Client c0(cluster[0], "c0");
    FieldTable args;
    args.setInt("qpid.msg_sequence", 1); 
    c0.session.queueDeclare(arg::queue="q");
    c0.session.exchangeDeclare(arg::exchange="ex", arg::type="direct", arg::arguments=args);
    c0.session.exchangeBind(arg::exchange="ex", arg::queue="q", arg::bindingKey="k");
    c0.session.messageTransfer(arg::content=Message("1", "k"), arg::destination="ex");
    c0.session.messageTransfer(arg::content=Message("2", "k"), arg::destination="ex");
    BOOST_CHECK_EQUAL(1, getMsgSequence(c0.subs.get("q", TIMEOUT)));
    BOOST_CHECK_EQUAL(2, getMsgSequence(c0.subs.get("q", TIMEOUT)));

    cluster.add();
    Client c1(cluster[1]);
    c1.session.messageTransfer(arg::content=Message("3", "k"), arg::destination="ex");    
    BOOST_CHECK_EQUAL(3, getMsgSequence(c1.subs.get("q", TIMEOUT)));
}

QPID_AUTO_TEST_CASE(testTxTransaction) {
    ClusterFixture cluster(1);
    Client c0(cluster[0], "c0");
    c0.session.queueDeclare(arg::queue="q");
    c0.session.messageTransfer(arg::content=Message("A", "q"));
    c0.session.messageTransfer(arg::content=Message("B", "q"));

    // Start a transaction that will commit.
    Session commitSession = c0.connection.newSession("commit");
    SubscriptionManager commitSubs(commitSession);
    commitSession.txSelect();
    commitSession.messageTransfer(arg::content=Message("a", "q"));
    commitSession.messageTransfer(arg::content=Message("b", "q"));
    BOOST_CHECK_EQUAL(commitSubs.get("q", TIMEOUT).getData(), "A");

    // Start a transaction that will roll back.
    Session rollbackSession = c0.connection.newSession("rollback");
    SubscriptionManager rollbackSubs(rollbackSession);
    rollbackSession.txSelect();
    rollbackSession.messageTransfer(arg::content=Message("1", "q"));
    Message rollbackMessage = rollbackSubs.get("q", TIMEOUT);
    BOOST_CHECK_EQUAL(rollbackMessage.getData(), "B");

    BOOST_CHECK_EQUAL(c0.session.queueQuery("q").getMessageCount(), 0u);
    // Add new member mid transaction.
    cluster.add();            
    Client c1(cluster[1], "c1");

    // More transactional work
    BOOST_CHECK_EQUAL(c1.session.queueQuery("q").getMessageCount(), 0u);
    rollbackSession.messageTransfer(arg::content=Message("2", "q"));
    commitSession.messageTransfer(arg::content=Message("c", "q"));
    rollbackSession.messageTransfer(arg::content=Message("3", "q"));

    BOOST_CHECK_EQUAL(c1.session.queueQuery("q").getMessageCount(), 0u);    

    // Commit/roll back.
    commitSession.txCommit();
    rollbackSession.txRollback();
    rollbackSession.messageRelease(rollbackMessage.getId());


    // Verify queue status: just the comitted messages and dequeues should remain.
    BOOST_CHECK_EQUAL(c1.session.queueQuery("q").getMessageCount(), 4u);
    BOOST_CHECK_EQUAL(c1.subs.get("q", TIMEOUT).getData(), "B");
    BOOST_CHECK_EQUAL(c1.subs.get("q", TIMEOUT).getData(), "a");
    BOOST_CHECK_EQUAL(c1.subs.get("q", TIMEOUT).getData(), "b");
    BOOST_CHECK_EQUAL(c1.subs.get("q", TIMEOUT).getData(), "c");
}

QPID_AUTO_TEST_CASE(testUnacked) {
    // Verify replication of unacknowledged messages.
    ClusterFixture cluster(1);
    Client c0(cluster[0], "c0"); 

    Message m;

    // Create unacked message: acquired but not accepted.
    SubscriptionSettings manualAccept(FlowControl::unlimited(), ACCEPT_MODE_EXPLICIT, ACQUIRE_MODE_PRE_ACQUIRED, 0);
    c0.session.queueDeclare("q1");
    c0.session.messageTransfer(arg::content=Message("11","q1"));
    LocalQueue q1;
    c0.subs.subscribe(q1, "q1", manualAccept);
    BOOST_CHECK_EQUAL(q1.get(TIMEOUT).getData(), "11"); // Acquired but not accepted
    BOOST_CHECK_EQUAL(c0.session.queueQuery("q1").getMessageCount(), 0u); // Gone from queue

    // Create unacked message: not acquired, accepted or completeed.
    SubscriptionSettings manualAcquire(FlowControl::unlimited(), ACCEPT_MODE_EXPLICIT, ACQUIRE_MODE_NOT_ACQUIRED, 0);
    c0.session.queueDeclare("q2");
    c0.session.messageTransfer(arg::content=Message("21","q2"));
    c0.session.messageTransfer(arg::content=Message("22","q2"));
    LocalQueue q2;
    c0.subs.subscribe(q2, "q2", manualAcquire);
    m = q2.get(TIMEOUT);  // Not acquired or accepted, still on queue
    BOOST_CHECK_EQUAL(m.getData(), "21");
    BOOST_CHECK_EQUAL(c0.session.queueQuery("q2").getMessageCount(), 2u); // Not removed
    c0.subs.getSubscription("q2").acquire(m); // Acquire manually
    BOOST_CHECK_EQUAL(c0.session.queueQuery("q2").getMessageCount(), 1u); // Removed
    BOOST_CHECK_EQUAL(q2.get(TIMEOUT).getData(), "22"); // Not acquired or accepted, still on queue
    BOOST_CHECK_EQUAL(c0.session.queueQuery("q2").getMessageCount(), 1u); // 1 not acquired.

    // Create empty credit record: acquire and accept but don't complete.
    SubscriptionSettings manualComplete(FlowControl::messageWindow(1), ACCEPT_MODE_EXPLICIT, ACQUIRE_MODE_PRE_ACQUIRED, 1, MANUAL_COMPLETION);
    c0.session.queueDeclare("q3");
    c0.session.messageTransfer(arg::content=Message("31", "q3"));
    c0.session.messageTransfer(arg::content=Message("32", "q3"));
    LocalQueue q3;
    c0.subs.subscribe(q3, "q3", manualComplete);
    Message m31=q3.get(TIMEOUT);
    BOOST_CHECK_EQUAL(m31.getData(), "31"); // Automatically acquired & accepted but not completed.
    BOOST_CHECK_EQUAL(c0.session.queueQuery("q3").getMessageCount(), 1u);    

    // Add new member while there are unacked messages.
    cluster.add();
    Client c1(cluster[1], "c1"); 

    // Check queue counts
    BOOST_CHECK_EQUAL(c1.session.queueQuery("q1").getMessageCount(), 0u);
    BOOST_CHECK_EQUAL(c1.session.queueQuery("q2").getMessageCount(), 1u);
    BOOST_CHECK_EQUAL(c1.session.queueQuery("q3").getMessageCount(), 1u);

    // Complete the empty credit message, should unblock the message behind it.
    BOOST_CHECK_THROW(q3.get(0), Exception);
    c0.session.markCompleted(SequenceSet(m31.getId()), true);
    BOOST_CHECK_EQUAL(q3.get(TIMEOUT).getData(), "32");
    BOOST_CHECK_EQUAL(c0.session.queueQuery("q3").getMessageCount(), 0u);
    BOOST_CHECK_EQUAL(c1.session.queueQuery("q3").getMessageCount(), 0u);
    
    // Close the original session - unacked messages should be requeued.
    c0.session.close();
    BOOST_CHECK_EQUAL(c1.session.queueQuery("q1").getMessageCount(), 1u);
    BOOST_CHECK_EQUAL(c1.session.queueQuery("q2").getMessageCount(), 2u);

    BOOST_CHECK_EQUAL(c1.subs.get("q1", TIMEOUT).getData(), "11");
    BOOST_CHECK_EQUAL(c1.subs.get("q2", TIMEOUT).getData(), "21");
    BOOST_CHECK_EQUAL(c1.subs.get("q2", TIMEOUT).getData(), "22");
}

QPID_AUTO_TEST_CASE_EXPECTED_FAILURES(testUpdateTxState, 1) {
    // Verify that we update transaction state correctly to new members.
    ClusterFixture cluster(1);
    Client c0(cluster[0], "c0");

    // Do work in a transaction.
    c0.session.txSelect();
    c0.session.queueDeclare("q");
    c0.session.messageTransfer(arg::content=Message("1","q"));
    c0.session.messageTransfer(arg::content=Message("2","q"));
    Message m;
    BOOST_CHECK(c0.subs.get(m, "q", TIMEOUT));
    BOOST_CHECK_EQUAL(m.getData(), "1");

    // New member, TX not comitted, c1 should see nothing.
    cluster.add();
    Client c1(cluster[1], "c1");
    BOOST_CHECK_EQUAL(c1.session.queueQuery(arg::queue="q").getMessageCount(), 0u);

    // After commit c1 shoudl see results of tx.
    c0.session.txCommit();
    BOOST_CHECK_EQUAL(c1.session.queueQuery(arg::queue="q").getMessageCount(), 1u);
    BOOST_CHECK(c1.subs.get(m, "q", TIMEOUT));
    BOOST_CHECK_EQUAL(m.getData(), "2");

    // Another transaction with both members active.
    c0.session.messageTransfer(arg::content=Message("3","q"));
    BOOST_CHECK_EQUAL(c1.session.queueQuery(arg::queue="q").getMessageCount(), 0u);
    c0.session.txCommit();
    BOOST_CHECK_EQUAL(c1.session.queueQuery(arg::queue="q").getMessageCount(), 1u);
    BOOST_CHECK(c1.subs.get(m, "q", TIMEOUT));
    BOOST_CHECK_EQUAL(m.getData(), "3");
}

QPID_AUTO_TEST_CASE(testUpdateMessageBuilder) {
    // Verify that we update a partially recieved message to a new member.
    ClusterFixture cluster(1);    
    Client c0(cluster[0], "c0");
    c0.session.queueDeclare("q");
    Sender sender(ConnectionAccess::getImpl(c0.connection), c0.session.getChannel());

    // Send first 2 frames of message.
    MessageTransferBody transfer(
        ProtocolVersion(), string(), // default exchange.
        framing::message::ACCEPT_MODE_NONE,
        framing::message::ACQUIRE_MODE_PRE_ACQUIRED);
    sender.send(transfer, true, false, true, true);
    AMQHeaderBody header;
    header.get<DeliveryProperties>(true)->setRoutingKey("q");
    sender.send(header, false, false, true, true);

    // No reliable way to ensure the partial message has arrived
    // before we start the new broker, so we sleep.
    sys::usleep(2500); 
    cluster.add();

    // Send final 2 frames of message.
    sender.send(AMQContentBody("ab"), false, true, true, false);
    sender.send(AMQContentBody("cd"), false, true, false, true);
    
    // Verify message is enqued correctly on second member.
    Message m;
    Client c1(cluster[1], "c1");
    BOOST_CHECK(c1.subs.get(m, "q", TIMEOUT));
    BOOST_CHECK_EQUAL(m.getData(), "abcd");
    BOOST_CHECK_EQUAL(2u, knownBrokerPorts(c1.connection).size());
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
    kb0 = knownBrokerPorts(c0.connection, 2);
    kb2 = knownBrokerPorts(c2.connection, 2);
    BOOST_CHECK_EQUAL(kb0.size(), 2u);
    BOOST_CHECK_EQUAL(kb0, kb2);
}

QPID_AUTO_TEST_CASE(testUpdateConsumers) {
    ClusterFixture cluster(1, 1);  

    Client c0(cluster[0], "c0"); 
    c0.session.queueDeclare("p");
    c0.session.queueDeclare("q");
    c0.subs.subscribe(c0.lq, "q", FlowControl::zero());
    LocalQueue lp;
    c0.subs.subscribe(lp, "p", FlowControl::messageCredit(1));
    c0.session.sync();

    // Start new members
    cluster.add();              // Local
    Client c1(cluster[1], "c1"); 
    cluster.add();
    Client c2(cluster[2], "c2"); 

    // Transfer messages
    c0.session.messageTransfer(arg::content=Message("aaa", "q"));

    c0.session.messageTransfer(arg::content=Message("bbb", "p"));
    c0.session.messageTransfer(arg::content=Message("ccc", "p"));

    // Activate the subscription, ensure message removed on all queues. 
    c0.subs.setFlowControl("q", FlowControl::unlimited());
    Message m;
    BOOST_CHECK(c0.lq.get(m, TIMEOUT));
    BOOST_CHECK_EQUAL(m.getData(), "aaa");
    BOOST_CHECK_EQUAL(c0.session.queueQuery("q").getMessageCount(), 0u);
    BOOST_CHECK_EQUAL(c1.session.queueQuery("q").getMessageCount(), 0u);
    BOOST_CHECK_EQUAL(c2.session.queueQuery("q").getMessageCount(), 0u);

    // Check second subscription's flow control: gets first message, not second.
    BOOST_CHECK(lp.get(m, TIMEOUT));
    BOOST_CHECK_EQUAL(m.getData(), "bbb");
    BOOST_CHECK_EQUAL(c0.session.queueQuery("p").getMessageCount(), 1u);
    BOOST_CHECK_EQUAL(c1.session.queueQuery("p").getMessageCount(), 1u);
    BOOST_CHECK_EQUAL(c2.session.queueQuery("p").getMessageCount(), 1u);

    BOOST_CHECK(c0.subs.get(m, "p", TIMEOUT));
    BOOST_CHECK_EQUAL(m.getData(), "ccc");
    
    // Kill the subscribing member, ensure further messages are not removed.
    cluster.killWithSilencer(0,c0.connection,9);
    BOOST_REQUIRE_EQUAL(knownBrokerPorts(c1.connection, 2).size(), 2u);
    for (int i = 0; i < 10; ++i) {
        c1.session.messageTransfer(arg::content=Message("xxx", "q"));
        BOOST_REQUIRE(c1.subs.get(m, "q", TIMEOUT));
        BOOST_REQUIRE_EQUAL(m.getData(), "xxx");
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
        sys::usleep(1000);    // Wait for message to show up on broker 0.

    // Add a new broker, it will catch up.
    cluster.add();

    // Do some work post-add
    c0.session.queueDeclare("p");
    c0.session.messageTransfer(arg::content=Message("pfoo","p"));

    // Do some work post-join
    BOOST_REQUIRE_EQUAL(knownBrokerPorts(c0.connection, 2).size(), 2u);
    c0.session.messageTransfer(arg::content=Message("pbar","p"));

    // Verify new brokers have state.
    Message m;

    Client c1(cluster[1], "c1");

    BOOST_CHECK(c1.subs.get(m, "q", TIMEOUT));
    BOOST_CHECK_EQUAL(m.getData(), "foo");
    BOOST_CHECK_EQUAL(m.getDeliveryProperties().getExchange(), "");
    BOOST_CHECK(c1.subs.get(m, "q", TIMEOUT));
    BOOST_CHECK_EQUAL(m.getData(), "bar");
    BOOST_CHECK_EQUAL(c1.session.queueQuery("q").getMessageCount(), 0u);

    // Add another broker, don't wait for join - should be stalled till ready.
    cluster.add();
    Client c2(cluster[2], "c2");
    BOOST_CHECK(c2.subs.get(m, "p", TIMEOUT));
    BOOST_CHECK_EQUAL(m.getData(), "pfoo");
    BOOST_CHECK(c2.subs.get(m, "p", TIMEOUT));
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
    BOOST_CHECK(c1.subs.get(msg, "q", TIMEOUT));
    BOOST_CHECK_EQUAL(string("foo"), msg.getData());
    BOOST_CHECK(c1.subs.get(msg, "q", TIMEOUT));
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
    Client c0(cluster[0]);
    BOOST_REQUIRE_EQUAL(knownBrokerPorts(c0.connection, 3).size(), 3u); // Wait for brokers.

    // First start a subscription.
    c0.session.queueDeclare("q");
    c0.subs.subscribe(c0.lq, "q", FlowControl::messageCredit(2));

    // Now send messages
    Client c1(cluster[1]);
    c1.session.messageTransfer(arg::content=Message("foo", "q"));
    c1.session.messageTransfer(arg::content=Message("bar", "q"));

    // Check they arrived
    Message m;
    BOOST_CHECK(c0.lq.get(m, TIMEOUT));
    BOOST_CHECK_EQUAL("foo", m.getData());
    BOOST_CHECK(c0.lq.get(m, TIMEOUT));
    BOOST_CHECK_EQUAL("bar", m.getData());

    // Queue should be empty on all cluster members.
    Client c2(cluster[2]);
    BOOST_CHECK_EQUAL(0u, c0.session.queueQuery("q").getMessageCount());
    BOOST_CHECK_EQUAL(0u, c1.session.queueQuery("q").getMessageCount());
    BOOST_CHECK_EQUAL(0u, c2.session.queueQuery("q").getMessageCount());
}

QPID_AUTO_TEST_CASE(testHeartbeatCancelledOnFailover) 
{
    struct Sender : FailoverManager::Command
    {
        std::string queue;
        std::string content;

        Sender(const std::string& q, const std::string& c) : queue(q), content(c) {}

        void execute(AsyncSession& session, bool)
        {
            session.messageTransfer(arg::content=Message(content, queue));
        }
    };

    struct Receiver : FailoverManager::Command, MessageListener, qpid::sys::Runnable
    {
        FailoverManager& mgr;
        std::string queue;
        std::string expectedContent;
        qpid::client::Subscription subscription;
        qpid::sys::Monitor lock;
        bool ready;

        Receiver(FailoverManager& m, const std::string& q, const std::string& c) : mgr(m), queue(q), expectedContent(c), ready(false) {}

        void received(Message& message) 
        {
            BOOST_CHECK_EQUAL(expectedContent, message.getData());
            subscription.cancel();
        }

        void execute(AsyncSession& session, bool)
        {
            session.queueDeclare(arg::queue=queue);
            SubscriptionManager subs(session);
            subscription = subs.subscribe(*this, queue);
            session.sync();
            setReady();
            subs.run();
            //cleanup:
            session.queueDelete(arg::queue=queue);
        }

        void run()
        {
            mgr.execute(*this);
        }

        void waitForReady()
        {
            qpid::sys::Monitor::ScopedLock l(lock);
            while (!ready) {
                lock.wait();
            }
        }

        void setReady()
        {
            qpid::sys::Monitor::ScopedLock l(lock);
            ready = true;
            lock.notify();
        }
    };

    ClusterFixture cluster(2);
    ConnectionSettings settings;
    settings.port = cluster[1];
    settings.heartbeat = 1;
    FailoverManager fmgr(settings);
    Sender sender("my-queue", "my-data");
    Receiver receiver(fmgr, "my-queue", "my-data");
    qpid::sys::Thread runner(receiver);
    receiver.waitForReady();
    cluster.kill(1);
    //sleep for 2 secs to allow the heartbeat task to fire on the now dead connection:
    ::usleep(2*1000*1000);
    fmgr.execute(sender);
    runner.join();
    fmgr.close();
}

QPID_AUTO_TEST_SUITE_END()
