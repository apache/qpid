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

#include "qpid/broker/DeliverableMessage.h"
#include "qpid/broker/DirectExchange.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/TopicExchange.h"
#include <qpid_test_plugin.h>
#include <iostream>

using namespace qpid::broker;
using namespace qpid::concurrent;

class ExchangeTest : public CppUnit::TestCase
{
    CPPUNIT_TEST_SUITE(ExchangeTest);
    CPPUNIT_TEST(testMe);
    CPPUNIT_TEST_SUITE_END();

  public:

    void testMe() 
    {
        Queue::shared_ptr queue(new Queue("queue", true, true));
        Queue::shared_ptr queue2(new Queue("queue2", true, true));

        TopicExchange topic("topic");
        topic.bind(queue, "abc", 0);
        topic.bind(queue2, "abc", 0);

        DirectExchange direct("direct");
        direct.bind(queue, "abc", 0);
        direct.bind(queue2, "abc", 0);

        queue.reset();
        queue2.reset();

        Message::shared_ptr msgPtr(new Message(0, "e", "A", true, true));
        DeliverableMessage msg(msgPtr);
        topic.route(msg, "abc", 0);
        direct.route(msg, "abc", 0);

    }
};
    
// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(ExchangeTest);
