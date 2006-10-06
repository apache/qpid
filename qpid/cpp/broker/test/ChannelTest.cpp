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
#include "Channel.h"
#include "Message.h"
#include <cppunit/TestCase.h>
#include <cppunit/TextTestRunner.h>
#include <cppunit/extensions/HelperMacros.h>
#include <cppunit/plugin/TestPlugIn.h>
#include <iostream>
#include <memory>

using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::concurrent;

struct MessageHolder{
    Message::shared_ptr last;
};

class DummyRouter{
    MessageHolder& holder;

public:
    DummyRouter(MessageHolder& _holder) : holder(_holder){
    }

    void operator()(Message::shared_ptr& msg){
        holder.last = msg;
    }
};


class ChannelTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(ChannelTest);
    CPPUNIT_TEST(testIncoming);
    CPPUNIT_TEST_SUITE_END();

  public:

    void testIncoming(){
        Channel channel(0, 0, 10000);
        string routingKey("my_routing_key");
        channel.handlePublish(new Message(0, "test", routingKey, false, false));
        AMQHeaderBody::shared_ptr header(new AMQHeaderBody(BASIC));
        header->setContentSize(14);
        string data1("abcdefg");
        string data2("hijklmn");
        AMQContentBody::shared_ptr part1(new AMQContentBody(data1));
        AMQContentBody::shared_ptr part2(new AMQContentBody(data2));        

        MessageHolder holder;
        channel.handleHeader(header, DummyRouter(holder));
        CPPUNIT_ASSERT(!holder.last);
        channel.handleContent(part1, DummyRouter(holder));
        CPPUNIT_ASSERT(!holder.last);
        channel.handleContent(part2, DummyRouter(holder));
        CPPUNIT_ASSERT(holder.last);
        CPPUNIT_ASSERT_EQUAL(routingKey, holder.last->getRoutingKey());
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(ChannelTest);

