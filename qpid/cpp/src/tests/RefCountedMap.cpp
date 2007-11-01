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

#include <boost/test/auto_unit_test.hpp>
BOOST_AUTO_TEST_SUITE(RefCountedMap);

using namespace std;
using namespace qpid;
using namespace qpid::sys;

struct TestMap : public  RefCountedMap<int,int> {
    static int instances;
    TestMap() { ++instances; }
    ~TestMap() { --instances; }
};

int TestMap::instances=0;

BOOST_AUTO_TEST_CASE(testRefCountedMap) {
    BOOST_CHECK_EQUAL(0, TestMap::instances);
    intrusive_ptr<TestMap> map=new TestMap();
    BOOST_CHECK_EQUAL(1, TestMap::instances);

    // Empty map
    BOOST_CHECK(map->empty());
    BOOST_CHECK_EQUAL(map->size(), 0u);
    BOOST_CHECK(map->begin()==map->end());
    BOOST_CHECK(!map->begin());
    BOOST_CHECK(!map->end());
    BOOST_CHECK(map->find(1)==map->end());
    BOOST_CHECK(!map->find(1));

    {
        // Add and modify an entry
        pair<TestMap::iterator, bool> ib=map->insert(TestMap::value_type(1,11));
        BOOST_CHECK(ib.second);
        TestMap::iterator p = ib.first;
        ib.first.reset();
        BOOST_CHECK(p);
        BOOST_CHECK_EQUAL(p->second, 11);
        p->second=22;
        BOOST_CHECK_EQUAL(22, map->find(1)->second);
        BOOST_CHECK(!map->empty());
        BOOST_CHECK_EQUAL(map->size(), 1u);

        // Find an entry
        TestMap::iterator q=map->find(1);
        BOOST_CHECK(q==p);
        BOOST_CHECK_EQUAL(q->first, 1);
    }

    BOOST_CHECK(map->empty());
    BOOST_CHECK_EQUAL(1, TestMap::instances); 

    {
        // Hold the map via a reference to an entry.
        TestMap::iterator p=map->insert(TestMap::value_type(2,22)).first;
        map=0;                      // Release the map->
        BOOST_CHECK_EQUAL(1, TestMap::instances); // Held by entry.
        BOOST_CHECK_EQUAL(p->second, 22);
    }

    BOOST_CHECK_EQUAL(0, TestMap::instances); 
}


BOOST_AUTO_TEST_CASE(testRefCountedMapIterator) {
    BOOST_CHECK_EQUAL(TestMap::instances, 0);
    {
        intrusive_ptr<TestMap> map=new TestMap();
        TestMap::iterator iter[4], p, q;
        for (int i = 0; i < 4; ++i) 
            iter[i] = map->insert(make_pair(i, 10+i)).first;
        int j=0;
        for (p = map->begin(); p != map->end(); ++p, ++j)  {
            BOOST_CHECK_EQUAL(p->first, j);
            BOOST_CHECK_EQUAL(p->second, 10+j);
        }
        BOOST_CHECK_EQUAL(4, j);

        // Release two entries.
        iter[0]=iter[2]=TestMap::iterator();
        
        p=map->begin();
        BOOST_CHECK_EQUAL(p->second, 11);
        ++p;
        BOOST_CHECK_EQUAL(p->second, 13);
    }
    BOOST_CHECK_EQUAL(TestMap::instances, 0);
}
