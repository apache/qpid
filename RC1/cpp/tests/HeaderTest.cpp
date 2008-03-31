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
#include <amqp_framing.h>
#include <qpid_test_plugin.h>

using namespace qpid::framing;

class HeaderTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(HeaderTest);
    CPPUNIT_TEST(testGenericProperties);
    CPPUNIT_TEST(testAllSpecificProperties);
    CPPUNIT_TEST(testSomeSpecificProperties);
    CPPUNIT_TEST_SUITE_END();

public:

    void testGenericProperties() 
    {
        AMQHeaderBody body(BASIC);
        dynamic_cast<BasicHeaderProperties*>(body.getProperties())->getHeaders().setString("A", "BCDE");
        Buffer buffer(100);

        body.encode(buffer);
        buffer.flip();     
        AMQHeaderBody body2;
        body2.decode(buffer, body.size());
        BasicHeaderProperties* props =
            dynamic_cast<BasicHeaderProperties*>(body2.getProperties());
        CPPUNIT_ASSERT_EQUAL(std::string("BCDE"),
                             props->getHeaders().getString("A"));
    }

    void testAllSpecificProperties(){
	string contentType("text/html");
	string contentEncoding("UTF8");
	u_int8_t deliveryMode(2);
	u_int8_t priority(3);
	string correlationId("abc");
	string replyTo("no-address");
	string expiration("why is this a string?");
	string messageId("xyz");
	u_int64_t timestamp(0xabcd);
	string type("eh?");
	string userId("guest");
	string appId("just testing");
	string clusterId("no clustering required");

        AMQHeaderBody body(BASIC);
        BasicHeaderProperties* properties = 
            dynamic_cast<BasicHeaderProperties*>(body.getProperties());
        properties->setContentType(contentType);
        properties->getHeaders().setString("A", "BCDE");
        properties->setDeliveryMode(deliveryMode);
        properties->setPriority(priority);
        properties->setCorrelationId(correlationId);
        properties->setReplyTo(replyTo);
        properties->setExpiration(expiration);
        properties->setMessageId(messageId);
        properties->setTimestamp(timestamp);
        properties->setType(type);
        properties->setUserId(userId);
        properties->setAppId(appId);
        properties->setClusterId(clusterId);

        Buffer buffer(10000);
        body.encode(buffer);
        buffer.flip();     
        AMQHeaderBody temp;
        temp.decode(buffer, body.size());
        properties = dynamic_cast<BasicHeaderProperties*>(temp.getProperties());

        CPPUNIT_ASSERT_EQUAL(contentType, properties->getContentType());
        CPPUNIT_ASSERT_EQUAL(std::string("BCDE"), properties->getHeaders().getString("A"));
        CPPUNIT_ASSERT_EQUAL(deliveryMode, properties->getDeliveryMode());
        CPPUNIT_ASSERT_EQUAL(priority, properties->getPriority());
        CPPUNIT_ASSERT_EQUAL(correlationId, properties->getCorrelationId());
        CPPUNIT_ASSERT_EQUAL(replyTo, properties->getReplyTo());
        CPPUNIT_ASSERT_EQUAL(expiration, properties->getExpiration());
        CPPUNIT_ASSERT_EQUAL(messageId, properties->getMessageId());
        CPPUNIT_ASSERT_EQUAL(timestamp, properties->getTimestamp());
        CPPUNIT_ASSERT_EQUAL(type, properties->getType());
        CPPUNIT_ASSERT_EQUAL(userId, properties->getUserId());
        CPPUNIT_ASSERT_EQUAL(appId, properties->getAppId());
        CPPUNIT_ASSERT_EQUAL(clusterId, properties->getClusterId());
    }

    void testSomeSpecificProperties(){
        string contentType("application/octet-stream");
        u_int8_t deliveryMode(5);
        u_int8_t priority(6);
        string expiration("Z");
        u_int64_t timestamp(0xabe4a34a);

        AMQHeaderBody body(BASIC);
        BasicHeaderProperties* properties = 
            dynamic_cast<BasicHeaderProperties*>(body.getProperties());
        properties->setContentType(contentType);
        properties->setDeliveryMode(deliveryMode);
        properties->setPriority(priority);
        properties->setExpiration(expiration);
        properties->setTimestamp(timestamp);

        Buffer buffer(100);
        body.encode(buffer);
        buffer.flip();     
        AMQHeaderBody temp;
        temp.decode(buffer, body.size());
        properties = dynamic_cast<BasicHeaderProperties*>(temp.getProperties());

        CPPUNIT_ASSERT_EQUAL(contentType, properties->getContentType());
        CPPUNIT_ASSERT_EQUAL((int) deliveryMode, (int) properties->getDeliveryMode());
        CPPUNIT_ASSERT_EQUAL((int) priority, (int) properties->getPriority());
        CPPUNIT_ASSERT_EQUAL(expiration, properties->getExpiration());
        CPPUNIT_ASSERT_EQUAL(timestamp, properties->getTimestamp());
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(HeaderTest);

