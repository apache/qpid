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

#include "qpid/InlineVector.h"
#include <boost/test/auto_unit_test.hpp>
BOOST_AUTO_TEST_SUITE(InlineVector);

using namespace qpid;
using namespace std;

typedef InlineVector<int, 3> Vec;

bool isInline(const Vec& v) {
    return (char*)&v <= (char*)v.data() &&
        (char*)v.data() < (char*)&v+sizeof(v);
}

BOOST_AUTO_TEST_CASE(testCtor) {
    {
        Vec v;
        BOOST_CHECK(isInline(v));
        BOOST_CHECK(v.empty());
    }
    {
        Vec v(3, 42);
        BOOST_CHECK(isInline(v));
        BOOST_CHECK_EQUAL(3u, v.size());
        BOOST_CHECK_EQUAL(v[0], 42);
        BOOST_CHECK_EQUAL(v[2], 42);

        Vec u(v);
        BOOST_CHECK(isInline(u));
        BOOST_CHECK_EQUAL(3u, u.size());
        BOOST_CHECK_EQUAL(u[0], 42);
        BOOST_CHECK_EQUAL(u[2], 42);
    }

    {
        Vec v(4, 42);

        BOOST_CHECK_EQUAL(v.size(), 4u);
        BOOST_CHECK(!isInline(v));
        Vec u(v);
        BOOST_CHECK_EQUAL(u.size(), 4u);
        BOOST_CHECK(!isInline(u));
    }
}

BOOST_AUTO_TEST_CASE(testInsert) {
    Vec v;
    v.push_back(1);
    BOOST_CHECK_EQUAL(v.size(), 1u);
    BOOST_CHECK_EQUAL(v.back(), 1);
    BOOST_CHECK(isInline(v));

    v.insert(v.begin(), 2);
    BOOST_CHECK_EQUAL(v.size(), 2u);
    BOOST_CHECK_EQUAL(v.back(), 1);
    BOOST_CHECK(isInline(v));

    v.push_back(3);
    BOOST_CHECK(isInline(v));

    v.push_back(4);
    BOOST_CHECK_EQUAL(v.size(), 4u);
    BOOST_CHECK(!isInline(v));
}


BOOST_AUTO_TEST_SUITE_END();
