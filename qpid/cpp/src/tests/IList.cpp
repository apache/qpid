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
#include "qpid/IList.h"
#include <boost/assign/list_of.hpp>
#include <vector>

using namespace qpid;
using namespace std;
using boost::assign::list_of;

// Comparison, op== for ILists and sequences of intrusive_ptr<T>
// in qpid namespace to satisfy template lookup rules
namespace qpid {
template <class T, int N, class S> bool operator==(const IList<T,N>& a, const S& b) { return seqEqual(a, b); }
template <class T, int N> std::ostream& operator<<(std::ostream& o, const IList<T,N>& l) { return seqPrint(o, l); }
}

QPID_AUTO_TEST_SUITE(IListTestSuite)

template <class T> bool operator==(const T& v, const intrusive_ptr<T>& p) { return v==*p; }

struct TestNode {
    static int instances;
    char id;
    TestNode(char i) : id(i) { ++instances; }
    ~TestNode() { --instances; }
    bool operator==(const TestNode& x) const { return this == &x; }
    bool operator==(const TestNode* x) const { return this == x; }
};
int TestNode::instances = 0;
ostream& operator<<(ostream& o, const TestNode& n) { return o << n.id; }

struct SingleNode : public TestNode, public IListNode<> { SingleNode(char i) : TestNode(i) {} };
typedef IList<SingleNode> TestList;

struct Fixture {
    intrusive_ptr<SingleNode> a, b, c, d;
    Fixture() : a(new SingleNode('a')),
                b(new SingleNode('b')),
                c(new SingleNode('c')),
                d(new SingleNode('d'))
    {
        BOOST_CHECK_EQUAL(4, TestNode::instances);
    }
};

BOOST_AUTO_TEST_CASE(TestFixture) {
    { Fixture f; }
    BOOST_CHECK_EQUAL(0, TestNode::instances);
}

BOOST_FIXTURE_TEST_CASE(TestSingleList, Fixture) {
    TestList l;
    BOOST_CHECK_EQUAL(0u, l.size());
    BOOST_CHECK(l.empty());

    l.push_back(*a);
    BOOST_CHECK_EQUAL(1u, l.size());
    BOOST_CHECK(!l.empty());
    BOOST_CHECK_EQUAL(l, list_of(a));

    TestList::iterator i = l.begin();
    BOOST_CHECK_EQUAL(*i, *a);
    
    l.push_back(*b);
    BOOST_CHECK_EQUAL(2u, l.size());
    BOOST_CHECK_EQUAL(l, list_of(a)(b));
    BOOST_CHECK_EQUAL(*i, *a);        // Iterator not invalidated
    BOOST_CHECK_EQUAL(l.front(), *a);
    BOOST_CHECK_EQUAL(l.back(), *b);

    l.push_front(*c);
    BOOST_CHECK_EQUAL(3u, l.size());
    BOOST_CHECK_EQUAL(l, list_of(c)(a)(b));
    BOOST_CHECK_EQUAL(*i, *a);        // Iterator not invalidated

    l.insert(i, *d);
    BOOST_CHECK_EQUAL(4u, l.size());
    BOOST_CHECK_EQUAL(l, list_of(c)(d)(a)(b));
    BOOST_CHECK_EQUAL(*i, *a);

    a = 0;                      // Not deleted yet, still in list.
    BOOST_CHECK_EQUAL(4, SingleNode::instances);
    l.erase(i);
    BOOST_CHECK_EQUAL(3, SingleNode::instances);
    BOOST_CHECK_EQUAL(l, list_of(c)(d)(b));

    l.pop_front();
    l.pop_back();
    c = 0; b = 0;
    BOOST_CHECK_EQUAL(1, SingleNode::instances);
    BOOST_CHECK_EQUAL(l, list_of(d));

    l.pop_back();
    BOOST_CHECK_EQUAL(0u, l.size());
    BOOST_CHECK(l.empty());
}

BOOST_FIXTURE_TEST_CASE(TestIterator, Fixture) {
    {
        TestList l;
        l.push_back(*a);
        l.push_back(*b);
        l.push_back(*c);
    
        TestList::iterator i = l.begin();
        BOOST_CHECK_EQUAL(*i, *a);
        i++;
        BOOST_CHECK_EQUAL(*i, *b);
        i++;
        BOOST_CHECK_EQUAL(*i, *c);
        i++;
        BOOST_CHECK(i == l.end());
        i--;
        BOOST_CHECK_EQUAL(*i, *c);
        i--;
        BOOST_CHECK_EQUAL(*i, *b);
        i--;
        BOOST_CHECK_EQUAL(*i, *a);
    }
    a = b = c = d = 0;
    BOOST_CHECK_EQUAL(0, TestNode::instances);
}    


BOOST_FIXTURE_TEST_CASE(testOwnership, Fixture) {
    { 
        TestList l2;
        l2.push_back(*a);
        l2.push_back(*b);
        l2.push_back(*c);
        l2.push_back(*d);
        a = b = c = d = 0;
        BOOST_CHECK_EQUAL(4, SingleNode::instances);
    }
    BOOST_CHECK_EQUAL(0, SingleNode::instances);
}

struct MultiNode : public TestNode, public IListNode<0>, public IListNode<1>, public IListNode<2> {
    MultiNode(char c) : TestNode(c) {}
};

struct MultiFixture {
    IList<MultiNode, 0> l0;
    IList<MultiNode, 1> l1;
    IList<MultiNode, 2> l2;

    intrusive_ptr<MultiNode> a, b, c;

    MultiFixture() : a(new MultiNode('a')),
                     b(new MultiNode('b')),
                     c(new MultiNode('c'))
    {    
        BOOST_CHECK_EQUAL(3, MultiNode::instances);
    }

    void push_back_all(intrusive_ptr<MultiNode> p) {
        l0.push_back(*p);
        l1.push_back(*p);
        l2.push_back(*p);
    }
};

BOOST_FIXTURE_TEST_CASE(TestMultiIList, MultiFixture) {
    BOOST_CHECK_EQUAL(a->id, 'a');
    push_back_all(a);
    push_back_all(b);
    push_back_all(c);

    BOOST_CHECK_EQUAL(3, MultiNode::instances);

    l0.pop_front();
    l1.pop_back();
    IList<MultiNode, 2>::iterator i = l2.begin();
    i++;
    l2.erase(i);
    BOOST_CHECK_EQUAL(3, MultiNode::instances);
    BOOST_CHECK_EQUAL(l0, list_of(b)(c));    
    BOOST_CHECK_EQUAL(l1, list_of(a)(b));    
    BOOST_CHECK_EQUAL(l2, list_of(a)(c));
    
    l1.pop_front();
    l2.clear();
    BOOST_CHECK_EQUAL(l0, list_of(b)(c));    
    BOOST_CHECK_EQUAL(l1, list_of(b));    
    BOOST_CHECK(l2.empty());
    a = 0;
    BOOST_CHECK_EQUAL(2, MultiNode::instances); // a gone

    l0.pop_back();
    l1.pop_front();
    BOOST_CHECK_EQUAL(l0, list_of(b));    
    BOOST_CHECK(l1.empty());
    BOOST_CHECK(l2.empty());
    BOOST_CHECK_EQUAL(2, MultiNode::instances); // c gone
    c = 0;

    l0.clear();
    b = 0;
    BOOST_CHECK_EQUAL(0, MultiNode::instances); // all gone
}

QPID_AUTO_TEST_SUITE_END()

