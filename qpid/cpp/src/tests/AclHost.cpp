/*
 *
 * Copyright (c) 2014 The Apache Software Foundation
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
#include "qpid/AclHost.h"
#include "qpid/sys/SocketAddress.h"
#include <boost/assign.hpp>

using namespace std;
using namespace qpid;
using namespace boost::assign;

namespace qpid {
namespace tests {

QPID_AUTO_TEST_SUITE(AclHostTestSuite)

#define ACLURL_CHECK_INVALID(STR) BOOST_CHECK_THROW(AclHost(STR), AclHost::Invalid)

#define SENSE_IP_VERSIONS() \
  bool haveIPv4(true); \
  try { \
    sys::SocketAddress sa("1.1.1.1", ""); \
    sa.firstAddress(); \
} catch (qpid::Exception) { \
    haveIPv4 = false; \
} \
  bool haveIPv6(true); \
  try { \
    sys::SocketAddress sa("::1", ""); \
    sa.firstAddress(); \
} catch (qpid::Exception) { \
    haveIPv6 = false; \
} \
(void) haveIPv4; \
(void) haveIPv6;

QPID_AUTO_TEST_CASE(TestParseTcpIPv4) {
    SENSE_IP_VERSIONS();
    if (haveIPv4) {
        BOOST_CHECK_EQUAL(AclHost("1.1.1.1").str(),         "(1.1.1.1,1.1.1.1)");
        BOOST_CHECK_EQUAL(AclHost("1.1.1.1,2.2.2.2").str(), "(1.1.1.1,2.2.2.2)");
    }
}

QPID_AUTO_TEST_CASE(TestParseTcpIPv6) {
    SENSE_IP_VERSIONS();
    if (haveIPv6) {
        BOOST_CHECK_EQUAL(AclHost("[::1]").str(),         "([::1],[::1])");
        BOOST_CHECK_EQUAL(AclHost("[::1],::5").str(),     "([::1],[::5])");
    }
}

QPID_AUTO_TEST_CASE(TestParseAll) {
    SENSE_IP_VERSIONS();
    if (haveIPv4 || haveIPv6) {
        BOOST_CHECK_EQUAL(AclHost("").str(), "(all)");
    }
}

QPID_AUTO_TEST_CASE(TestInvalidMixedIpFamilies) {
    SENSE_IP_VERSIONS();
    if (haveIPv4 && haveIPv6) {
        ACLURL_CHECK_INVALID("1.1.1.1,[::1]");
        ACLURL_CHECK_INVALID("[::1],1.1.1.1");
    }
}

QPID_AUTO_TEST_CASE(TestMalformedIPv4) {
    SENSE_IP_VERSIONS();
    if (haveIPv4) {
        ACLURL_CHECK_INVALID("1.1.1.1.1");
        ACLURL_CHECK_INVALID("1.1.1.777");
        ACLURL_CHECK_INVALID("1.1.1.1abcd");
        ACLURL_CHECK_INVALID("1.1.1.*");
    }
}

QPID_AUTO_TEST_CASE(TestRangeWithInvertedSizeOrder) {
    SENSE_IP_VERSIONS();
    if (haveIPv4) {
        ACLURL_CHECK_INVALID("1.1.1.100,1.1.1.1");
    }
    if (haveIPv6) {
        ACLURL_CHECK_INVALID("[FF::1],[::1]");
    }
}

QPID_AUTO_TEST_CASE(TestSingleHostResolvesMultipleAddresses) {
    SENSE_IP_VERSIONS();
    AclHost XX("localhost");
}

QPID_AUTO_TEST_CASE(TestMatchSingleAddresses) {
    SENSE_IP_VERSIONS();
    if (haveIPv4) {
        AclHost host1("1.1.1.1");
        BOOST_CHECK(host1.match("1.1.1.1") == true);
        BOOST_CHECK(host1.match("1.2.1.1") == false);
    }
    if (haveIPv6) {
        AclHost host2("FF::1");
        BOOST_CHECK(host2.match("00FF:0000::1") == true);
    }
}

QPID_AUTO_TEST_CASE(TestMatchIPv4Range) {
    SENSE_IP_VERSIONS();
    if (haveIPv4) {
        AclHost host1("192.168.0.0,192.168.255.255");
        BOOST_CHECK(host1.match("128.1.1.1")       == false);
        BOOST_CHECK(host1.match("192.167.255.255") == false);
        BOOST_CHECK(host1.match("192.168.0.0")     == true);
        BOOST_CHECK(host1.match("192.168.0.1")     == true);
        BOOST_CHECK(host1.match("192.168.1.0")     == true);
        BOOST_CHECK(host1.match("192.168.255.254") == true);
        BOOST_CHECK(host1.match("192.168.255.255") == true);
        BOOST_CHECK(host1.match("192.169.0.0")     == false);
        if (haveIPv6) {
            BOOST_CHECK(host1.match("::1")             == false);
        }
    }
}

QPID_AUTO_TEST_CASE(TestMatchIPv6Range) {
    SENSE_IP_VERSIONS();
    if (haveIPv6) {
        AclHost host1("::10,::1:0");
        BOOST_CHECK(host1.match("::1")     == false);
        BOOST_CHECK(host1.match("::f")     == false);
        BOOST_CHECK(host1.match("::10")    == true);
        BOOST_CHECK(host1.match("::11")    == true);
        BOOST_CHECK(host1.match("::ffff")  == true);
        BOOST_CHECK(host1.match("::1:0")   == true);
        BOOST_CHECK(host1.match("::1:1")   == false);
        if (haveIPv4) {
            BOOST_CHECK(host1.match("192.169.0.0")     == false);
        }
        AclHost host2("[fc00::],[fc00::ff]");
        BOOST_CHECK(host2.match("fc00::")     == true);
        BOOST_CHECK(host2.match("fc00::1")    == true);
        BOOST_CHECK(host2.match("fc00::ff")   == true);
        BOOST_CHECK(host2.match("fc00::100")  == false);

    }
}
QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests
