/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
#include "qpid/broker/TopicExchange.h"
#include "unit_test.h"
#include "test_tools.h"

using namespace qpid::broker;

Tokens makeTokens(const char** begin, const char** end)
{
    Tokens t;
    t.insert(t.end(), begin, end);
    return t;
}

// Calculate size of an array. 
#define LEN(a) (sizeof(a)/sizeof(a[0]))

// Convert array to token vector
#define TOKENS(a) makeTokens(a, a + LEN(a))

#define ASSERT_NORMALIZED(expect, pattern)                              \
    BOOST_CHECK_EQUAL(Tokens(expect), static_cast<Tokens>(TopicPattern(pattern)))


QPID_AUTO_TEST_SUITE(TopicExchangeTestSuite)

QPID_AUTO_TEST_CASE(testTokens) 
{
    Tokens tokens("hello.world");
    const char* expect[] = {"hello", "world"};
    BOOST_CHECK_EQUAL(TOKENS(expect), tokens);
        
    tokens = "a.b.c";
    const char* expect2[] = { "a", "b", "c" };
    BOOST_CHECK_EQUAL(TOKENS(expect2), tokens);

    tokens = "";
    BOOST_CHECK(tokens.empty());

    tokens = "x";
    const char* expect3[] = { "x" };
    BOOST_CHECK_EQUAL(TOKENS(expect3), tokens);

    tokens = (".x");
    const char* expect4[] = { "", "x" };
    BOOST_CHECK_EQUAL(TOKENS(expect4), tokens);

    tokens = ("x.");
    const char* expect5[] = { "x", "" };
    BOOST_CHECK_EQUAL(TOKENS(expect5), tokens);

    tokens = (".");
    const char* expect6[] = { "", "" };
    BOOST_CHECK_EQUAL(TOKENS(expect6), tokens);        

    tokens = ("..");
    const char* expect7[] = { "", "", "" };
    BOOST_CHECK_EQUAL(TOKENS(expect7), tokens);        
}

QPID_AUTO_TEST_CASE(testNormalize) 
{
    BOOST_CHECK(TopicPattern("").empty());
    ASSERT_NORMALIZED("a.b.c", "a.b.c");
    ASSERT_NORMALIZED("a.*.c", "a.*.c");
    ASSERT_NORMALIZED("#", "#");
    ASSERT_NORMALIZED("#", "#.#.#.#");
    ASSERT_NORMALIZED("*.*.*.#", "#.*.#.*.#.#.*");
    ASSERT_NORMALIZED("a.*.*.*.#", "a.*.#.*.#.*.#");
    ASSERT_NORMALIZED("a.*.*.*.#", "a.*.#.*.#.*");
}
    
QPID_AUTO_TEST_CASE(testPlain) 
{
    TopicPattern p("ab.cd.e");
    BOOST_CHECK(p.match("ab.cd.e"));
    BOOST_CHECK(!p.match("abx.cd.e"));
    BOOST_CHECK(!p.match("ab.cd"));
    BOOST_CHECK(!p.match("ab.cd..e."));
    BOOST_CHECK(!p.match("ab.cd.e."));
    BOOST_CHECK(!p.match(".ab.cd.e"));

    p = "";
    BOOST_CHECK(p.match(""));

    p = ".";
    BOOST_CHECK(p.match("."));
}


QPID_AUTO_TEST_CASE(testStar) 
{
    TopicPattern p("a.*.b");
    BOOST_CHECK(p.match("a.xx.b"));
    BOOST_CHECK(!p.match("a.b"));

    p = "*.x";
    BOOST_CHECK(p.match("y.x"));
    BOOST_CHECK(p.match(".x"));
    BOOST_CHECK(!p.match("x"));

    p = "x.x.*";
    BOOST_CHECK(p.match("x.x.y"));
    BOOST_CHECK(p.match("x.x."));
    BOOST_CHECK(!p.match("x.x"));
    BOOST_CHECK(!p.match("q.x.y"));
}

QPID_AUTO_TEST_CASE(testHash) 
{
    TopicPattern p("a.#.b");
    BOOST_CHECK(p.match("a.b"));
    BOOST_CHECK(p.match("a.x.b"));
    BOOST_CHECK(p.match("a..x.y.zz.b"));
    BOOST_CHECK(!p.match("a.b."));
    BOOST_CHECK(!p.match("q.x.b"));

    p = "a.#";
    BOOST_CHECK(p.match("a"));
    BOOST_CHECK(p.match("a.b"));
    BOOST_CHECK(p.match("a.b.c"));

    p = "#.a";
    BOOST_CHECK(p.match("a"));
    BOOST_CHECK(p.match("x.y.a"));
}

QPID_AUTO_TEST_CASE(testMixed) 
{
    TopicPattern p("*.x.#.y");
    BOOST_CHECK(p.match("a.x.y"));
    BOOST_CHECK(p.match("a.x.p.qq.y"));
    BOOST_CHECK(!p.match("a.a.x.y"));
    BOOST_CHECK(!p.match("aa.x.b.c"));

    p = "a.#.b.*";
    BOOST_CHECK(p.match("a.b.x"));
    BOOST_CHECK(p.match("a.x.x.x.b.x"));
}

QPID_AUTO_TEST_CASE(testCombo) 
{
    TopicPattern p("*.#.#.*.*.#");
    BOOST_CHECK(p.match("x.y.z"));
    BOOST_CHECK(p.match("x.y.z.a.b.c"));
    BOOST_CHECK(!p.match("x.y"));
    BOOST_CHECK(!p.match("x"));
}

QPID_AUTO_TEST_SUITE_END()
