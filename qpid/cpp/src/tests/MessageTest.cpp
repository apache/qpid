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
#include "qpid/broker/Protocol.h"
#include "qpid/framing/AMQP_HighestVersion.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/framing/Uuid.h"
#include "MessageUtils.h"

#include "unit_test.h"

#include <iostream>

using namespace qpid::broker;
using namespace qpid::framing;

using std::string;

namespace qpid {
namespace tests {

QPID_AUTO_TEST_SUITE(MessageTestSuite)

QPID_AUTO_TEST_CASE(testEncodeDecode)
{
    string exchange = "MyExchange";
    string routingKey = "MyRoutingKey";
    uint64_t ttl(60);
    Uuid messageId(true);
    string data("abcdefghijklmn");

    qpid::types::Variant::Map properties;
    properties["routing-key"] = routingKey;
    properties["ttl"] = ttl;
    properties["durable"] = true;
    properties["message-id"] = qpid::types::Uuid(messageId.data());
    properties["abc"] = "xyz";
    Message msg = MessageUtils::createMessage(properties, data);

    std::vector<char> bytes(msg.getPersistentContext()->encodedSize());
    qpid::framing::Buffer buffer(&bytes[0], bytes.size());
    msg.getPersistentContext()->encode(buffer);
    buffer.reset();
    ProtocolRegistry registry(std::set<std::string>(), 0);
    msg = registry.decode(buffer);

    BOOST_CHECK_EQUAL(routingKey, msg.getRoutingKey());
    BOOST_CHECK_EQUAL((uint64_t) data.size(), msg.getContent().size());
    BOOST_CHECK_EQUAL(data, msg.getContent());
    //BOOST_CHECK_EQUAL(messageId, msg->getProperties<MessageProperties>()->getMessageId());
    BOOST_CHECK_EQUAL(string("xyz"), msg.getPropertyAsString("abc"));
    BOOST_CHECK(msg.isPersistent());
}

QPID_AUTO_TEST_CASE(testMessageProperties)
{
  string data("abcdefghijklmn");

  qpid::types::Variant::Map properties;
  properties["abc"] = "xyz";
  Message msg = MessageUtils::createMessage(properties, data);

  // Regression test that looking up a property doesn't return a prefix
  BOOST_CHECK_EQUAL(msg.getProperty("abcdef").getType(), qpid::types::VAR_VOID);
}

QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests
