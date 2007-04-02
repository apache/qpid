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
#include "../broker/BrokerMessage.h"
#include "qpid_test_plugin.h"
#include <iostream>
#include "AMQP_HighestVersion.h"
#include "../framing/AMQFrame.h"
#include "MockChannel.h"

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

        BasicMessage::shared_ptr msg(
            new BasicMessage(0, exchange, routingKey, false, false,
                             MockChannel::basicGetBody()));
        AMQHeaderBody::shared_ptr header(new AMQHeaderBody(BASIC));
        header->setContentSize(14);        
        AMQContentBody::shared_ptr part1(new AMQContentBody(data1));
        AMQContentBody::shared_ptr part2(new AMQContentBody(data2));        
        msg->setHeader(header);
        msg->addContent(part1);
        msg->addContent(part2);

        msg->getHeaderProperties()->setMessageId(messageId);
        msg->getHeaderProperties()->setDeliveryMode(PERSISTENT);
        msg->getHeaderProperties()->getHeaders().setString("abc", "xyz");

        Buffer buffer(msg->encodedSize());
        msg->encode(buffer);
        buffer.flip();
        
        msg.reset(new BasicMessage());
        msg->decode(buffer);
        CPPUNIT_ASSERT_EQUAL(exchange, msg->getExchange());
        CPPUNIT_ASSERT_EQUAL(routingKey, msg->getRoutingKey());
        CPPUNIT_ASSERT_EQUAL(messageId, msg->getHeaderProperties()->getMessageId());
        CPPUNIT_ASSERT_EQUAL(PERSISTENT, msg->getHeaderProperties()->getDeliveryMode());
        CPPUNIT_ASSERT_EQUAL(string("xyz"), msg->getHeaderProperties()->getHeaders().getString("abc"));
        CPPUNIT_ASSERT_EQUAL((uint64_t) 14, msg->contentSize());

        MockChannel channel(1);
        msg->deliver(channel, "ignore", 0, 100); 
        CPPUNIT_ASSERT_EQUAL((size_t) 3, channel.out.frames.size());
        AMQContentBody::shared_ptr contentBody(dynamic_pointer_cast<AMQContentBody, AMQBody>(channel.out.frames[2].getBody()));
        CPPUNIT_ASSERT(contentBody);
        CPPUNIT_ASSERT_EQUAL(data1 + data2, contentBody->getData());
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(MessageTest);

