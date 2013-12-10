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

/** Unit tests for various broker configuration options **/

#include "unit_test.h"
#include "test_tools.h"
#include "MessagingFixture.h"

#include "qpid/messaging/Address.h"
#include "qpid/messaging/Connection.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/Receiver.h"
#include "qpid/messaging/Sender.h"
#include "qpid/messaging/Session.h"

namespace qpid {
namespace tests {

QPID_AUTO_TEST_SUITE(BrokerOptionsTestSuite)

using namespace qpid::broker;
using namespace qpid::messaging;
using namespace qpid::types;
using namespace qpid;

QPID_AUTO_TEST_CASE(testDisabledTimestamp)
{
    // by default, there should be no timestamp added by the broker
    MessagingFixture fix;

    Sender sender = fix.session.createSender("test-q; {create:always, delete:sender}");
    messaging::Message msg("hi");
    sender.send(msg);

    Receiver receiver = fix.session.createReceiver("test-q");
    messaging::Message in;
    BOOST_CHECK(receiver.fetch(in, Duration::IMMEDIATE));
    Variant::Map props = in.getProperties();
    BOOST_CHECK(props.find("x-amqp-0-10.timestamp") == props.end());
}

QPID_AUTO_TEST_CASE(testEnabledTimestamp)
{
    // when enabled, the 0.10 timestamp is added by the broker
    Broker::Options opts;
    opts.timestampRcvMsgs = true;
    MessagingFixture fix(opts, true);

    Sender sender = fix.session.createSender("test-q; {create:always, delete:sender}");
    messaging::Message msg("one");
    sender.send(msg);

    Receiver receiver = fix.session.createReceiver("test-q");
    messaging::Message in;
    BOOST_CHECK(receiver.fetch(in, Duration::IMMEDIATE));
    Variant::Map props = in.getProperties();
    BOOST_CHECK(props.find("x-amqp-0-10.timestamp") != props.end());
    BOOST_CHECK(props["x-amqp-0-10.timestamp"]);
}

QPID_AUTO_TEST_SUITE_END()

}}
