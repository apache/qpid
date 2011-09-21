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

///@file
// Tests using a dummy broker::Cluster implementation to verify the expected
// Cluster functions are called for various actions on the broker.
//

#include "unit_test.h"
#include "test_tools.h"
#include "qpid/broker/Cluster.h"
#include "qpid/broker/Queue.h"
#include "qpid/client/Connection.h"
#include "qpid/client/Session.h"
#include "qpid/messaging/Connection.h"
#include "qpid/messaging/Session.h"
#include "qpid/messaging/Sender.h"
#include "qpid/messaging/Receiver.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/Duration.h"
#include "BrokerFixture.h"
#include <boost/assign.hpp>
#include <boost/format.hpp>

using namespace std;
using namespace boost;
using namespace boost::assign;
using namespace qpid::messaging;
using boost::format;
using boost::intrusive_ptr;

namespace qpid {
namespace tests {

class DummyCluster : public broker::Cluster
{
  private:
    /** Flag used to ignore events other than enqueues while routing,
     * e.g. acquires and accepts generated in a ring queue to replace an element..
     * In real impl would be a thread-local variable.
     */
    bool isRouting;

    // Record a QueuedMessage
    void recordQm(const string& op, const broker::QueuedMessage& qm) {
        history += (format("%s(%s, %d, %s)") % op % qm.queue->getName()
                    % qm.position % qm.payload->getFrames().getContent()).str();
    }

    // Record a message
    void recordMsg(const string& op, broker::Queue& q, intrusive_ptr<broker::Message> msg) {
        history += (format("%s(%s, %s)") % op % q.getName() % msg->getFrames().getContent()).str();
    }

    // Record a string
    void recordStr(const string& op, const string& name) {
        history += (format("%s(%s)") % op % name).str();
    }
  public:
    // Messages

    virtual void routing(const boost::intrusive_ptr<broker::Message>& m) {
        isRouting = true;
        history += (format("routing(%s)") % m->getFrames().getContent()).str();
    }

    virtual bool enqueue(broker::Queue& q, const intrusive_ptr<broker::Message>&msg) {
        recordMsg("enqueue", q, msg);
        return true;
    }

    virtual void routed(const boost::intrusive_ptr<broker::Message>& m) {
        history += (format("routed(%s)") % m->getFrames().getContent()).str();
        isRouting = false;
    }
    virtual void acquire(const broker::QueuedMessage& qm) {
        if (!isRouting) recordQm("acquire", qm);
    }
    virtual void release(const broker::QueuedMessage& qm) {
        if (!isRouting) recordQm("release", qm);
    }
    virtual bool dequeue(const broker::QueuedMessage& qm) {
        if (!isRouting) recordQm("dequeue", qm);
    }

    // Consumers

    virtual void consume(broker::Queue& q, size_t n) {
        history += (format("consume(%s, %d)") % q.getName() % n).str();
    }
    virtual void cancel(broker::Queue& q, size_t n) {
        history += (format("cancel(%s, %d)") % q.getName() % n).str();
    }

    // Queues
    // FIXME aconway 2011-05-18: update test to exercise empty()
    virtual void empty(broker::Queue& q) { recordStr("empty", q.getName()); }
    virtual void stopped(broker::Queue& q) { recordStr("stopped", q.getName()); }

    // Wiring

