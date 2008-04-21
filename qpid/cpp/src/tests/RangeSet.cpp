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
#include "qpid/RangeSet.h"

using namespace std;
using namespace qpid;

QPID_AUTO_TEST_SUITE(RangeSetTestSuite)

typedef qpid::Range<int> Range;
typedef qpid::RangeSet<int> RangeSet;

QPID_AUTO_TEST_CASE(testEmptyRange) {
    Range r;
    BOOST_CHECK(r.empty());
    BOOST_CHECK(!r.contains(0));
    //    BOOST_CHECK(r.contiguous(0));
}

QPID_AUTO_TEST_CASE(testRangeSetAddPoint) {
    RangeSet r;
    BOOST_CHECK(r.empty());
    r += 3;
    BOOST_CHECK_MESSAGE(r.contains(3), r);
    BOOST_CHECK_MESSAGE(r.contains(Range(3,4)), r);
    BOOST_CHECK(!r.empty());
    r += 5;
    BOOST_CHECK_MESSAGE(r.contains(5), r);        
    BOOST_CHECK_MESSAGE(r.contains(Range(5,6)), r);        
    BOOST_CHECK_MESSAGE(!r.contains(Range(3,6)), r);
    r += 4;
    BOOST_CHECK_MESSAGE(r.contains(Range(3,6)), r);
}

QPID_AUTO_TEST_CASE(testRangeSetAddRange) {
    RangeSet r;
    r += Range(0,3);
    BOOST_CHECK(r.contains(Range(0,3)));
    r += Range(4,6);
    BOOST_CHECK_MESSAGE(r.contains(Range(4,6)), r);
    r += 3;
    BOOST_CHECK_MESSAGE(r.contains(Range(0,6)), r);
    BOOST_CHECK(r.front() == 0);
    BOOST_CHECK(r.back() == 6);
}

QPID_AUTO_TEST_CASE(testRangeSetIterate) {
    RangeSet r;
    (((r += 1) += 10) += Range(4,7)) += 2;
    BOOST_MESSAGE(r);
    std::vector<int> actual;
    std::copy(r.begin(), r.end(), std::back_inserter(actual));
    std::vector<int> expect = boost::assign::list_of(1)(2)(4)(5)(6)(10);
    BOOST_CHECK_EQUAL(expect, actual);
}

QPID_AUTO_TEST_CASE(testRangeSetRemove) {
    BOOST_CHECK_EQUAL(RangeSet(0,5)-3, RangeSet(0,3)+Range(4,5));
    BOOST_CHECK_EQUAL(RangeSet(1,5)-5, RangeSet(1,5));
    BOOST_CHECK_EQUAL(RangeSet(1,5)-0, RangeSet(1,5));

    RangeSet r(RangeSet(0,5)+Range(10,15)+Range(20,25));

    BOOST_CHECK_EQUAL(r-Range(0,5), RangeSet(10,15)+Range(20,25));
    BOOST_CHECK_EQUAL(r-Range(10,15), RangeSet(0,5)+Range(20,25));
    BOOST_CHECK_EQUAL(r-Range(20,25), RangeSet(0,5)+Range(10,15));

    BOOST_CHECK_EQUAL(r-Range(-5, 30), RangeSet());

    BOOST_CHECK_EQUAL(r-Range(-5, 7), RangeSet(10,15)+Range(20,25));
    BOOST_CHECK_EQUAL(r-Range(8,19), RangeSet(0,5)+Range(20,25));
    BOOST_CHECK_EQUAL(r-Range(17,30), RangeSet(0,5)+Range(10,15));

    BOOST_CHECK_EQUAL(r-Range(-5, 5), RangeSet(10,15)+Range(20,25));
    BOOST_CHECK_EQUAL(r-Range(10,19), RangeSet(0,5)+Range(20,25));
    BOOST_CHECK_EQUAL(r-Range(18,25), RangeSet(0,5)+Range(10,15));

    BOOST_CHECK_EQUAL(r-Range(-3, 3), RangeSet(3,5)+Range(10,15)+Range(20,25));
    BOOST_CHECK_EQUAL(r-Range(3, 7), RangeSet(0,2)+Range(10,15)+Range(20,25));
    BOOST_CHECK_EQUAL(r-Range(3, 12), RangeSet(0,3)+Range(12,15)+Range(20,25));
    BOOST_CHECK_EQUAL(r-Range(3, 22), RangeSet(12,15)+Range(22,25));
    BOOST_CHECK_EQUAL(r-Range(12, 22), RangeSet(0,5)+Range(10,11)+Range(22,25));
}

QPID_AUTO_TEST_CASE(testRangeContaining) {
    RangeSet r;
    (((r += 1) += Range(3,5)) += 7);
    BOOST_CHECK_EQUAL(r.rangeContaining(0), Range(0,0));
    BOOST_CHECK_EQUAL(r.rangeContaining(1), Range(1,2));
    BOOST_CHECK_EQUAL(r.rangeContaining(2), Range(2,2));
    BOOST_CHECK_EQUAL(r.rangeContaining(3), Range(3,5));
    BOOST_CHECK_EQUAL(r.rangeContaining(4), Range(3,5));
    BOOST_CHECK_EQUAL(r.rangeContaining(5), Range(5,5));
    BOOST_CHECK_EQUAL(r.rangeContaining(6), Range(6,6));
    BOOST_CHECK_EQUAL(r.rangeContaining(7), Range(7,8));
}

QPID_AUTO_TEST_SUITE_END()
