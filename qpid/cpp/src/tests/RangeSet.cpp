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

namespace qpid {
namespace tests {

QPID_AUTO_TEST_SUITE(RangeSetTestSuite)

typedef qpid::Range<int> TR;    // Test Range
typedef RangeSet<int> TRSet;

QPID_AUTO_TEST_CASE(testEmptyRange) {
    TR r;
    BOOST_CHECK_EQUAL(r, TR(0,0));
    BOOST_CHECK(r.empty());
    BOOST_CHECK(!r.contains(0));
}

QPID_AUTO_TEST_CASE(testRangeSetAddPoint) {
    TRSet r;
    BOOST_CHECK(r.empty());
    r += 3;
    BOOST_CHECK_MESSAGE(r.contains(3), r);
    BOOST_CHECK_MESSAGE(r.contains(TR(3,4)), r);
    BOOST_CHECK(!r.empty());
    r += 5;
    BOOST_CHECK_MESSAGE(r.contains(5), r);
    BOOST_CHECK_MESSAGE(r.contains(TR(5,6)), r);
    BOOST_CHECK_MESSAGE(!r.contains(TR(3,6)), r);
    r += 4;
    BOOST_CHECK_MESSAGE(r.contains(TR(3,6)), r);
}

QPID_AUTO_TEST_CASE(testRangeSetAddRange) {
    TRSet r;
    r += TR(0,3);
    BOOST_CHECK(r.contains(TR(0,3)));
    BOOST_CHECK(r.contiguous());
    r += TR(4,6);
    BOOST_CHECK(!r.contiguous());
    BOOST_CHECK_MESSAGE(r.contains(TR(4,6)), r);
    r += 3;
    BOOST_CHECK_MESSAGE(r.contains(TR(0,6)), r);
    BOOST_CHECK(r.front() == 0);
    BOOST_CHECK(r.back() == 6);

    // Merging additions
    r = TRSet(0,3)+TR(5,6);
    TRSet e(0,6);
    BOOST_CHECK_EQUAL(r + TR(3,5), e);
    BOOST_CHECK(e.contiguous());
    r = TRSet(0,5)+TR(10,15)+TR(20,25)+TR(30,35)+TR(40,45);
    BOOST_CHECK_EQUAL(r + TR(11,37), TRSet(0,5)+TR(11,37)+TR(40,45));
}

QPID_AUTO_TEST_CASE(testRangeSetAddSet) {
    TRSet r;
    TRSet s = TRSet(0,3)+TR(5,10);
    r += s;
    BOOST_CHECK_EQUAL(r,s);
    r += TRSet(3,5) + TR(7,12) + 15;
    BOOST_CHECK_EQUAL(r, TRSet(0,12) + 15);

    r.clear();
    BOOST_CHECK(r.empty());
    r += TR::makeClosed(6,10);
    BOOST_CHECK_EQUAL(r, TRSet(6,11));
    r += TRSet(2,6)+8;
    BOOST_CHECK_EQUAL(r, TRSet(2,11));
}

QPID_AUTO_TEST_CASE(testRangeSetIterate) {
    TRSet r = TRSet(1,3)+TR(4,7)+TR(10,11);
    std::vector<int> actual;
    std::copy(r.begin(), r.end(), std::back_inserter(actual));
    std::vector<int> expect = boost::assign::list_of(1)(2)(4)(5)(6)(10);
    BOOST_CHECK_EQUAL(expect, actual);
}

QPID_AUTO_TEST_CASE(testRangeSetRemove) {
    // points
    BOOST_CHECK_EQUAL(TRSet(0,5)-3, TRSet(0,3)+TR(4,5));
    BOOST_CHECK_EQUAL(TRSet(1,5)-5, TRSet(1,5));
    BOOST_CHECK_EQUAL(TRSet(1,5)-0, TRSet(1,5));

    TRSet r(TRSet(0,5)+TR(10,15)+TR(20,25));

    // TRs
    BOOST_CHECK_EQUAL(r-TR(0,5), TRSet(10,15)+TR(20,25));
    BOOST_CHECK_EQUAL(r-TR(10,15), TRSet(0,5)+TR(20,25));
    BOOST_CHECK_EQUAL(r-TR(20,25), TRSet(0,5)+TR(10,15));

    BOOST_CHECK_EQUAL(r-TR(-5, 30), TRSet());

    BOOST_CHECK_EQUAL(r-TR(-5, 7), TRSet(10,15)+TR(20,25));
    BOOST_CHECK_EQUAL(r-TR(8,19), TRSet(0,5)+TR(20,25));
    BOOST_CHECK_EQUAL(r-TR(17,30), TRSet(0,5)+TR(10,15));

    BOOST_CHECK_EQUAL(r-TR(-5, 5), TRSet(10,15)+TR(20,25));
    BOOST_CHECK_EQUAL(r-TR(10,19), TRSet(0,5)+TR(20,25));
    BOOST_CHECK_EQUAL(r-TR(18,25), TRSet(0,5)+TR(10,15));
    BOOST_CHECK_EQUAL(r-TR(23,25), TRSet(0,5)+TR(10,15)+TR(20,23));

    BOOST_CHECK_EQUAL(r-TR(-3, 3), TRSet(3,5)+TR(10,15)+TR(20,25));
    BOOST_CHECK_EQUAL(r-TR(3, 7), TRSet(0,2)+TR(10,15)+TR(20,25));
    BOOST_CHECK_EQUAL(r-TR(3, 12), TRSet(0,3)+TR(12,15)+TR(20,25));
    BOOST_CHECK_EQUAL(r-TR(3, 22), TRSet(12,15)+TR(22,25));
    BOOST_CHECK_EQUAL(r-TR(12, 22), TRSet(0,5)+TR(10,11)+TR(22,25));

    // Sets
    BOOST_CHECK_EQUAL(r-(TRSet(-1,6)+TR(11,14)+TR(23,25)),
                      TRSet(10,11)+TR(14,15)+TR(20,23));
    // Split the ranges
    BOOST_CHECK_EQUAL(r-(TRSet(2,3)+TR(11,13)+TR(21,23)),
                      TRSet(0,2)+TR(4,5)+
                      TR(10,11)+TR(14,15)+
                      TR(20,21)+TR(23,25));
    // Truncate the ranges
    BOOST_CHECK_EQUAL(r-(TRSet(0,3)+TR(13,15)+TR(19,23)),
                      TRSet(3,5)+TR(10,13)+TR(20,23));
    // Remove multiple ranges with truncation
    BOOST_CHECK_EQUAL(r-(TRSet(3,23)), TRSet(0,3)+TR(23,25));
    // Remove multiple ranges in middle
    TRSet r2 = TRSet(0,5)+TR(10,15)+TR(20,25)+TR(30,35);
    BOOST_CHECK_EQUAL(r2-TRSet(11,24),
                      TRSet(0,5)+TR(10,11)+TR(24,25)+TR(30,35));
}

QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests
