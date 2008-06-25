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
#include "qpid/client/AckPolicy.h"
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
        if (--expected == 0) {
            dispatcher.stop();
        }
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

QPID_AUTO_TEST_CASE(testQueueQuery) {
    ClientSessionFixture fix;
    fix.session = fix.connection.newSession();
    fix.session.queueDeclare(queue="my-queue", alternateExchange="amq.fanout", exclusive=true, autoDelete=true);
    QueueQueryResult result = fix.session.queueQuery(string("my-queue"));
    BOOST_CHECK_EQUAL(false, result.getDurable());
    BOOST_CHECK_EQUAL(true, result.getExclusive());
    BOOST_CHECK_EQUAL(string("amq.fanout"),
                      result.getAlternateExchange());
}

QPID_AUTO_TEST_CASE(testTransfer)
{
    ClientSessionFixture fix;
    fix.session=fix.connection.newSession();
    fix.declareSubscribe();
    fix.session.messageTransfer(acceptMode=1, content=TransferContent("my-message", "my-queue"));
    //get & test the message:
    FrameSet::shared_ptr msg = fix.session.get();
    BOOST_CHECK(msg->isA<MessageTransferBody>());
    BOOST_CHECK_EQUAL(string("my-message"), msg->getContent());
    //confirm receipt:
    AckPolicy autoAck;
    autoAck.ack(Message(*msg), fix.session);
}

QPID_AUTO_TEST_CASE(testDispatcher)
{
    ClientSessionFixture fix;
    fix.session =fix.connection.newSession();
    fix.declareSubscribe();
    size_t count = 100;
    for (size_t i = 0; i < count; ++i) 
        fix.session.messageTransfer(content=TransferContent(lexical_cast<string>(i), "my-queue"));
    DummyListener listener(fix.session, "my-dest", count);
    listener.run();
    BOOST_CHECK_EQUAL(count, listener.messages.size());        
    for (size_t i = 0; i < count; ++i) 
        BOOST_CHECK_EQUAL(lexical_cast<string>(i), listener.messages[i].getData());
}

QPID_AUTO_TEST_CASE(testDispatcherThread)
{
    ClientSessionFixture fix;
    fix.session =fix.connection.newSession();
    fix.declareSubscribe();
    size_t count = 10;
    DummyListener listener(fix.session, "my-dest", count);
    sys::Thread t(listener);
    for (size_t i = 0; i < count; ++i) {
        fix.session.messageTransfer(content=TransferContent(lexical_cast<string>(i), "my-queue"));
    }
    t.join();
    BOOST_CHECK_EQUAL(count, listener.messages.size());        
    for (size_t i = 0; i < count; ++i) 
        BOOST_CHECK_EQUAL(lexical_cast<string>(i), listener.messages[i].getData());
}

// FIXME aconway 2008-05-26: Re-enable with final resume implementation.
// 
// QPID_AUTO_TEST_CASE_EXPECTED_FAILURES(testSuspend0Timeout, 1)
// {
//     ClientSessionFixture fix;
//     fix.session.suspend();  // session has 0 timeout.
//     try {
//         fix.connection.resume(fix.session);
//         BOOST_FAIL("Expected InvalidArgumentException.");
//     } catch(const InternalErrorException&) {}
// }

// QPID_AUTO_TEST_CASE_EXPECTED_FAILURES(testUseSuspendedError, 1)
// {
//     ClientSessionFixture fix;
//     fix.session =fix.session.timeout(60);
//     fix.session.suspend();
//     try {
//         fix.session.exchangeQuery(name="amq.fanout");
//         BOOST_FAIL("Expected session suspended exception");
//     } catch(const CommandInvalidException&) {}
// }

// QPID_AUTO_TEST_CASE_EXPECTED_FAILURES(testSuspendResume, 1)
// {
//     ClientSessionFixture fix;
//     fix.session.timeout(60);
//     fix.declareSubscribe();
//     fix.session.suspend();
//     // Make sure we are still subscribed after resume.
//     fix.connection.resume(fix.session);
//     fix.session.messageTransfer(content=TransferContent("my-message", "my-queue"));
//     FrameSet::shared_ptr msg = fix.session.get();
//     BOOST_CHECK_EQUAL(string("my-message"), msg->getContent());
// }


QPID_AUTO_TEST_CASE(testSendToSelf) {
    ClientSessionFixture fix;
    SimpleListener mylistener;
    fix.session.queueDeclare(queue="myq", exclusive=true, autoDelete=true);
    fix.subs.subscribe(mylistener, "myq");
    sys::Thread runner(fix.subs);//start dispatcher thread
    string data("msg");
    Message msg(data, "myq");
    const uint count=10;
    for (uint i = 0; i < count; ++i) {
        fix.session.messageTransfer(content=msg);
    }
    mylistener.waitFor(count);
    fix.subs.cancel("myq");
    fix.subs.stop();
    runner.join();
    fix.session.close();
    BOOST_CHECK_EQUAL(mylistener.messages.size(), count);
    for (uint j = 0; j < count; ++j) {
        BOOST_CHECK_EQUAL(mylistener.messages[j].getData(), data);
    }
}

QPID_AUTO_TEST_CASE(testLocalQueue) {
    ClientSessionFixture fix;
    fix.session.queueDeclare(queue="lq", exclusive=true, autoDelete=true);
    LocalQueue lq;
    fix.subs.subscribe(lq, "lq", FlowControl(2, FlowControl::UNLIMITED, false));
    fix.session.messageTransfer(content=Message("foo0", "lq"));
    fix.session.messageTransfer(content=Message("foo1", "lq"));
    fix.session.messageTransfer(content=Message("foo2", "lq"));
    BOOST_CHECK_EQUAL("foo0", lq.pop().getData());
    BOOST_CHECK_EQUAL("foo1", lq.pop().getData());
    BOOST_CHECK(lq.empty());    // Credit exhausted.
    fix.subs.setFlowControl("lq", FlowControl::unlimited());
    BOOST_CHECK_EQUAL("foo2", lq.pop().getData());    
}

QPID_AUTO_TEST_CASE(testGet) {
    ClientSessionFixture fix;
    fix.session.queueDeclare(queue="getq", exclusive=true, autoDelete=true);
    fix.session.messageTransfer(content=Message("foo0", "getq"));
    fix.session.messageTransfer(content=Message("foo1", "getq"));
    BOOST_CHECK_EQUAL("foo0", fix.subs.get("getq").getData());
    BOOST_CHECK_EQUAL("foo1", fix.subs.get("getq").getData());
}

QPID_AUTO_TEST_SUITE_END()


