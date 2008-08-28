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

QPID_AUTO_TEST_SUITE(UrlTestSuite)

QPID_AUTO_TEST_CASE(testUrl_str) {
    Url url;
    url.push_back(TcpAddress("foo.com"));
    url.push_back(TcpAddress("bar.com", 6789));
    BOOST_CHECK_EQUAL("amqp:tcp:foo.com:5672,tcp:bar.com:6789", url.str());
    BOOST_CHECK(Url().str().empty());
}


QPID_AUTO_TEST_CASE(testUrl_parse) {
    Url url;
    url.parse("amqp:foo.com,tcp:bar.com:1234");
    BOOST_CHECK_EQUAL(2u, url.size());
    BOOST_CHECK_EQUAL("foo.com", boost::get<TcpAddress>(url[0]).host);
    BOOST_CHECK_EQUAL("amqp:tcp:foo.com:5672,tcp:bar.com:1234", url.str());

    url.parse("amqp:foo/ignorethis");
    BOOST_CHECK_EQUAL("amqp:tcp:foo:5672", url.str());

    url.parse("amqp:");
    BOOST_CHECK_EQUAL("amqp:tcp::5672", url.str());

    try {
        url.parse("invalid url");
        BOOST_FAIL("Expected InvalidUrl exception");
    }
    catch (const Url::InvalidUrl&) {}

    url.parseNoThrow("invalid url");
    BOOST_CHECK(url.empty());
}


QPID_AUTO_TEST_SUITE_END()
