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
#include "test_tools.h"
#include "BrokerFixture.h"
#include "qpid/messaging/Connection.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/MessageListener.h"
#include "qpid/messaging/Receiver.h"
#include "qpid/messaging/Sender.h"
#include "qpid/messaging/Session.h"
#include "qpid/client/Connection.h"
#include "qpid/client/Session.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/sys/Time.h"
#include <boost/assign.hpp>
#include <boost/format.hpp>
#include <string>
#include <vector>

QPID_AUTO_TEST_SUITE(MessagingSessionTests)

using namespace qpid::messaging;
using namespace qpid;
using qpid::broker::Broker;

struct BrokerAdmin
{
    qpid::client::Connection connection;
    qpid::client::Session session;

    BrokerAdmin(uint16_t port)
    {
        connection.open("localhost", port);
        session = connection.newSession();
    }

    void createQueue(const std::string& name)
    {
        session.queueDeclare(qpid::client::arg::queue=name);
    }

    void deleteQueue(const std::string& name)
    {
        session.queueDelete(qpid::client::arg::queue=name);
    }

    void createExchange(const std::string& name, const std::string& type)
    {
        session.exchangeDeclare(qpid::client::arg::exchange=name, qpid::client::arg::type=type);
    }

    void deleteExchange(const std::string& name)
    {
        session.exchangeDelete(qpid::client::arg::exchange=name);
    }

    ~BrokerAdmin()
    {
        session.close();
        connection.close();
    }
};

struct MessagingFixture : public BrokerFixture
{
    Connection connection;
    Session session;
    BrokerAdmin admin;

    MessagingFixture(Broker::Options opts = Broker::Options()) : 
        BrokerFixture(opts),
        connection(Connection::open((boost::format("amqp:tcp:localhost:%1%") % (broker->getPort(Broker::TCP_TRANSPORT))).str())),
        session(connection.newSession()),
        admin(broker->getPort(Broker::TCP_TRANSPORT)) {}

    ~MessagingFixture()
    {
        session.close();
        connection.close();
    }
};

struct QueueFixture : MessagingFixture
{
    std::string queue;

    QueueFixture(const std::string& name = "test-queue") : queue(name)
    {
        admin.createQueue(queue);
    }

    ~QueueFixture()
    {
        admin.deleteQueue(queue);
    }

};

struct TopicFixture : MessagingFixture
{
    std::string topic;

    TopicFixture(const std::string& name = "test-topic", const std::string& type="fanout") : topic(name)
    {
        admin.createExchange(topic, type);
    }

    ~TopicFixture()
    {
        admin.deleteExchange(topic);
    }

};

struct MultiQueueFixture : MessagingFixture
{
    typedef std::vector<std::string>::const_iterator const_iterator; 
    std::vector<std::string> queues;

    MultiQueueFixture(const std::vector<std::string>& names = boost::assign::list_of<std::string>("q1")("q2")("q3")) : queues(names)
    {
        for (const_iterator i = queues.begin(); i != queues.end(); ++i) {
            admin.createQueue(*i);
        }
    }

    ~MultiQueueFixture()
    {
        for (const_iterator i = queues.begin(); i != queues.end(); ++i) {
            admin.deleteQueue(*i);
        }
    }

};

struct MessageDataCollector : MessageListener
{
    std::vector<std::string> messageData;

    void received(Message& message) {
        messageData.push_back(message.getBytes());
    }
};

std::vector<std::string> fetch(Receiver& receiver, int count, qpid::sys::Duration timeout=qpid::sys::TIME_SEC*5) 
{
    std::vector<std::string> data;
    Message message;
    for (int i = 0; i < count && receiver.fetch(message, timeout); i++) {
        data.push_back(message.getBytes());
    }
    return data;
}

QPID_AUTO_TEST_CASE(testSimpleSendReceive)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    Message out("test-message");
    sender.send(out);
    Receiver receiver = fix.session.createReceiver(fix.queue);
    Message in = receiver.fetch(5 * qpid::sys::TIME_SEC);
    fix.session.acknowledge();
    BOOST_CHECK_EQUAL(in.getBytes(), out.getBytes());
}

QPID_AUTO_TEST_CASE(testSenderError)
{
    MessagingFixture fix;
    //TODO: this is the wrong type for the exception; define explicit set in messaging namespace
    BOOST_CHECK_THROW(fix.session.createSender("NonExistentAddress"), qpid::framing::NotFoundException);
}

QPID_AUTO_TEST_CASE(testReceiverError)
{
    MessagingFixture fix;
    //TODO: this is the wrong type for the exception; define explicit set in messaging namespace
    BOOST_CHECK_THROW(fix.session.createReceiver("NonExistentAddress"), qpid::framing::NotFoundException);
}

