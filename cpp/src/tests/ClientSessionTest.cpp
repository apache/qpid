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
#include "unit_test.h"
#include "BrokerFixture.h"
#include "qpid/client/Dispatcher.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Runnable.h"
#include "qpid/client/Session_0_10.h"
#include "qpid/framing/TransferContent.h"
#include "qpid/framing/reply_exceptions.h"

#include <boost/optional.hpp>
#include <boost/lexical_cast.hpp>

#include <vector>

QPID_AUTO_TEST_SUITE(ClientSessionTest)

using namespace qpid::client;
using namespace qpid::client::arg;
using namespace qpid::framing;
using namespace qpid;
using std::string;
using std::cout;
using std::endl;
using namespace boost;


struct DummyListener : public sys::Runnable, public MessageListener {
    std::vector<Message> messages;
    string name;
    uint expected;
    Dispatcher dispatcher;

    DummyListener(Session_0_10& session, const string& n, uint ex) :
        name(n), expected(ex), dispatcher(session) {}

    void run()
    {
        dispatcher.listen(name, this);
        dispatcher.run();
    }

    void received(Message& msg)
    {
        messages.push_back(msg);
        if (--expected == 0)
            dispatcher.stop();
    }
};

struct ClientSessionFixture : public ProxySessionFixture
{
    void declareSubscribe(const string& q="my-queue",
                          const string& dest="my-dest")
    {
        session.queueDeclare(queue=q);
        session.messageSubscribe(queue=q, destination=dest, acquireMode=1);
        session.messageFlow(destination=dest, unit=0, value=0xFFFFFFFF);//messages
        session.messageFlow(destination=dest, unit=1, value=0xFFFFFFFF);//bytes
    }
};

BOOST_FIXTURE_TEST_CASE(testQueueQuery, ClientSessionFixture) {
    session =connection.newSession();
    session.queueDeclare(queue="my-queue", alternateExchange="amq.fanout", exclusive=true, autoDelete=true);
    TypedResult<QueueQueryResult> result = session.queueQuery(string("my-queue"));
    BOOST_CHECK_EQUAL(false, result.get().getDurable());
    BOOST_CHECK_EQUAL(true, result.get().getExclusive());
    BOOST_CHECK_EQUAL(string("amq.fanout"),
                      result.get().getAlternateExchange());
}

BOOST_FIXTURE_TEST_CASE(testTransfer, ClientSessionFixture)
{
    session=connection.newSession();
    declareSubscribe();
    session.messageTransfer(content=TransferContent("my-message", "my-queue"));
    //get & test the message:
    FrameSet::shared_ptr msg = session.get();
    BOOST_CHECK(msg->isA<MessageTransferBody>());
    BOOST_CHECK_EQUAL(string("my-message"), msg->getContent());
    //confirm receipt:
    session.getExecution().completed(msg->getId(), true, true);
}

BOOST_FIXTURE_TEST_CASE(testDispatcher, ClientSessionFixture)
{
    session =connection.newSession();
    declareSubscribe();

    TransferContent msg1("One");
    msg1.getDeliveryProperties().setRoutingKey("my-queue");
    session.messageTransfer(content=msg1);

    TransferContent msg2("Two");
    msg2.getDeliveryProperties().setRoutingKey("my-queue");
    session.messageTransfer(content=msg2);

    TransferContent msg3("Three");
    msg3.getDeliveryProperties().setRoutingKey("my-queue");
    session.messageTransfer(content=msg3);
                
    DummyListener listener(session, "my-dest", 3);
    listener.run();
    BOOST_CHECK_EQUAL((size_t) 3, listener.messages.size());        
    BOOST_CHECK_EQUAL(std::string("One"), listener.messages[0].getData());
    BOOST_CHECK_EQUAL(std::string("Two"), listener.messages[1].getData());
    BOOST_CHECK_EQUAL(std::string("Three"), listener.messages[2].getData());
}

BOOST_FIXTURE_TEST_CASE(_FIXTURE, ClientSessionFixture)
{
    session =connection.newSession(0);
    session.suspend();  // session has 0 timeout.
    try {
        connection.resume(session);
        BOOST_FAIL("Expected InvalidArgumentException.");
    } catch(const InternalErrorException&) {}
}

BOOST_FIXTURE_TEST_CASE(testUseSuspendedError, ClientSessionFixture)
{
    session =connection.newSession(60);
    session.suspend();
    try {
        session.exchangeQuery(name="amq.fanout");
        BOOST_FAIL("Expected session suspended exception");
    } catch(const CommandInvalidException&) {}
}

BOOST_FIXTURE_TEST_CASE(testSuspendResume, ClientSessionFixture)
{
    session =connection.newSession(60);
    declareSubscribe();
    session.suspend();
    // Make sure we are still subscribed after resume.
    connection.resume(session);
    session.messageTransfer(content=TransferContent("my-message", "my-queue"));
    FrameSet::shared_ptr msg = session.get();
    BOOST_CHECK_EQUAL(string("my-message"), msg->getContent());
}

QPID_AUTO_TEST_SUITE_END()

