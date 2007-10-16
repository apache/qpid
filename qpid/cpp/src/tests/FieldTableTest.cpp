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
#include <iostream>
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/FieldValue.h"

#include "qpid_test_plugin.h"

using namespace qpid::framing;

class FieldTableTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(FieldTableTest);
    CPPUNIT_TEST(testMe);
    CPPUNIT_TEST(testAssignment);
    CPPUNIT_TEST_SUITE_END();

  public:

    void testMe() 
    {
        FieldTable ft;
        ft.setString("A", "BCDE");
        CPPUNIT_ASSERT(StringValue("BCDE") == *ft.get("A"));

        char buff[100];
        Buffer wbuffer(buff, 100);
        wbuffer.put(ft);

        Buffer rbuffer(buff, 100);
        FieldTable ft2;
        rbuffer.get(ft2);
        CPPUNIT_ASSERT(StringValue("BCDE") == *ft2.get("A"));

    }

    void testAssignment()
    {
        FieldTable a;
        FieldTable b;

        a.setString("A", "BBBB");
        a.setInt("B", 1234);
        b = a;
        a.setString("A", "CCCC");
        
        CPPUNIT_ASSERT(StringValue("CCCC") == *a.get("A"));
        CPPUNIT_ASSERT(StringValue("BBBB") == *b.get("A"));
        CPPUNIT_ASSERT_EQUAL(1234, a.getInt("B"));
        CPPUNIT_ASSERT_EQUAL(1234, b.getInt("B"));
        CPPUNIT_ASSERT(IntegerValue(1234) == *a.get("B"));
        CPPUNIT_ASSERT(IntegerValue(1234) == *b.get("B"));

        FieldTable d;
        {
            FieldTable c;
            c = a;
            
            char* buff = static_cast<char*>(::alloca(c.size()));
            Buffer wbuffer(buff, c.size());
            wbuffer.put(c);

            Buffer rbuffer(buff, c.size());
            rbuffer.get(d);
            CPPUNIT_ASSERT_EQUAL(c, d);
            CPPUNIT_ASSERT(StringValue("CCCC") == *c.get("A"));
            CPPUNIT_ASSERT(IntegerValue(1234) == *c.get("B"));
        }
        CPPUNIT_ASSERT(StringValue("CCCC") == *d.get("A"));
        CPPUNIT_ASSERT(IntegerValue(1234) == *d.get("B"));
    }
};


// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(FieldTableTest);

