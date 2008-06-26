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
#include "BrokerFixture.h"

#include "qpid/cluster/Cpg.h"
#include "qpid/framing/AMQBody.h"

#include <boost/bind.hpp>
#include <boost/ptr_container/ptr_vector.hpp>

#include <string>
#include <iostream>
#include <iterator>
#include <vector>
#include <algorithm>

QPID_AUTO_TEST_SUITE(CpgTestSuite)


using namespace std;
using namespace qpid::cluster;
using namespace qpid::framing;
using namespace qpid::client;
using boost::ptr_vector;

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


QPID_AUTO_TEST_CASE(CpgMulti) {
    // Verify using multiple handles in one process.
    //
    Cpg::Name group("CpgMulti");
    Callback cb1(group.str());
    Cpg cpg1(cb1);

    Callback cb2(group.str());
    Cpg cpg2(cb2);
    
    cpg1.join(group);
    cpg2.join(group);
    iovec iov1 = { (void*)"Hello1", 6 };
    iovec iov2 = { (void*)"Hello2", 6 };
    cpg1.mcast(group, &iov1, 1);
    cpg2.mcast(group, &iov2, 1);
    cpg1.leave(group);
    cpg2.leave(group);

    cpg1.dispatchSome();
    BOOST_REQUIRE_EQUAL(2u, cb1.delivered.size());
    BOOST_CHECK_EQUAL("Hello1", cb1.delivered[0]);
    BOOST_CHECK_EQUAL("Hello2", cb1.delivered[1]);

    cpg2.dispatchSome();
    BOOST_REQUIRE_EQUAL(2u, cb1.delivered.size());
    BOOST_CHECK_EQUAL("Hello1", cb1.delivered[0]);
    BOOST_CHECK_EQUAL("Hello2", cb1.delivered[1]);
}

// Test cluster of BrokerFixtures.
struct ClusterFixture : public ptr_vector<BrokerFixture> {
    ClusterFixture(size_t n=0) { add(n); }
    void add(size_t n) { for (size_t i=0; i < n; ++i) add(); }
    void add();
};

void ClusterFixture::add() {
    qpid::broker::Broker::Options opts;
    // Assumes the cluster plugin is loaded.
    qpid::Plugin::Factory::addOptions(opts);
    const char* argv[] = { "--cluster-name", ::getenv("USERNAME") };
    // FIXME aconway 2008-06-26: fix parse() signature, should not need cast.
    opts.parse(sizeof(argv)/sizeof(*argv), const_cast<char**>(argv));
    push_back(new BrokerFixture(opts));
}

#if 0                           // FIXME aconway 2008-06-26: TODO


QPID_AUTO_TEST_CASE(testWiringReplication) {
    const size_t SIZE=3;
    ClusterFixture cluster(SIZE);
    Client c0(cluster[0].getPort());
    BOOST_CHECK(c0.session.queueQuery("q").getQueue().empty());
    BOOST_CHECK(c0.session.exchangeQuery("ex").getType().empty()); 
    c0.session.queueDeclare("q");
    c0.session.exchangeDeclare("ex", arg::type="direct");
    BOOST_CHECK_EQUAL("q", c0.session.queueQuery("q").getQueue());
    BOOST_CHECK_EQUAL("direct", c0.session.exchangeQuery("ex").getType());

    // Verify all brokers get wiring update.
    for (size_t i = 1; i < cluster.size(); ++i) {
        Client c(cluster[i].getPort());
        BOOST_CHECK_EQUAL("q", c.session.queueQuery("q").getQueue());
        BOOST_CHECK_EQUAL("direct", c.session.exchangeQuery("ex").getType());
    }    
}

QPID_AUTO_TEST_CASE(testMessageReplication) {
    // Enqueue on one broker, dequeue on another.
    ClusterConnections cluster;
    BOOST_REQUIRE(cluster.size() > 1);

    Session broker0 = cluster[0]->newSession();
    broker0.queueDeclare(queue="q");
    broker0.messageTransfer(content=TransferContent("data", "q"));
    broker0.close();
    
    Session broker1 = cluster[1]->newSession();
    broker1.
        c.session.messageSubscribe(queue="q", destination="q");
        c.session.messageFlow(destination="q", unit=0, value=1);//messages
        FrameSet::shared_ptr msg = c.session.get();
        BOOST_CHECK(msg->isA<MessageTransferBody>());
        BOOST_CHECK_EQUAL(string("data"), msg->getContent());
        c.session.getExecution().completed(msg->getId(), true, true);
        cluster[i]->close();
    }    
}

// TODO aconway 2008-06-25: dequeue replication, exactly once delivery, failover.

#endif

QPID_AUTO_TEST_SUITE_END()
