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
#include "qpid/broker/Message.h"
#include "qpid/framing/AMQP_HighestVersion.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/FieldValue.h"

#include "qpid_test_plugin.h"

#include <iostream>

using namespace boost;
using namespace qpid::broker;
using namespace qpid::framing;


class MessageTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(MessageTest);
    CPPUNIT_TEST(testEncodeDecode);
    CPPUNIT_TEST_SUITE_END();

  public:

    void testEncodeDecode()
    {
        string exchange = "MyExchange";
        string routingKey = "MyRoutingKey";
        string messageId = "MyMessage";
        string data1("abcdefg");
        string data2("hijklmn");

        intrusive_ptr<Message> msg(new Message());

        AMQFrame method(in_place<MessageTransferBody>(
                            ProtocolVersion(), 0, exchange, 0, 0));
        AMQFrame header(in_place<AMQHeaderBody>());
        AMQFrame content1(in_place<AMQContentBody>(data1));
        AMQFrame content2(in_place<AMQContentBody>(data2));

        msg->getFrames().append(method);
        msg->getFrames().append(header);
        msg->getFrames().append(content1);
        msg->getFrames().append(content2);

        MessageProperties* mProps = msg->getFrames().getHeaders()->get<MessageProperties>(true);
        mProps->setContentLength(data1.size() + data2.size());        
        mProps->setMessageId(messageId);
        FieldTable applicationHeaders;
        applicationHeaders.setString("abc", "xyz");
        mProps->setApplicationHeaders(applicationHeaders);
        DeliveryProperties* dProps = msg->getFrames().getHeaders()->get<DeliveryProperties>(true);
        dProps->setRoutingKey(routingKey);
        dProps->setDeliveryMode(PERSISTENT);
        CPPUNIT_ASSERT(msg->isPersistent());

        char* buff = static_cast<char*>(::alloca(msg->encodedSize()));
        Buffer wbuffer(buff, msg->encodedSize());
        msg->encode(wbuffer);
        
        Buffer rbuffer(buff, msg->encodedSize());
        msg = new Message();
        msg->decodeHeader(rbuffer);
        msg->decodeContent(rbuffer);
        CPPUNIT_ASSERT_EQUAL(exchange, msg->getExchangeName());
        CPPUNIT_ASSERT_EQUAL(routingKey, msg->getRoutingKey());
        CPPUNIT_ASSERT_EQUAL((uint64_t) data1.size() + data2.size(), msg->contentSize());
        CPPUNIT_ASSERT_EQUAL((uint64_t) data1.size() + data2.size(), msg->getProperties<MessageProperties>()->getContentLength());
        CPPUNIT_ASSERT_EQUAL(messageId, msg->getProperties<MessageProperties>()->getMessageId());
        CPPUNIT_ASSERT(StringValue("xyz") == *msg->getProperties<MessageProperties>()->getApplicationHeaders().get("abc"));
        CPPUNIT_ASSERT_EQUAL((uint8_t) PERSISTENT, msg->getProperties<DeliveryProperties>()->getDeliveryMode());
        CPPUNIT_ASSERT(msg->isPersistent());
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(MessageTest);

