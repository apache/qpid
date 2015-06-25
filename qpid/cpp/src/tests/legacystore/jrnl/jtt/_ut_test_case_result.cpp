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

#include "../../unit_test.h"

#include <iostream>
#include "qpid/legacystore/jrnl/jexception.h"
#include "test_case_result.h"

using namespace boost::unit_test;
using namespace mrg::journal;
using namespace mrg::jtt;
using namespace std;

QPID_AUTO_TEST_SUITE(jtt_test_case_result)

const string test_filename("_ut_test_case_result");

QPID_AUTO_TEST_CASE(constructor)
{
    cout << test_filename << ".constructor: " << flush;
    const string jid("journal id 1");
    test_case_result tcr(jid);
    BOOST_CHECK_EQUAL(tcr.jid(), jid);
    BOOST_CHECK_EQUAL(tcr.exception(), false);
    BOOST_CHECK_EQUAL(tcr.exception_count(), 0U);
    const time_ns& ts1 = tcr.start_time();
    BOOST_CHECK(ts1.is_zero());
    const time_ns& ts2 = tcr.stop_time();
    BOOST_CHECK(ts2.is_zero());
    const time_ns& ts3 = tcr.test_time();
    BOOST_CHECK(ts3.is_zero());
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(start_stop)
{
    cout << test_filename << ".start_stop: " << flush;
    const string jid("journal id 2");
    test_case_result tcr(jid);
    BOOST_CHECK_EQUAL(tcr.exception(), false);
    BOOST_CHECK_EQUAL(tcr.exception_count(), 0U);
    const time_ns& ts1 = tcr.start_time();
    BOOST_CHECK(ts1.is_zero());
    const time_ns& ts2 = tcr.stop_time();
    BOOST_CHECK(ts2.is_zero());
    const time_ns& ts3 = tcr.test_time();
    BOOST_CHECK(ts3.is_zero());

    tcr.set_start_time();
    BOOST_CHECK_EQUAL(tcr.exception(), false);
    BOOST_CHECK_EQUAL(tcr.exception_count(), 0U);
    const time_ns& ts4 = tcr.start_time();
    BOOST_CHECK(!ts4.is_zero());
    const time_ns& ts5 = tcr.stop_time();
    BOOST_CHECK(ts5.is_zero());
    const time_ns& ts6 = tcr.test_time();
    BOOST_CHECK(ts6.is_zero());

    ::usleep(1100000); // 1.1 sec in microseconds
    tcr.set_stop_time();
    BOOST_CHECK_EQUAL(tcr.exception(), false);
    BOOST_CHECK_EQUAL(tcr.exception_count(), 0U);
    const time_ns& ts7 = tcr.stop_time();
    BOOST_CHECK(!ts7.is_zero());
    const time_ns& ts8 = tcr.test_time();
    BOOST_CHECK(ts8.tv_sec == 1);
    BOOST_CHECK(ts8.tv_nsec > 100000000); // 0.1 sec in nanoseconds
    BOOST_CHECK(ts8.tv_nsec < 200000000); // 0.2 sec in nanoseconds
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(start_exception_stop_1)
{
    cout << test_filename << ".start_exception_stop_1: " << flush;
    const string jid("journal id 3");
    test_case_result tcr(jid);
    const u_int32_t err_code = 0x321;
    const string err_msg = "exception message";
    const jexception e(err_code, err_msg);
    tcr.set_start_time();
    ::usleep(1100000); // 1.1 sec in microseconds
    tcr.add_exception(e);
    BOOST_CHECK_EQUAL(tcr.exception(), true);
    BOOST_CHECK_EQUAL(tcr.exception_count(), 1U);
    BOOST_CHECK_EQUAL(tcr[0], e.what());
    const time_ns& ts1 = tcr.stop_time();
    BOOST_CHECK(!ts1.is_zero());
    const time_ns& ts2 = tcr.test_time();
    BOOST_CHECK(ts2.tv_sec == 1);
    BOOST_CHECK(ts2.tv_nsec > 100000000); // 0.1 sec in nanoseconds
    BOOST_CHECK(ts2.tv_nsec < 200000000); // 0.2 sec in nanoseconds
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(start_exception_stop_2)
{
    cout << test_filename << ".start_exception_stop_2: " << flush;
    const string jid("journal id 4");
    test_case_result tcr(jid);
    const string err_msg = "exception message";
    tcr.set_start_time();
    ::usleep(1100000); // 1.1 sec in microseconds
    tcr.add_exception(err_msg);
    BOOST_CHECK_EQUAL(tcr.exception(), true);
    BOOST_CHECK_EQUAL(tcr.exception_count(), 1U);
    BOOST_CHECK_EQUAL(tcr[0], err_msg);
    const time_ns& ts1 = tcr.stop_time();
    BOOST_CHECK(!ts1.is_zero());
    const time_ns& ts2 = tcr.test_time();
    BOOST_CHECK(ts2.tv_sec == 1);
    BOOST_CHECK(ts2.tv_nsec > 100000000); // 0.1 sec in nanoseconds
    BOOST_CHECK(ts2.tv_nsec < 200000000); // 0.2 sec in nanoseconds
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(start_exception_stop_3)
{
    cout << test_filename << ".start_exception_stop_3: " << flush;
    const string jid("journal id 5");
    test_case_result tcr(jid);
    const char* err_msg = "exception message";
    tcr.set_start_time();
    ::usleep(1100000); // 1.1 sec in microseconds
    tcr.add_exception(err_msg);
    BOOST_CHECK_EQUAL(tcr.exception(), true);
    BOOST_CHECK_EQUAL(tcr.exception_count(), 1U);
    BOOST_CHECK_EQUAL(tcr[0], err_msg);
    const time_ns& ts1 = tcr.stop_time();
    BOOST_CHECK(!ts1.is_zero());
    const time_ns& ts2 = tcr.test_time();
    BOOST_CHECK(ts2.tv_sec == 1);
    BOOST_CHECK(ts2.tv_nsec > 100000000); // 0.1 sec in nanoseconds
    BOOST_CHECK(ts2.tv_nsec < 200000000); // 0.2 sec in nanoseconds
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(start_exception)
{
    cout << test_filename << ".start_exception: " << flush;
    const string jid("journal id 6");
    test_case_result tcr(jid);
    u_int32_t err_code = 0x654;
    const string err_msg = "exception message";
    const jexception e(err_code, err_msg);
    tcr.set_start_time();
    ::usleep(1100000); // 1.1 sec in microseconds
    tcr.add_exception(e, false);
    BOOST_CHECK_EQUAL(tcr.exception(), true);
    BOOST_CHECK_EQUAL(tcr.exception_count(), 1U);
    BOOST_CHECK_EQUAL(tcr[0], e.what());
    const time_ns& ts1 = tcr.stop_time();
    BOOST_CHECK(ts1.is_zero());
    const time_ns& ts2 = tcr.test_time();
    BOOST_CHECK(ts2.is_zero());
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(counters)
{
    cout << test_filename << ".counters: " << flush;
    const u_int32_t num_enq = 125;
    const u_int32_t num_deq = 64;
    const u_int32_t num_read = 22;
    const string jid("journal id 7");
    test_case_result tcr(jid);
    BOOST_CHECK_EQUAL(tcr.num_enq(), u_int32_t(0));
    BOOST_CHECK_EQUAL(tcr.num_deq(), u_int32_t(0));
    BOOST_CHECK_EQUAL(tcr.num_read(), u_int32_t(0));
    for (unsigned i=0; i<num_enq; i++)
        tcr.incr_num_enq();
    BOOST_CHECK_EQUAL(tcr.num_enq(), num_enq);
    BOOST_CHECK_EQUAL(tcr.num_deq(), u_int32_t(0));
    BOOST_CHECK_EQUAL(tcr.num_read(), u_int32_t(0));
    for (unsigned j=0; j<num_deq; j++)
        tcr.incr_num_deq();
    BOOST_CHECK_EQUAL(tcr.num_enq(), num_enq);
    BOOST_CHECK_EQUAL(tcr.num_deq(), num_deq);
    BOOST_CHECK_EQUAL(tcr.num_read(), u_int32_t(0));
    for (unsigned k=0; k<num_read; k++)
        tcr.incr_num_read();
    BOOST_CHECK_EQUAL(tcr.num_enq(), num_enq);
    BOOST_CHECK_EQUAL(tcr.num_deq(), num_deq);
    BOOST_CHECK_EQUAL(tcr.num_read(), num_read);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_SUITE_END()
