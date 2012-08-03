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
#include "qpid/broker/TopicKeyNode.h"
#include "qpid/broker/TopicExchange.h"
#include "unit_test.h"
#include "test_tools.h"

using namespace qpid::broker;
using namespace std;


namespace qpid {
namespace broker {

// Class for exercising the pattern match code in the TopicExchange
class TopicExchange::TopicExchangeTester {

public:
    typedef std::vector<std::string> BindingVec;
    typedef TopicKeyNode<TopicExchange::BindingKey> TestBindingNode;

private:
    // binding node iterator that collects all routes that are bound
    class TestFinder : public TestBindingNode::TreeIterator {
    public:
        TestFinder(BindingVec& m) : bv(m) {};
        ~TestFinder() {};
        bool visit(TestBindingNode& node) {
            if (!node.bindings.bindingVector.empty())
                bv.push_back(node.routePattern);
            return true;
        }

        BindingVec& bv;
    };

public:
    TopicExchangeTester() {};
    ~TopicExchangeTester() {};
    bool addBindingKey(const std::string& bKey) {
        string routingPattern = normalize(bKey);
        BindingKey *bk = bindingTree.add(routingPattern);
        if (bk) {
            // push a dummy binding to mark this node as "non-leaf"
            bk->bindingVector.push_back(Binding::shared_ptr());
            return true;
        }
        return false;
    }

    bool removeBindingKey(const std::string& bKey){
        string routingPattern = normalize(bKey);
        BindingKey *bk = bindingTree.get(routingPattern);
        if (bk) {
            bk->bindingVector.pop_back();
            if (bk->bindingVector.empty()) {
                // no more bindings - remove this node
                bindingTree.remove(routingPattern);
            }
            return true;
        }
        return false;
    }

    void findMatches(const std::string& rKey, BindingVec& matches) {
        TestFinder testFinder(matches);
        bindingTree.iterateMatch( rKey, testFinder );
    }

    void getAll(BindingVec& bindings) {
        TestFinder testFinder(bindings);
        bindingTree.iterateAll( testFinder );
    }

private:
    TestBindingNode bindingTree;
};
} // namespace broker


namespace tests {

QPID_AUTO_TEST_SUITE(TopicExchangeTestSuite)

#define CHECK_NORMALIZED(expect, pattern) BOOST_CHECK_EQUAL(expect, TopicExchange::normalize(pattern));

namespace {
    // return the count of bindings that match 'pattern'
    int match(TopicExchange::TopicExchangeTester &tt,
              const std::string& pattern)
    {
        TopicExchange::TopicExchangeTester::BindingVec bv;
        tt.findMatches(pattern, bv);
        return int(bv.size());
    }

