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
using namespace std;

class HeaderTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(HeaderTest);
    CPPUNIT_TEST(testGenericProperties);
    CPPUNIT_TEST(testMessageProperties);
    CPPUNIT_TEST(testDeliveryProperies);
    CPPUNIT_TEST_SUITE_END();

  public:

    void testGenericProperties() 
    {
        AMQHeaderBody body;
        body.get<MessageProperties>(true)->getApplicationHeaders().setString(
            "A", "BCDE");
        char buff[100];
        Buffer wbuffer(buff, 100);
        body.encode(wbuffer);

        Buffer rbuffer(buff, 100);
        AMQHeaderBody body2;
        body2.decode(rbuffer, body.size());
        MessageProperties* props =
            body2.get<MessageProperties>(true);
        CPPUNIT_ASSERT_EQUAL(
            string("BCDE"),
            props->getApplicationHeaders().get("A")->get<string>());
    }

    void testMessageProperties() {
        AMQFrame out(in_place<AMQHeaderBody>());
        MessageProperties* props1 = 
            out.castBody<AMQHeaderBody>()->get<MessageProperties>(true);

        props1->setContentLength(42);
        props1->setMessageId(Uuid(true));
        props1->setCorrelationId("correlationId");
        props1->setReplyTo(ReplyTo("ex","key"));
        props1->setContentType("contentType");
        props1->setContentEncoding("contentEncoding");
        props1->setUserId("userId");
        props1->setAppId("appId");

        char buff[10000];
        Buffer wbuffer(buff, 10000);
        out.encode(wbuffer);

        Buffer rbuffer(buff, 10000);
        AMQFrame in;
        in.decode(rbuffer);
        MessageProperties* props2 =
            in.castBody<AMQHeaderBody>()->get<MessageProperties>(true);

        CPPUNIT_ASSERT_EQUAL(props1->getContentLength(), props2->getContentLength());
        CPPUNIT_ASSERT_EQUAL(props1->getMessageId(), props2->getMessageId());
        CPPUNIT_ASSERT_EQUAL(props1->getCorrelationId(), props2->getCorrelationId());
        CPPUNIT_ASSERT_EQUAL(props1->getContentType(), props2->getContentType());
        CPPUNIT_ASSERT_EQUAL(props1->getContentEncoding(), props2->getContentEncoding());
        CPPUNIT_ASSERT_EQUAL(props1->getUserId(), props2->getUserId());
        CPPUNIT_ASSERT_EQUAL(props1->getAppId(), props2->getAppId());

    }

    void testDeliveryProperies() {
        AMQFrame out(in_place<AMQHeaderBody>());
        DeliveryProperties* props1 = 
            out.castBody<AMQHeaderBody>()->get<DeliveryProperties>(true);

        props1->setDiscardUnroutable(true);
        props1->setExchange("foo");

        char buff[10000];
        Buffer wbuffer(buff, 10000);
        out.encode(wbuffer);

        Buffer rbuffer(buff, 10000);
        AMQFrame in;
        in.decode(rbuffer);
        DeliveryProperties* props2 =
            in.castBody<AMQHeaderBody>()->get<DeliveryProperties>(true);

        CPPUNIT_ASSERT(props2->getDiscardUnroutable());
        CPPUNIT_ASSERT_EQUAL(string("foo"), props2->getExchange());
        CPPUNIT_ASSERT(!props2->hasTimestamp());
    }

};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(HeaderTest);

