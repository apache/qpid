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
#include <iostream>
#include <memory>
#include "qpid_test_plugin.h"
#include "Reference.h"
#include "BrokerMessageMessage.h"
#include "MessageTransferBody.h"
#include "MessageAppendBody.h"
#include "CompletionHandler.h"

using namespace boost;
using namespace qpid::broker;
using namespace qpid::framing;
using namespace std;

class ReferenceTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(ReferenceTest);
    CPPUNIT_TEST(testRegistry);
    CPPUNIT_TEST(testReference);
    CPPUNIT_TEST_SUITE_END();


    struct MockCompletionHandler : public CompletionHandler {
        std::vector<Message::shared_ptr> messages;
        void complete(Message::shared_ptr msg) { messages.push_back(msg); }
    };

    MockCompletionHandler handler;
    ProtocolVersion v;
    ReferenceRegistry registry;
    MessageTransferBody::shared_ptr t1, t2;
    MessageAppendBody::shared_ptr a1, a2;
  public:

    ReferenceTest() :
        registry(handler),
        t1(new MessageTransferBody(v)),
        t2(new MessageTransferBody(v)),
        a1(new MessageAppendBody(v)),
        a2(new MessageAppendBody(v))
    {}

    void testRegistry() {
        Reference& ref = registry.open("foo");
        CPPUNIT_ASSERT_EQUAL(string("foo"), ref.getId());
        CPPUNIT_ASSERT(&ref == &registry.get("foo"));
        try {
            registry.get("none");
            CPPUNIT_FAIL("Expected exception");
        } catch (...) {}
        try {
            registry.open("foo");
            CPPUNIT_FAIL("Expected exception");
        } catch(...) {}
    }

    MessageMessage& handlerMessage(size_t i) {
        CPPUNIT_ASSERT(handler.messages.size() > i);
        MessageMessage* msg = dynamic_cast<MessageMessage*>(
            handler.messages[i].get());
        CPPUNIT_ASSERT(msg);
        return *msg;
    }
    
    void testReference() {
        Reference& ref = registry.open("foo");
        ref.transfer(t1);
        ref.transfer(t2);
        CPPUNIT_ASSERT_EQUAL(size_t(2), ref.getTransfers().size());
        ref.append(a1);
        ref.append(a2);
        CPPUNIT_ASSERT_EQUAL(size_t(2), ref.getAppends().size());
        ref.close();
        try {
            registry.open("foo");
            CPPUNIT_FAIL("Expected exception");
        } catch(...) {}

        vector<Message::shared_ptr>& messages = handler.messages;
        CPPUNIT_ASSERT_EQUAL(size_t(2), messages.size());

        CPPUNIT_ASSERT_EQUAL(handlerMessage(0).getTransfer(), t1);
        CPPUNIT_ASSERT_EQUAL(handlerMessage(0).getAppends()[0], a1);
        CPPUNIT_ASSERT_EQUAL(handlerMessage(0).getAppends()[1], a2);

        CPPUNIT_ASSERT_EQUAL(handlerMessage(1).getTransfer(), t2);
        CPPUNIT_ASSERT_EQUAL(handlerMessage(1).getAppends()[0], a1);
        CPPUNIT_ASSERT_EQUAL(handlerMessage(1).getAppends()[1], a2);
    }
                             
    
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(ReferenceTest);