    // return true if expected contains exactly all bindings that match
    // against pattern.
    bool compare(TopicExchange::TopicExchangeTester& tt,
                 const std::string& pattern,
                 const TopicExchange::TopicExchangeTester::BindingVec& expected)
    {
        TopicExchange::TopicExchangeTester::BindingVec bv;
        tt.findMatches(pattern, bv);
        if (expected.size() != bv.size()) {
            // std::cout << "match failed 1 f=[" << bv << "]" << std::endl;
            // std::cout << "match failed 1 e=[" << expected << "]" << std::endl;
            return false;
        }
        TopicExchange::TopicExchangeTester::BindingVec::const_iterator i;
        for (i = expected.begin(); i != expected.end(); i++) {
            TopicExchange::TopicExchangeTester::BindingVec::iterator j;
            for (j = bv.begin(); j != bv.end(); j++) {
                // std::cout << "matched [" << *j << "]" << std::endl;
                if (*i == *j) break;
            }
            if (j == bv.end()) {
                // std::cout << "match failed 2 [" << bv << "]" << std::endl;
                return false;
            }
        }
        return true;
    }
}


QPID_AUTO_TEST_CASE(testNormalize)
{
    CHECK_NORMALIZED("", "");
    CHECK_NORMALIZED("a.b.c", "a.b.c");
    CHECK_NORMALIZED("a.*.c", "a.*.c");
    CHECK_NORMALIZED("#", "#");
    CHECK_NORMALIZED("#", "#.#.#.#");
    CHECK_NORMALIZED("*.*.*.#", "#.*.#.*.#.#.*");
    CHECK_NORMALIZED("a.*.*.*.#", "a.*.#.*.#.*.#");
    CHECK_NORMALIZED("a.*.*.*.#", "a.*.#.*.#.*");
    CHECK_NORMALIZED("*.*.*.#", "*.#.#.*.*.#");
}

QPID_AUTO_TEST_CASE(testPlain)
{
    TopicExchange::TopicExchangeTester tt;
    string pattern("ab.cd.e");

    BOOST_CHECK(tt.addBindingKey(pattern));
    BOOST_CHECK_EQUAL(1, match(tt, "ab.cd.e"));
    BOOST_CHECK_EQUAL(0, match(tt, "abx.cd.e"));
    BOOST_CHECK_EQUAL(0, match(tt, "ab.cd"));
    BOOST_CHECK_EQUAL(0, match(tt, "ab.cd..e."));
    BOOST_CHECK_EQUAL(0, match(tt, "ab.cd.e."));
    BOOST_CHECK_EQUAL(0, match(tt, ".ab.cd.e"));
    BOOST_CHECK(tt.removeBindingKey(pattern));

    pattern = "";
    BOOST_CHECK(tt.addBindingKey(pattern));
    BOOST_CHECK_EQUAL(1, match(tt, ""));
    BOOST_CHECK(tt.removeBindingKey(pattern));

    pattern = ".";
    BOOST_CHECK(tt.addBindingKey(pattern));
    BOOST_CHECK_EQUAL(1, match(tt, "."));
    BOOST_CHECK(tt.removeBindingKey(pattern));
}


QPID_AUTO_TEST_CASE(testStar)
{
    TopicExchange::TopicExchangeTester tt;
    string pattern("a.*.b");
    BOOST_CHECK(tt.addBindingKey(pattern));
    BOOST_CHECK_EQUAL(1, match(tt, "a.xx.b"));
    BOOST_CHECK_EQUAL(0, match(tt, "a.b"));
    BOOST_CHECK(tt.removeBindingKey(pattern));

    pattern = "*.x";
    BOOST_CHECK(tt.addBindingKey(pattern));
    BOOST_CHECK_EQUAL(1, match(tt, "y.x"));
    BOOST_CHECK_EQUAL(1, match(tt, ".x"));
    BOOST_CHECK_EQUAL(0, match(tt, "x"));
    BOOST_CHECK(tt.removeBindingKey(pattern));

    pattern = "x.x.*";
    BOOST_CHECK(tt.addBindingKey(pattern));
    BOOST_CHECK_EQUAL(1, match(tt, "x.x.y"));
    BOOST_CHECK_EQUAL(1, match(tt, "x.x."));
    BOOST_CHECK_EQUAL(0, match(tt, "x.x"));
    BOOST_CHECK_EQUAL(0, match(tt, "q.x.y"));
    BOOST_CHECK(tt.removeBindingKey(pattern));
}

QPID_AUTO_TEST_CASE(testHash)
{
    TopicExchange::TopicExchangeTester tt;
    string pattern("a.#.b");
    BOOST_CHECK(tt.addBindingKey(pattern));
    BOOST_CHECK_EQUAL(1, match(tt, "a.b"));
    BOOST_CHECK_EQUAL(1, match(tt, "a.x.b"));
    BOOST_CHECK_EQUAL(1, match(tt, "a..x.y.zz.b"));
    BOOST_CHECK_EQUAL(0, match(tt, "a.b."));
    BOOST_CHECK_EQUAL(0, match(tt, "q.x.b"));
    BOOST_CHECK(tt.removeBindingKey(pattern));

    pattern = "a.#";
    BOOST_CHECK(tt.addBindingKey(pattern));
    BOOST_CHECK_EQUAL(1, match(tt, "a"));
    BOOST_CHECK_EQUAL(1, match(tt, "a.b"));
    BOOST_CHECK_EQUAL(1, match(tt, "a.b.c"));
    BOOST_CHECK(tt.removeBindingKey(pattern));

    pattern = "#.a";
    BOOST_CHECK(tt.addBindingKey(pattern));
    BOOST_CHECK_EQUAL(1, match(tt, "a"));
    BOOST_CHECK_EQUAL(1, match(tt, "x.y.a"));
    BOOST_CHECK(tt.removeBindingKey(pattern));

    pattern = "a.#.b.#.c";
    BOOST_CHECK(tt.addBindingKey(pattern));
    BOOST_CHECK_EQUAL(1, match(tt, "a.b.c"));
    BOOST_CHECK_EQUAL(1, match(tt, "a.x.b.y.c"));
    BOOST_CHECK_EQUAL(1, match(tt, "a.x.x.b.y.y.c"));
    BOOST_CHECK(tt.removeBindingKey(pattern));
}

QPID_AUTO_TEST_CASE(testMixed)
{
    TopicExchange::TopicExchangeTester tt;
    string pattern("*.x.#.y");
    BOOST_CHECK(tt.addBindingKey(pattern));
    BOOST_CHECK_EQUAL(1, match(tt, "a.x.y"));
    BOOST_CHECK_EQUAL(1, match(tt, "a.x.p.qq.y"));
    BOOST_CHECK_EQUAL(0, match(tt, "a.a.x.y"));
    BOOST_CHECK_EQUAL(0, match(tt, "aa.x.b.c"));
    BOOST_CHECK(tt.removeBindingKey(pattern));

    pattern = "a.#.b.*";
    BOOST_CHECK(tt.addBindingKey(pattern));
    BOOST_CHECK_EQUAL(1, match(tt, "a.b.x"));
    BOOST_CHECK_EQUAL(1, match(tt, "a.x.x.x.b.x"));
    BOOST_CHECK(tt.removeBindingKey(pattern));

    pattern = "*.*.*.#";
    BOOST_CHECK(tt.addBindingKey(pattern));
    BOOST_CHECK_EQUAL(1, match(tt, "x.y.z"));
    BOOST_CHECK_EQUAL(1, match(tt, "x.y.z.a.b.c"));
    BOOST_CHECK_EQUAL(0, match(tt, "x.y"));
    BOOST_CHECK_EQUAL(0, match(tt, "x"));
    BOOST_CHECK(tt.removeBindingKey(pattern));
}


QPID_AUTO_TEST_CASE(testMultiple)
{
    TopicExchange::TopicExchangeTester tt;
    const std::string bindings[] =
      { "a",        "b",
        "a.b",      "b.c",
        "a.b.c.d",  "b.c.d.e",
        "a.*",      "a.#",        "a.*.#",
        "#.b",      "*.b",        "*.#.b",
        "a.*.b",    "a.#.b",      "a.*.#.b",
        "*.b.*",    "#.b.#",
      };
    const size_t nBindings = sizeof(bindings)/sizeof(bindings[0]);

    // setup bindings
    for (size_t idx = 0; idx < nBindings; idx++) {
        BOOST_CHECK(tt.addBindingKey(bindings[idx]));
    }

    {
        // read all bindings, and verify all are present
        TopicExchange::TopicExchangeTester::BindingVec b;
        tt.getAll(b);
        BOOST_CHECK_EQUAL(b.size(), nBindings);
        for (size_t idx = 0; idx < nBindings; idx++) {
            bool found = false;
            for (TopicExchange::TopicExchangeTester::BindingVec::iterator i = b.begin();
                 i != b.end(); i++) {
                if (*i == bindings[idx]) {
                    found = true;
                    break;
                }
            }
            BOOST_CHECK(found);
        }
    }

    {   // test match on pattern "a"
        const std::string matches[] = { "a", "a.#" };
        const size_t nMatches = 2;
        TopicExchange::TopicExchangeTester::BindingVec expected(matches, matches + nMatches);
        BOOST_CHECK(compare(tt, "a", expected));
    }

    {   // test match on pattern "a.z"
        const std::string matches[] = { "a.*", "a.#", "a.*.#" };
        const size_t nMatches = 3;
        TopicExchange::TopicExchangeTester::BindingVec expected(matches, matches + nMatches);
        BOOST_CHECK(compare(tt, "a.z", expected));
    }

    {   // test match on pattern "a.b"
        const std::string matches[] = {
            "a.b", "a.*", "a.#", "a.*.#",
            "#.b", "#.b.#", "*.#.b", "*.b",
            "a.#.b"
        };
        const size_t nMatches = 9;
        TopicExchange::TopicExchangeTester::BindingVec expected(matches, matches + nMatches);
        BOOST_CHECK(compare(tt, "a.b", expected));
    }

    {   // test match on pattern "a.c.c.b"

        const std::string matches[] = {
            "#.b",  "#.b.#",  "*.#.b",  "a.#.b",
            "a.#",  "a.*.#.b",  "a.*.#"
        };
        const size_t nMatches = 7;
        TopicExchange::TopicExchangeTester::BindingVec expected(matches, matches + nMatches);
        BOOST_CHECK(compare(tt, "a.c.c.b", expected));
    }

    {   // test match on pattern "a.b.c"

        const std::string matches[] = {
            "#.b.#",  "*.b.*",  "a.#",  "a.*.#"
        };
        const size_t nMatches = 4;
        TopicExchange::TopicExchangeTester::BindingVec expected(matches, matches + nMatches);
        BOOST_CHECK(compare(tt, "a.b.c", expected));
    }

    {   // test match on pattern "b"

        const std::string matches[] = {
            "#.b",  "#.b.#",  "b"
        };
        const size_t nMatches = 3;
        TopicExchange::TopicExchangeTester::BindingVec expected(matches, matches + nMatches);
        BOOST_CHECK(compare(tt, "b", expected));
    }

    {   // test match on pattern "x.b"

        const std::string matches[] = {
            "#.b", "#.b.#", "*.#.b", "*.b"
        };
        const size_t nMatches = 4;
        TopicExchange::TopicExchangeTester::BindingVec expected(matches, matches + nMatches);
        BOOST_CHECK(compare(tt, "x.b", expected));
    }

    {   // test match on pattern "x.y.z.b"

        const std::string matches[] = {
            "#.b", "#.b.#", "*.#.b"
        };
        const size_t nMatches = 3;
        TopicExchange::TopicExchangeTester::BindingVec expected(matches, matches + nMatches);
        BOOST_CHECK(compare(tt, "x.y.z.b", expected));
    }

    {   // test match on pattern "x.y.z.b.a.b.c"

        const std::string matches[] = {
            "#.b.#", "#.b.#"
        };
        const size_t nMatches = 2;
        TopicExchange::TopicExchangeTester::BindingVec expected(matches, matches + nMatches);
        BOOST_CHECK(compare(tt, "x.y.z.b.a.b.c", expected));
    }

    {   // test match on pattern "a.b.c.d"

        const std::string matches[] = {
            "#.b.#", "a.#", "a.*.#", "a.b.c.d",
        };
        const size_t nMatches = 4;
        TopicExchange::TopicExchangeTester::BindingVec expected(matches, matches + nMatches);
        BOOST_CHECK(compare(tt, "a.b.c.d", expected));
    }

    // cleanup bindings
    for (size_t idx = 0; idx < nBindings; idx++) {
        BOOST_CHECK(tt.removeBindingKey(bindings[idx]));
    }
}

QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests
