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
//#include <iostream>
//#include <AMQP_HighestVersion.h>
#include "../framing/amqp_framing.h"
#include "qpid_test_plugin.h"

#include "../broker/BrokerAdapter.h"

using namespace qpid::framing;
using namespace qpid::broker;

class MessageHandlerTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(MessageHandlerTest);
    CPPUNIT_TEST(testOpenMethod);
    CPPUNIT_TEST_SUITE_END();
private:
	
public:
        
    MessageHandlerTest()
    {
    }

    void testOpenMethod() 
    {
        //AMQFrame frame(highestProtocolVersion, 0, method);
        //TestBodyHandler handler(method);
        //handler.handleBody(frame.getBody());
    }

};


// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(MessageHandlerTest);

