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
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Runnable.h"
#include "qpid/client/Session.h"
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
using qpid::sys::Monitor;
using std::string;
using std::cout;
using std::endl;
using namespace boost;


struct DummyListener : public sys::Runnable, public MessageListener {
    std::vector<Message> messages;
    string name;
    uint expected;
    Dispatcher dispatcher;

    DummyListener(Session& session, const string& n, uint ex) :
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

struct SimpleListener : public MessageListener
{
    Monitor lock;
    std::vector<Message> messages;

    void received(Message& msg)
    {
        Monitor::ScopedLock l(lock);
        messages.push_back(msg);
        lock.notifyAll();
    }

    void waitFor(const uint n)
    {
        Monitor::ScopedLock l(lock);
        while (messages.size() < n) {
            lock.wait();
        }
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
    session =connection.newSession(ASYNC);
    session.queueDeclare(queue="my-queue", alternateExchange="amq.fanout", exclusive=true, autoDelete=true);
    TypedResult<QueueQueryResult> result = session.queueQuery(string("my-queue"));
    BOOST_CHECK_EQUAL(false, result.get().getDurable());
    BOOST_CHECK_EQUAL(true, result.get().getExclusive());
    BOOST_CHECK_EQUAL(string("amq.fanout"),
                      result.get().getAlternateExchange());
}

BOOST_FIXTURE_TEST_CASE(testTransfer, ClientSessionFixture)
{
    session=connection.newSession(ASYNC);
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
    session =connection.newSession(ASYNC);
    declareSubscribe();
    size_t count = 100;
    for (size_t i = 0; i < count; ++i) 
        session.messageTransfer(content=TransferContent(lexical_cast<string>(i), "my-queue"));
    DummyListener listener(session, "my-dest", count);
    listener.run();
    BOOST_REQUIRE_EQUAL(count, listener.messages.size());        
    for (size_t i = 0; i < count; ++i) 
        BOOST_CHECK_EQUAL(lexical_cast<string>(i), listener.messages[i].getData());
}

/* FIXME aconway 2008-01-28: hangs
BOOST_FIXTURE_TEST_CASE(testDispatcherThread, ClientSessionFixture)
{
    session =connection.newSession(ASYNC);
    declareSubscribe();
    size_t count = 10000;
    DummyListener listener(session, "my-dest", count);
    sys::Thread t(listener);
    for (size_t i = 0; i < count; ++i) {
        session.messageTransfer(content=TransferContent(lexical_cast<string>(i), "my-queue"));
        if (i%100 == 0) cout << "T" << i << std::flush;
    }
    t.join();
    BOOST_REQUIRE_EQUAL(count, listener.messages.size());        
    for (size_t i = 0; i < count; ++i) 
        BOOST_CHECK_EQUAL(lexical_cast<string>(i), listener.messages[i].getData());
}
*/

BOOST_FIXTURE_TEST_CASE(_FIXTURE, ClientSessionFixture)
{
    session =connection.newSession(ASYNC, 0);
    session.suspend();  // session has 0 timeout.
    try {
        connection.resume(session);
        BOOST_FAIL("Expected InvalidArgumentException.");
    } catch(const InternalErrorException&) {}
}

BOOST_FIXTURE_TEST_CASE(testUseSuspendedError, ClientSessionFixture)
{
    session =connection.newSession(ASYNC, 60);
    session.suspend();
    try {
        session.exchangeQuery(name="amq.fanout");
        BOOST_FAIL("Expected session suspended exception");
    } catch(const CommandInvalidException&) {}
}

BOOST_FIXTURE_TEST_CASE(testSuspendResume, ClientSessionFixture)
{
    session =connection.newSession(ASYNC, 60);
    declareSubscribe();
    session.suspend();
    // Make sure we are still subscribed after resume.
    connection.resume(session);
    session.messageTransfer(content=TransferContent("my-message", "my-queue"));
    FrameSet::shared_ptr msg = session.get();
    BOOST_CHECK_EQUAL(string("my-message"), msg->getContent());
}

/**
 * Currently broken due to a deadlock in SessionCore
 *
BOOST_FIXTURE_TEST_CASE(testSendToSelf, SessionFixture) {
    // Deadlock if SubscriptionManager  run() concurrent with session ack.
    SimpleListener mylistener;
    session.queueDeclare(queue="myq", exclusive=true, autoDelete=true);
    subs.subscribe(mylistener, "myq", "myq");
    sys::Thread runner(subs);//start dispatcher thread
    string data("msg");
    Message msg(data, "myq");
    const uint count=10000;
    for (uint i = 0; i < count; ++i) {
        session.messageTransfer(content=msg);
    }
    mylistener.waitFor(count);
    subs.cancel("myq");
    subs.stop();
    session.close();
    BOOST_CHECK_EQUAL(mylistener.messages.size(), count);
    for (uint j = 0; j < count; ++j) {
        BOOST_CHECK_EQUAL(mylistener.messages[j].getData(), data);
    }
}
*/

QPID_AUTO_TEST_SUITE_END()

