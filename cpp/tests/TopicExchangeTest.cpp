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
#include <TopicExchange.h>
#include <qpid_test_plugin.h>

using namespace qpid::broker;

Tokens makeTokens(char** begin, char** end)
{
    Tokens t;
    t.insert(t.end(), begin, end);
    return t;
}

// Calculate size of an array. 
#define LEN(a) (sizeof(a)/sizeof(a[0]))

// Convert array to token vector
#define TOKENS(a) makeTokens(a, a + LEN(a))

// Allow CPPUNIT_EQUALS to print a Tokens.
CppUnit::OStringStream& operator <<(CppUnit::OStringStream& out, const Tokens& v)
{
    out << "[ ";
    for (Tokens::const_iterator i = v.begin();
         i != v.end(); ++i)
    {
        out << '"' << *i << '"' << (i+1 == v.end() ? "]" : ", ");
    }
    return out;
}


class TokensTest : public CppUnit::TestCase
{
    CPPUNIT_TEST_SUITE(TokensTest);
    CPPUNIT_TEST(testTokens);
    CPPUNIT_TEST_SUITE_END();

  public:
    void testTokens() 
    {
        Tokens tokens("hello.world");
        char* expect[] = {"hello", "world"};
        CPPUNIT_ASSERT_EQUAL(TOKENS(expect), tokens);
        
        tokens = "a.b.c";
        char* expect2[] = { "a", "b", "c" };
        CPPUNIT_ASSERT_EQUAL(TOKENS(expect2), tokens);

        tokens = "";
        CPPUNIT_ASSERT(tokens.empty());

        tokens = "x";
        char* expect3[] = { "x" };
        CPPUNIT_ASSERT_EQUAL(TOKENS(expect3), tokens);

        tokens = (".x");
        char* expect4[] = { "", "x" };
        CPPUNIT_ASSERT_EQUAL(TOKENS(expect4), tokens);

        tokens = ("x.");
        char* expect5[] = { "x", "" };
        CPPUNIT_ASSERT_EQUAL(TOKENS(expect5), tokens);

        tokens = (".");
        char* expect6[] = { "", "" };
        CPPUNIT_ASSERT_EQUAL(TOKENS(expect6), tokens);        

        tokens = ("..");
        char* expect7[] = { "", "", "" };
        CPPUNIT_ASSERT_EQUAL(TOKENS(expect7), tokens);        
    }
    
};

#define ASSERT_NORMALIZED(expect, pattern) \
    CPPUNIT_ASSERT_EQUAL(Tokens(expect), static_cast<Tokens>(TopicPattern(pattern)))
class TopicPatternTest : public CppUnit::TestCase 
{
    CPPUNIT_TEST_SUITE(TopicPatternTest);
    CPPUNIT_TEST(testNormalize);
    CPPUNIT_TEST(testPlain);
    CPPUNIT_TEST(testStar);
    CPPUNIT_TEST(testHash);
    CPPUNIT_TEST(testMixed);
    CPPUNIT_TEST(testCombo);
    CPPUNIT_TEST_SUITE_END();

  public:

    void testNormalize() 
    {
        CPPUNIT_ASSERT(TopicPattern("").empty());
        ASSERT_NORMALIZED("a.b.c", "a.b.c");
        ASSERT_NORMALIZED("a.*.c", "a.*.c");
        ASSERT_NORMALIZED("#", "#");
        ASSERT_NORMALIZED("#", "#.#.#.#");
        ASSERT_NORMALIZED("*.*.*.#", "#.*.#.*.#.#.*");
        ASSERT_NORMALIZED("a.*.*.*.#", "a.*.#.*.#.*.#");
        ASSERT_NORMALIZED("a.*.*.*.#", "a.*.#.*.#.*");
    }
    
    void testPlain() {
        TopicPattern p("ab.cd.e");
        CPPUNIT_ASSERT(p.match("ab.cd.e"));
        CPPUNIT_ASSERT(!p.match("abx.cd.e"));
        CPPUNIT_ASSERT(!p.match("ab.cd"));
        CPPUNIT_ASSERT(!p.match("ab.cd..e."));
        CPPUNIT_ASSERT(!p.match("ab.cd.e."));
        CPPUNIT_ASSERT(!p.match(".ab.cd.e"));

        p = "";
        CPPUNIT_ASSERT(p.match(""));

        p = ".";
        CPPUNIT_ASSERT(p.match("."));
    }


    void testStar() 
    {
        TopicPattern p("a.*.b");
        CPPUNIT_ASSERT(p.match("a.xx.b"));
        CPPUNIT_ASSERT(!p.match("a.b"));

        p = "*.x";
        CPPUNIT_ASSERT(p.match("y.x"));
        CPPUNIT_ASSERT(p.match(".x"));
        CPPUNIT_ASSERT(!p.match("x"));

        p = "x.x.*";
        CPPUNIT_ASSERT(p.match("x.x.y"));
        CPPUNIT_ASSERT(p.match("x.x."));
        CPPUNIT_ASSERT(!p.match("x.x"));
        CPPUNIT_ASSERT(!p.match("q.x.y"));
    }

    void testHash() 
    {
        TopicPattern p("a.#.b");
        CPPUNIT_ASSERT(p.match("a.b"));
        CPPUNIT_ASSERT(p.match("a.x.b"));
        CPPUNIT_ASSERT(p.match("a..x.y.zz.b"));
        CPPUNIT_ASSERT(!p.match("a.b."));
        CPPUNIT_ASSERT(!p.match("q.x.b"));

        p = "a.#";
        CPPUNIT_ASSERT(p.match("a"));
        CPPUNIT_ASSERT(p.match("a.b"));
        CPPUNIT_ASSERT(p.match("a.b.c"));

        p = "#.a";
        CPPUNIT_ASSERT(p.match("a"));
        CPPUNIT_ASSERT(p.match("x.y.a"));
    }

    void testMixed() 
    {
        TopicPattern p("*.x.#.y");
        CPPUNIT_ASSERT(p.match("a.x.y"));
        CPPUNIT_ASSERT(p.match("a.x.p.qq.y"));
        CPPUNIT_ASSERT(!p.match("a.a.x.y"));
        CPPUNIT_ASSERT(!p.match("aa.x.b.c"));

        p = "a.#.b.*";
        CPPUNIT_ASSERT(p.match("a.b.x"));
        CPPUNIT_ASSERT(p.match("a.x.x.x.b.x"));
    }

    void testCombo() {
        TopicPattern p("*.#.#.*.*.#");
        CPPUNIT_ASSERT(p.match("x.y.z"));
        CPPUNIT_ASSERT(p.match("x.y.z.a.b.c"));
        CPPUNIT_ASSERT(!p.match("x.y"));
        CPPUNIT_ASSERT(!p.match("x"));
    }
};

    
// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(TopicPatternTest);
CPPUNIT_TEST_SUITE_REGISTRATION(TokensTest);
