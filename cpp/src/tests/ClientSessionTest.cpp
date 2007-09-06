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
#include <vector>
#include "qpid_test_plugin.h"
#include "InProcessBroker.h"
#include "qpid/client/Session.h"

using namespace qpid::client;
using namespace qpid::framing;

class ClientSessionTest : public CppUnit::TestCase
{
    CPPUNIT_TEST_SUITE(ClientSessionTest);
    CPPUNIT_TEST(testQueueQuery);;
    CPPUNIT_TEST_SUITE_END();

    boost::shared_ptr<Connector> broker;
    Connection connection;
    Session session;

  public:

    ClientSessionTest() : broker(new qpid::broker::InProcessBroker()), connection(broker) 
    {
        connection.open("");
        session = connection.newSession();
    }

    void testQueueQuery() 
    {
        std::string name("my-queue");
        std::string alternate("amq.fanout");
        session.queueDeclare(0, name, alternate, false, false, true, true, FieldTable());
        TypedResult<QueueQueryResult> result = session.queueQuery(name);
        CPPUNIT_ASSERT_EQUAL(false, result.get().getDurable());
        CPPUNIT_ASSERT_EQUAL(true, result.get().getExclusive());
        CPPUNIT_ASSERT_EQUAL(alternate, result.get().getAlternateExchange());
    }

    void testCompletion()
    {
        std::string queue("my-queue");
        std::string dest("my-dest");
        session.queueDeclare(0, queue, "", false, false, true, true, FieldTable());
        //subcribe to the queue with confirm_mode = 1
        session.messageSubscribe(0, queue, dest, false, 1, 0, false, FieldTable());
        //publish some messages
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(ClientSessionTest);
