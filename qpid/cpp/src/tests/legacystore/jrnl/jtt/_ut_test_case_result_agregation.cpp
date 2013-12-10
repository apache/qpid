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
#include <ctime>
#include <iostream>
#include "test_case_result_agregation.h"

using namespace boost::unit_test;
using namespace mrg::journal;
using namespace mrg::jtt;
using namespace std;

QPID_AUTO_TEST_SUITE(jtt_test_case_result_agregation)

const string test_filename("_ut_test_case_result_agregation");

// === Helper functions ===

void check_agregate(const test_case_result_agregation& tcra, const u_int32_t num_enq,
        const u_int32_t num_deq, const u_int32_t num_reads, const u_int32_t num_results,
        const u_int32_t num_exceptions, const std::time_t secs, const long nsec)
{
    BOOST_CHECK_EQUAL(tcra.num_enq(), num_enq);
    BOOST_CHECK_EQUAL(tcra.num_deq(), num_deq);
    BOOST_CHECK_EQUAL(tcra.num_read(), num_reads);
    BOOST_CHECK_EQUAL(tcra.num_results(), num_results);
    BOOST_CHECK_EQUAL(tcra.exception_count(), num_exceptions);
    BOOST_CHECK_EQUAL(tcra.exception(), num_exceptions > 0);
    const time_ns& ts1 = tcra.test_time();
    BOOST_CHECK_EQUAL(ts1.tv_sec, secs);
    BOOST_CHECK_EQUAL(ts1.tv_nsec, nsec);
}

test_case_result::shared_ptr make_result(const string& jid, const u_int32_t num_enq,
        const u_int32_t num_deq, const u_int32_t num_reads, const std::time_t secs, const long nsec)
{
    test_case_result::shared_ptr tcrp(new test_case_result(jid));
    for (unsigned i=0; i<num_enq; i++)
        tcrp->incr_num_enq();
    for (unsigned i=0; i<num_deq; i++)
        tcrp->incr_num_deq();
    for (unsigned i=0; i<num_reads; i++)
        tcrp->incr_num_read();
    time_ns ts(secs, nsec);
    tcrp->set_test_time(ts);
    return tcrp;
}

// === Test suite ===

