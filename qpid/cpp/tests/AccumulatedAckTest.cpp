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
#include <AccumulatedAck.h>
#include <qpid_test_plugin.h>
#include <iostream>
#include <list>

using std::list;
using namespace qpid::broker;

class AccumulatedAckTest : public CppUnit::TestCase  
{
        CPPUNIT_TEST_SUITE(AccumulatedAckTest);
        CPPUNIT_TEST(testCovers);
        CPPUNIT_TEST(testUpdateAndConsolidate);
        CPPUNIT_TEST_SUITE_END();

    public:
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
            ack.update(1, false);
            ack.update(3, false);
            ack.update(10, false);
            ack.update(8, false);
            ack.update(6, false);
            ack.update(3, true);
            ack.update(2, true);
            ack.update(5, true);
            ack.consolidate();
            CPPUNIT_ASSERT_EQUAL((u_int64_t) 5, ack.range);
            CPPUNIT_ASSERT_EQUAL((size_t) 3, ack.individual.size());
            list<u_int64_t>::iterator i = ack.individual.begin();
            CPPUNIT_ASSERT_EQUAL((u_int64_t) 6, *i);
            i++;
            CPPUNIT_ASSERT_EQUAL((u_int64_t) 8, *i);
            i++;
            CPPUNIT_ASSERT_EQUAL((u_int64_t) 10, *i);

        }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(AccumulatedAckTest);