QPID_AUTO_TEST_CASE(testSimpleTopic)
{
    TopicFixture fix;

    Sender sender = fix.session.createSender(fix.topic);
    Message msg("one");
    sender.send(msg);
    Receiver sub1 = fix.session.createReceiver(fix.topic);
    sub1.setCapacity(10u);
    sub1.start();
    msg.setBytes("two");
    sender.send(msg);
    Receiver sub2 = fix.session.createReceiver(fix.topic);
    sub2.setCapacity(10u);
    sub2.start();
    msg.setBytes("three");
    sender.send(msg);
    Receiver sub3 = fix.session.createReceiver(fix.topic);
    sub3.setCapacity(10u);
    sub3.start();
    msg.setBytes("four");
    sender.send(msg);
    BOOST_CHECK_EQUAL(fetch(sub2, 2), boost::assign::list_of<std::string>("three")("four"));
    sub2.cancel();

    msg.setBytes("five");
    sender.send(msg);
    BOOST_CHECK_EQUAL(fetch(sub1, 4), boost::assign::list_of<std::string>("two")("three")("four")("five"));
    BOOST_CHECK_EQUAL(fetch(sub3, 2), boost::assign::list_of<std::string>("four")("five"));
    Message in;
    BOOST_CHECK(!sub2.fetch(in, 0));//TODO: or should this raise an error?

    
    //TODO: check pending messages...
}

QPID_AUTO_TEST_CASE(testSessionFetch)
{
    MultiQueueFixture fix;
    
    for (uint i = 0; i < fix.queues.size(); i++) {
        Receiver r = fix.session.createReceiver(fix.queues[i]);
        r.setCapacity(10u);
        r.start();//TODO: add Session::start
    }

    for (uint i = 0; i < fix.queues.size(); i++) {
        Sender s = fix.session.createSender(fix.queues[i]);
        Message msg((boost::format("Message_%1%") % (i+1)).str());
        s.send(msg);
    }    
    
    for (uint i = 0; i < fix.queues.size(); i++) {
        Message msg;
        BOOST_CHECK(fix.session.fetch(msg, qpid::sys::TIME_SEC));
        BOOST_CHECK_EQUAL(msg.getBytes(), (boost::format("Message_%1%") % (i+1)).str());
    }
}

QPID_AUTO_TEST_CASE(testSessionDispatch)
{
    MultiQueueFixture fix;
    
    MessageDataCollector collector;
    for (uint i = 0; i < fix.queues.size(); i++) {
        Receiver r = fix.session.createReceiver(fix.queues[i]);
        r.setListener(&collector);
        r.setCapacity(10u);
        r.start();//TODO: add Session::start
    }

    for (uint i = 0; i < fix.queues.size(); i++) {
        Sender s = fix.session.createSender(fix.queues[i]);
        Message msg((boost::format("Message_%1%") % (i+1)).str());
        s.send(msg);
    }    

    while (fix.session.dispatch(qpid::sys::TIME_SEC)) ;
    
    BOOST_CHECK_EQUAL(collector.messageData, boost::assign::list_of<std::string>("Message_1")("Message_2")("Message_3"));
}


QPID_AUTO_TEST_CASE(testMapMessage)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    Message out;
    out.getContent().asMap()["abc"] = "def";
    out.getContent().asMap()["pi"] = 3.14f;
    sender.send(out);
    Receiver receiver = fix.session.createReceiver(fix.queue);
    Message in = receiver.fetch(5 * qpid::sys::TIME_SEC);    
    BOOST_CHECK_EQUAL(in.getBytes(), out.getBytes());
    BOOST_CHECK_EQUAL(in.getContent().asMap()["abc"].asString(), "def");
    BOOST_CHECK_EQUAL(in.getContent().asMap()["pi"].asFloat(), 3.14f);
    fix.session.acknowledge();
}

QPID_AUTO_TEST_CASE(testListMessage)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    Message out;
    out.getContent() = Variant::List();
    out.getContent() << "abc";
    out.getContent() << 1234;
    out.getContent() << "def";
    out.getContent() << 56.789;    
    sender.send(out);
    Receiver receiver = fix.session.createReceiver(fix.queue);
    Message in = receiver.fetch(5 * qpid::sys::TIME_SEC);    
    BOOST_CHECK_EQUAL(in.getBytes(), out.getBytes());
    Variant::List& list = in.getContent().asList();    
    BOOST_CHECK_EQUAL(list.size(), out.getContent().asList().size());
    BOOST_CHECK_EQUAL(list.front().asString(), "abc");
    list.pop_front();
    BOOST_CHECK_EQUAL(list.front().asInt64(), 1234);
    list.pop_front();
    BOOST_CHECK_EQUAL(list.front().asString(), "def");
    list.pop_front();
    BOOST_CHECK_EQUAL(list.front().asDouble(), 56.789);
    fix.session.acknowledge();
}

QPID_AUTO_TEST_SUITE_END()
