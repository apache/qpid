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
#include <QueuePolicy.h>
#include <qpid_test_plugin.h>

using namespace qpid::broker;
using namespace qpid::framing;

class QueuePolicyTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(QueuePolicyTest);
    CPPUNIT_TEST(testCount);
    CPPUNIT_TEST(testSize);
    CPPUNIT_TEST(testBoth);
    CPPUNIT_TEST(testSettings);
    CPPUNIT_TEST_SUITE_END();

  public:
    void testCount(){
        QueuePolicy policy(5, 0);
        CPPUNIT_ASSERT(!policy.limitExceeded());
        for (int i = 0; i < 5; i++) policy.enqueued(10);
        CPPUNIT_ASSERT_EQUAL((u_int64_t) 0, policy.getMaxSize());
        CPPUNIT_ASSERT_EQUAL((u_int32_t) 5, policy.getMaxCount());
        CPPUNIT_ASSERT(!policy.limitExceeded());
        policy.enqueued(10);
        CPPUNIT_ASSERT(policy.limitExceeded());
        policy.dequeued(10);
        CPPUNIT_ASSERT(!policy.limitExceeded());
        policy.enqueued(10);
        CPPUNIT_ASSERT(policy.limitExceeded());        
    }

    void testSize(){
        QueuePolicy policy(0, 50);
        for (int i = 0; i < 5; i++) policy.enqueued(10);
        CPPUNIT_ASSERT(!policy.limitExceeded());
        policy.enqueued(10);
        CPPUNIT_ASSERT(policy.limitExceeded());
        policy.dequeued(10);
        CPPUNIT_ASSERT(!policy.limitExceeded());
        policy.enqueued(10);
        CPPUNIT_ASSERT(policy.limitExceeded());        
    }

    void testBoth(){
        QueuePolicy policy(5, 50);
        for (int i = 0; i < 5; i++) policy.enqueued(11);
        CPPUNIT_ASSERT(policy.limitExceeded());
        policy.dequeued(20);
        CPPUNIT_ASSERT(!policy.limitExceeded());//fails
        policy.enqueued(5);
        policy.enqueued(10);
        CPPUNIT_ASSERT(policy.limitExceeded());
    }

    void testSettings(){
        //test reading and writing the policy from/to field table
        FieldTable settings;
        QueuePolicy a(101, 303);
        a.update(settings);
        QueuePolicy b(settings);
        CPPUNIT_ASSERT_EQUAL(a.getMaxCount(), b.getMaxCount());
        CPPUNIT_ASSERT_EQUAL(a.getMaxSize(), b.getMaxSize());
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(QueuePolicyTest);

