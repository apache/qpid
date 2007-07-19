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

using namespace qpid::framing;

class SequenceNumberTest : public CppUnit::TestCase
{
    CPPUNIT_TEST_SUITE(SequenceNumberTest);
    CPPUNIT_TEST(testIncrementPostfix);
    CPPUNIT_TEST(testIncrementPrefix);
    CPPUNIT_TEST(testWrapAround);
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

        //increment until b wraps around
        for (int i = 0; i < 6; i++) {
            CPPUNIT_ASSERT(++a < ++b);//test prefix
        }
        //verify we have wrapped around
        CPPUNIT_ASSERT(a.getValue() > b.getValue());
        //keep incrementing until a also wraps around
        for (int i = 0; i < 6; i++) {            
            CPPUNIT_ASSERT(a++ < b++);//test postfix
        }
        //let a 'catch up'
        for (int i = 0; i < 5; i++) {
            a++;
        }
        CPPUNIT_ASSERT(a == b);
        CPPUNIT_ASSERT(++a > b);
    }
};
    
// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(SequenceNumberTest);
