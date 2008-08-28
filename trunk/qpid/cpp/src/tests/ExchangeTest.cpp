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

#include "qpid/Exception.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/DeliverableMessage.h"
#include "qpid/broker/DirectExchange.h"
#include "qpid/broker/ExchangeRegistry.h"
#include "qpid/broker/FanOutExchange.h"
#include "qpid/broker/HeadersExchange.h"
#include "qpid/broker/TopicExchange.h"
#include "qpid/framing/reply_exceptions.h"
#include "unit_test.h"
#include <iostream>
#include "MessageUtils.h"

using boost::intrusive_ptr;
using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;
using namespace qpid;

QPID_AUTO_TEST_SUITE(ExchangeTestSuite)

QPID_AUTO_TEST_CASE(testMe) 
{
    Queue::shared_ptr queue(new Queue("queue", true));
    Queue::shared_ptr queue2(new Queue("queue2", true));

    TopicExchange topic("topic");
    topic.bind(queue, "abc", 0);
    topic.bind(queue2, "abc", 0);

    DirectExchange direct("direct");
    direct.bind(queue, "abc", 0);
    direct.bind(queue2, "abc", 0);

    queue.reset();
    queue2.reset();

    intrusive_ptr<Message> msgPtr(MessageUtils::createMessage("exchange", "key", "id"));
    DeliverableMessage msg(msgPtr);
    topic.route(msg, "abc", 0);
    direct.route(msg, "abc", 0);

}

QPID_AUTO_TEST_CASE(testIsBound)
{
    Queue::shared_ptr a(new Queue("a", true));
    Queue::shared_ptr b(new Queue("b", true));
    Queue::shared_ptr c(new Queue("c", true));
    Queue::shared_ptr d(new Queue("d", true));
        
    string k1("abc");
    string k2("def");
    string k3("xyz");

    FanOutExchange fanout("fanout");
    fanout.bind(a, "", 0);
    fanout.bind(b, "", 0);
    fanout.bind(c, "", 0);

    BOOST_CHECK(fanout.isBound(a, 0, 0));
    BOOST_CHECK(fanout.isBound(b, 0, 0));
    BOOST_CHECK(fanout.isBound(c, 0, 0));
    BOOST_CHECK(!fanout.isBound(d, 0, 0));

    DirectExchange direct("direct");
    direct.bind(a, k1, 0);
    direct.bind(a, k3, 0);
    direct.bind(b, k2, 0);
    direct.bind(c, k1, 0);

    BOOST_CHECK(direct.isBound(a, 0, 0));
    BOOST_CHECK(direct.isBound(a, &k1, 0));
    BOOST_CHECK(direct.isBound(a, &k3, 0));
    BOOST_CHECK(!direct.isBound(a, &k2, 0));
    BOOST_CHECK(direct.isBound(b, 0, 0));
    BOOST_CHECK(direct.isBound(b, &k2, 0));
    BOOST_CHECK(direct.isBound(c, &k1, 0));
    BOOST_CHECK(!direct.isBound(d, 0, 0));
    BOOST_CHECK(!direct.isBound(d, &k1, 0));
    BOOST_CHECK(!direct.isBound(d, &k2, 0));
    BOOST_CHECK(!direct.isBound(d, &k3, 0));

    TopicExchange topic("topic");
    topic.bind(a, k1, 0);
    topic.bind(a, k3, 0);
    topic.bind(b, k2, 0);
    topic.bind(c, k1, 0);

    BOOST_CHECK(topic.isBound(a, 0, 0));
    BOOST_CHECK(topic.isBound(a, &k1, 0));
    BOOST_CHECK(topic.isBound(a, &k3, 0));
    BOOST_CHECK(!topic.isBound(a, &k2, 0));
    BOOST_CHECK(topic.isBound(b, 0, 0));
    BOOST_CHECK(topic.isBound(b, &k2, 0));
    BOOST_CHECK(topic.isBound(c, &k1, 0));
    BOOST_CHECK(!topic.isBound(d, 0, 0));
    BOOST_CHECK(!topic.isBound(d, &k1, 0));
    BOOST_CHECK(!topic.isBound(d, &k2, 0));
    BOOST_CHECK(!topic.isBound(d, &k3, 0));

    HeadersExchange headers("headers");
    FieldTable args1;
    args1.setString("x-match", "all");
    args1.setString("a", "A");
    args1.setInt("b", 1);
    FieldTable args2;
    args2.setString("x-match", "any");
    args2.setString("a", "A");
    args2.setInt("b", 1);
    FieldTable args3;
    args3.setString("x-match", "any");
    args3.setString("c", "C");
    args3.setInt("b", 6);

    headers.bind(a, "", &args1);
    headers.bind(a, "", &args3);
    headers.bind(b, "", &args2);
    headers.bind(c, "", &args1);
        
    BOOST_CHECK(headers.isBound(a, 0, 0));
    BOOST_CHECK(headers.isBound(a, 0, &args1));
    BOOST_CHECK(headers.isBound(a, 0, &args3));
    BOOST_CHECK(!headers.isBound(a, 0, &args2));
    BOOST_CHECK(headers.isBound(b, 0, 0));
    BOOST_CHECK(headers.isBound(b, 0, &args2));
    BOOST_CHECK(headers.isBound(c, 0, &args1));
    BOOST_CHECK(!headers.isBound(d, 0, 0));
    BOOST_CHECK(!headers.isBound(d, 0, &args1));
    BOOST_CHECK(!headers.isBound(d, 0, &args2));
    BOOST_CHECK(!headers.isBound(d, 0, &args3));
}

QPID_AUTO_TEST_CASE(testDeleteGetAndRedeclare) 
{
    ExchangeRegistry exchanges;
    exchanges.declare("my-exchange", "direct", false, FieldTable());
    exchanges.destroy("my-exchange");
    try {
        exchanges.get("my-exchange");
    } catch (const NotFoundException&) {}
    std::pair<Exchange::shared_ptr, bool> response = exchanges.declare("my-exchange", "direct", false, FieldTable());
    BOOST_CHECK_EQUAL(string("direct"), response.first->getType());  
}

QPID_AUTO_TEST_SUITE_END()
