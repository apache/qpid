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
#include "qpid/cluster/Cpg.h"
#include "qpid/framing/AMQBody.h"

#include <boost/bind.hpp>
#include "unit_test.h"

#include <string>
#include <iostream>
#include <iterator>
#include <vector>
#include <algorithm>

QPID_AUTO_TEST_SUITE(CpgTestSuite)


using namespace std;
using namespace qpid::cluster;
using namespace qpid::framing;

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


QPID_AUTO_TEST_SUITE_END()
