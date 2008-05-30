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
#include "qpid/broker/QueuePolicy.h"
#include "unit_test.h"

using namespace qpid::broker;
using namespace qpid::framing;

QPID_AUTO_TEST_SUITE(QueuePolicyTestSuite)

QPID_AUTO_TEST_CASE(testCount)
{
    QueuePolicy policy(5, 0);
    BOOST_CHECK(!policy.limitExceeded());
    for (int i = 0; i < 5; i++) policy.enqueued(10);
    BOOST_CHECK_EQUAL((uint64_t) 0, policy.getMaxSize());
    BOOST_CHECK_EQUAL((uint32_t) 5, policy.getMaxCount());
    BOOST_CHECK(!policy.limitExceeded());
    policy.enqueued(10);
    BOOST_CHECK(policy.limitExceeded());
    policy.dequeued(10);
    BOOST_CHECK(!policy.limitExceeded());
    policy.enqueued(10);
    BOOST_CHECK(policy.limitExceeded());        
}

QPID_AUTO_TEST_CASE(testSize)
{
    QueuePolicy policy(0, 50);
    for (int i = 0; i < 5; i++) policy.enqueued(10);
    BOOST_CHECK(!policy.limitExceeded());
    policy.enqueued(10);
    BOOST_CHECK(policy.limitExceeded());
    policy.dequeued(10);
    BOOST_CHECK(!policy.limitExceeded());
    policy.enqueued(10);
    BOOST_CHECK(policy.limitExceeded());        
}

QPID_AUTO_TEST_CASE(testBoth)
{
    QueuePolicy policy(5, 50);
    for (int i = 0; i < 5; i++) policy.enqueued(11);
    BOOST_CHECK(policy.limitExceeded());
    policy.dequeued(20);
    BOOST_CHECK(!policy.limitExceeded());//fails
    policy.enqueued(5);
    policy.enqueued(10);
    BOOST_CHECK(policy.limitExceeded());
}

QPID_AUTO_TEST_CASE(testSettings)
{
    //test reading and writing the policy from/to field table
    FieldTable settings;
    QueuePolicy a(101, 303);
    a.update(settings);
    QueuePolicy b(settings);
    BOOST_CHECK_EQUAL(a.getMaxCount(), b.getMaxCount());
    BOOST_CHECK_EQUAL(a.getMaxSize(), b.getMaxSize());
}

QPID_AUTO_TEST_SUITE_END()
