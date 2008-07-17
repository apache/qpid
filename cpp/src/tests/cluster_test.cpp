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
#include "qpid/framing/AMQBody.h"
#include "qpid/client/Connection.h"
#include "qpid/client/Session.h"
#include "qpid/framing/Uuid.h"

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
};

ClusterFixture::ClusterFixture(size_t n) : name(Uuid(true).str()) {
    add(n);
    // Wait for all n members to join the cluster
    int retry=20;            // TODO aconway 2008-07-16: nasty sleeps, clean this up.
    while (retry && getGlobalCluster()->size() != n) {
        ::sleep(1);
        --retry;
    }
    BOOST_CHECK_EQUAL(n, getGlobalCluster()->size());
}

void ClusterFixture::add() {
    std::ostringstream os;
    os << "broker" << size();
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

struct Callback : public Cpg::Handler {
    Callback(const string group_) : group(group_) {}
    string group;
    vector<string> delivered;
    vector<int> configChanges;

    void deliver (
        cpg_handle_t /*handle*/,
        struct cpg_name *grp,
        uint32_t /*nodeid*/,
        uint32_t /*pid*/,
        void* msg,
        int msg_len)
    {
        BOOST_CHECK_EQUAL(group, Cpg::str(*grp));
        delivered.push_back(string((char*)msg,msg_len));
    }

    void configChange(
        cpg_handle_t /*handle*/,
        struct cpg_name *grp,
        struct cpg_address */*members*/, int nMembers,
        struct cpg_address */*left*/, int nLeft,
        struct cpg_address */*joined*/, int nJoined
    )
    {
        BOOST_CHECK_EQUAL(group, Cpg::str(*grp));
        configChanges.push_back(nMembers);
        BOOST_MESSAGE("configChange: "<<
                      nLeft<<" left "<<
                      nJoined<<" joined "<<
                      nMembers<<" members.");
    }
};

QPID_AUTO_TEST_CASE(CpgBasic) {
    // Verify basic functionality of cpg. This will catch any
    // openais configuration or permission errors.
    //
    Cpg::Name group("CpgBasic");
    Callback cb(group.str());
    Cpg cpg(cb);
    cpg.join(group);
    iovec iov = { (void*)"Hello!", 6 };
    cpg.mcast(group, &iov, 1);
    cpg.leave(group);
    cpg.dispatchSome();

    BOOST_REQUIRE_EQUAL(1u, cb.delivered.size());
    BOOST_CHECK_EQUAL("Hello!", cb.delivered.front());
    BOOST_REQUIRE_EQUAL(2u, cb.configChanges.size());
    BOOST_CHECK_EQUAL(1, cb.configChanges[0]);
    BOOST_CHECK_EQUAL(0, cb.configChanges[1]);
}


QPID_AUTO_TEST_CASE(testForkedBroker) {
    // Verify the ForkedBroker works as expected.
    const char* argv[] = { "", "--auth=no", "--no-data-dir", "--log-prefix=testForkedBroker" };
    ForkedBroker broker(sizeof(argv)/sizeof(argv[0]), argv);
    Client c(broker.getPort());
    BOOST_CHECK_EQUAL("direct", c.session.exchangeQuery("amq.direct").getType()); 
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

#if 0

// FIXME aconway 2008-07-16: Implement cluster dequeue notification, enable this test.

QPID_AUTO_TEST_CASE(testMessageDequeue) {
    // Enqueue on one broker, dequeue on two others.
    ClusterFixture cluster (3);
    Client c0(cluster[0]);
    c0.session.queueDeclare("q");
    c0.session.messageTransfer(arg::content=TransferContent("foo", "q"));
    c0.session.messageTransfer(arg::content=TransferContent("bar", "q"));
    c0.session.close();

    Message msg;

    Client c1(cluster[1]);
    BOOST_CHECK(c1.subs.get(msg, "q"));
    BOOST_CHECK_EQUAL("foo", msg.getData());
    
    Client c2(cluster[2]);
    BOOST_CHECK(c1.subs.get(msg, "q"));
    BOOST_CHECK_EQUAL("bar", msg.getData());
    QueueQueryResult r = c2.session.queueQuery("q");
    BOOST_CHECK_EQUAL(0u, r.getMessageCount());
}

// TODO aconway 2008-06-25: failover.

#endif

QPID_AUTO_TEST_SUITE_END()
