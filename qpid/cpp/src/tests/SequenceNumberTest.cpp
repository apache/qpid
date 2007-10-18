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

#include "qpid_test_plugin.h"
#include <iostream>
#include "qpid/framing/SequenceNumber.h"
#include "qpid/framing/SequenceNumberSet.h"

using namespace qpid::framing;

class SequenceNumberTest : public CppUnit::TestCase
{
    CPPUNIT_TEST_SUITE(SequenceNumberTest);
    CPPUNIT_TEST(testIncrementPostfix);
    CPPUNIT_TEST(testIncrementPrefix);
    CPPUNIT_TEST(testWrapAround);
    CPPUNIT_TEST(testCondense);
    CPPUNIT_TEST(testCondenseSingleRange);
    CPPUNIT_TEST(testCondenseSingleItem);
    CPPUNIT_TEST(testDifference);
    CPPUNIT_TEST(testDifferenceWithWrapAround1);
    CPPUNIT_TEST(testDifferenceWithWrapAround2);
    CPPUNIT_TEST_SUITE_END();

  public:

    void testIncrementPostfix() 
    {
        SequenceNumber a;
        SequenceNumber b;
        CPPUNIT_ASSERT(!(a > b));
        CPPUNIT_ASSERT(!(b < a));
        CPPUNIT_ASSERT(a == b);

        SequenceNumber c = a++;
        CPPUNIT_ASSERT(a > b);
        CPPUNIT_ASSERT(b < a);
        CPPUNIT_ASSERT(a != b);
        CPPUNIT_ASSERT(c < a);
        CPPUNIT_ASSERT(a != c);

        b++;
        CPPUNIT_ASSERT(!(a > b));
        CPPUNIT_ASSERT(!(b < a));
        CPPUNIT_ASSERT(a == b);
        CPPUNIT_ASSERT(c < b);
        CPPUNIT_ASSERT(b != c);
    }

    void testIncrementPrefix() 
    {
        SequenceNumber a;
        SequenceNumber b;
        CPPUNIT_ASSERT(!(a > b));
        CPPUNIT_ASSERT(!(b < a));
        CPPUNIT_ASSERT(a == b);

        SequenceNumber c = ++a;
        CPPUNIT_ASSERT(a > b);
        CPPUNIT_ASSERT(b < a);
        CPPUNIT_ASSERT(a != b);
        CPPUNIT_ASSERT(a == c);

        ++b;
        CPPUNIT_ASSERT(!(a > b));
        CPPUNIT_ASSERT(!(b < a));
        CPPUNIT_ASSERT(a == b);
    }

    void testWrapAround()
    {
        const uint32_t max = 0xFFFFFFFF;
        SequenceNumber a(max - 10);
        SequenceNumber b(max - 5);
        checkComparison(a, b, 5);

        const uint32_t max_signed = 0x7FFFFFFF;
        SequenceNumber c(max_signed - 10);
        SequenceNumber d(max_signed - 5);
        checkComparison(c, d, 5);
    }

    void checkComparison(SequenceNumber& a, SequenceNumber& b, int gap)
    {
        //increment until b wraps around
        for (int i = 0; i < (gap + 2); i++) {
            CPPUNIT_ASSERT(++a < ++b);//test prefix
        }
        //keep incrementing until a also wraps around
        for (int i = 0; i < (gap + 2); i++) {            
            CPPUNIT_ASSERT(a++ < b++);//test postfix
        }
        //let a 'catch up'
        for (int i = 0; i < gap; i++) {
            a++;
        }
        CPPUNIT_ASSERT(a == b);
        CPPUNIT_ASSERT(++a > b);
    }

    void testCondense()
    {
        SequenceNumberSet set;
        for (uint i = 0; i < 6; i++) {
            set.push_back(SequenceNumber(i));
        }
        set.push_back(SequenceNumber(7));
        for (uint i = 9; i < 13; i++) {
            set.push_back(SequenceNumber(i));
        }
        set.push_back(SequenceNumber(13));
        SequenceNumberSet actual = set.condense();

        SequenceNumberSet expected;
        expected.addRange(SequenceNumber(0), SequenceNumber(5));
        expected.addRange(SequenceNumber(7), SequenceNumber(7));
        expected.addRange(SequenceNumber(9), SequenceNumber(13));
        CPPUNIT_ASSERT_EQUAL(expected, actual);
    }

    void testCondenseSingleRange()
    {
        SequenceNumberSet set;
        for (uint i = 0; i < 6; i++) {
            set.push_back(SequenceNumber(i));
        }
        SequenceNumberSet actual = set.condense();

        SequenceNumberSet expected;
        expected.addRange(SequenceNumber(0), SequenceNumber(5));
        CPPUNIT_ASSERT_EQUAL(expected, actual);
    }

    void testCondenseSingleItem()
    {
        SequenceNumberSet set;
        set.push_back(SequenceNumber(1));
        SequenceNumberSet actual = set.condense();

        SequenceNumberSet expected;
        expected.addRange(SequenceNumber(1), SequenceNumber(1));
        CPPUNIT_ASSERT_EQUAL(expected, actual);
    }

    void testDifference()
    {
        SequenceNumber a;
        SequenceNumber b;

        for (int i = 0; i < 10; i++, ++a) {
            CPPUNIT_ASSERT_EQUAL(i, a - b);
            CPPUNIT_ASSERT_EQUAL(-i, b - a);
        }

        b = a;

        for (int i = 0; i < 10; i++, ++b) {
            CPPUNIT_ASSERT_EQUAL(-i, a - b);
            CPPUNIT_ASSERT_EQUAL(i, b - a);
        }
    }

    void testDifferenceWithWrapAround1()
    {
        const uint32_t max = 0xFFFFFFFF;
        SequenceNumber a(max - 5);
        SequenceNumber b(max - 10);
        checkDifference(a, b, 5);
    }

    void testDifferenceWithWrapAround2()
    {
        const uint32_t max_signed = 0x7FFFFFFF;
        SequenceNumber c(max_signed - 5);
        SequenceNumber d(max_signed - 10);
        checkDifference(c, d, 5);
    }

    void checkDifference(SequenceNumber& a, SequenceNumber& b, int gap)
    {
        CPPUNIT_ASSERT_EQUAL(gap, a - b);
        CPPUNIT_ASSERT_EQUAL(-gap, b - a);

        //increment until b wraps around
        for (int i = 0; i < (gap + 2); i++, ++a, ++b) {
            CPPUNIT_ASSERT_EQUAL(gap, a - b);
        }
        //keep incrementing until a also wraps around
        for (int i = 0; i < (gap + 2); i++, ++a, ++b) {
            CPPUNIT_ASSERT_EQUAL(gap, a - b);
        }
        //let b catch up and overtake
        for (int i = 0; i < (gap*2); i++, ++b) {
            CPPUNIT_ASSERT_EQUAL(gap - i, a - b);
            CPPUNIT_ASSERT_EQUAL(i - gap, b - a);
        }
    }
};
    
// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(SequenceNumberTest);
