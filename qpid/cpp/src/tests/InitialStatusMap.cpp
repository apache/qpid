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


#include "unit_test.h"
#include "test_tools.h"
#include "qpid/cluster/InitialStatusMap.h"
#include <boost/assign.hpp>

using namespace std;
using namespace qpid::cluster;
using namespace qpid::framing;
using namespace boost::assign;

namespace qpid {
namespace tests {

QPID_AUTO_TEST_SUITE(InitialStatusMapTestSuite)

typedef InitialStatusMap::Status Status;

Status activeStatus() { return Status(ProtocolVersion(), true, false, FieldTable()); }
Status newcomerStatus() { return Status(ProtocolVersion(), false, false, FieldTable()); }

QPID_AUTO_TEST_CASE(testFirstInCluster) {
    // Single member is first in cluster.
    InitialStatusMap map(MemberId(0));
    BOOST_CHECK(!map.isComplete());
    MemberSet members = list_of(MemberId(0));
    map.configChange(members);
    BOOST_CHECK(!map.isComplete());
    map.received(MemberId(0), newcomerStatus());
    BOOST_CHECK(map.isComplete());
    BOOST_CHECK(map.getElders().empty());
    BOOST_CHECK(!map.isUpdateNeeded());
}

QPID_AUTO_TEST_CASE(testJoinExistingCluster) {
    // Single member 0 joins existing cluster 1,2
    InitialStatusMap map(MemberId(0));
    MemberSet members = list_of(MemberId(0))(MemberId(1))(MemberId(2));
    map.configChange(members);
    BOOST_CHECK(map.isResendNeeded());
    BOOST_CHECK(!map.isComplete());
    map.received(MemberId(0), newcomerStatus());
    map.received(MemberId(1), activeStatus());
    BOOST_CHECK(!map.isComplete());
    map.received(MemberId(2), activeStatus());
    BOOST_CHECK(map.isComplete());
    BOOST_CHECK_EQUAL(map.getElders(), list_of<MemberId>(1)(2));
    BOOST_CHECK(map.isUpdateNeeded());
}

QPID_AUTO_TEST_CASE(testMultipleFirstInCluster) {
    // Multiple members 0,1,2 join at same time.
    InitialStatusMap map(MemberId(1)); // self is 1
    MemberSet members = list_of(MemberId(0))(MemberId(1))(MemberId(2));
    map.configChange(members);
    BOOST_CHECK(map.isResendNeeded());

    // All new members
    map.received(MemberId(0), newcomerStatus());
    map.received(MemberId(1), newcomerStatus());
    map.received(MemberId(2), newcomerStatus());
    BOOST_CHECK(!map.isResendNeeded());
    BOOST_CHECK(map.isComplete());
    BOOST_CHECK_EQUAL(map.getElders(), list_of(MemberId(2)));
    BOOST_CHECK(!map.isUpdateNeeded());
}

QPID_AUTO_TEST_CASE(testMultipleJoinExisting) {
    // Multiple members 1,2,3 join existing cluster containing 0.
    InitialStatusMap map(MemberId(2)); // self is 2
    MemberSet members = list_of(MemberId(0))(MemberId(1))(MemberId(2))(MemberId(3));
    map.configChange(members);
    BOOST_CHECK(map.isResendNeeded());

    map.received(MemberId(1), newcomerStatus());
    map.received(MemberId(2), newcomerStatus());
    map.received(MemberId(3), newcomerStatus());
    map.received(MemberId(0), activeStatus());
    BOOST_CHECK(!map.isResendNeeded());
    BOOST_CHECK(map.isComplete());
    BOOST_CHECK_EQUAL(map.getElders(), list_of(MemberId(0))(MemberId(3)));
    BOOST_CHECK(map.isUpdateNeeded());
}

QPID_AUTO_TEST_CASE(testMembersLeave) {
    // Test that map completes if members leave rather than send status.
    InitialStatusMap map(MemberId(0));
    map.configChange(list_of(MemberId(0))(MemberId(1))(MemberId(2)));
    map.received(MemberId(0), newcomerStatus());
    map.received(MemberId(1), activeStatus());
    BOOST_CHECK(!map.isComplete());
    map.configChange(list_of(MemberId(0))(MemberId(1))); // 2 left
    BOOST_CHECK(map.isComplete());
    BOOST_CHECK_EQUAL(map.getElders(), list_of(MemberId(1)));
}

QPID_AUTO_TEST_CASE(testInteveningConfig) {
    // Multiple config changes arrives before we complete the map.
    InitialStatusMap map(MemberId(0));

    map.configChange(list_of<MemberId>(0)(1));
    BOOST_CHECK(map.isResendNeeded());
    map.received(MemberId(0), newcomerStatus());
    BOOST_CHECK(!map.isComplete());
    BOOST_CHECK(!map.isResendNeeded());
    // New member 2 joins before we receive 1
    map.configChange(list_of<MemberId>(0)(1)(2));
    BOOST_CHECK(!map.isComplete());
    BOOST_CHECK(map.isResendNeeded());
    map.received(1, activeStatus());
    map.received(2, newcomerStatus());
    // We should not be complete as we haven't received 0 since new member joined
    BOOST_CHECK(!map.isComplete());
    BOOST_CHECK(!map.isResendNeeded());

    map.received(0, newcomerStatus());
    BOOST_CHECK(map.isComplete());
    BOOST_CHECK_EQUAL(map.getElders(), list_of<MemberId>(1));
}

QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests
