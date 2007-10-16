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
#include "qpid/framing/amqp_framing.h"
#include "qpid/framing/FieldValue.h"
#include "qpid_test_plugin.h"

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
        AMQHeaderBody body;
        body.get<BasicHeaderProperties>(true)->getHeaders().setString("A", "BCDE");
        char buff[100];
        Buffer wbuffer(buff, 100);
        body.encode(wbuffer);

        Buffer rbuffer(buff, 100);
        AMQHeaderBody body2;
        body2.decode(rbuffer, body.size());
        BasicHeaderProperties* props =
            body2.get<BasicHeaderProperties>(true);
        CPPUNIT_ASSERT(StringValue("BCDE") == *props->getHeaders().get("A"));
    }

    void testAllSpecificProperties(){
	string contentType("text/html");
	string contentEncoding("UTF8");
	DeliveryMode deliveryMode(PERSISTENT);
	uint8_t priority(3);
	string correlationId("abc");
	string replyTo("no-address");
	string expiration("why is this a string?");
	string messageId("xyz");
	uint64_t timestamp(0xabcd);
	string type("eh?");
	string userId("guest");
	string appId("just testing");
	string clusterId("no clustering required");
        uint64_t contentLength(54321);

        AMQFrame out(0, AMQHeaderBody());
        BasicHeaderProperties* properties = 
            out.castBody<AMQHeaderBody>()->get<BasicHeaderProperties>(true);
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
        properties->setContentLength(contentLength);

        char buff[10000];
        Buffer wbuffer(buff, 10000);
        out.encode(wbuffer);

        Buffer rbuffer(buff, 10000);
        AMQFrame in;
        in.decode(rbuffer);
        properties = in.castBody<AMQHeaderBody>()->get<BasicHeaderProperties>(true);

        CPPUNIT_ASSERT_EQUAL(contentType, properties->getContentType());
        CPPUNIT_ASSERT(StringValue("BCDE") == *properties->getHeaders().get("A"));
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
        CPPUNIT_ASSERT_EQUAL(contentLength, properties->getContentLength());
    }

    void testSomeSpecificProperties(){
        string contentType("application/octet-stream");
        DeliveryMode deliveryMode(PERSISTENT);
        uint8_t priority(6);
        string expiration("Z");
        uint64_t timestamp(0xabe4a34a);

        AMQHeaderBody body;
        BasicHeaderProperties* properties = 
            body.get<BasicHeaderProperties>(true);
        properties->setContentType(contentType);
        properties->setDeliveryMode(deliveryMode);
        properties->setPriority(priority);
        properties->setExpiration(expiration);
        properties->setTimestamp(timestamp);

        char buff[100];
        Buffer wbuffer(buff, 100);
        body.encode(wbuffer);

        Buffer rbuffer(buff, 100);
        AMQHeaderBody temp;
        temp.decode(rbuffer, body.size());
        properties = temp.get<BasicHeaderProperties>(true);

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

