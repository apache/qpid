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

#include "../unit_test.h"

#include <ctime>
#include <iostream>
#include "qpid/legacystore/jrnl/time_ns.h"

using namespace boost::unit_test;
using namespace mrg::journal;
using namespace std;

QPID_AUTO_TEST_SUITE(time_ns_suite)

const string test_filename("_ut_time_ns");

QPID_AUTO_TEST_CASE(constructors)
{
    cout << test_filename << ".constructors: " << flush;
    const std::time_t sec = 123;
    const long nsec = 123456789;

    time_ns t1;
    BOOST_CHECK_EQUAL(t1.tv_sec, 0);
    BOOST_CHECK_EQUAL(t1.tv_nsec, 0);
    BOOST_CHECK_EQUAL(t1.is_zero(), true);
    time_ns t2(sec, nsec);
    BOOST_CHECK_EQUAL(t2.tv_sec, sec);
    BOOST_CHECK_EQUAL(t2.tv_nsec, nsec);
    BOOST_CHECK_EQUAL(t2.is_zero(), false);
    time_ns t3(t1);
    BOOST_CHECK_EQUAL(t3.tv_sec, 0);
    BOOST_CHECK_EQUAL(t3.tv_nsec, 0);
    BOOST_CHECK_EQUAL(t3.is_zero(), true);
    time_ns t4(t2);
    BOOST_CHECK_EQUAL(t4.tv_sec, sec);
    BOOST_CHECK_EQUAL(t4.tv_nsec, nsec);
    BOOST_CHECK_EQUAL(t4.is_zero(), false);
    t4.set_zero();
    BOOST_CHECK_EQUAL(t4.tv_sec, 0);
    BOOST_CHECK_EQUAL(t4.tv_nsec, 0);
    BOOST_CHECK_EQUAL(t4.is_zero(), true);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(operators)
{
    cout << test_filename << ".operators: " << flush;
    const std::time_t sec1 = 123;
    const long nsec1 = 123456789;
    const std::time_t sec2 = 1;
    const long nsec2 = 999999999;
    const std::time_t sec_sum = sec1 + sec2 + 1;
    const long nsec_sum = nsec1 + nsec2 - 1000000000;
    const std::time_t sec_1_minus_2 = sec1 - sec2 - 1;
    const long nsec_1_minus_2 = nsec1 - nsec2 + 1000000000;
    const std::time_t sec_2_minus_1 = sec2 - sec1;
    const long nsec_2_minus_1 = nsec2 - nsec1;
    time_ns z;
    time_ns t1(sec1, nsec1);
    time_ns t2(sec2, nsec2);

    time_ns t3 = z;
    BOOST_CHECK_EQUAL(t3.tv_sec, 0);
    BOOST_CHECK_EQUAL(t3.tv_nsec, 0);
    BOOST_CHECK_EQUAL(t3 == z, true);
    BOOST_CHECK_EQUAL(t3 != z, false);
    BOOST_CHECK_EQUAL(t3 > z, false);
    BOOST_CHECK_EQUAL(t3 >= z, true);
    BOOST_CHECK_EQUAL(t3 < z, false);
    BOOST_CHECK_EQUAL(t3 <= z, true);

    t3 = t1;
    BOOST_CHECK_EQUAL(t3.tv_sec, sec1);
    BOOST_CHECK_EQUAL(t3.tv_nsec, nsec1);
    BOOST_CHECK_EQUAL(t3 == t1, true);
    BOOST_CHECK_EQUAL(t3 != t1, false);
    BOOST_CHECK_EQUAL(t3 > t1, false);
    BOOST_CHECK_EQUAL(t3 >= t1, true);
    BOOST_CHECK_EQUAL(t3 < t1, false);
    BOOST_CHECK_EQUAL(t3 <= t1, true);

    t3 += z;
    BOOST_CHECK_EQUAL(t3.tv_sec, sec1);
    BOOST_CHECK_EQUAL(t3.tv_nsec, nsec1);

    t3 = t2;
    BOOST_CHECK_EQUAL(t3.tv_sec, sec2);
    BOOST_CHECK_EQUAL(t3.tv_nsec, nsec2);
    BOOST_CHECK_EQUAL(t3 == t2, true);
    BOOST_CHECK_EQUAL(t3 != t2, false);
    BOOST_CHECK_EQUAL(t3 > t2, false);
    BOOST_CHECK_EQUAL(t3 >= t2, true);
    BOOST_CHECK_EQUAL(t3 < t2, false);
    BOOST_CHECK_EQUAL(t3 <= t2, true);

    t3 += z;
    BOOST_CHECK_EQUAL(t3.tv_sec, sec2);
    BOOST_CHECK_EQUAL(t3.tv_nsec, nsec2);

    t3 = t1;
    t3 += t2;
    BOOST_CHECK_EQUAL(t3.tv_sec, sec_sum);
    BOOST_CHECK_EQUAL(t3.tv_nsec, nsec_sum);

    t3 = t1;
    t3 -= t2;
    BOOST_CHECK_EQUAL(t3.tv_sec, sec_1_minus_2);
    BOOST_CHECK_EQUAL(t3.tv_nsec, nsec_1_minus_2);

    t3 = t2;
    t3 -= t1;
    BOOST_CHECK_EQUAL(t3.tv_sec, sec_2_minus_1);
    BOOST_CHECK_EQUAL(t3.tv_nsec, nsec_2_minus_1);

    t3 = t1 + t2;
    BOOST_CHECK_EQUAL(t3.tv_sec, sec_sum);
    BOOST_CHECK_EQUAL(t3.tv_nsec, nsec_sum);

    t3 = t1 - t2;
    BOOST_CHECK_EQUAL(t3.tv_sec, sec_1_minus_2);
    BOOST_CHECK_EQUAL(t3.tv_nsec, nsec_1_minus_2);

    t3 = t2 - t1;
    BOOST_CHECK_EQUAL(t3.tv_sec, sec_2_minus_1);
    BOOST_CHECK_EQUAL(t3.tv_nsec, nsec_2_minus_1);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(str)
{
    cout << test_filename << ".str: " << flush;
    time_ns t1(123, 123456789);
    BOOST_CHECK_EQUAL(t1.str(), "123.123457");
    BOOST_CHECK_EQUAL(t1.str(9), "123.123456789");
    BOOST_CHECK_EQUAL(t1.str(0), "123");
    time_ns t2(1, 1);
    BOOST_CHECK_EQUAL(t2.str(9), "1.000000001");
    time_ns t3(-12, 345);
    BOOST_CHECK_EQUAL(t3.str(9), "-11.999999655");
    cout << "ok" << endl;
}

QPID_AUTO_TEST_SUITE_END()
