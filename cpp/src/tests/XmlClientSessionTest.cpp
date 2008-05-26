/*
 *
 * Licensed to  the Apachef Software Foundation (ASF) under one
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
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Runnable.h"
#include "qpid/framing/TransferContent.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/client/Connection.h"
#include "qpid/client/Dispatcher.h"
#include "qpid/client/LocalQueue.h"
#include "qpid/client/Session.h"
#include "qpid/client/SubscriptionManager.h"

#include <boost/optional.hpp>
#include <boost/lexical_cast.hpp>

#include <vector>

QPID_AUTO_TEST_SUITE(XmlClientSessionTest)

using namespace qpid::client;

using namespace qpid::client::arg;
using namespace qpid::framing;
using namespace qpid;
using qpid::sys::Monitor;
using std::string;
using std::cout;
using std::endl;


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


class SubscribedLocalQueue : public LocalQueue {
  private:
    SubscriptionManager& subscriptions;
  public:
    SubscribedLocalQueue(SubscriptionManager& subs) : subscriptions(subs) {}
    Message get () { return pop(); }
    virtual ~SubscribedLocalQueue() {}
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
    void declareSubscribe(const string& q="odd_blue",
                          const string& dest="xml")
    {
        session.queueDeclare(queue=q);
        session.messageSubscribe(queue=q, destination=dest, acquireMode=1);
        session.messageFlow(destination=dest, unit=0, value=0xFFFFFFFF);//messages
        session.messageFlow(destination=dest, unit=1, value=0xFFFFFFFF);//bytes
    }
};

// ########### START HERE ####################################

QPID_AUTO_TEST_CASE(testXmlBinding) {
  ClientSessionFixture f;

  Session session = f.connection.newSession();
  SubscriptionManager subscriptions(session);
  SubscribedLocalQueue localQueue(subscriptions);

  session.exchangeDeclare(qpid::client::arg::exchange="xml", qpid::client::arg::type="xml");
  session.queueDeclare(qpid::client::arg::queue="odd_blue");
  subscriptions.subscribe(localQueue, "odd_blue");

  FieldTable binding;
  binding.setString("xquery", "declare variable $color external;"
                               "(./message/id mod 2 = 1) and ($color = 'blue')");
  session.exchangeBind(qpid::client::arg::exchange="xml", qpid::client::arg::queue="odd_blue", qpid::client::arg::bindingKey="query_name", qpid::client::arg::arguments=binding); 

  Message message;
  message.getDeliveryProperties().setRoutingKey("query_name"); 

  message.getHeaders().setString("color", "blue");
  string m = "<message><id>1</id></message>";
  message.setData(m);

  session.messageTransfer(qpid::client::arg::content=message,  qpid::client::arg::destination="xml");

  Message m2 = localQueue.get();
  BOOST_CHECK_EQUAL(m, m2.getData());  
}

//### Test: Bad XML does not kill the server

//### Test: Bad XQuery does not kill the server

//### Test: Bindings persist, surviving broker restart

QPID_AUTO_TEST_SUITE_END()

