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

#include "qpid/RefCounted.h"

#include <boost/test/auto_unit_test.hpp>
BOOST_AUTO_TEST_SUITE(RefCounted);

using namespace std;
using namespace qpid;

struct DummyCounted : public AbstractRefCounted {
    DummyCounted() : count(0) {}
    mutable int count;
    virtual void addRef() const { count++; }
    virtual void release() const { count--; }
};

BOOST_AUTO_TEST_CASE(testIntrusivePtr) {
    DummyCounted dummy;
    BOOST_CHECK_EQUAL(0, dummy.count);
    {
        intrusive_ptr<DummyCounted> p(&dummy);
        BOOST_CHECK_EQUAL(1, dummy.count);
        {
            intrusive_ptr<DummyCounted> q(p);
            BOOST_CHECK_EQUAL(2, dummy.count);
            intrusive_ptr<DummyCounted> r;
            r=q;
            BOOST_CHECK_EQUAL(3, dummy.count);
        }
        BOOST_CHECK_EQUAL(1, dummy.count);
    }
    BOOST_CHECK_EQUAL(0, dummy.count);
}

struct CountMe : public RefCounted {
    static int instances;
    CountMe() { ++instances; }
    ~CountMe() { --instances; }
};

int CountMe::instances=0;

BOOST_AUTO_TEST_CASE(testRefCounted) {
    BOOST_CHECK_EQUAL(0, CountMe::instances);
    intrusive_ptr<CountMe> p(new CountMe());
    BOOST_CHECK_EQUAL(1, CountMe::instances);
    intrusive_ptr<CountMe> q(p);
    BOOST_CHECK_EQUAL(1, CountMe::instances);
    q=0;
    BOOST_CHECK_EQUAL(1, CountMe::instances);
    p=0;
    BOOST_CHECK_EQUAL(0, CountMe::instances);
}

BOOST_AUTO_TEST_SUITE_END();
