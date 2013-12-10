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
#include "jrnl_init_params.h"
#include <iostream>

using namespace boost::unit_test;
using namespace mrg::jtt;
using namespace std;

QPID_AUTO_TEST_SUITE(jtt_jrnl_init_params)

const string test_filename("_ut_jrnl_init_params");

QPID_AUTO_TEST_CASE(constructor)
{
    cout << test_filename << ".constructor: " << flush;
    const string jid = "jid";
    const string jdir = "jdir";
    const string bfn = "base filename";
    const u_int16_t num_jfiles = 123;
    const bool ae = false;
    const u_int16_t ae_max_jfiles = 456;
    const u_int32_t jfsize_sblks = 789;
    jrnl_init_params jip(jid, jdir, bfn, num_jfiles, ae, ae_max_jfiles, jfsize_sblks);
    BOOST_CHECK_EQUAL(jip.jid(), jid);
    BOOST_CHECK_EQUAL(jip.jdir(), jdir);
    BOOST_CHECK_EQUAL(jip.base_filename(), bfn);
    BOOST_CHECK_EQUAL(jip.num_jfiles(), num_jfiles);
    BOOST_CHECK_EQUAL(jip.is_ae(), ae);
    BOOST_CHECK_EQUAL(jip.ae_max_jfiles(), ae_max_jfiles);
    BOOST_CHECK_EQUAL(jip.jfsize_sblks(), jfsize_sblks);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(copy_constructor_1)
{
    cout << test_filename << ".copy_constructor_1: " << flush;
    const string jid = "jid";
    const string jdir = "jdir";
    const string bfn = "base filename";
    const u_int16_t num_jfiles = 123;
    const bool ae = false;
    const u_int16_t ae_max_jfiles = 456;
    const u_int32_t jfsize_sblks = 789;
    jrnl_init_params jip1(jid, jdir, bfn, num_jfiles, ae, ae_max_jfiles, jfsize_sblks);
    jrnl_init_params jip2(jip1);
    BOOST_CHECK_EQUAL(jip2.jid(), jid);
    BOOST_CHECK_EQUAL(jip2.jdir(), jdir);
    BOOST_CHECK_EQUAL(jip2.base_filename(), bfn);
    BOOST_CHECK_EQUAL(jip2.num_jfiles(), num_jfiles);
    BOOST_CHECK_EQUAL(jip2.is_ae(), ae);
    BOOST_CHECK_EQUAL(jip2.ae_max_jfiles(), ae_max_jfiles);
    BOOST_CHECK_EQUAL(jip2.jfsize_sblks(), jfsize_sblks);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(copy_constructor_2)
{
    cout << test_filename << ".copy_constructor_2: " << flush;
    const string jid = "jid";
    const string jdir = "jdir";
    const string bfn = "base filename";
    const u_int16_t num_jfiles = 123;
    const bool ae = false;
    const u_int16_t ae_max_jfiles = 456;
    const u_int32_t jfsize_sblks = 789;
    jrnl_init_params::shared_ptr p(new jrnl_init_params(jid, jdir, bfn, num_jfiles, ae, ae_max_jfiles, jfsize_sblks));
    jrnl_init_params jip2(p.get());
    BOOST_CHECK_EQUAL(jip2.jid(), jid);
    BOOST_CHECK_EQUAL(jip2.jdir(), jdir);
    BOOST_CHECK_EQUAL(jip2.base_filename(), bfn);
    BOOST_CHECK_EQUAL(jip2.num_jfiles(), num_jfiles);
    BOOST_CHECK_EQUAL(jip2.is_ae(), ae);
    BOOST_CHECK_EQUAL(jip2.ae_max_jfiles(), ae_max_jfiles);
    BOOST_CHECK_EQUAL(jip2.jfsize_sblks(), jfsize_sblks);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_SUITE_END()

