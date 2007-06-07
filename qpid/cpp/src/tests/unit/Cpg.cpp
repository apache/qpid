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

#define BOOST_AUTO_TEST_MAIN    // Must come before #include<boost/test/*>
#include "test_tools.h"
#include "qpid/cluster/Cpg.h"
#include <string>
#include <iostream>
#include <iterator>
#include <vector>
#include <algorithm>

using namespace std;
using namespace qpid::cluster;

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
    cout << "}";
    return o;
}

const string testGroup("foo");
vector<string> delivered;
vector<int> configChanges;

void testDeliver (
    cpg_handle_t /*handle*/,
    struct cpg_name *group,
    uint32_t /*nodeid*/,
    uint32_t /*pid*/,
    void* msg,
    int msg_len)
{
    BOOST_CHECK_EQUAL(testGroup, Cpg::str(*group));
    delivered.push_back(string((char*)msg,msg_len));
}

void testConfigChange(
    cpg_handle_t /*handle*/,
    struct cpg_name *group,
    struct cpg_address */*members*/, int nMembers,
    struct cpg_address */*left*/, int /*nLeft*/,
    struct cpg_address */*joined*/, int /*nJoined*/
)
{
    BOOST_CHECK_EQUAL(testGroup, Cpg::str(*group));
    configChanges.push_back(nMembers);
}

BOOST_AUTO_TEST_CASE(basic) {
    // Verify basic functionality of cpg. This will catch any
    // openais configuration or permission errors.
    // 
    Cpg cpg(&testDeliver, &testConfigChange);
    Cpg::Name group("foo");

    cpg.join(group);
    iovec iov = { (void*)"Hello!", 6 };
    cpg.mcast(group, &iov, 1);
    cpg.leave(group);

    cpg.dispatch(CPG_DISPATCH_ONE); // Wait for at least one.
    cpg.dispatch(CPG_DISPATCH_ALL);
    BOOST_REQUIRE_EQUAL(1u, delivered.size());
    BOOST_CHECK_EQUAL("Hello!", delivered.front());
    BOOST_REQUIRE_EQUAL(2u, configChanges.size());
    BOOST_CHECK_EQUAL(1, configChanges[0]);
    BOOST_CHECK_EQUAL(0, configChanges[1]);
}
