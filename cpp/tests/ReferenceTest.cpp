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
        std::vector<MessageMessage::shared_ptr> messages;
        void complete(Message::shared_ptr m) {
            MessageMessage::shared_ptr mm =
                dynamic_pointer_cast<MessageMessage>(m);
            CPPUNIT_ASSERT(mm);
            messages.push_back(mm);
        }
    };

    MockCompletionHandler handler;
    ProtocolVersion v;
    ReferenceRegistry registry;
    MessageTransferBody::shared_ptr t1, t2;
    MessageMessage::shared_ptr m1, m2;
    MessageAppendBody::shared_ptr a1, a2;
  public:

    ReferenceTest() :
        registry(handler),
        t1(new MessageTransferBody(v)),
        t2(new MessageTransferBody(v)),
        m1(new MessageMessage(0, t1)),
        m2(new MessageMessage(0, t2)),
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

    void testReference() {
        Reference& ref = registry.open("foo");
        ref.addMessage(m1);
        ref.addMessage(m2);
        CPPUNIT_ASSERT_EQUAL(size_t(2), ref.getMessages().size());
        ref.append(a1);
        ref.append(a2);
        CPPUNIT_ASSERT_EQUAL(size_t(2), ref.getAppends().size());
        ref.close();
        try {
            registry.open("foo");
            CPPUNIT_FAIL("Expected exception");
        } catch(...) {}

        vector<MessageMessage::shared_ptr>& messages = handler.messages;
        CPPUNIT_ASSERT_EQUAL(size_t(2), messages.size());

        CPPUNIT_ASSERT_EQUAL(messages[0], m1);
        CPPUNIT_ASSERT_EQUAL(messages[0]->getAppends()[0], a1);
        CPPUNIT_ASSERT_EQUAL(messages[0]->getAppends()[1], a2);

        CPPUNIT_ASSERT_EQUAL(messages[1], m2);
        CPPUNIT_ASSERT_EQUAL(messages[1]->getAppends()[0], a1);
        CPPUNIT_ASSERT_EQUAL(messages[1]->getAppends()[1], a2);
    }
                             
    
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(ReferenceTest);
