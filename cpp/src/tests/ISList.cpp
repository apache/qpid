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
#include "qpid/ISList.h"
#include "qpid/RefCounted.h"
#include "unit_test.h"
#include "test_tools.h"
#include <boost/assign/list_of.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/intrusive_ptr.hpp>
#include <vector>

QPID_AUTO_TEST_SUITE(ISListTestSuite)

using namespace qpid;
using namespace std;
using boost::assign::list_of;
using boost::intrusive_ptr;

// Comparison, op== and << for ILists in qpid namespace for template lookup.

template <class T, class S> bool operator==(const ISList<T>& a, const S& b) { return seqEqual(a, b); }
template <class T> ostream& operator<<(std::ostream& o, const ISList<T>& l) { return seqPrint(o, l); }
template <class T>
ostream& operator<<(ostream& o, typename ISList<T>::iterator i) {
    return i? o << "(nil)" : o << *i;
}

struct NodeBase {
    static int instances;
    char value;

    NodeBase(char c) { value=c; ++instances; }
    NodeBase(const NodeBase& n) { value=n.value; ++instances; }
    ~NodeBase() { --instances; }
    bool operator==(const NodeBase& n) const { return value == n.value; }
};

int NodeBase::instances = 0;

ostream& operator<<(ostream& o, const NodeBase& n) { return o << n.value; }

struct Fixture {
    struct Node : public NodeBase, public ISListNode<Node*> {
        Node(char c) : NodeBase(c) {}
    };
    typedef ISList<Node> List;
    Node a, b, c, d, e;
    List l;
    Fixture() :a('a'),b('b'),c('c'),d('d'),e('e') {}
};

QPID_AUTO_TEST_CASE(default_ctor) {
    Fixture fix;
    BOOST_CHECK(fix.l.empty());
    BOOST_CHECK(fix.l.begin() == fix.l.end());
    BOOST_CHECK_EQUAL(0u, fix.l.size());
}

QPID_AUTO_TEST_CASE(push_front) {
    Fixture fix;
    fix.l.push_front(&(fix.a));
    BOOST_CHECK_EQUAL(1u, fix.l.size());
    BOOST_CHECK_EQUAL(fix.l, list_of(fix.a));
    fix.l.push_front(&(fix.b));
    BOOST_CHECK_EQUAL(2u, fix.l.size());
    BOOST_CHECK_EQUAL(fix.l, list_of(fix.b)(fix.a));
}

QPID_AUTO_TEST_CASE(push_back) {
    Fixture fix;
    fix.l.push_back(&(fix.a));
    BOOST_CHECK_EQUAL(1u, fix.l.size());
    BOOST_CHECK_EQUAL(fix.l, list_of(fix.a));
    fix.l.push_back(&(fix.b));
    BOOST_CHECK_EQUAL(2u, fix.l.size());
    BOOST_CHECK_EQUAL(fix.l, list_of(fix.a)(fix.b));
}

QPID_AUTO_TEST_CASE(insert) {
    Fixture fix;
    Fixture::List::iterator i(fix.l.begin());
    i = fix.l.insert(i, &(fix.a));
    BOOST_CHECK_EQUAL(fix.l, list_of(fix.a));
    BOOST_CHECK(i == fix.l.begin());

    i = fix.l.insert(i, &(fix.b));
    BOOST_CHECK_EQUAL(fix.l, list_of(fix.b)(fix.a));
    BOOST_CHECK(i == fix.l.begin());

    i++;
    BOOST_CHECK_EQUAL(*i, fix.a);    
    i = fix.l.insert(i, &(fix.c));
    BOOST_CHECK_EQUAL(fix.l, list_of(fix.b)(fix.c)(fix.a));
    BOOST_CHECK_EQUAL(*i, fix.c);

    i = fix.l.insert(i, &(fix.d));
    BOOST_CHECK_EQUAL(fix.l, list_of(fix.b)(fix.d)(fix.c)(fix.a));
    BOOST_CHECK_EQUAL(*i, fix.d);
}

QPID_AUTO_TEST_CASE(iterator_test) {
    Fixture fix;
    fix.l.push_back(&(fix.a));
    fix.l.push_back(&(fix.b));
    
    Fixture::List::iterator i = fix.l.begin();
    BOOST_CHECK_EQUAL(*i, fix.a);
    BOOST_CHECK_EQUAL(static_cast<Fixture::Node*>(i), &(fix.a));
    Fixture::List::const_iterator ci = i;
    BOOST_CHECK_EQUAL(static_cast<const Fixture::Node*>(ci), &(fix.a));

    i++;
    BOOST_CHECK_EQUAL(*i, fix.b);    
    BOOST_CHECK_EQUAL(static_cast<Fixture::Node*>(i), &(fix.b));
    i++;
    BOOST_CHECK(i == fix.l.end());
}

QPID_AUTO_TEST_CASE(pop_front) {
    Fixture fix;
    fix.l.push_back(&(fix.a));
    fix.l.push_back(&(fix.b));
    fix.l.pop_front();
    BOOST_CHECK_EQUAL(fix.l, list_of(fix.b));
    fix.l.pop_front();
    BOOST_CHECK(fix.l.empty());
}

QPID_AUTO_TEST_CASE(erase) {
    Fixture fix;
    fix.l.push_back(&(fix.a));
    fix.l.push_back(&(fix.b));
    fix.l.push_back(&(fix.c));

    Fixture::List::iterator i=fix.l.begin();
    i++;
    fix.l.erase(i);
    BOOST_CHECK_EQUAL(fix.l, list_of(fix.a)(fix.c));

    i=fix.l.begin();
    i++;
    fix.l.erase(i);
    BOOST_CHECK_EQUAL(fix.l, list_of(fix.a));

    fix.l.erase(fix.l.begin());
    BOOST_CHECK(fix.l.empty());
}


// ================ Test smart pointer  types.

template <class Node> void smart_pointer_test() {
    typedef typename Node::pointer pointer;
    typedef ISList<Node> List;
    List l;

    BOOST_CHECK_EQUAL(0, NodeBase::instances);
    l.push_back(pointer(new Node()));
    l.push_back(pointer(new Node()));
    BOOST_CHECK_EQUAL(2, NodeBase::instances); // maintains a reference.
        
    pointer p = l.begin();
    l.pop_front();
    BOOST_CHECK_EQUAL(2, NodeBase::instances); // transfers ownership.
    p = pointer();
    BOOST_CHECK_EQUAL(1, NodeBase::instances); 

    l.clear();
    BOOST_CHECK_EQUAL(0, NodeBase::instances);
    {                       // Dtor cleans up
        List ll;
        ll.push_back(pointer(new Node()));
        BOOST_CHECK_EQUAL(1, NodeBase::instances);        
    }
    BOOST_CHECK_EQUAL(0, NodeBase::instances);
}

struct IntrusiveNode : public NodeBase, public RefCounted,
                       public ISListNode<intrusive_ptr<IntrusiveNode> >
{
    IntrusiveNode() : NodeBase(0) {}
};


QPID_AUTO_TEST_CASE(intrusive_ptr_test) {
    smart_pointer_test<IntrusiveNode>();
}


struct SharedNode : public NodeBase, public ISListNode<boost::shared_ptr<SharedNode> > {
    SharedNode() : NodeBase(0) {}
};

QPID_AUTO_TEST_CASE(shared_ptr_test) {
    smart_pointer_test<SharedNode>();
}

QPID_AUTO_TEST_SUITE_END()