    virtual void create(broker::Queue& q) { recordStr("createq", q.getName()); }
    virtual void destroy(broker::Queue& q) { recordStr("destroyq", q.getName()); }
    virtual void create(broker::Exchange& ex) { recordStr("createex", ex.getName()); }
    virtual void destroy(broker::Exchange& ex) { recordStr("destroyex", ex.getName()); }
    virtual void bind(broker::Queue& q, broker::Exchange& ex, const std::string& key, const framing::FieldTable& /*args*/) {
        history += (format("bind(%s, %s, %s)") % q.getName() % ex.getName() % key).str();
    }
    virtual void unbind(broker::Queue& q, broker::Exchange& ex, const std::string& key, const framing::FieldTable& /*args*/) {
        history += (format("unbind(%s, %s, %s)")% q.getName()%ex.getName()%key).str();
    }
    vector<string> history;
};

QPID_AUTO_TEST_SUITE(BrokerClusterCallsTestSuite)

// Broker fixture with DummyCluster set up and some new API client bits.
struct DummyClusterFixture: public BrokerFixture {
    Connection c;
    Session s;
    DummyCluster*dc;
    DummyClusterFixture() {
        broker->setCluster(auto_ptr<broker::Cluster>(new DummyCluster));
        dc = &static_cast<DummyCluster&>(broker->getCluster());
        c = Connection("localhost:"+lexical_cast<string>(getPort()));
        c.open();
        s = c.createSession();
    }
    ~DummyClusterFixture() {
        c.close();
    }
};

QPID_AUTO_TEST_CASE(testSimplePubSub) {
    DummyClusterFixture f;
    vector<string>& h = f.dc->history;

    // Queue creation
    Sender sender = f.s.createSender("q;{create:always,delete:always}");
    size_t i = 0;
    BOOST_CHECK_EQUAL(h.at(i++), "createq(q)"); // Note: at() does bounds checking.
    BOOST_CHECK_EQUAL(h.size(), i);

    // Consumer
    Receiver receiver = f.s.createReceiver("q");
    f.s.sync();
    BOOST_CHECK_EQUAL(h.at(i++), "consume(q, 1)");
    BOOST_CHECK_EQUAL(h.size(), i);

    // Send message
    sender.send(Message("a"));
    f.s.sync();
    BOOST_CHECK_EQUAL(h.at(i++), "routing(a)");
    BOOST_CHECK_EQUAL(h.at(i++), "enqueue(q, a)");
    BOOST_CHECK_EQUAL(h.at(i++), "routed(a)");
    // Don't check size here as it is uncertain whether acquire has happened yet.

    // Acquire message
    Message m = receiver.fetch(Duration::SECOND);
    BOOST_CHECK_EQUAL(h.at(i++), "acquire(q, 1, a)");
    BOOST_CHECK_EQUAL(h.size(), i);

    // Acknowledge message
    f.s.acknowledge(true);
    f.s.sync();
    BOOST_CHECK_EQUAL(h.at(i++), "dequeue(q, 1, a)");
    BOOST_CHECK_EQUAL(h.size(), i);

    // Close a consumer
    receiver.close();
    BOOST_CHECK_EQUAL(h.at(i++), "cancel(q, 0)");
    BOOST_CHECK_EQUAL(h.size(), i);

    // Destroy the queue
    f.c.close();
    BOOST_CHECK_EQUAL(h.at(i++), "destroyq(q)");
    BOOST_CHECK_EQUAL(h.size(), i);
}

QPID_AUTO_TEST_CASE(testReleaseReject) {
    DummyClusterFixture f;
    vector<string>& h = f.dc->history;

    Sender sender = f.s.createSender("q;{create:always,delete:always,node:{x-declare:{alternate-exchange:amq.fanout}}}");
    sender.send(Message("a"));
    Receiver receiver = f.s.createReceiver("q");
    Receiver altReceiver = f.s.createReceiver("amq.fanout;{link:{name:altq}}");
    Message m = receiver.fetch(Duration::SECOND);
    h.clear();

    // Explicit release
    f.s.release(m);
    f.s.sync();
    size_t i = 0;
    BOOST_CHECK_EQUAL(h.at(i++), "release(q, 1, a)");
    BOOST_CHECK_EQUAL(h.size(), i);

    // Implicit release on closing connection.
    Connection c("localhost:"+lexical_cast<string>(f.getPort()));
    c.open();
    Session s = c.createSession();
    Receiver r = s.createReceiver("q");
    m = r.fetch(Duration::SECOND);
    h.clear();
    i = 0;
    c.close();
    BOOST_CHECK_EQUAL(h.at(i++), "cancel(q, 1)");
    BOOST_CHECK_EQUAL(h.at(i++), "release(q, 1, a)");
    BOOST_CHECK_EQUAL(h.size(), i);

    // Reject message, goes to alternate exchange.
    m = receiver.fetch(Duration::SECOND);
    h.clear();
    i = 0;
    f.s.reject(m);
    BOOST_CHECK_EQUAL(h.at(i++), "routing(a)"); // Routing to alt exchange
    BOOST_CHECK_EQUAL(h.at(i++), "enqueue(altq, a)");
    BOOST_CHECK_EQUAL(h.at(i++), "routed(a)");
    BOOST_CHECK_EQUAL(h.at(i++), "dequeue(q, 1, a)");
    BOOST_CHECK_EQUAL(h.size(), i);
    m = altReceiver.fetch(Duration::SECOND);
    BOOST_CHECK_EQUAL(m.getContent(), "a");

    // Timed out message
    h.clear();
    i = 0;
    m = Message("t");
    m.setTtl(Duration(1));      // Timeout 1ms
    sender.send(m);
    usleep(2000);               // Sleep 2ms
    bool received = receiver.fetch(m, Duration::IMMEDIATE);
    BOOST_CHECK(!received);                     // Timed out
    BOOST_CHECK_EQUAL(h.at(i++), "routing(t)");
    BOOST_CHECK_EQUAL(h.at(i++), "enqueue(q, t)");
    BOOST_CHECK_EQUAL(h.at(i++), "routed(t)");
    BOOST_CHECK_EQUAL(h.at(i++), "dequeue(q, 2, t)");
    // FIXME aconway 2011-07-25: empty called once per receiver?
    BOOST_CHECK_EQUAL(h.at(i++), "empty(q)");
    BOOST_CHECK_EQUAL(h.at(i++), "empty(q)");
    BOOST_CHECK_EQUAL(h.size(), i);

    // Message replaced on LVQ
    sender = f.s.createSender("lvq;{create:always,delete:always,node:{x-declare:{arguments:{qpid.last_value_queue:1}}}}");
    m = Message("a");
    m.getProperties()["qpid.LVQ_key"] = "foo";
    sender.send(m);
    f.s.sync();
    BOOST_CHECK_EQUAL(h.at(i++), "createq(lvq)");
    BOOST_CHECK_EQUAL(h.at(i++), "routing(a)");
    BOOST_CHECK_EQUAL(h.at(i++), "enqueue(lvq, a)");
    BOOST_CHECK_EQUAL(h.at(i++), "routed(a)");
    BOOST_CHECK_EQUAL(h.size(), i);

    m = Message("b");
    m.getProperties()["qpid.LVQ_key"] = "foo";
    sender.send(m);
    f.s.sync();
    BOOST_CHECK_EQUAL(h.at(i++), "routing(b)");
    BOOST_CHECK_EQUAL(h.at(i++), "enqueue(lvq, b)");
    BOOST_CHECK_EQUAL(h.at(i++), "routed(b)");
    BOOST_CHECK_EQUAL(h.size(), i);

    receiver = f.s.createReceiver("lvq");
    BOOST_CHECK_EQUAL(receiver.fetch(Duration::SECOND).getContent(), "b");
    f.s.acknowledge(true);
    BOOST_CHECK_EQUAL(h.at(i++), "consume(lvq, 1)");
    BOOST_CHECK_EQUAL(h.at(i++), "acquire(lvq, 1, b)");
    BOOST_CHECK_EQUAL(h.at(i++), "dequeue(lvq, 1, b)");
    BOOST_CHECK_EQUAL(h.size(), i);
}

QPID_AUTO_TEST_CASE(testFanout) {
    DummyClusterFixture f;
    vector<string>& h = f.dc->history;

    Receiver r1 = f.s.createReceiver("amq.fanout;{link:{name:r1}}");
    Receiver r2 = f.s.createReceiver("amq.fanout;{link:{name:r2}}");
    Sender sender = f.s.createSender("amq.fanout");
    r1.setCapacity(0);          // Don't receive immediately.
    r2.setCapacity(0);
    h.clear();
    size_t i = 0;

    // Send message
    sender.send(Message("a"));
    f.s.sync();
    BOOST_CHECK_EQUAL(h.at(i++), "routing(a)");
    BOOST_CHECK_EQUAL(0u, h.at(i++).find("enqueue(r"));
    BOOST_CHECK_EQUAL(0u, h.at(i++).find("enqueue(r"));
    BOOST_CHECK(h.at(i-1) != h.at(i-2));
    BOOST_CHECK_EQUAL(h.at(i++), "routed(a)");
    BOOST_CHECK_EQUAL(h.size(), i);

    // Receive messages
    Message m1 = r1.fetch(Duration::SECOND);
    f.s.acknowledge(m1, true);
    Message m2 = r2.fetch(Duration::SECOND);
    f.s.acknowledge(m2, true);

    BOOST_CHECK_EQUAL(h.at(i++), "acquire(r1, 1, a)");
    BOOST_CHECK_EQUAL(h.at(i++), "dequeue(r1, 1, a)");
    BOOST_CHECK_EQUAL(h.at(i++), "acquire(r2, 1, a)");
    BOOST_CHECK_EQUAL(h.at(i++), "dequeue(r2, 1, a)");
    BOOST_CHECK_EQUAL(h.size(), i);
}

QPID_AUTO_TEST_CASE(testRingQueue) {
    DummyClusterFixture f;
    vector<string>& h = f.dc->history;

    // FIXME aconway 2010-10-15: QPID-2908 ring queue address string is not working,
    // so we can't do this:
    // Sender sender = f.s.createSender("ring;{create:always,node:{x-declare:{arguments:{qpid.max_size:3,qpid.policy_type:ring}}}}");
    // Must use old API to declare ring queue:
    qpid::client::Connection c;
    f.open(c);
    qpid::client::Session s = c.newSession();
    qpid::framing::FieldTable args;
    args.setInt("qpid.max_size", 3);
    args.setString("qpid.policy_type","ring");
    s.queueDeclare(qpid::client::arg::queue="ring", qpid::client::arg::arguments=args);
    c.close();
    Sender sender = f.s.createSender("ring");

    size_t i = 0;
    // Send message
    sender.send(Message("a"));
    sender.send(Message("b"));
    sender.send(Message("c"));
    sender.send(Message("d"));
    f.s.sync();

    BOOST_CHECK_EQUAL(h.at(i++), "createq(ring)");

    BOOST_CHECK_EQUAL(h.at(i++), "routing(a)");
    BOOST_CHECK_EQUAL(h.at(i++), "enqueue(ring, a)");
    BOOST_CHECK_EQUAL(h.at(i++), "routed(a)");

    BOOST_CHECK_EQUAL(h.at(i++), "routing(b)");
    BOOST_CHECK_EQUAL(h.at(i++), "enqueue(ring, b)");
    BOOST_CHECK_EQUAL(h.at(i++), "routed(b)");

    BOOST_CHECK_EQUAL(h.at(i++), "routing(c)");
    BOOST_CHECK_EQUAL(h.at(i++), "enqueue(ring, c)");
    BOOST_CHECK_EQUAL(h.at(i++), "routed(c)");

    BOOST_CHECK_EQUAL(h.at(i++), "routing(d)");
    BOOST_CHECK_EQUAL(h.at(i++), "enqueue(ring, d)");
    BOOST_CHECK_EQUAL(h.at(i++), "routed(d)");

    Receiver receiver = f.s.createReceiver("ring");
    BOOST_CHECK_EQUAL(receiver.fetch().getContent(), "b");
    BOOST_CHECK_EQUAL(receiver.fetch().getContent(), "c");
    BOOST_CHECK_EQUAL(receiver.fetch().getContent(), "d");
    f.s.acknowledge(true);

    BOOST_CHECK_EQUAL(h.at(i++), "consume(ring, 1)");
    BOOST_CHECK_EQUAL(h.at(i++), "acquire(ring, 2, b)");
    BOOST_CHECK_EQUAL(h.at(i++), "acquire(ring, 3, c)");
    BOOST_CHECK_EQUAL(h.at(i++), "acquire(ring, 4, d)");
    BOOST_CHECK_EQUAL(h.at(i++), "dequeue(ring, 2, b)");
    BOOST_CHECK_EQUAL(h.at(i++), "dequeue(ring, 3, c)");
    BOOST_CHECK_EQUAL(h.at(i++), "dequeue(ring, 4, d)");

    BOOST_CHECK_EQUAL(h.size(), i);
}

QPID_AUTO_TEST_CASE(testTransactions) {
    DummyClusterFixture f;
    vector<string>& h = f.dc->history;
    Session ts = f.c.createTransactionalSession();
    Sender sender = ts.createSender("q;{create:always,delete:always}");
    size_t i = 0;
    BOOST_CHECK_EQUAL(h.at(i++), "createq(q)"); // Note: at() does bounds checking.
    BOOST_CHECK_EQUAL(h.size(), i);

    sender.send(Message("a"));
    sender.send(Message("b"));
    ts.sync();
    BOOST_CHECK_EQUAL(h.at(i++), "routing(a)");
    BOOST_CHECK_EQUAL(h.at(i++), "routed(a)");
    BOOST_CHECK_EQUAL(h.at(i++), "routing(b)");
    BOOST_CHECK_EQUAL(h.at(i++), "routed(b)");
    BOOST_CHECK_EQUAL(h.size(), i); // Not replicated till commit
    ts.commit();
    // FIXME aconway 2010-10-18: As things stand the cluster is not
    // compatible with transactions
    // - enqueues occur after routing is complete
    // - no call to Cluster::enqueue, should be in Queue::process?
    // - no transaction context associated with messages in the Cluster interface.
    // - no call to Cluster::accept in Queue::dequeueCommitted
    // BOOST_CHECK_EQUAL(h.at(i++), "enqueue(q, a)");
    // BOOST_CHECK_EQUAL(h.at(i++), "enqueue(q, b)");
    BOOST_CHECK_EQUAL(h.size(), i);


    Receiver receiver = ts.createReceiver("q");
    BOOST_CHECK_EQUAL(receiver.fetch().getContent(), "a");
    BOOST_CHECK_EQUAL(receiver.fetch().getContent(), "b");
    ts.acknowledge();
    ts.sync();
    BOOST_CHECK_EQUAL(h.at(i++), "consume(q, 1)");
    BOOST_CHECK_EQUAL(h.at(i++), "acquire(q, 1, a)");
    BOOST_CHECK_EQUAL(h.at(i++), "acquire(q, 2, b)");
    BOOST_CHECK_EQUAL(h.size(), i);
    ts.commit();
    ts.sync();
    // BOOST_CHECK_EQUAL(h.at(i++), "accept(q, 1, a)");
    BOOST_CHECK_EQUAL(h.at(i++), "dequeue(q, 1, a)");
    // BOOST_CHECK_EQUAL(h.at(i++), "accept(q, 2, b)");
    BOOST_CHECK_EQUAL(h.at(i++), "dequeue(q, 2, b)");
    BOOST_CHECK_EQUAL(h.size(), i);
}

QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests
