#ifndef TESTS_MESSAGINGFIXTURE_H
#define TESTS_MESSAGINGFIXTURE_H

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
#include "BrokerFixture.h"
#include "unit_test.h"
#include "test_tools.h"
#include "qpid/client/Connection.h"
#include "qpid/client/Session.h"
#include "qpid/framing/Uuid.h"
#include "qpid/messaging/Connection.h"
#include "qpid/messaging/Session.h"
#include "qpid/messaging/Sender.h"
#include "qpid/messaging/Receiver.h"
#include "qpid/messaging/Message.h"

namespace qpid {
namespace tests {

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

    bool checkQueueExists(const std::string& name)
    {
        return session.queueQuery(name).getQueue() == name;
    }

    bool checkExchangeExists(const std::string& name, std::string& type)
    {
        qpid::framing::ExchangeQueryResult result = session.exchangeQuery(name);
        type = result.getType();
        return !result.getNotFound();
    }

    void send(qpid::client::Message& message, const std::string& exchange=std::string())
    {
        session.messageTransfer(qpid::client::arg::destination=exchange, qpid::client::arg::content=message);
    }

    ~BrokerAdmin()
    {
        session.close();
        connection.close();
    }
};

struct MessagingFixture : public BrokerFixture
{
    messaging::Connection connection;
    messaging::Session session;
    BrokerAdmin admin;

    MessagingFixture(Broker::Options opts = Broker::Options(), bool mgmtEnabled=false) :
        BrokerFixture(opts, mgmtEnabled),
        connection(open(broker->getPort(Broker::TCP_TRANSPORT))),
        session(connection.createSession()),
        admin(broker->getPort(Broker::TCP_TRANSPORT))
    {
    }

    static messaging::Connection open(uint16_t port)
    {
        messaging::Connection connection(
            (boost::format("amqp:tcp:localhost:%1%") % (port)).str());
        connection.open();
        return connection;
    }

    /** Open a connection to the broker. */
    qpid::messaging::Connection newConnection()
    {
        qpid::messaging::Connection connection(
            (boost::format("amqp:tcp:localhost:%1%") % (broker->getPort(qpid::broker::Broker::TCP_TRANSPORT))).str());
        return connection;
    }

    void ping(const qpid::messaging::Address& address)
    {
        messaging::Receiver r = session.createReceiver(address);
        messaging::Sender s = session.createSender(address);
        messaging::Message out(framing::Uuid(true).str());
        s.send(out);
        messaging::Message in;
        BOOST_CHECK(r.fetch(in, 5*messaging::Duration::SECOND));
        BOOST_CHECK_EQUAL(out.getContent(), in.getContent());
        r.close();
        s.close();
    }

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
        connection.close();
        for (const_iterator i = queues.begin(); i != queues.end(); ++i) {
            admin.deleteQueue(*i);
        }
    }

};

inline std::vector<std::string> fetch(messaging::Receiver& receiver, int count, messaging::Duration timeout=messaging::Duration::SECOND*5)
{
    std::vector<std::string> data;
    messaging::Message message;
    for (int i = 0; i < count && receiver.fetch(message, timeout); i++) {
        data.push_back(message.getContent());
    }
    return data;
}


inline void send(messaging::Sender& sender, uint count = 1, uint start = 1,
          const std::string& base = "Message")
{
    for (uint i = start; i < start + count; ++i) {
        sender.send(messaging::Message((boost::format("%1%_%2%") % base % i).str()));
    }
}

inline void receive(messaging::Receiver& receiver, uint count = 1, uint start = 1,
             const std::string& base = "Message",
             messaging::Duration timeout=messaging::Duration::SECOND*5)
{
    for (uint i = start; i < start + count; ++i) {
        BOOST_CHECK_EQUAL(receiver.fetch(timeout).getContent(), (boost::format("%1%_%2%") % base % i).str());
    }
}

}} // namespace qpid::tests

#endif  /*!TESTS_MESSAGINGFIXTURE_H*/
