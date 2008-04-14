
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
#include "qpid/framing/AccumulatedAck.h"
#include "qpid_test_plugin.h"
#include <iostream>
#include <list>

using std::list;
using namespace qpid::framing;

class AccumulatedAckTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(AccumulatedAckTest);
    CPPUNIT_TEST(testGeneral);
    CPPUNIT_TEST(testCovers);
    CPPUNIT_TEST(testUpdateFromCompletionData);
    CPPUNIT_TEST(testCase1);
    CPPUNIT_TEST(testCase2);
    CPPUNIT_TEST(testCase3);
    CPPUNIT_TEST(testCase4);
    CPPUNIT_TEST(testConsolidation1);
    CPPUNIT_TEST(testConsolidation2);
    CPPUNIT_TEST(testConsolidation3);
    CPPUNIT_TEST(testConsolidation4);
    CPPUNIT_TEST_SUITE_END();

public:
    bool covers(const AccumulatedAck& ack, int i)
    {
        return ack.covers(SequenceNumber(i));
    }

    void update(AccumulatedAck& ack, int start, int end)
    {
        ack.update(SequenceNumber(start), SequenceNumber(end));
    }

    void testGeneral()
    {
        AccumulatedAck ack(0);
        ack.clear();
        update(ack, 3,3);
        update(ack, 7,7);
        update(ack, 9,9);
        update(ack, 1,2);
        update(ack, 4,5);
        update(ack, 6,6);

        for(int i = 1; i <= 7; i++) CPPUNIT_ASSERT(covers(ack, i));
        CPPUNIT_ASSERT(covers(ack, 9));

        CPPUNIT_ASSERT(!covers(ack, 8));
        CPPUNIT_ASSERT(!covers(ack, 10));

        ack.consolidate();

        for(int i = 1; i <= 7; i++) CPPUNIT_ASSERT(covers(ack, i));
        CPPUNIT_ASSERT(covers(ack, 9));

        CPPUNIT_ASSERT(!covers(ack, 8));
        CPPUNIT_ASSERT(!covers(ack, 10));
    }

    void testCovers()
    {
        AccumulatedAck ack(5);
        update(ack, 7, 7);
        update(ack, 9, 9);

        CPPUNIT_ASSERT(covers(ack, 1));
        CPPUNIT_ASSERT(covers(ack, 2));
        CPPUNIT_ASSERT(covers(ack, 3));
        CPPUNIT_ASSERT(covers(ack, 4));
        CPPUNIT_ASSERT(covers(ack, 5));
        CPPUNIT_ASSERT(covers(ack, 7));
        CPPUNIT_ASSERT(covers(ack, 9));

        CPPUNIT_ASSERT(!covers(ack, 6));
        CPPUNIT_ASSERT(!covers(ack, 8));
        CPPUNIT_ASSERT(!covers(ack, 10));
    }

    void testUpdateFromCompletionData()
    {
        AccumulatedAck ack(0);
        SequenceNumber mark(2);
        SequenceNumberSet ranges;
        ranges.addRange(SequenceNumber(5), SequenceNumber(8));
        ranges.addRange(SequenceNumber(10), SequenceNumber(15));
        ranges.addRange(SequenceNumber(9), SequenceNumber(9));
        ranges.addRange(SequenceNumber(3), SequenceNumber(4));

        ack.update(mark, ranges);

        for(int i = 0; i <= 15; i++) {            
            CPPUNIT_ASSERT(covers(ack, i));
        }
        CPPUNIT_ASSERT(!covers(ack, 16));
        CPPUNIT_ASSERT_EQUAL((uint32_t) 15, ack.mark.getValue());
    }

    void testCase1()
    {
        AccumulatedAck ack(3);
        update(ack, 1,2);
        for(int i = 1; i <= 3; i++) CPPUNIT_ASSERT(covers(ack, i));
        CPPUNIT_ASSERT(!covers(ack, 4));
    }

    void testCase2()
    {
        AccumulatedAck ack(3);
        update(ack, 3,6);
        for(int i = 1; i <= 6; i++) CPPUNIT_ASSERT(covers(ack, i));
        CPPUNIT_ASSERT(!covers(ack, 7));
    }

    void testCase3()
    {
        AccumulatedAck ack(3);
        update(ack, 4,6);
        for(int i = 1; i <= 6; i++) {
            CPPUNIT_ASSERT(covers(ack, i));
        }
        CPPUNIT_ASSERT(!covers(ack, 7));
    }

    void testCase4()
    {
        AccumulatedAck ack(3);
        update(ack, 5,6);
        for(int i = 1; i <= 6; i++) {
            if (i == 4) CPPUNIT_ASSERT(!covers(ack, i));
            else CPPUNIT_ASSERT(covers(ack, i));
        }
        CPPUNIT_ASSERT(!covers(ack, 7));
    }

    void testConsolidation1()
    {
        AccumulatedAck ack(3);
        update(ack, 7,7);
        CPPUNIT_ASSERT_EQUAL((uint32_t) 3, ack.mark.getValue());
        CPPUNIT_ASSERT_EQUAL((size_t) 1, ack.ranges.size());

        update(ack, 8,9);
        CPPUNIT_ASSERT_EQUAL((uint32_t) 3, ack.mark.getValue());
        CPPUNIT_ASSERT_EQUAL((size_t) 1, ack.ranges.size());

        update(ack, 1,2);
        CPPUNIT_ASSERT_EQUAL((uint32_t) 3, ack.mark.getValue());
        CPPUNIT_ASSERT_EQUAL((size_t) 1, ack.ranges.size());

        update(ack, 4,5);
        CPPUNIT_ASSERT_EQUAL((uint32_t) 5, ack.mark.getValue());
        CPPUNIT_ASSERT_EQUAL((size_t) 1, ack.ranges.size());

        update(ack, 6,6);
        CPPUNIT_ASSERT_EQUAL((uint32_t) 9, ack.mark.getValue());
        CPPUNIT_ASSERT_EQUAL((size_t) 0, ack.ranges.size());

        for(int i = 1; i <= 9; i++) CPPUNIT_ASSERT(covers(ack, i));
        CPPUNIT_ASSERT(!covers(ack, 10));
    }

    void testConsolidation2()
    {
        AccumulatedAck ack(0);
        update(ack, 10,12);
        CPPUNIT_ASSERT_EQUAL((uint32_t) 0, ack.mark.getValue());
        CPPUNIT_ASSERT_EQUAL((size_t) 1, ack.ranges.size());

        update(ack, 7,9);
        CPPUNIT_ASSERT_EQUAL((uint32_t) 0, ack.mark.getValue());
        CPPUNIT_ASSERT_EQUAL((size_t) 1, ack.ranges.size());
        CPPUNIT_ASSERT_EQUAL((uint32_t) 7, ack.ranges.front().start.getValue());
        CPPUNIT_ASSERT_EQUAL((uint32_t) 12, ack.ranges.front().end.getValue());

        update(ack, 5,7);
        CPPUNIT_ASSERT_EQUAL((uint32_t) 0, ack.mark.getValue());
        CPPUNIT_ASSERT_EQUAL((size_t) 1, ack.ranges.size());
        CPPUNIT_ASSERT_EQUAL((uint32_t) 5, ack.ranges.front().start.getValue());
        CPPUNIT_ASSERT_EQUAL((uint32_t) 12, ack.ranges.front().end.getValue());

        update(ack, 3,4);
        CPPUNIT_ASSERT_EQUAL((uint32_t) 0, ack.mark.getValue());
        CPPUNIT_ASSERT_EQUAL((size_t) 1, ack.ranges.size());
        CPPUNIT_ASSERT_EQUAL((uint32_t) 3, ack.ranges.front().start.getValue());
        CPPUNIT_ASSERT_EQUAL((uint32_t) 12, ack.ranges.front().end.getValue());

        update(ack, 1,2);
        CPPUNIT_ASSERT_EQUAL((uint32_t) 12, ack.mark.getValue());
        CPPUNIT_ASSERT_EQUAL((size_t) 0, ack.ranges.size());

        for(int i = 1; i <= 12; i++) CPPUNIT_ASSERT(covers(ack, i));
        CPPUNIT_ASSERT(!covers(ack, 13));
    }

    void testConsolidation3()
    {
        AccumulatedAck ack(0);
        update(ack, 10,12);
        update(ack, 6,7);
        update(ack, 3,4);
        update(ack, 1,15);
        CPPUNIT_ASSERT_EQUAL((uint32_t) 15, ack.mark.getValue());
        CPPUNIT_ASSERT_EQUAL((size_t) 0, ack.ranges.size());
    }

    void testConsolidation4()
    {
        AccumulatedAck ack(0);
        ack.update(SequenceNumber(0), SequenceNumber(2));
        ack.update(SequenceNumber(5), SequenceNumber(8));
        ack.update(SequenceNumber(10), SequenceNumber(15));
        ack.update(SequenceNumber(9), SequenceNumber(9));
        ack.update(SequenceNumber(3), SequenceNumber(4));

        for(int i = 0; i <= 15; i++) {            
            CPPUNIT_ASSERT(covers(ack, i));
        }
        CPPUNIT_ASSERT(!covers(ack, 16));
        CPPUNIT_ASSERT_EQUAL((uint32_t) 15, ack.mark.getValue());
    }

};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(AccumulatedAckTest);

