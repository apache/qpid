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
#include <boost/test/auto_unit_test.hpp>
#include "test_tools.h"
#include "qpid/Url.h"
#include <boost/assign.hpp>

using namespace std;
using namespace qpid;
using namespace boost::assign;

BOOST_AUTO_TEST_CASE(testUrl_str) {
    Url url;
    url.push_back(TcpAddress("foo.com"));
    url.push_back(TcpAddress("bar.com", 6789));
    
    BOOST_CHECK_EQUAL(
        url.str(), "amqp:tcp:foo.com:5672,tcp:bar.com:6789");
    BOOST_CHECK_EQUAL(Url().str(), "amqp:");
}


BOOST_AUTO_TEST_CASE(testUrl_ctor) {
    BOOST_CHECK_EQUAL(
        Url("amqp:foo.com,tcp:bar.com:1234").str(),
        "amqp:tcp:foo.com:5672,tcp:bar.com:1234");
    BOOST_CHECK_EQUAL(
        Url("amqp:foo/ignorethis").str(),
        "amqp:tcp:foo:5672");
    BOOST_CHECK_EQUAL("amqp:tcp::5672", Url("amqp:").str());
    BOOST_CHECK_EQUAL(0u, Url("xxx", nothrow).size());
    try {
        Url invalid("xxx");
        BOOST_FAIL("Expected InvalidUrl exception");
    }
    catch (const Url::InvalidUrl&) {}
}


