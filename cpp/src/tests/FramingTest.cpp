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
#include "qpid/client/Exchange.h"
#include "qpid/client/Queue.h"
#include "qpid/client/Connection.h"
#include "qpid/client/Connector.h"
#include "qpid/framing/AMQP_HighestVersion.h"
#include "qpid/framing/BasicGetOkBody.h"
#include "qpid/framing/ConnectionRedirectBody.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/framing/all_method_bodies.h"
#include "qpid/framing/amqp_framing.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid_test_plugin.h"

#include <boost/bind.hpp>
#include <boost/lexical_cast.hpp>
#include <iostream>

#include <memory>
#include <sstream>
#include <typeinfo>

using namespace qpid;
using namespace qpid::framing;
using namespace std;

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
    CPPUNIT_TEST(testConnectionRedirectBodyFrame);
    CPPUNIT_TEST(testBasicConsumeOkBodyFrame);
    CPPUNIT_TEST(testInlineContent);
    CPPUNIT_TEST(testContentReference);
    CPPUNIT_TEST(testContentValidation);
    CPPUNIT_TEST_SUITE_END();

  private:
    char buffer[1024];
    ProtocolVersion version;
    
  public:

    FramingTest() : version(highestProtocolVersion) {}

    void testBasicQosBody() 
    {
        Buffer wbuff(buffer, sizeof(buffer));
        BasicQosBody in(version, 0xCAFEBABE, 0xABBA, true);
        in.encode(wbuff);

        Buffer rbuff(buffer, sizeof(buffer));
        BasicQosBody out(version);
        out.decode(rbuff);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }
    
    void testConnectionSecureBody() 
    {
        Buffer wbuff(buffer, sizeof(buffer));
        std::string s = "security credential";
        ConnectionSecureBody in(version, s);
        in.encode(wbuff);

        Buffer rbuff(buffer, sizeof(buffer));
        ConnectionSecureBody out(version);
        out.decode(rbuff);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }

    void testConnectionRedirectBody()
    {
        Buffer wbuff(buffer, sizeof(buffer));
        std::string a = "hostA";
        std::string b = "hostB";
        ConnectionRedirectBody in(version, a, b);
        in.encode(wbuff);
        
        Buffer rbuff(buffer, sizeof(buffer));
        ConnectionRedirectBody out(version);
        out.decode(rbuff);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }

    void testAccessRequestBody()
    {
        Buffer wbuff(buffer, sizeof(buffer));
        std::string s = "text";
        AccessRequestBody in(version, s, true, false, true, false, true);
        in.encode(wbuff);

        Buffer rbuff(buffer, sizeof(buffer));
        AccessRequestBody out(version);
        out.decode(rbuff);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }

    void testBasicConsumeBody()
    {
        Buffer wbuff(buffer, sizeof(buffer));
        std::string q = "queue";
        std::string t = "tag";
        BasicConsumeBody in(version, 0, q, t, false, true, false, false,
                            FieldTable());
        in.encode(wbuff);

        Buffer rbuff(buffer, sizeof(buffer));
        BasicConsumeBody out(version);
        out.decode(rbuff);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }
    

    void testConnectionRedirectBodyFrame()
    {
        Buffer wbuff(buffer, sizeof(buffer));
        std::string a = "hostA";
        std::string b = "hostB";
        AMQFrame in(in_place<ConnectionRedirectBody>(version, a, b));
        in.setChannel(999);
        in.encode(wbuff);

        Buffer rbuff(buffer, sizeof(buffer));
        AMQFrame out;
        out.decode(rbuff);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }

    void testBasicConsumeOkBodyFrame()
    {
        Buffer wbuff(buffer, sizeof(buffer));
        std::string s = "hostA";
        AMQFrame in(in_place<BasicConsumeOkBody>(version, s));
        in.setChannel(999);
        in.encode(wbuff);

        Buffer rbuff(buffer, sizeof(buffer));
        AMQFrame out;
        out.decode(rbuff);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }

    void testInlineContent() {        
        Buffer wbuff(buffer, sizeof(buffer));
        Content content(INLINE, "MyData");
        CPPUNIT_ASSERT(content.isInline());
        content.encode(wbuff);

        Buffer rbuff(buffer, sizeof(buffer));
        Content recovered;
        recovered.decode(rbuff);
        CPPUNIT_ASSERT(recovered.isInline());
        CPPUNIT_ASSERT_EQUAL(content.getValue(), recovered.getValue());
    }

    void testContentReference() {        
        Buffer wbuff(buffer, sizeof(buffer));
        Content content(REFERENCE, "MyRef");
        CPPUNIT_ASSERT(content.isReference());
        content.encode(wbuff);

        Buffer rbuff(buffer, sizeof(buffer));
        Content recovered;
        recovered.decode(rbuff);
        CPPUNIT_ASSERT(recovered.isReference());
        CPPUNIT_ASSERT_EQUAL(content.getValue(), recovered.getValue());
    }

    void testContentValidation() {
        try {
            Content content(REFERENCE, "");
            CPPUNIT_ASSERT(false);//fail, expected exception
        } catch (const InvalidArgumentException& e) {}
        
        try {
            Content content(2, "Blah");
            CPPUNIT_ASSERT(false);//fail, expected exception
        } catch (const SyntaxErrorException& e) {}
        
        try {
            Buffer wbuff(buffer, sizeof(buffer));
            wbuff.putOctet(2);
            wbuff.putLongString("blah, blah");
            
            Buffer rbuff(buffer, sizeof(buffer));
            Content content;
            content.decode(rbuff);
            CPPUNIT_FAIL("Expected exception");
        } catch (Exception& e) {}
        
    }

 };


// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(FramingTest);



