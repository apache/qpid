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
#include "Exchange.h"
#include "ExchangeRegistry.h"
#include "Message.h"
#include "Router.h"
#include <cppunit/TestCase.h>
#include <cppunit/TextTestRunner.h>
#include <cppunit/extensions/HelperMacros.h>
#include <cppunit/plugin/TestPlugIn.h>
#include <iostream>
#include <memory>

using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::concurrent;

struct TestExchange : public Exchange{
    Message::shared_ptr msg;
    string routingKey; 
    FieldTable* args;

    TestExchange() : Exchange("test"), args(0) {}
    
    void bind(Queue::shared_ptr queue, const string& routingKey, FieldTable* args){
    }

    void unbind(Queue::shared_ptr queue, const string& routingKey, FieldTable* args){
    }

    void route(Message::shared_ptr& msg, const string& routingKey, FieldTable* args){
        this->msg = msg;
        this->routingKey = routingKey;
        this->args = args;
    }
};

class RouterTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(RouterTest);
    CPPUNIT_TEST(test);
    CPPUNIT_TEST_SUITE_END();

  public:

    void test() 
    {
        ExchangeRegistry registry;
        TestExchange* exchange = new TestExchange();
        registry.declare(exchange);

        string routingKey("my_routing_key");
        string name("name");
        string value("value");
        Message::shared_ptr msg(new Message(0, "test", routingKey, false, false));
        AMQHeaderBody::shared_ptr header(new AMQHeaderBody(BASIC));

        dynamic_cast<BasicHeaderProperties*>(header->getProperties())->getHeaders().setString(name, value);
        msg->setHeader(header);

        Router router(registry);
        router(msg);

        CPPUNIT_ASSERT(exchange->msg);
        CPPUNIT_ASSERT_EQUAL(msg, exchange->msg);
        CPPUNIT_ASSERT_EQUAL(routingKey, exchange->msg->getRoutingKey());
        CPPUNIT_ASSERT_EQUAL(routingKey, exchange->routingKey);
        CPPUNIT_ASSERT_EQUAL(value, exchange->args->getString(name));
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(RouterTest);

