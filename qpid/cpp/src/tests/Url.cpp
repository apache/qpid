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
#include "qpid/Url.h"
#include <boost/assign.hpp>

using namespace std;
using namespace qpid;
using namespace boost::assign;

namespace qpid {
namespace tests {

QPID_AUTO_TEST_SUITE(UrlTestSuite)

#define URL_CHECK_STR(STR) BOOST_CHECK_EQUAL(Url(STR).str(), STR)
#define URL_CHECK_INVALID(STR) BOOST_CHECK_THROW(Url(STR), Url::Invalid)

QPID_AUTO_TEST_CASE(TestParseTcp) {
    URL_CHECK_STR("amqp:tcp:host:42");
    URL_CHECK_STR("amqp:tcp:host-._~%ff%23:42"); // unreserved chars and pct encoded hex.
    // Check defaults
    BOOST_CHECK_EQUAL(Url("amqp:host:42").str(), "amqp:tcp:host:42");
    BOOST_CHECK_EQUAL(Url("amqp:tcp:host").str(), "amqp:tcp:host:5672");
    BOOST_CHECK_EQUAL(Url("host").str(), "amqp:tcp:host:5672");
}

QPID_AUTO_TEST_CASE(TestParseInvalid) {
    //host is required:
    URL_CHECK_INVALID("amqp:tcp:");
    URL_CHECK_INVALID("amqp:");
    URL_CHECK_INVALID("amqp::42");
    URL_CHECK_INVALID("");

    // Port must be numeric
    URL_CHECK_INVALID("host:badPort");
}

QPID_AUTO_TEST_CASE(TestParseXyz) {
    Url::addProtocol("xyz");
    URL_CHECK_STR("amqp:xyz:host:123");
    BOOST_CHECK_EQUAL(Url("xyz:host").str(), "amqp:xyz:host:5672");
}

QPID_AUTO_TEST_CASE(TestParseTricky) {
    BOOST_CHECK_EQUAL(Url("amqp").str(), "amqp:tcp:amqp:5672");
    BOOST_CHECK_EQUAL(Url("amqp:tcp").str(), "amqp:tcp:tcp:5672");
    // These are ambiguous parses and arguably not the best result
    BOOST_CHECK_EQUAL(Url("amqp:876").str(), "amqp:tcp:876:5672");
    BOOST_CHECK_EQUAL(Url("tcp:567").str(), "amqp:tcp:567:5672");
}

QPID_AUTO_TEST_CASE(TestParseIPv6) {
    Url u1("[::]");
    BOOST_CHECK_EQUAL(u1[0].host, "::");
    BOOST_CHECK_EQUAL(u1[0].port, 5672);
    Url u2("[::1]");
    BOOST_CHECK_EQUAL(u2[0].host, "::1");
    BOOST_CHECK_EQUAL(u2[0].port, 5672);
    Url u3("[::127.0.0.1]");
    BOOST_CHECK_EQUAL(u3[0].host, "::127.0.0.1");
    BOOST_CHECK_EQUAL(u3[0].port, 5672);
    Url u4("[2002::222:68ff:fe0b:e61a]");
    BOOST_CHECK_EQUAL(u4[0].host, "2002::222:68ff:fe0b:e61a");
    BOOST_CHECK_EQUAL(u4[0].port, 5672);
    Url u5("[2002::222:68ff:fe0b:e61a]:123");
    BOOST_CHECK_EQUAL(u5[0].host, "2002::222:68ff:fe0b:e61a");
    BOOST_CHECK_EQUAL(u5[0].port, 123);
}

QPID_AUTO_TEST_CASE(TestParseMultiAddress) {
    Url::addProtocol("xyz");
    URL_CHECK_STR("amqp:tcp:host:0,xyz:foo:123,tcp:foo:0,xyz:bar:1");
    URL_CHECK_STR("amqp:xyz:foo:222,tcp:foo:0");
    URL_CHECK_INVALID("amqp:tcp:h:0,");
    URL_CHECK_INVALID(",amqp:tcp:h");
}

QPID_AUTO_TEST_CASE(TestParseUserPass) {
    URL_CHECK_STR("amqp:user/pass@tcp:host:123");
    URL_CHECK_STR("amqp:user@tcp:host:123");
    BOOST_CHECK_EQUAL(Url("user/pass@host").str(), "amqp:user/pass@tcp:host:5672");
    BOOST_CHECK_EQUAL(Url("user@host").str(), "amqp:user@tcp:host:5672");

    Url u("user/pass@host");
    BOOST_CHECK_EQUAL(u.getUser(), "user");
    BOOST_CHECK_EQUAL(u.getPass(), "pass");
    Url v("foo@host");
    BOOST_CHECK_EQUAL(v.getUser(), "foo");
    BOOST_CHECK_EQUAL(v.getPass(), "");
    u = v;
    BOOST_CHECK_EQUAL(u.getUser(), "foo");
    BOOST_CHECK_EQUAL(u.getPass(), "");
}

QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests
