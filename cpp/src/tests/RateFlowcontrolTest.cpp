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

#include "unit_test.h"

#include "qpid/broker/RateFlowcontrol.h"
#include "qpid/sys/Time.h"

using namespace qpid::broker;
using namespace qpid::sys;

QPID_AUTO_TEST_SUITE(RateFlowcontrolTestSuite)

QPID_AUTO_TEST_CASE(RateFlowcontrolTest)
{
    // BOOST_CHECK(predicate);
    // BOOST_CHECK_EQUAL(a, b);
    
   RateFlowcontrol fc(100);
   AbsTime n=AbsTime::now();
   
   BOOST_CHECK_EQUAL( fc.receivedMessage(n, 0), 0U );
   
   fc.sentCredit(n, 0);
   
   BOOST_CHECK_EQUAL( fc.receivedMessage(n, 0), 0U );
   fc.sentCredit(n, 100);

   Duration d=250*TIME_MSEC;
   
   n = AbsTime(n,d);
   BOOST_CHECK_EQUAL( fc.receivedMessage(n, 48), 25U );
   fc.sentCredit(n, 25);

   n = AbsTime(n,d);
   BOOST_CHECK_EQUAL( fc.receivedMessage(n, 0), 23U );
   fc.sentCredit(n, 23);
   BOOST_CHECK_EQUAL( fc.receivedMessage(n, 100), 0U);
   BOOST_CHECK(fc.flowStopped());

   n = AbsTime(n,d);
   n = AbsTime(n,d);
   BOOST_CHECK_EQUAL( fc.receivedMessage(n, 0), 50U);
}

QPID_AUTO_TEST_SUITE_END()
