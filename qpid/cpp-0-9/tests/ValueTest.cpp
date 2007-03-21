/*
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
 */
#include <Value.h>
#include <qpid_test_plugin.h>

using namespace qpid::framing;


class ValueTest : public CppUnit::TestCase 
{
    CPPUNIT_TEST_SUITE(ValueTest);
    CPPUNIT_TEST(testStringValueEquals);
    CPPUNIT_TEST(testIntegerValueEquals);
    CPPUNIT_TEST(testDecimalValueEquals);
    CPPUNIT_TEST(testFieldTableValueEquals);
    CPPUNIT_TEST_SUITE_END();

    StringValue s;
    IntegerValue i;
    DecimalValue d;
    FieldTableValue ft;
    EmptyValue e;

  public:
    ValueTest() :
        s("abc"),
        i(42),
        d(1234,2)
        
    {
        ft.getValue().setString("foo", "FOO");
        ft.getValue().setInt("magic", 7);
    }
    
    void testStringValueEquals() 
    {
        
        CPPUNIT_ASSERT(StringValue("abc") == s);
        CPPUNIT_ASSERT(s != StringValue("foo"));
        CPPUNIT_ASSERT(s != e);
        CPPUNIT_ASSERT(e != d);
        CPPUNIT_ASSERT(e != ft);
    }

    void testIntegerValueEquals()
    {
        CPPUNIT_ASSERT(IntegerValue(42) == i);
        CPPUNIT_ASSERT(IntegerValue(5) != i);
        CPPUNIT_ASSERT(i != e);
        CPPUNIT_ASSERT(i != d);
    }

    void testDecimalValueEquals() 
    {
        CPPUNIT_ASSERT(DecimalValue(1234, 2) == d);
        CPPUNIT_ASSERT(DecimalValue(12345, 2) != d);
        CPPUNIT_ASSERT(DecimalValue(1234, 3) != d);
        CPPUNIT_ASSERT(d != s);
    }


    void testFieldTableValueEquals()
    {
        CPPUNIT_ASSERT_EQUAL(std::string("FOO"),
                             ft.getValue().getString("foo"));
        CPPUNIT_ASSERT_EQUAL(7, ft.getValue().getInt("magic"));
        
        FieldTableValue f2;
        CPPUNIT_ASSERT(ft != f2);
        f2.getValue().setString("foo", "FOO");
        CPPUNIT_ASSERT(ft != f2);
        f2.getValue().setInt("magic", 7);
        CPPUNIT_ASSERT_EQUAL(ft,f2);
        CPPUNIT_ASSERT(ft == f2);
        f2.getValue().setString("foo", "BAR");
        CPPUNIT_ASSERT(ft != f2);
        CPPUNIT_ASSERT(ft != i);
    }
    
};

    
// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(ValueTest);

