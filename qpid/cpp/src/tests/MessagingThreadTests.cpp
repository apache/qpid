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
#include "MessagingFixture.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Thread.h"
#include <boost/lexical_cast.hpp>

namespace qpid {
namespace tests {
QPID_AUTO_TEST_SUITE(MessagingThreadTests)

using namespace messaging;
using namespace boost::assign;
using namespace std;

struct ReceiveThread : public sys::Runnable {
    Receiver receiver;
    vector<string> received;
    string error;

    ReceiveThread(Receiver s) : receiver(s) {}
    void run() {
        try {
            while(true) {
                Message m = receiver.fetch(Duration::SECOND*5);
                if (m.getContent() == "END") break;
                received.push_back(m.getContent());
            }
        } catch (const NoMessageAvailable& e) {
            // Indicates that fetch timed out OR receiver was closed by other thread.
            if (!receiver.isClosed()) // timeout
                error = e.what();
        } catch (const std::exception& e) {
            error = e.what();
        }
    }
};

struct NextReceiverThread : public sys::Runnable {
    Session session;
    vector<string> received;
    string error;

    NextReceiverThread(Session s) : session(s) {}
    void run() {
        try {
            while(true) {
                Message m = session.nextReceiver(Duration::SECOND*5).fetch();
                if (m.getContent() == "END") break;
                received.push_back(m.getContent());
            }
        } catch (const std::exception& e) {
            error = e.what();
        }
    }
};


QPID_AUTO_TEST_CASE(testConcurrentSendReceive) {
    MessagingFixture fix;
    Sender s = fix.session.createSender("concurrent;{create:always}");
    Receiver r = fix.session.createReceiver("concurrent;{create:always,link:{reliability:unreliable}}");
    ReceiveThread rt(r);
    sys::Thread thread(rt);
    const size_t COUNT=100;
    for (size_t i = 0; i < COUNT; ++i) {
        s.send(Message());
    }
    s.send(Message("END"));
    thread.join();
    BOOST_CHECK_EQUAL(rt.error, string());
    BOOST_CHECK_EQUAL(COUNT, rt.received.size());
}

QPID_AUTO_TEST_CASE(testCloseBusyReceiver) {
    MessagingFixture fix;
    Receiver r = fix.session.createReceiver("closeReceiver;{create:always}");
    ReceiveThread rt(r);
    sys::Thread thread(rt);
    sys::usleep(1000);          // Give the receive thread time to block.
    r.close();
    thread.join();
    BOOST_CHECK_EQUAL(rt.error, string());

    // Fetching on closed receiver should fail.
    Message m;
    BOOST_CHECK(!r.fetch(m, Duration(0)));
    BOOST_CHECK_THROW(r.fetch(Duration(0)), NoMessageAvailable);
}

QPID_AUTO_TEST_CASE(testCloseSessionBusyReceiver) {
    MessagingFixture fix;
    Receiver r = fix.session.createReceiver("closeSession;{create:always}");
    ReceiveThread rt(r);
    sys::Thread thread(rt);
    sys::usleep(1000);          // Give the receive thread time to block.
    fix.session.close();
    thread.join();
    BOOST_CHECK_EQUAL(rt.error, string());

    // Fetching on closed receiver should fail.
    Message m;
    BOOST_CHECK(!r.fetch(m, Duration(0)));
    BOOST_CHECK_THROW(r.fetch(Duration(0)), NoMessageAvailable);
}

QPID_AUTO_TEST_CASE(testConcurrentSendNextReceiver) {
    MessagingFixture fix;
    Receiver r = fix.session.createReceiver("concurrent;{create:always,link:{reliability:unreliable}}");
    const size_t COUNT=100;
    r.setCapacity(COUNT);
    NextReceiverThread rt(fix.session);
    sys::Thread thread(rt);
    sys::usleep(1000);          // Give the receive thread time to block.
    Sender s = fix.session.createSender("concurrent;{create:always}");
    for (size_t i = 0; i < COUNT; ++i) {
        s.send(Message());
    }
    s.send(Message("END"));
    thread.join();
    BOOST_CHECK_EQUAL(rt.error, string());
    BOOST_CHECK_EQUAL(COUNT, rt.received.size());
}

QPID_AUTO_TEST_SUITE_END()
}} // namespace qpid::tests
