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

BOOST_FIXTURE_TEST_CASE(default_ctor, Fixture) {
    BOOST_CHECK(l.empty());
    BOOST_CHECK(l.begin() == l.end());
    BOOST_CHECK_EQUAL(0u, l.size());
}

BOOST_FIXTURE_TEST_CASE(push_front, Fixture) {
    l.push_front(&a);
    BOOST_CHECK_EQUAL(1u, l.size());
    BOOST_CHECK_EQUAL(l, list_of(a));
    l.push_front(&b);
    BOOST_CHECK_EQUAL(2u, l.size());
    BOOST_CHECK_EQUAL(l, list_of(b)(a));
}

BOOST_FIXTURE_TEST_CASE(push_back, Fixture) {
    l.push_back(&a);
    BOOST_CHECK_EQUAL(1u, l.size());
    BOOST_CHECK_EQUAL(l, list_of(a));
    l.push_back(&b);
    BOOST_CHECK_EQUAL(2u, l.size());
    BOOST_CHECK_EQUAL(l, list_of(a)(b));
}

BOOST_FIXTURE_TEST_CASE(insert, Fixture) {
    List::iterator i(l.begin());
    i = l.insert(i, &a);
    BOOST_CHECK_EQUAL(l, list_of(a));
    BOOST_CHECK(i == l.begin());

    i = l.insert(i, &b);
    BOOST_CHECK_EQUAL(l, list_of(b)(a));
    BOOST_CHECK(i == l.begin());

    i++;
    BOOST_CHECK_EQUAL(*i, a);    
    i = l.insert(i, &c);
    BOOST_CHECK_EQUAL(l, list_of(b)(c)(a));
    BOOST_CHECK_EQUAL(*i, c);

    i = l.insert(i, &d);
    BOOST_CHECK_EQUAL(l, list_of(b)(d)(c)(a));
    BOOST_CHECK_EQUAL(*i, d);
}

BOOST_FIXTURE_TEST_CASE(iterator_test, Fixture) {
    l.push_back(&a);
    l.push_back(&b);
    
    List::iterator i = l.begin();
    BOOST_CHECK_EQUAL(*i, a);
    BOOST_CHECK_EQUAL(static_cast<Node*>(i), &a);
    List::const_iterator ci = i;
    BOOST_CHECK_EQUAL(static_cast<const Node*>(ci), &a);

    i++;
    BOOST_CHECK_EQUAL(*i, b);    
    BOOST_CHECK_EQUAL(static_cast<Node*>(i), &b);
    i++;
    BOOST_CHECK(i == l.end());
}

BOOST_FIXTURE_TEST_CASE(pop_front, Fixture) {
    l.push_back(&a);
    l.push_back(&b);
    l.pop_front();
    BOOST_CHECK_EQUAL(l, list_of(b));
    l.pop_front();
    BOOST_CHECK(l.empty());
}

BOOST_FIXTURE_TEST_CASE(erase, Fixture) {
    l.push_back(&a);
    l.push_back(&b);
    l.push_back(&c);

    List::iterator i=l.begin();
    i++;
    l.erase(i);
    BOOST_CHECK_EQUAL(l, list_of(a)(c));

    i=l.begin();
    i++;
    l.erase(i);
    BOOST_CHECK_EQUAL(l, list_of(a));

    l.erase(l.begin());
    BOOST_CHECK(l.empty());
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


BOOST_AUTO_TEST_CASE(intrusive_ptr_test) {
    smart_pointer_test<IntrusiveNode>();
}


struct SharedNode : public NodeBase, public ISListNode<boost::shared_ptr<SharedNode> > {
    SharedNode() : NodeBase(0) {}
};

BOOST_AUTO_TEST_CASE(shared_ptr_test) {
    smart_pointer_test<SharedNode>();
}

QPID_AUTO_TEST_SUITE_END()
