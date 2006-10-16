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
#include "../../src/qpid/framing/amqp_framing.h"
#include "ConnectionRedirectBody.h"
#include <iostream>
#include <sstream>
#include <qpid_test_plugin.h>
#include <typeinfo>

using namespace qpid::framing;

// TODO aconway 2006-09-12: Why do we  need explicit qpid::framing:: below?

template <class T>
std::string tostring(const T& x) 
{
    std::ostringstream out;
    out << x;
    return out.str();
}

class FramingTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(FramingTest);
    CPPUNIT_TEST(testBasicQosBody); 
    CPPUNIT_TEST(testConnectionSecureBody); 
    CPPUNIT_TEST(testConnectionRedirectBody);
    CPPUNIT_TEST(testAccessRequestBody);
    CPPUNIT_TEST(testBasicConsumeBody);
    CPPUNIT_TEST(ConnectionRedirectBody);
    CPPUNIT_TEST(BasicConsumeOkBody);
    CPPUNIT_TEST_SUITE_END();

  private:
    Buffer buffer;

  public:

    FramingTest() : buffer(100) {}

    void testBasicQosBody() 
    {
        BasicQosBody in(0xCAFEBABE, 0xABBA, true);
        in.encodeContent(buffer);
        buffer.flip(); 
        BasicQosBody out;
        out.decodeContent(buffer);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }
    
    void testConnectionSecureBody() 
    {
        std::string s = "security credential";
        ConnectionSecureBody in(s);
        in.encodeContent(buffer);
        buffer.flip(); 
        ConnectionSecureBody out;
        out.decodeContent(buffer);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }

    void testConnectionRedirectBody()
    {
        std::string a = "hostA";
        std::string b = "hostB";
        qpid::framing::ConnectionRedirectBody in(a, b);
        in.encodeContent(buffer);
        buffer.flip(); 
        qpid::framing::ConnectionRedirectBody out;
        out.decodeContent(buffer);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }

    void testAccessRequestBody()
    {
        std::string s = "text";
        AccessRequestBody in(s, true, false, true, false, true);
        in.encodeContent(buffer);
        buffer.flip(); 
        AccessRequestBody out;
        out.decodeContent(buffer);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }

    void testBasicConsumeBody()
    {
        std::string q = "queue";
        std::string t = "tag";
        BasicConsumeBody in(0, q, t, false, true, false, false);
        in.encodeContent(buffer);
        buffer.flip(); 
        BasicConsumeBody out;
        out.decodeContent(buffer);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }
    

    void ConnectionRedirectBody()
    {
        std::string a = "hostA";
        std::string b = "hostB";
        AMQFrame in(999, new qpid::framing::ConnectionRedirectBody(a, b));
        in.encode(buffer);
        buffer.flip(); 
        AMQFrame out;
        out.decode(buffer);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }

    void BasicConsumeOkBody()
    {
        std::string s = "hostA";
        AMQFrame in(999, new qpid::framing::BasicConsumeOkBody(s));
        in.encode(buffer);
        buffer.flip(); 
        AMQFrame out;
        for(int i = 0; i < 5; i++){
            out.decode(buffer);
            CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
        }
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(FramingTest);



