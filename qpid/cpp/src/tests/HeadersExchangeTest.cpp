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

#include "qpid/Exception.h"
#include "qpid/broker/HeadersExchange.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/FieldValue.h"
#include "qpid_test_plugin.h"

using namespace qpid::broker;
using namespace qpid::framing;

class HeadersExchangeTest : public CppUnit::TestCase
{
    CPPUNIT_TEST_SUITE(HeadersExchangeTest);
    CPPUNIT_TEST(testMatchAll);
    CPPUNIT_TEST(testMatchAny);
    CPPUNIT_TEST(testMatchEmptyValue);
    CPPUNIT_TEST(testMatchEmptyArgs);
    CPPUNIT_TEST(testMatchNoXMatch);
    CPPUNIT_TEST(testBindNoXMatch);
    CPPUNIT_TEST_SUITE_END();

  public:

    void testMatchAll() 
    {
        FieldTable b, m, n;
        b.setString("x-match", "all");
        b.setString("foo", "FOO");
        b.setInt("n", 42);
        m.setString("foo", "FOO");
        m.setInt("n", 42);
        CPPUNIT_ASSERT(HeadersExchange::match(b, m));

        // Ignore extras.
        m.setString("extra", "x");
        CPPUNIT_ASSERT(HeadersExchange::match(b, m));
        
        // Fail mismatch, wrong value.
        m.setString("foo", "NotFoo");
        CPPUNIT_ASSERT(!HeadersExchange::match(b, m));

        // Fail mismatch, missing value
        n.setInt("n", 42);
        n.setString("extra", "x");
        CPPUNIT_ASSERT(!HeadersExchange::match(b, n));
    }

    void testMatchAny() 
    {
        FieldTable b, m, n;
        b.setString("x-match", "any");
        b.setString("foo", "FOO");
        b.setInt("n", 42);
        m.setString("foo", "FOO");
        CPPUNIT_ASSERT(!HeadersExchange::match(b, n));
        CPPUNIT_ASSERT(HeadersExchange::match(b, m));
        m.setInt("n", 42);
        CPPUNIT_ASSERT(HeadersExchange::match(b, m));
    }

    void testMatchEmptyValue() 
    {
        FieldTable b, m;
        b.setString("x-match", "all");
        b.set("foo", FieldTable::ValuePtr());
        b.set("n", FieldTable::ValuePtr());
        CPPUNIT_ASSERT(!HeadersExchange::match(b, m));
        m.setString("foo", "blah");
        m.setInt("n", 123);
    }

    void testMatchEmptyArgs()
    {
        FieldTable b, m;
        m.setString("foo", "FOO");
        
        b.setString("x-match", "all");
        CPPUNIT_ASSERT(HeadersExchange::match(b, m));
        b.setString("x-match", "any");
        CPPUNIT_ASSERT(!HeadersExchange::match(b, m));
    }
    

    void testMatchNoXMatch() 
    {
        FieldTable b, m;
        b.setString("foo", "FOO");
        m.setString("foo", "FOO");
        CPPUNIT_ASSERT(!HeadersExchange::match(b, m));
    }
    
    void testBindNoXMatch() 
    {
        HeadersExchange exchange("test");
        Queue::shared_ptr queue;
        std::string key;
        FieldTable args;
        try {
            //just checking this doesn't cause assertion etc
            exchange.bind(queue, key, &args);
        } catch(qpid::Exception&) {
            //expected
        }
    }
    
    
};
    
// make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(HeadersExchangeTest);
