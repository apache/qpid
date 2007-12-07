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
#include "qpid_test_plugin.h"
#include "BrokerFixture.h"
#include "qpid/client/Dispatcher.h"
#include "qpid/client/Session_0_10.h"
#include "qpid/framing/TransferContent.h"
#include "qpid/framing/reply_exceptions.h"

#include <boost/optional.hpp>

#include <list>

using namespace qpid::client;
using namespace qpid::client::arg;
using namespace qpid::framing;
using namespace qpid;
using namespace boost;

struct DummyListener : public MessageListener
{
    std::list<Message> messages;
    std::string name;
    uint expected;
    uint count;
    Dispatcher dispatcher;

    DummyListener(Session_0_10& session, const std::string& _name, uint _expected) : name(_name), expected(_expected), count(0), 
                                                                                dispatcher(session) {}

    void listen()
    {
        dispatcher.listen(name, this);
        dispatcher.run();
    }

    void received(Message& msg)
    {
        messages.push_back(msg);
        if (++count == expected) {
            dispatcher.stop();
        }
    }
};

class ClientSessionTest : public CppUnit::TestCase, public BrokerFixture
{
    CPPUNIT_TEST_SUITE(ClientSessionTest);
    CPPUNIT_TEST(testQueueQuery);
    CPPUNIT_TEST(testTransfer);
    CPPUNIT_TEST(testDispatcher);
    CPPUNIT_TEST(testResumeExpiredError);
    CPPUNIT_TEST(testUseSuspendedError);
    CPPUNIT_TEST(testSuspendResume);
    CPPUNIT_TEST(testDisconnectResume);
    CPPUNIT_TEST_SUITE_END();

    shared_ptr<broker::Broker> broker;

  public:

    void setUp() {
        broker = broker::Broker::create();
    }

    void tearDown() {
    }

    void declareSubscribe(const std::string& q="my-queue",
                          const std::string& dest="my-dest")
    {
        session.queueDeclare(queue=q);
        session.messageSubscribe(queue=q, destination=dest, acquireMode=1);
        session.messageFlow(destination=dest, unit=0, value=0xFFFFFFFF);//messages
        session.messageFlow(destination=dest, unit=1, value=0xFFFFFFFF);//bytes
    }

    bool queueExists(const std::string& q) {
        TypedResult<QueueQueryResult> result = session.queueQuery(q);
        return result.get().getQueue() == q;
    }
    
    void testQueueQuery() 
    {
        session =connection.newSession();
        session.queueDeclare(queue="my-queue", alternateExchange="amq.fanout", exclusive=true, autoDelete=true);
        TypedResult<QueueQueryResult> result = session.queueQuery(std::string("my-queue"));
        CPPUNIT_ASSERT_EQUAL(false, result.get().getDurable());
        CPPUNIT_ASSERT_EQUAL(true, result.get().getExclusive());
        CPPUNIT_ASSERT_EQUAL(std::string("amq.fanout"),
                             result.get().getAlternateExchange());
    }

    void testTransfer()
    {
        session =connection.newSession();
        declareSubscribe();
        session.messageTransfer(content=TransferContent("my-message", "my-queue"));
        //get & test the message:
        FrameSet::shared_ptr msg = session.get();
        CPPUNIT_ASSERT(msg->isA<MessageTransferBody>());
        CPPUNIT_ASSERT_EQUAL(std::string("my-message"), msg->getContent());
        //confirm receipt:
        session.getExecution().completed(msg->getId(), true, true);
    }

    void testDispatcher()
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
        listener.listen();
        CPPUNIT_ASSERT_EQUAL((size_t) 3, listener.messages.size());        
        CPPUNIT_ASSERT_EQUAL(std::string("One"), listener.messages.front().getData());
        listener.messages.pop_front();
        CPPUNIT_ASSERT_EQUAL(std::string("Two"), listener.messages.front().getData());
        listener.messages.pop_front();
        CPPUNIT_ASSERT_EQUAL(std::string("Three"), listener.messages.front().getData());
        listener.messages.pop_front();

    }

    void testResumeExpiredError() {
        session =connection.newSession(0);
        session.suspend();  // session has 0 timeout.
        try {
           connection.resume(session);
            CPPUNIT_FAIL("Expected InvalidArgumentException.");
        } catch(const InternalErrorException&) {}
    }

    void testUseSuspendedError() {
        session =connection.newSession(60);
        session.suspend();
        try {
            session.exchangeQuery(name="amq.fanout");
            CPPUNIT_FAIL("Expected session suspended exception");
        } catch(const CommandInvalidException&) {}
    }

    void testSuspendResume() {
        session =connection.newSession(60);
        declareSubscribe();
        session.suspend();
        // Make sure we are still subscribed after resume.
       connection.resume(session);
        session.messageTransfer(content=TransferContent("my-message", "my-queue"));
        FrameSet::shared_ptr msg = session.get();
        CPPUNIT_ASSERT_EQUAL(string("my-message"), msg->getContent());
    }

    void testDisconnectResume() {
        session =connection.newSession(60);
        session.queueDeclare(queue="before");
        CPPUNIT_ASSERT(queueExists("before"));
        session.queueDeclare(queue=string("after"));
        disconnect(connection);
        Connection c2;
        open(c2);
        c2.resume(session);
        CPPUNIT_ASSERT(queueExists("after"));
        c2.close();
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(ClientSessionTest);
