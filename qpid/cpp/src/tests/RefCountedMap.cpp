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
#include <boost/bind.hpp>

QPID_AUTO_TEST_SUITE(RefCountedMapTestSuite)

using namespace std;
using namespace qpid;
using namespace qpid::sys;

template <int ID> struct CountEm {
    static int instances;
    CountEm() { instances++; }
    ~CountEm() { instances--; }
    CountEm(const CountEm&) { instances++; }
};
template <int ID> int CountEm<ID>::instances = 0;
    
struct Data;

struct Attachment : public RefCounted, public CountEm<1> {
    intrusive_ptr<Data> link;
};

struct Data : public RefCounted, public CountEm<2> {
    intrusive_ptr<Attachment> link;
    void attach(intrusive_ptr<Attachment> a) {
        if (!a) return;
        a->link=this;
        link=a;
    }
    void detach() {
        if (!link) return;
        intrusive_ptr<Data> protect(this);
        link->link=0;
        link=0;
    }
};
typedef intrusive_ptr<Data> DataPtr;

struct Map : public  RefCountedMap<int,Data>, public CountEm<3> {};




BOOST_AUTO_TEST_CASE(testRefCountedMap) {
    BOOST_CHECK_EQUAL(0, Map::instances);
    BOOST_CHECK_EQUAL(0, Data::instances);

    intrusive_ptr<Map> map=new Map();
    BOOST_CHECK_EQUAL(1, Map::instances);

    // Empty map
    BOOST_CHECK(!map->isClosed());
    BOOST_CHECK(map->empty());
    BOOST_CHECK_EQUAL(map->size(), 0u);
    BOOST_CHECK(!map->find(1));

    {
        // Add entries
        DataPtr p=map->get(1);
        DataPtr q=map->get(2);

        BOOST_CHECK_EQUAL(Data::instances, 2);
        BOOST_CHECK_EQUAL(map->size(), 2u);

        p=0;                    // verify erased
        BOOST_CHECK_EQUAL(Data::instances, 1);
        BOOST_CHECK_EQUAL(map->size(), 1u);

        p=map->find(2);
        BOOST_CHECK(q==p);
    }

    BOOST_CHECK(map->empty());
    BOOST_CHECK_EQUAL(1, Map::instances); 
    BOOST_CHECK_EQUAL(0, Data::instances); 

    {
        // Hold the map via a reference to an entry.
        DataPtr p=map->get(3);
        map=0;               
        BOOST_CHECK_EQUAL(1, Map::instances); // Held by entry.
    }
    BOOST_CHECK_EQUAL(0, Map::instances); // entry released
}


BOOST_AUTO_TEST_CASE(testRefCountedMapAttachClose) {
    intrusive_ptr<Map> map=new Map();
    DataPtr d=map->get(5);
    d->attach(new Attachment());
    d=0;
    // Attachment keeps entry pinned
    BOOST_CHECK_EQUAL(1u, map->size());
    BOOST_CHECK(map->find(5));

    // Close breaks attachment
    map->close(boost::bind(&Data::detach, _1));
    BOOST_CHECK(map->empty());
    BOOST_CHECK(map->isClosed());
    BOOST_CHECK_EQUAL(0, Data::instances);
    BOOST_CHECK_EQUAL(0, Attachment::instances);
}

QPID_AUTO_TEST_SUITE_END()
