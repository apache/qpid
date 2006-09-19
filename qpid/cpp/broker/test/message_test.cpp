/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include "APRBase.h"
#include "Message.h"
#include <cppunit/TestCase.h>
#include <cppunit/TextTestRunner.h>
#include <cppunit/extensions/HelperMacros.h>
#include <cppunit/plugin/TestPlugIn.h>
#include <iostream>

using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::concurrent;

class MessageTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(MessageTest);
    CPPUNIT_TEST(testMe);
    CPPUNIT_TEST_SUITE_END();

  public:

    // TODO aconway 2006-09-12: Need more detailed tests,
    // need tests to assert something!
    // 
    void testMe() 
    {
        APRBase::increment();
        const int size(10);
        for(int i = 0; i < size; i++){
            Message::shared_ptr msg = Message::shared_ptr(new Message(0, "A", "B", true, true));
            msg->setHeader(AMQHeaderBody::shared_ptr(new AMQHeaderBody()));
            msg->addContent(AMQContentBody::shared_ptr(new AMQContentBody()));
            msg.reset();
        }
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(MessageTest);

