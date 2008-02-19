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

#include "qpid/sys/RefCountedMap.h"
#include "unit_test.h"
#include "test_tools.h"
#include <boost/bind.hpp>
#include <map>

QPID_AUTO_TEST_SUITE(RefCountedMapTestSuite)

using namespace std;
using namespace qpid;
using namespace qpid::sys;

template <int Id> struct Counted {
    static int instances;
    Counted() { ++instances; }
    Counted(const Counted&) { ++instances; }
    ~Counted() { --instances; }
};
template <int Id>int Counted<Id>::instances=0;

struct Data : public RefCountedMapData<int, Data>, public Counted<2> {
    Data(int i=0) : value(i) {}
    int value;
    void inc()  { value++; }
};

struct Container : public RefCounted, public Counted<3>  {
    Data::Map map;
    Container() : map(*this) {}
};
    
struct RefCountedMapFixture {
    intrusive_ptr<Container> cont;
    intrusive_ptr<Data> p, q;
    RefCountedMapFixture() :
        cont(new Container()), p(new Data(1)), q(new Data(2))
    {
        cont->map.insert(1,p);
        cont->map.insert(2,q);
    }
    ~RefCountedMapFixture() { if (cont) cont->map.clear(); }
};

BOOST_FIXTURE_TEST_CASE(testFixtureSetup, RefCountedMapFixture) {
    BOOST_CHECK_EQUAL(1, Container::instances);
    BOOST_CHECK_EQUAL(2, Data::instances);
    BOOST_CHECK_EQUAL(cont->map.size(), 2u);
    BOOST_CHECK_EQUAL(cont->map.find(1)->value, 1);
    BOOST_CHECK_EQUAL(cont->map.find(2)->value, 2);
}

BOOST_FIXTURE_TEST_CASE(testReleaseRemoves, RefCountedMapFixture)
{
    // Release external ref, removes from map
    p = 0;
    BOOST_CHECK_EQUAL(Data::instances, 1);
    BOOST_CHECK_EQUAL(cont->map.size(), 1u);
    BOOST_CHECK(!cont->map.find(1));
    BOOST_CHECK_EQUAL(cont->map.find(2)->value, 2);

    q = 0;
    BOOST_CHECK(cont->map.empty());
}

// Functor that releases values as a side effect.
struct Release {
    RefCountedMapFixture& f ;
    Release(RefCountedMapFixture& ff) : f(ff) {}
    void operator()(const intrusive_ptr<Data>& ptr) {
        BOOST_CHECK(ptr->value > 0); // Make sure ptr is not released.
        f.p = 0;
        f.q = 0;
        BOOST_CHECK(ptr->value > 0); // Make sure ptr is not released.
    }
};


BOOST_FIXTURE_TEST_CASE(testApply, RefCountedMapFixture) {
    cont->map.apply(boost::bind(&Data::inc, _1));
    BOOST_CHECK_EQUAL(2, p->value);
    BOOST_CHECK_EQUAL(3, q->value);

    // Allow functors to release valuse as side effects.
    cont->map.apply(Release(*this));
    BOOST_CHECK(cont->map.empty());
    BOOST_CHECK_EQUAL(Data::instances, 0);
}

BOOST_FIXTURE_TEST_CASE(testClearFunctor, RefCountedMapFixture) {
    cont->map.clear(boost::bind(&Data::inc, _1));
    BOOST_CHECK(cont->map.empty());
    BOOST_CHECK_EQUAL(2, p->value);
    BOOST_CHECK_EQUAL(3, q->value);
}

BOOST_FIXTURE_TEST_CASE(testReleaseEmptyMap, RefCountedMapFixture) {
    // Container must not be deleted till map is empty.
    cont = 0;
    BOOST_CHECK_EQUAL(1, Container::instances); // Not empty.
    p = 0;
    q = 0;
    BOOST_CHECK_EQUAL(0, Container::instances); // Deleted
}

QPID_AUTO_TEST_SUITE_END()
