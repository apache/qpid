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
#include "qpid/broker/Reference.h"
#include "qpid/broker/BrokerMessageMessage.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/framing/MessageAppendBody.h"
#include "qpid/broker/CompletionHandler.h"

using namespace boost;
using namespace qpid;
using namespace qpid::broker;
using namespace qpid::framing;
using namespace std;

class ReferenceTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(ReferenceTest);
    CPPUNIT_TEST(testRegistry);
    CPPUNIT_TEST(testReference);
    CPPUNIT_TEST_SUITE_END();

    ProtocolVersion v;
    ReferenceRegistry registry;

  public:
    void testRegistry() {
        Reference::shared_ptr ref = registry.open("foo");
        CPPUNIT_ASSERT_EQUAL(string("foo"), ref->getId());
        CPPUNIT_ASSERT(ref == registry.get("foo"));
        try {
            registry.get("none");
            CPPUNIT_FAIL("Expected exception");
        } catch (...) {}
        try {
            registry.open("foo");
            CPPUNIT_FAIL("Expected exception");
        } catch(...) {}
        ref->close();
        try {
            registry.get("foo");
            CPPUNIT_FAIL("Expected exception");
        } catch(...) {}
    }

    void testReference() {

        Reference::shared_ptr r1(registry.open("bar"));

        MessageTransferBody::shared_ptr t1(new MessageTransferBody(v));
        // TODO aconway 2007-04-03: hack around lack of generated setters. Clean this up.
        const_cast<framing::Content&>(t1->getBody()) = framing::Content(REFERENCE,"bar");
        MessageMessage::shared_ptr m1(new MessageMessage(0, t1, r1));

        MessageTransferBody::shared_ptr  t2(new MessageTransferBody(v));
        const_cast<framing::Content&>(t2->getBody()) = framing::Content(REFERENCE,"bar");
        MessageMessage::shared_ptr m2(new MessageMessage(0, t2, r1));
        
        MessageAppendBody::shared_ptr a1(new MessageAppendBody(v));
        MessageAppendBody::shared_ptr a2(new MessageAppendBody(v));

        r1->addMessage(m1);
        r1->addMessage(m2);
        CPPUNIT_ASSERT_EQUAL(size_t(2), r1->getMessages().size());
        r1->append(a1);
        r1->append(a2);
        CPPUNIT_ASSERT_EQUAL(size_t(2), r1->getAppends().size());
        r1->close();

        CPPUNIT_ASSERT_EQUAL(m1->getReference()->getAppends()[0], a1);
        CPPUNIT_ASSERT_EQUAL(m1->getReference()->getAppends()[1], a2);

        CPPUNIT_ASSERT_EQUAL(m2->getReference()->getAppends()[0], a1);
        CPPUNIT_ASSERT_EQUAL(m2->getReference()->getAppends()[1], a2);
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(ReferenceTest);