QPID_AUTO_TEST_CASE(constructor_1)
{
    cout << test_filename << ".constructor_1: " << flush;
    test_case_result_agregation tcra;
    BOOST_CHECK_EQUAL(tcra.tc_average_mode(), true);
    BOOST_CHECK_EQUAL(tcra.jid(), "Average");
    BOOST_CHECK_EQUAL(tcra.exception(), false);
    BOOST_CHECK_EQUAL(tcra.exception_count(), 0U);
    const time_ns& ts1 = tcra.start_time();
    BOOST_CHECK(ts1.is_zero());
    const time_ns& ts2 = tcra.stop_time();
    BOOST_CHECK(ts2.is_zero());
    const time_ns& ts3 = tcra.test_time();
    BOOST_CHECK(ts3.is_zero());
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(constructor_2)
{
    cout << test_filename << ".constructor_2: " << flush;
    string jid("journal id");
    test_case_result_agregation tcra(jid);
    BOOST_CHECK_EQUAL(tcra.tc_average_mode(), false);
    BOOST_CHECK_EQUAL(tcra.jid(), jid);
    BOOST_CHECK_EQUAL(tcra.exception(), false);
    BOOST_CHECK_EQUAL(tcra.exception_count(), 0U);
    const time_ns& ts1 = tcra.start_time();
    BOOST_CHECK(ts1.is_zero());
    const time_ns& ts2 = tcra.stop_time();
    BOOST_CHECK(ts2.is_zero());
    const time_ns& ts3 = tcra.test_time();
    BOOST_CHECK(ts3.is_zero());
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(add_test_case)
{
    cout << test_filename << ".add_test_case: " << flush;
    string jid("jid1");
    test_case_result::shared_ptr tcrp1 = make_result("jid1",  10,  10,   0,   1, 101010101L);
    test_case_result::shared_ptr tcrp2 = make_result("jid1",  25,   0,  35,  10,  20202020L);
    test_case_result::shared_ptr tcrp3 = make_result("jid1",   0,  15,   5,   2, 555555555L);
    test_case_result::shared_ptr tcrp4 = make_result("jid2", 100, 100, 100, 100, 323232324L);
    test_case_result::shared_ptr tcrp5 = make_result("jid1",   5,   0,   0,   0,       100L);
    tcrp5->add_exception(string("error 1"), false);
    test_case_result::shared_ptr tcrp6 = make_result("jid3",   0,   5,   0,   0,       100L);
    jexception e(0x123, "exception 2");
    tcrp6->add_exception(e, false);
    test_case_result::shared_ptr tcrp7 = make_result("jid1",   0,   0,   0,   0,         0L);
    test_case_result::shared_ptr tcrp8 = make_result("jid1", 200, 100, 300,  12, 323232224L);

    test_case_result_agregation tcra(jid);
    check_agregate(tcra,   0,   0,   0, 0, 0,   0,         0L);
    tcra.add_test_result(tcrp1);
    check_agregate(tcra,  10,  10,   0, 1, 0,   1, 101010101L);
    tcra.add_test_result(tcrp2);
    check_agregate(tcra,  35,  10,  35, 2, 0,  11, 121212121L);
    tcra.add_test_result(tcrp3);
    check_agregate(tcra,  35,  25,  40, 3, 0,  13, 676767676L);
    tcra.add_test_result(tcrp4);
    check_agregate(tcra,  35,  25,  40, 3, 0,  13, 676767676L);
    tcra.add_test_result(tcrp5);
    check_agregate(tcra,  40,  25,  40, 4, 1,  13, 676767776L);
    tcra.add_test_result(tcrp6);
    check_agregate(tcra,  40,  25,  40, 4, 1,  13, 676767776L);
    tcra.add_test_result(tcrp7);
    check_agregate(tcra,  40,  25,  40, 5, 1,  13, 676767776L);
    tcra.add_test_result(tcrp8);
    check_agregate(tcra, 240, 125, 340, 6, 1,  26,         0L);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(add_test_case_average)
{
    cout << test_filename << ".add_test_case_average: " << flush;
    test_case_result::shared_ptr tcrp1 = make_result("jid1",  10,  10,   0,   1, 101010101L);
    test_case_result::shared_ptr tcrp2 = make_result("jid2",  25,   0,  35,  10,  20202020L);
    test_case_result::shared_ptr tcrp3 = make_result("jid3",   0,  15,   5,   2, 555555555L);
    test_case_result::shared_ptr tcrp4 = make_result("jid4", 100, 100, 100, 100, 323232324L);
    test_case_result::shared_ptr tcrp5 = make_result("jid5",   5,   0,   0,   0,       100L);
    tcrp5->add_exception(string("error 1"), false);
    test_case_result::shared_ptr tcrp6 = make_result("jid6",   0,   5,   0,   0,       100L);
    jexception e(0x123, "exception 2");
    tcrp6->add_exception(e, false);
    test_case_result::shared_ptr tcrp7 = make_result("jid7",   0,   0,   0,   0,         0L);
    test_case_result::shared_ptr tcrp8 = make_result("jid8", 200, 100, 300,  12, 222222022L);

    test_case_result_agregation tcra;
    check_agregate(tcra,   0,   0,   0, 0, 0,   0,         0L);
    tcra.add_test_result(tcrp1);
    check_agregate(tcra,  10,  10,   0, 1, 0,   1, 101010101L);
    tcra.add_test_result(tcrp2);
    check_agregate(tcra,  35,  10,  35, 2, 0,  11, 121212121L);
    tcra.add_test_result(tcrp3);
    check_agregate(tcra,  35,  25,  40, 3, 0,  13, 676767676L);
    tcra.add_test_result(tcrp4);
    check_agregate(tcra, 135, 125, 140, 4, 0, 114,         0L);
    tcra.add_test_result(tcrp5);
    check_agregate(tcra, 140, 125, 140, 5, 1, 114,       100L);
    tcra.add_test_result(tcrp6);
    check_agregate(tcra, 140, 130, 140, 6, 2, 114,       200L);
    tcra.add_test_result(tcrp7);
    check_agregate(tcra, 140, 130, 140, 7, 2, 114,       200L);
    tcra.add_test_result(tcrp8);
    check_agregate(tcra, 340, 230, 440, 8, 2, 126, 222222222L);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_SUITE_END()
