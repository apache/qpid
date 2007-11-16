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
#include <AMQP_HighestVersion.h>
#include <amqp_framing.h>
#include <qpid_test_plugin.h>
using namespace qpid::framing;

class BodyHandlerTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(BodyHandlerTest);
    CPPUNIT_TEST(testMethod);
    CPPUNIT_TEST(testHeader);
    CPPUNIT_TEST(testContent);
    CPPUNIT_TEST(testHeartbeat);
    CPPUNIT_TEST_SUITE_END();
private:

    class TestBodyHandler : public BodyHandler{
        AMQMethodBody* const method;
        AMQHeaderBody* const header;
        AMQContentBody* const content;
        AMQHeartbeatBody* const heartbeat;        

    public:

        TestBodyHandler(AMQMethodBody* _method) : method(_method), header(0), content(0), heartbeat(0){}
        TestBodyHandler(AMQHeaderBody* _header) : method(0), header(_header), content(0), heartbeat(0){}
        TestBodyHandler(AMQContentBody* _content) : method(0), header(0), content(_content), heartbeat(0){}
        TestBodyHandler(AMQHeartbeatBody* _heartbeat) : method(0), header(0), content(0), heartbeat(_heartbeat){}

	virtual void handleMethod(AMQMethodBody::shared_ptr body){
            CPPUNIT_ASSERT(method);
            CPPUNIT_ASSERT_EQUAL(method, body.get());
        }
	virtual void handleHeader(AMQHeaderBody::shared_ptr body){
            CPPUNIT_ASSERT(header);
            CPPUNIT_ASSERT_EQUAL(header, body.get());
        }
	virtual void handleContent(AMQContentBody::shared_ptr body){
            CPPUNIT_ASSERT(content);
            CPPUNIT_ASSERT_EQUAL(content, body.get());
        }
	virtual void handleHeartbeat(AMQHeartbeatBody::shared_ptr body){
            CPPUNIT_ASSERT(heartbeat);
            CPPUNIT_ASSERT_EQUAL(heartbeat, body.get());
        }
    };
  	ProtocolVersion v;

public:
        
    BodyHandlerTest() : v(8, 0) {}

    void testMethod() 
    {
        AMQMethodBody* method = new QueueDeclareBody(v);
        AMQFrame frame(highestProtocolVersion, 0, method);
        TestBodyHandler handler(method);
        handler.handleBody(frame.getBody());
    }

    void testHeader() 
    {
        AMQHeaderBody* header = new AMQHeaderBody();
        AMQFrame frame(highestProtocolVersion, 0, header);
        TestBodyHandler handler(header);
        handler.handleBody(frame.getBody());
    }

    void testContent() 
    {
        AMQContentBody* content = new AMQContentBody();
        AMQFrame frame(highestProtocolVersion, 0, content);
        TestBodyHandler handler(content);
        handler.handleBody(frame.getBody());
    }

    void testHeartbeat() 
    {
        AMQHeartbeatBody* heartbeat = new AMQHeartbeatBody();
        AMQFrame frame(highestProtocolVersion, 0, heartbeat);
        TestBodyHandler handler(heartbeat);
        handler.handleBody(frame.getBody());
    }
};


// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(BodyHandlerTest);

