/*
 *
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
 *
 */
#include "qpid/IList.h"
#include "unit_test.h"
#include "test_tools.h"
#include <boost/assign/list_of.hpp>
#include <vector>

QPID_AUTO_TEST_SUITE(IListTestSuite)

using namespace qpid;
using namespace std;
using boost::assign::list_of;

// Comparison, op== and << for ILists in qpid namespace for template lookup.

template <class T, class S> bool operator==(const IList<T>& a, const S& b) { return seqEqual(a, b); }
template <class T> ostream& operator<<(std::ostream& o, const IList<T>& l) { return seqPrint(o, l); }
template <class T>
ostream& operator<<(ostream& o, typename IList<T>::iterator i) {
    return i? o << "(nil)" : o << *i;
}

struct IListFixture {
    struct Node : public IListNode<Node*> {
        char value;
        Node(char c) { value=c; }
        bool operator==(const Node& n) const { return value == n.value; }
    };
    typedef IList<Node> List;
    Node a, b, c, d, e;
    IListFixture() :a('a'),b('b'),c('c'),d('d'),e('e') {}
};

ostream& operator<<(ostream& o, const IListFixture::Node& n) { return o << n.value; }

QPID_AUTO_TEST_CASE(IList_default_ctor) {
    IListFixture fix;
    IListFixture::List l;
    BOOST_CHECK(l.empty());
    BOOST_CHECK(l.begin() == l.end());
    BOOST_CHECK_EQUAL(0u, l.size());
}

QPID_AUTO_TEST_CASE(IList_push_front) {
    IListFixture fix;
    IListFixture::List l;
    l.push_front(&(fix.a));
    BOOST_CHECK_EQUAL(1u, l.size());
    BOOST_CHECK_EQUAL(l, list_of(fix.a));
    l.push_front(&(fix.b));
    BOOST_CHECK_EQUAL(2u, l.size());
    BOOST_CHECK_EQUAL(l, list_of(fix.b)(fix.a));
}

QPID_AUTO_TEST_CASE(IList_push_back) {
    IListFixture fix;
    IListFixture::List l;
    l.push_back(&(fix.a));
    BOOST_CHECK_EQUAL(1u, l.size());
    BOOST_CHECK_EQUAL(l, list_of(fix.a));
    l.push_back(&(fix.b));
    BOOST_CHECK_EQUAL(2u, l.size());
    BOOST_CHECK_EQUAL(l, list_of(fix.a)(fix.b));
}

QPID_AUTO_TEST_CASE(IList_insert) {
    IListFixture fix;
    IListFixture::List l;
    IListFixture::List::iterator i(l.begin());
    i = l.insert(i, &(fix.a));
    BOOST_CHECK_EQUAL(l, list_of(fix.a));
    BOOST_CHECK(i == l.begin());

    i = l.insert(i, &(fix.b));
    BOOST_CHECK_EQUAL(l, list_of(fix.b)(fix.a));
    BOOST_CHECK(i == l.begin());

    i++;
    BOOST_CHECK_EQUAL(*i, fix.a);    
    i = l.insert(i, &(fix.c));
    BOOST_CHECK_EQUAL(l, list_of(fix.b)(fix.c)(fix.a));
    BOOST_CHECK_EQUAL(*i, fix.c);

    i = l.insert(i, &(fix.d));
    BOOST_CHECK_EQUAL(l, list_of(fix.b)(fix.d)(fix.c)(fix.a));
    BOOST_CHECK_EQUAL(*i, fix.d);
}

QPID_AUTO_TEST_CASE(IList_iterator_test) {
    IListFixture fix;
    IListFixture::List l;
    l.push_back(&(fix.a));
    l.push_back(&(fix.b));
    
    IListFixture::List::iterator i = l.begin();
    BOOST_CHECK_EQUAL(*i, fix.a);
    BOOST_CHECK_EQUAL(static_cast<IListFixture::Node*>(i), &(fix.a));
    IListFixture::List::const_iterator ci = i;
    BOOST_CHECK_EQUAL(static_cast<const IListFixture::Node*>(ci), &(fix.a));

    i++;
    BOOST_CHECK_EQUAL(*i, fix.b);    
    BOOST_CHECK_EQUAL(static_cast<IListFixture::Node*>(i), &(fix.b));
    i++;
    BOOST_CHECK(i == l.end());
}

QPID_AUTO_TEST_CASE(IList_pop_front) {
    IListFixture fix;
    IListFixture::List l;
    l.push_back(&(fix.a));
    l.push_back(&(fix.b));
    BOOST_CHECK_EQUAL(l, list_of(fix.a)(fix.b));
    l.pop_front();
    BOOST_CHECK_EQUAL(l, list_of(fix.b));
    l.pop_front();
    BOOST_CHECK(l.empty());
}

QPID_AUTO_TEST_CASE(IList_pop_back) {
    IListFixture fix;
    IListFixture::List l;
    l.push_back(&(fix.a));
    l.push_back(&(fix.b));
    l.pop_back();
    BOOST_CHECK_EQUAL(l, list_of(fix.a));
    l.pop_back();
    BOOST_CHECK(l.empty());
}

QPID_AUTO_TEST_CASE(IList_erase) {
    IListFixture fix;
    IListFixture::List l;
    l.push_back(&(fix.a));
    l.push_back(&(fix.b));
    l.push_back(&(fix.c));

    IListFixture::List::iterator i=l.begin();
    i++;
    l.erase(i);
    BOOST_CHECK_EQUAL(l, list_of(fix.a)(fix.c));

    i=l.begin();
    i++;
    l.erase(i);
    BOOST_CHECK_EQUAL(l, list_of(fix.a));

    l.erase(l.begin());
    BOOST_CHECK(l.empty());
}

QPID_AUTO_TEST_SUITE_END()

