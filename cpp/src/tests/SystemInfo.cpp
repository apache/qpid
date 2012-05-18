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
#include "qpid/sys/SystemInfo.h"
#include <boost/assign.hpp>

using namespace std;
using namespace qpid::sys;
using namespace boost::assign;

namespace qpid {
namespace tests {

QPID_AUTO_TEST_SUITE(SystemInfoTestSuite)

QPID_AUTO_TEST_CASE(TestIsLocalHost) {
    // Test that local hostname and addresses are considered local
    Address a;
    BOOST_ASSERT(SystemInfo::getLocalHostname(a));
    BOOST_ASSERT(SystemInfo::isLocalHost(a.host));
    std::vector<Address> addrs;
    SystemInfo::getLocalIpAddresses(0, addrs);
    for (std::vector<Address>::iterator i = addrs.begin(); i != addrs.end(); ++i)
        BOOST_ASSERT(SystemInfo::isLocalHost(i->host));
    // Check some non-local addresses
    BOOST_ASSERT(!SystemInfo::isLocalHost("123.4.5.6"));
    BOOST_ASSERT(!SystemInfo::isLocalHost("nosuchhost"));
    BOOST_ASSERT(SystemInfo::isLocalHost("127.0.0.1"));
    BOOST_ASSERT(SystemInfo::isLocalHost("::1"));
}

QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests
