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
#include <ConnectionRedirectBody.h>
#include <ProtocolVersion.h>
#include <amqp_framing.h>
#include <iostream>
#include <qpid_test_plugin.h>
#include <sstream>
#include <typeinfo>
#include <AMQP_HighestVersion.h>
#include "AMQRequestBody.h"
#include "AMQResponseBody.h"


using namespace qpid::framing;

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
    CPPUNIT_TEST(testRequestBodyFrame);
    CPPUNIT_TEST(testResponseBodyFrame);
    CPPUNIT_TEST_SUITE_END();

  private:
    Buffer buffer;
    ProtocolVersion version;
    AMQP_MethodVersionMap versionMap;
    
  public:

    FramingTest() : buffer(1024), version(highestProtocolVersion) {}

    void testBasicQosBody() 
    {
        BasicQosBody in(version, 0xCAFEBABE, 0xABBA, true);
        in.encodeContent(buffer);
        buffer.flip(); 
        BasicQosBody out(version);
        out.decodeContent(buffer);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }
    
    void testConnectionSecureBody() 
    {
        std::string s = "security credential";
        ConnectionSecureBody in(version, s);
        in.encodeContent(buffer);
        buffer.flip(); 
        ConnectionSecureBody out(version);
        out.decodeContent(buffer);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }

    void testConnectionRedirectBody()
    {
        std::string a = "hostA";
        std::string b = "hostB";
        ConnectionRedirectBody in(version, a, b);
        in.encodeContent(buffer);
        buffer.flip(); 
        ConnectionRedirectBody out(version);
        out.decodeContent(buffer);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }

    void testAccessRequestBody()
    {
        std::string s = "text";
        AccessRequestBody in(version, s, true, false, true, false, true);
        in.encodeContent(buffer);
        buffer.flip(); 
        AccessRequestBody out(version);
        out.decodeContent(buffer);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }

    void testBasicConsumeBody()
    {
        std::string q = "queue";
        std::string t = "tag";
        BasicConsumeBody in(version, 0, q, t, false, true, false, false,
                            FieldTable());
        in.encodeContent(buffer);
        buffer.flip(); 
        BasicConsumeBody out(version);
        out.decodeContent(buffer);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }
    

    void testConnectionRedirectBodyFrame()
    {
        std::string a = "hostA";
        std::string b = "hostB";
        AMQFrame in(version, 999, new ConnectionRedirectBody(version, a, b));
        in.encode(buffer);
        buffer.flip(); 
        AMQFrame out;
        out.decode(buffer);
        CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
    }

    void testBasicConsumeOkBodyFrame()
    {
        std::string s = "hostA";
        AMQFrame in(version, 999, new BasicConsumeOkBody(version, s));
        in.encode(buffer);
        buffer.flip(); 
        AMQFrame out;
        for(int i = 0; i < 5; i++){
            out.decode(buffer);
            CPPUNIT_ASSERT_EQUAL(tostring(in), tostring(out));
        }
    }

    void testRequestBodyFrame() {
        AMQMethodBody::shared_ptr method(new ChannelOkBody(version));
        AMQRequestBody::shared_ptr request(
            new AMQRequestBody(versionMap, version, 111, 222, method));
        AMQFrame in(version, 999, request);
        in.encode(buffer);
        buffer.flip();
        AMQFrame out;
        out.decode(buffer);
        request = boost::dynamic_pointer_cast<AMQRequestBody>(out.getBody());
        CPPUNIT_ASSERT(request);
        CPPUNIT_ASSERT_EQUAL(111ULL, request->getRequestId());
        CPPUNIT_ASSERT_EQUAL(222ULL, request->getResponseMark());
        AMQMethodBody& body = request->getMethodBody();
        CPPUNIT_ASSERT(dynamic_cast<ChannelOkBody*>(&body));
    }
    
    void testResponseBodyFrame() {
        AMQMethodBody::shared_ptr method(new ChannelOkBody(version));
        AMQResponseBody::shared_ptr response(
            new AMQResponseBody(versionMap, version, 111, 222, 333, method));
        AMQFrame in(version, 999, response);
        in.encode(buffer);
        buffer.flip();
        AMQFrame out;
        out.decode(buffer);
        response = boost::dynamic_pointer_cast<AMQResponseBody>(out.getBody());
        CPPUNIT_ASSERT(response);
        CPPUNIT_ASSERT_EQUAL(111ULL, response->getResponseId());
        CPPUNIT_ASSERT_EQUAL(222ULL, response->getRequestId());
        CPPUNIT_ASSERT_EQUAL(333U, response->getBatchOffset());
        AMQMethodBody& body = response->getMethodBody();
        CPPUNIT_ASSERT(dynamic_cast<ChannelOkBody*>(&body));
    }
};


// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(FramingTest);



