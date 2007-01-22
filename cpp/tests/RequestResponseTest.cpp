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

#include <qpid_test_plugin.h>
#include "BrokerSingleton.h"
#include "broker/Broker.h"
#include "client/Connection.h"
#include "client/ClientChannel.h"

/**
 * Round trip test using in-process broker.
 * Verify request/response IDs
 */
class RequestResponseTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(RequestResponseTest);
    CPPUNIT_TEST(testAsRequester);
    CPPUNIT_TEST(testAsResponder);
    CPPUNIT_TEST_SUITE_END();

    qpid::broker::Broker::shared_ptr broker;

  public:

    void testAsRequester() {
// FIXME aconway 2007-01-22:         CPPUNIT_FAIL("unfinished"); 
    }
    void testAsResponder() {
// FIXME aconway 2007-01-22:         CPPUNIT_FAIL("unfinished");
    }
};


// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(RequestResponseTest);



