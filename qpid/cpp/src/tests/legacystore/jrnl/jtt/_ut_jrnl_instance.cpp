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
#include "jrnl_init_params.h"
#include "jrnl_instance.h"
#include "qpid/legacystore/jrnl/jdir.h"
#include "qpid/legacystore/jrnl/jerrno.h"

using namespace boost::unit_test;
using namespace mrg::journal;
using namespace mrg::jtt;
using namespace std;

QPID_AUTO_TEST_SUITE(jtt_jrnl_instance)

const string test_filename("_ut_jrnl_instance");
const char* tdp = getenv("TMP_DATA_DIR");
const string test_dir(tdp && strlen(tdp) > 0 ? tdp : "/var/tmp/JttTest");

QPID_AUTO_TEST_CASE(constructor_1)
{
    cout << test_filename << ".constructor_1: " << flush;
    const string jid = "jid1";
    const string jdir = test_dir + "/test1";
    const string bfn = "test";
    const u_int16_t num_jfiles = 20;
    const bool ae = false;
    const u_int16_t ae_max_jfiles = 45;
    const u_int32_t jfsize_sblks = 128;

    args a("a1");
    using mrg::jtt::test_case;
    test_case::shared_ptr p(new test_case(1, 0, 0, 0, false, 0, 0, test_case::JTT_PERSISTNET, test_case::JDL_INTERNAL,
            "t1"));
    jrnl_instance ji(jid, jdir, bfn, num_jfiles, ae, ae_max_jfiles, jfsize_sblks);
    ji.init_tc(p, &a);
    ji.run_tc();
    ji.tc_wait_compl();
    try { jdir::verify_dir(jdir, bfn); }
    catch (const jexception& e) { BOOST_ERROR(e.what()); }
    jdir::delete_dir(jdir);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(constructor_2)
{
    cout << test_filename << ".constructor_2: " << flush;
    const string jid = "jid2";
    const string jdir = test_dir + "/test2";
    const string bfn = "test";
    const u_int16_t num_jfiles = 20;
    const bool ae = false;
    const u_int16_t ae_max_jfiles = 45;
    const u_int32_t jfsize_sblks = 128;

    args a("a2");
    using mrg::jtt::test_case;
    test_case::shared_ptr p(new test_case(2, 0, 0, 0, false, 0, 0, test_case::JTT_PERSISTNET, test_case::JDL_INTERNAL,
            "t2"));
    jrnl_init_params::shared_ptr jpp(new jrnl_init_params(jid, jdir, bfn, num_jfiles, ae, ae_max_jfiles, jfsize_sblks));
    jrnl_instance ji(jpp);
    ji.init_tc(p, &a);
    ji.run_tc();
    ji.tc_wait_compl();
    try { jdir::verify_dir(jdir, bfn); }
    catch (const jexception& e) { BOOST_ERROR(e.what()); }
    jdir::delete_dir(jdir);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(constructor_3)
{
    cout << test_filename << ".constructor_3: " << flush;
    const string jid = "jid3";
    const string jdir = test_dir + "/test3";
    const string bfn = "test";
    const u_int16_t num_jfiles = 20;
    const bool ae = false;
    const u_int16_t ae_max_jfiles = 45;
    const u_int32_t jfsize_sblks = 128;

    args a("a3");
    using mrg::jtt::test_case;
    test_case::shared_ptr p(new test_case(3, 0, 0, 0, false, 0, 0, test_case::JTT_PERSISTNET, test_case::JDL_INTERNAL,
            "t3"));
    jrnl_init_params::shared_ptr jpp(new jrnl_init_params(jid, jdir, bfn, num_jfiles, ae, ae_max_jfiles, jfsize_sblks));
    jrnl_instance ji(jpp);
    ji.init_tc(p, &a);
    ji.run_tc();
    ji.tc_wait_compl();
    try { jdir::verify_dir(jdir, bfn); }
    catch (const jexception& e) { BOOST_ERROR(e.what()); }
    jdir::delete_dir(jdir);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(recover)
{
    cout << test_filename << ".recover: " << flush;
    const string jid = "jid5";
    const string jdir = test_dir + "/test5";
    const string bfn = "test";
    const u_int16_t num_jfiles = 20;
    const bool ae = false;
    const u_int16_t ae_max_jfiles = 0;
    const u_int32_t jfsize_sblks = 128;

    args a("a4");
    using mrg::jtt::test_case;
    test_case::shared_ptr p(new test_case(5, 0, 0, 0, false, 0, 0, test_case::JTT_PERSISTNET, test_case::JDL_INTERNAL,
            "t5"));
    jrnl_init_params::shared_ptr jpp(new jrnl_init_params(jid, jdir, bfn, num_jfiles, ae, ae_max_jfiles, jfsize_sblks));
    jrnl_instance ji(jpp);
    ji.init_tc(p, &a);
    ji.run_tc();
    ji.tc_wait_compl();
    try { jdir::verify_dir(jdir, bfn); }
    catch (const jexception& e) { BOOST_ERROR(e.what()); }
    a.recover_mode = true;
    ji.init_tc(p, &a);
    ji.run_tc();
    ji.tc_wait_compl();
    try { jdir::verify_dir(jdir, bfn); }
    catch (const jexception& e) { BOOST_ERROR(e.what()); }
    jdir::delete_dir(jdir);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(recover_no_files)
{
    cout << test_filename << ".recover_no_files: " << flush;
    const string jid = "jid6";
    const string jdir = test_dir + "/test6";
    const string bfn = "test";
    const u_int16_t num_jfiles = 20;
    const bool ae = false;
    const u_int16_t ae_max_jfiles = 0;
    const u_int32_t jfsize_sblks = 128;

    args a("a5");
    a.recover_mode = true;
    using mrg::jtt::test_case;
    test_case::shared_ptr p(new test_case(6, 0, 0, 0, false, 0, 0, test_case::JTT_PERSISTNET, test_case::JDL_INTERNAL,
            "t6"));
    jrnl_init_params::shared_ptr jpp(new jrnl_init_params(jid, jdir, bfn, num_jfiles, ae, ae_max_jfiles, jfsize_sblks));
    jrnl_instance ji(jpp);
    ji.init_tc(p, &a);
    ji.run_tc();
    ji.tc_wait_compl();
    try { jdir::verify_dir(jdir, bfn); }
    catch (const jexception& e) { BOOST_ERROR(e.what()); }
    jdir::delete_dir(jdir);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_SUITE_END()

