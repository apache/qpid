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
#include <BrokerMessage.h>
#include <qpid_test_plugin.h>
#include <iostream>
#include <AMQP_HighestVersion.h>

using namespace boost;
using namespace qpid::broker;
using namespace qpid::framing;

struct DummyHandler : OutputHandler{
    std::vector<AMQFrame*> frames; 

    virtual void send(AMQDataBlock* block){
        frames.push_back(dynamic_cast<AMQFrame*>(block));
    }
};

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

        Message::shared_ptr msg = Message::shared_ptr(new Message(0, exchange, routingKey, false, false));
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
        
        msg = Message::shared_ptr(new Message(buffer));
        CPPUNIT_ASSERT_EQUAL(exchange, msg->getExchange());
        CPPUNIT_ASSERT_EQUAL(routingKey, msg->getRoutingKey());
        CPPUNIT_ASSERT_EQUAL(messageId, msg->getHeaderProperties()->getMessageId());
        CPPUNIT_ASSERT_EQUAL((u_int8_t) PERSISTENT, msg->getHeaderProperties()->getDeliveryMode());
        CPPUNIT_ASSERT_EQUAL(string("xyz"), msg->getHeaderProperties()->getHeaders().getString("abc"));
        CPPUNIT_ASSERT_EQUAL((u_int64_t) 14, msg->contentSize());

        DummyHandler handler;
        msg->deliver(&handler, 0, "ignore", 0, 100, &(qpid::framing::highestProtocolVersion)); 
        CPPUNIT_ASSERT_EQUAL((size_t) 3, handler.frames.size());
        AMQContentBody::shared_ptr contentBody(dynamic_pointer_cast<AMQContentBody, AMQBody>(handler.frames[2]->getBody()));
        CPPUNIT_ASSERT(contentBody);
        CPPUNIT_ASSERT_EQUAL(data1 + data2, contentBody->getData());
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(MessageTest);

