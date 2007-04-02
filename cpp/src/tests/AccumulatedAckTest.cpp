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
#include "../broker/AccumulatedAck.h"
#include "qpid_test_plugin.h"
#include <iostream>
#include <list>

using std::list;
using namespace qpid::broker;

class AccumulatedAckTest : public CppUnit::TestCase  
{
        CPPUNIT_TEST_SUITE(AccumulatedAckTest);
        CPPUNIT_TEST(testGeneral);
        CPPUNIT_TEST(testCovers);
        CPPUNIT_TEST(testUpdateAndConsolidate);
        CPPUNIT_TEST_SUITE_END();

    public:
        void testGeneral()
        {
            AccumulatedAck ack(0);
            ack.clear();
            ack.update(3,3);
            ack.update(7,7);
            ack.update(9,9);
            ack.update(1,2);
            ack.update(4,5);
            ack.update(6,6);

            for(int i = 1; i <= 7; i++) CPPUNIT_ASSERT(ack.covers(i));
            CPPUNIT_ASSERT(ack.covers(9));

            CPPUNIT_ASSERT(!ack.covers(8));
            CPPUNIT_ASSERT(!ack.covers(10));

            ack.consolidate();

            for(int i = 1; i <= 7; i++) CPPUNIT_ASSERT(ack.covers(i));
            CPPUNIT_ASSERT(ack.covers(9));

            CPPUNIT_ASSERT(!ack.covers(8));
            CPPUNIT_ASSERT(!ack.covers(10));
        }

        void testCovers()
        {
            AccumulatedAck ack(5);
            ack.individual.push_back(7);
            ack.individual.push_back(9);
            
            CPPUNIT_ASSERT(ack.covers(1));
            CPPUNIT_ASSERT(ack.covers(2));
            CPPUNIT_ASSERT(ack.covers(3));
            CPPUNIT_ASSERT(ack.covers(4));
            CPPUNIT_ASSERT(ack.covers(5));
            CPPUNIT_ASSERT(ack.covers(7));
            CPPUNIT_ASSERT(ack.covers(9));

            CPPUNIT_ASSERT(!ack.covers(6));
            CPPUNIT_ASSERT(!ack.covers(8));
            CPPUNIT_ASSERT(!ack.covers(10));
        }

        void testUpdateAndConsolidate()
        {
            AccumulatedAck ack(0);
            ack.update(1, 1);
            ack.update(3, 3);
            ack.update(10, 10);
            ack.update(8, 8);
            ack.update(6, 6);
            ack.update(3, 3);
            ack.update(2, 2);
            ack.update(0, 5);
            ack.consolidate();
            CPPUNIT_ASSERT_EQUAL((uint64_t) 6, ack.range);
            CPPUNIT_ASSERT_EQUAL((size_t) 2, ack.individual.size());
            list<uint64_t>::iterator i = ack.individual.begin();
            CPPUNIT_ASSERT_EQUAL((uint64_t) 8, *i);
            i++;
            CPPUNIT_ASSERT_EQUAL((uint64_t) 10, *i);
        }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(AccumulatedAckTest);

