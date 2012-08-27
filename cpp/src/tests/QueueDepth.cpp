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

#include "qpid/broker/QueueDepth.h"

#include "unit_test.h"

namespace qpid {
namespace tests {

QPID_AUTO_TEST_SUITE(QueueDepthTestSuite)

using namespace qpid::broker;

QPID_AUTO_TEST_CASE(testCompare)
{
    QueueDepth a(0, 0);
    QueueDepth b(1, 1);
    QueueDepth c(2, 2);
    QueueDepth d(1, 1);

    BOOST_CHECK(a < b);
    BOOST_CHECK(b < c);
    BOOST_CHECK(a < c);

    BOOST_CHECK(b > a);
    BOOST_CHECK(c > b);
    BOOST_CHECK(c > a);

    BOOST_CHECK(b == d);
    BOOST_CHECK(d == b);
    BOOST_CHECK(a != b);
    BOOST_CHECK(b != a);

    QueueDepth e; e.setCount(1);
    QueueDepth f; f.setCount(2);
    BOOST_CHECK(e < f);
    BOOST_CHECK(f > e);

    QueueDepth g; g.setSize(1);
    QueueDepth h; h.setSize(2);
    BOOST_CHECK(g < h);
    BOOST_CHECK(h > g);
}

QPID_AUTO_TEST_CASE(testIncrement)
{
    QueueDepth a(5, 10);
    QueueDepth b(3, 6);
    QueueDepth c(8, 16);
    a += b;
    BOOST_CHECK(a == c);
    BOOST_CHECK_EQUAL(8u, a.getCount());
    BOOST_CHECK_EQUAL(16u, a.getSize());
}

QPID_AUTO_TEST_CASE(testDecrement)
{
    QueueDepth a(5, 10);
    QueueDepth b(3, 6);
    QueueDepth c(2, 4);
    a -= b;
    BOOST_CHECK(a == c);
    BOOST_CHECK_EQUAL(2u, a.getCount());
    BOOST_CHECK_EQUAL(4u, a.getSize());
}

QPID_AUTO_TEST_CASE(testAddition)
{
    QueueDepth a(5, 10);
    QueueDepth b(3, 6);

    QueueDepth c = a + b;
    BOOST_CHECK_EQUAL(8u, c.getCount());
    BOOST_CHECK_EQUAL(16u, c.getSize());
}

QPID_AUTO_TEST_CASE(testSubtraction)
{
    QueueDepth a(5, 10);
    QueueDepth b(3, 6);

    QueueDepth c = a - b;
    BOOST_CHECK_EQUAL(2u, c.getCount());
    BOOST_CHECK_EQUAL(4u, c.getSize());
}

QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests
