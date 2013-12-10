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
#include <cmath>
#include <iostream>
#include "qpid/legacystore/jrnl/jcntl.h"
#include "qpid/legacystore/jrnl/lpmgr.h"

using namespace boost::unit_test;
using namespace mrg::journal;
using namespace std;

QPID_AUTO_TEST_SUITE(arr_cnt_suite)

const string test_filename("_ut_lpmgr");

#include "_st_helper_fns.h"

// === Helper functions and definitions ===

typedef vector<u_int16_t> flist;
typedef flist::const_iterator flist_citr;

class lpmgr_test_helper
{
    lpmgr_test_helper() {}
    virtual ~lpmgr_test_helper() {}

public:
    static void check_pfids_lfids(const lpmgr& lm, const u_int16_t pfids[], const u_int16_t lfids[],
            const size_t pfid_lfid_size)
    {
        vector<u_int16_t> res;
        lm.get_pfid_list(res);
        vectors_equal(lm, pfids, pfid_lfid_size, res, true);
        lm.get_lfid_list(res);
        vectors_equal(lm, lfids, pfid_lfid_size, res, false);
    }

    static void check_pfids_lfids(const lpmgr& lm, const flist& pfids, const flist lfids)
    {
        vector<u_int16_t> res;
        lm.get_pfid_list(res);
        vectors_equal(lm, pfids, res, true);
        lm.get_lfid_list(res);
        vectors_equal(lm, lfids, res, false);
    }

    static void check_linear_pfids_lfids(const lpmgr& lm, const size_t pfid_lfid_size)
    {
        vector<u_int16_t> res;
        lm.get_pfid_list(res);
        linear_vectors_equal(lm, pfid_lfid_size, res, true);
        lm.get_lfid_list(res);
        linear_vectors_equal(lm, pfid_lfid_size, res, false);
    }

    static void rcvdat_init(rcvdat& rd, const u_int16_t num_jfiles, const bool ae, const u_int16_t ae_max_jfiles,
        const u_int16_t pfids[])
    {
        rd.reset(num_jfiles, ae, ae_max_jfiles);
        load_vector(pfids, num_jfiles, rd._fid_list);
        rd._jempty = false;
        rd._lfid = pfids[num_jfiles - 1];
        rd._eo = 100 * JRNL_DBLK_SIZE * JRNL_SBLK_SIZE;
    }

    static void rcvdat_init(rcvdat& rd, const flist& pfidl, const bool ae, const u_int16_t ae_max_jfiles)
    {
        const u_int16_t num_jfiles = pfidl.size();
        rd.reset(num_jfiles, ae, ae_max_jfiles);
        load_vector(pfidl, rd._fid_list);
        rd._jempty = false;
        rd._lfid = pfidl[num_jfiles - 1];
        rd._eo = 100 * JRNL_DBLK_SIZE * JRNL_SBLK_SIZE;
    }

    static void initialize(lpmgr& lm, test_jrnl& jc, const u_int16_t num_jfiles, const bool ae,
            const u_int16_t ae_max_jfiles)
    {
        lm.initialize(num_jfiles, ae, ae_max_jfiles, &jc, &jc.new_fcntl);
        BOOST_CHECK_EQUAL(lm.is_init(), true);
        BOOST_CHECK_EQUAL(lm.is_ae(), ae);
        BOOST_CHECK_EQUAL(lm.ae_max_jfiles(), ae_max_jfiles);
        BOOST_CHECK_EQUAL(lm.num_jfiles(), num_jfiles);
        if (num_jfiles)
            check_linear_pfids_lfids(lm, num_jfiles);
        else
            BOOST_CHECK_EQUAL(lm.get_fcntlp(0), (void*)0);
    }

    // version which sets up the lfid_pfid_map for later manipulation by insert tests
    static void initialize(lfid_pfid_map& lfm, lpmgr& lm, test_jrnl& jc, const u_int16_t num_jfiles, const bool ae,
            const u_int16_t ae_max_jfiles)
    {
        lfm.journal_create(num_jfiles, num_jfiles);
        initialize(lm, jc, num_jfiles, ae, ae_max_jfiles);
    }

    static void prepare_recover(lfid_pfid_map& lfm, const u_int16_t size)
    {
        if (size < 4) BOOST_FAIL("prepare_recover(): size parameter (" << size << ") too small.");
        lfm.journal_create(4, 4); // initial journal of size 4
        u_int16_t s = 4; // cumulative size
        while (s < size)
        {
            const u_int16_t ins_posn = u_int16_t(s * ::drand48()); // this insert posn
            if (3.0 * ::drand48() > 1.0 || size - s < 2) // 2:1 chance of single insert when >= 2 still to insert
            {
                lfm.journal_insert(ins_posn); // single insert
                s++;
            }
            else
            {
                // multiple insert, either 2 - 5
                const u_int16_t max_ins_size = size - s >5 ? 5 : size - s;
                const u_int16_t ins_size = 2 + u_int16_t((max_ins_size - 2) * ::drand48()); // this insert size
                lfm.journal_insert(ins_posn, ins_size);
                s += ins_size;
            }
        }
    }

    static void recover(lfid_pfid_map& lfm, lpmgr& lm, test_jrnl& jc, const bool ae, const u_int16_t ae_max_jfiles)
    {
        flist pfidl;
        flist lfidl;
        rcvdat rd;
        const u_int16_t num_jfiles = lfm.size();

        lfm.get_pfid_list(pfidl);
        lfm.get_lfid_list(lfidl);
        lm.finalize(); // clear all file handles before erasing old journal files
        lfm.write_journal(ae, ae_max_jfiles, JFSIZE_SBLKS);

        lpmgr_test_helper::rcvdat_init(rd, pfidl, ae, ae_max_jfiles);
        lm.recover(rd, &jc, &jc.new_fcntl);
        BOOST_CHECK_EQUAL(lm.is_init(), true);
        BOOST_CHECK_EQUAL(lm.is_ae(), ae);
        BOOST_CHECK_EQUAL(lm.ae_max_jfiles(), ae_max_jfiles);
        BOOST_CHECK_EQUAL(lm.num_jfiles(), num_jfiles);
        if (num_jfiles)
            check_pfids_lfids(lm, pfidl, lfidl);
        else
            BOOST_CHECK_EQUAL(lm.get_fcntlp(0), (void*)0);
    }

    static void finalize(lpmgr& lm)
    {
        lm.finalize();
        BOOST_CHECK_EQUAL(lm.is_init(), false);
        BOOST_CHECK_EQUAL(lm.is_ae(), false);
        BOOST_CHECK_EQUAL(lm.ae_max_jfiles(), u_int16_t(0));
        BOOST_CHECK_EQUAL(lm.num_jfiles(), u_int16_t(0));
        BOOST_CHECK_EQUAL(lm.get_fcntlp(0), (void*)0);
        vector<u_int16_t> res;
        lm.get_pfid_list(res);
        BOOST_CHECK_EQUAL(res.size(), u_int16_t(0));
        lm.get_lfid_list(res);
        BOOST_CHECK_EQUAL(res.size(), u_int16_t(0));
    }

    static void insert(lfid_pfid_map& lfm, lpmgr& lm, test_jrnl& jc, const u_int16_t after_lfid, const u_int16_t incr = 1)
    {
        flist pfidl;
        flist lfidl;
        const u_int16_t num_jfiles = lm.num_jfiles();
        lfm.journal_insert(after_lfid, incr);
        lfm.get_pfid_list(pfidl);
        lfm.get_lfid_list(lfidl);
        lm.insert(after_lfid, &jc, &jc.new_fcntl, incr);
        BOOST_CHECK_EQUAL(lm.num_jfiles(), num_jfiles + incr);
        lpmgr_test_helper::check_pfids_lfids(lm, pfidl, lfidl);
    }

    static void check_ae_max_jfiles(lpmgr& lm, const u_int16_t num_jfiles, const u_int16_t ae_max_jfiles)
    {
        bool legal = ae_max_jfiles > num_jfiles || ae_max_jfiles == 0;

        lm.set_ae(false);
        BOOST_CHECK(!lm.is_ae());
        if (legal)
        {
            lm.set_ae_max_jfiles(ae_max_jfiles);
            BOOST_CHECK_EQUAL(lm.ae_max_jfiles(), ae_max_jfiles);
            lm.set_ae(true);
            BOOST_CHECK(lm.is_ae());
            BOOST_CHECK_EQUAL(lm.ae_jfiles_rem(), ae_max_jfiles
                                                  ? ae_max_jfiles - num_jfiles
                                                  : JRNL_MAX_NUM_FILES - num_jfiles);
        }
        else
        {
            lm.set_ae_max_jfiles(ae_max_jfiles);
            BOOST_CHECK_EQUAL(lm.ae_max_jfiles(), ae_max_jfiles);
            try
            {
                lm.set_ae(true); // should raise exception
                BOOST_ERROR("Auto-expand enabled with out-of-range ae_max_jfiles");
            }
            catch (const jexception& e) { BOOST_CHECK_EQUAL(e.err_code(), jerrno::JERR_LFMGR_BADAEFNUMLIM); }
            BOOST_CHECK(!lm.is_ae());
            BOOST_CHECK_EQUAL(lm.ae_jfiles_rem(), 0);
        }
        BOOST_CHECK_EQUAL(lm.ae_max_jfiles(), ae_max_jfiles);
    }

    static void check_multiple_initialization_recover(lfid_pfid_map& lfm, test_jrnl& jc,
            const u_int16_t num_jfiles_arr[][2], const bool init_flag_0, const bool finalize_flag,
            const bool init_flag_1)
    {
        unsigned i_njf = 0;
        while (num_jfiles_arr[i_njf][0] && num_jfiles_arr[i_njf][1]) // cycle through each entry in num_jfiles_arr
        {
            for (unsigned i1_njf = 0; i1_njf <= 1; i1_njf++) // cycle through the two numbers in each entry of num_jfiles_arr
            {
                const u_int16_t num_jfiles_0 = num_jfiles_arr[i_njf][i1_njf == 0]; // first number in pair
                const u_int16_t num_jfiles_1 = num_jfiles_arr[i_njf][i1_njf != 0]; // second number in pair

                for (unsigned i_ae = 0; i_ae < 4; i_ae++) // cycle through combinations of enabling AE
                {
                    const bool ae_0 = i_ae & 0x1; // first bit: enable AE on first init
                    const bool ae_1 = i_ae & 0x2; // second bit: enable AE on second init
                    for (unsigned i_aemjf = 0; i_aemjf < 4; i_aemjf++) // cycle through combinations of enabling/disabling ae limit
                    {
                        const u_int16_t ae_max_jfiles_0 = i_aemjf & 0x1 ? 3 * num_jfiles_0 : 0; // max ae files, 0 = disable max
                        const u_int16_t ae_max_jfiles_1 = i_aemjf & 0x2 ? 4 * num_jfiles_1 : 0; // max ae files, 0 = disable max

                        lpmgr lm; // DUT

                        if (init_flag_0)
                            initialize(lm, jc, num_jfiles_0, ae_0, ae_max_jfiles_0);
                        else
                        {
                            prepare_recover(lfm, num_jfiles_0);
                            recover(lfm, lm, jc, ae_1, ae_max_jfiles_0);
                            lfm.destroy_journal();
                        }

                        if (finalize_flag) finalize(lm);

                        if (init_flag_1)
                            initialize(lm, jc, num_jfiles_1, ae_1, ae_max_jfiles_1);
                        else
                        {
                            prepare_recover(lfm, num_jfiles_1);
                            recover(lfm, lm, jc, ae_1, ae_max_jfiles_1);
                            lfm.destroy_journal();
                        }
                    }
                }
            }
            i_njf++;
        }
    }

    static void check_insert(lfid_pfid_map& lfm, lpmgr& lm, test_jrnl& jc, const u_int16_t after_lfid,
            const u_int16_t incr = 1)
    {
        const u_int16_t num_jfiles = lm.num_jfiles();
        const u_int16_t ae_max_jfiles = lm.ae_max_jfiles();
        const u_int16_t effective_ae_max_jfiles = ae_max_jfiles ? ae_max_jfiles : JRNL_MAX_NUM_FILES;
        BOOST_CHECK_EQUAL(lm.ae_jfiles_rem(), effective_ae_max_jfiles - num_jfiles);
        bool legal = lm.is_ae() && num_jfiles + incr <= effective_ae_max_jfiles;
        if (legal)
        {
            insert(lfm, lm, jc, after_lfid, incr);
            BOOST_CHECK_EQUAL(lm.num_jfiles(), num_jfiles + incr);
            BOOST_CHECK_EQUAL(lm.ae_jfiles_rem(), effective_ae_max_jfiles - num_jfiles - incr);
        }
        else
        {
            try
            {
                insert(lfm, lm, jc, after_lfid, incr);
                if (lm.is_ae())
                    BOOST_ERROR("lpmgr::insert() succeeded and exceeded limit");
                else
                    BOOST_ERROR("lpmgr::insert() succeeded with auto-expand disabled");
            }
            catch (const jexception& e)
            {
                if (lm.is_ae())
                    BOOST_CHECK_EQUAL(e.err_code(), jerrno::JERR_LFMGR_AEFNUMLIMIT);
                else
                    BOOST_CHECK_EQUAL(e.err_code(), jerrno::JERR_LFMGR_AEDISABLED);
            }
            BOOST_CHECK_EQUAL(lm.num_jfiles(), num_jfiles);
            BOOST_CHECK_EQUAL(lm.ae_jfiles_rem(), effective_ae_max_jfiles - num_jfiles);
        }
    }

    static void check_limit(lfid_pfid_map& lfm, test_jrnl& jc, const bool ae, const u_int16_t num_jfiles,
            const u_int16_t ae_max_jfiles)
    {
        lpmgr lm;

        for (unsigned i = 0; i < 2; i++)
        {
            if (i)
                initialize(lfm, lm, jc, num_jfiles, ae, ae_max_jfiles);
            else
            {
                prepare_recover(lfm, num_jfiles);
                recover(lfm, lm, jc, ae, ae_max_jfiles);
            }

            // use up all available files
            unsigned j = ae_max_jfiles ? ae_max_jfiles : JRNL_MAX_NUM_FILES;
            while (ae && j > num_jfiles)
            {
                const u_int16_t posn = static_cast<u_int16_t>((lm.num_jfiles() - 1) * ::drand48());
                const u_int16_t incr = 1 + static_cast<u_int16_t>((lm.ae_jfiles_rem() > 4
                        ? 3 : lm.ae_jfiles_rem() - 1) * ::drand48());
                check_insert(lfm, lm, jc, posn, incr);
                j -= incr;
            }
            // these should be over the limit or illegal
            check_insert(lfm, lm, jc, 0);
            check_insert(lfm, lm, jc, 2, 2);
            lfm.destroy_journal();
        }
    }

private:
    static void load_vector(const u_int16_t a[], const size_t n, flist& v)
    {
        for (size_t i = 0; i < n; i++)
            v.push_back(a[i]);
    }

    static void load_vector(const flist& a, flist& b)
    {
        for (flist_citr i = a.begin(); i < a.end(); i++)
            b.push_back(*i);
    }

    static void vectors_equal(const lpmgr& lm, const u_int16_t a[], const size_t n, const flist& b,
            const bool pfid_check)
    {
        BOOST_CHECK_EQUAL(n, b.size());
        for (size_t i = 0; i < n; i++)
        {
            BOOST_CHECK_EQUAL(a[i], b[i]);
            fcntl* fp = lm.get_fcntlp(i);
            BOOST_CHECK_MESSAGE(fp != (void*)0, "Unexpected void pointer returned by lpmgr::get_fcntlp()");
            if (fp) BOOST_CHECK_EQUAL(pfid_check ? fp->pfid() : fp->lfid(), pfid_check ? a[i] : i);
        }
    }

    static void vectors_equal(const lpmgr& lm, const flist& a, const flist& b, const bool pfid_check)
    {
        BOOST_CHECK_EQUAL(a.size(), b.size());
        for (size_t i = 0; i < a.size(); i++)
        {
            BOOST_CHECK_EQUAL(a[i], b[i]);
            fcntl* fp = lm.get_fcntlp(i);
            BOOST_CHECK_MESSAGE(fp != (void*)0, "Unexpected void pointer returned by lpmgr::get_fcntlp()");
            if (fp) BOOST_CHECK_EQUAL(pfid_check ? fp->pfid() : fp->lfid(), pfid_check ? a[i] : i);
        }
    }

    static void linear_vectors_equal(const lpmgr& lm, const size_t n, const flist& f, const bool pfid_check)
    {
        BOOST_CHECK_EQUAL(n, f.size());
        for (size_t i = 0; i < n; i++)
        {
            BOOST_CHECK_EQUAL(i, f[i]);
            fcntl* fp = lm.get_fcntlp(i);
            BOOST_CHECK_MESSAGE(fp != (void*)0, "Unexpected void pointer returned by lpmgr::get_fcntlp()");
            if (fp) BOOST_CHECK_EQUAL(pfid_check ? fp->pfid() : fp->lfid(), i);
        }
    }
};

// === Tests ===

#ifndef LONG_TEST
/*
 * ==============================================
 *                  NORMAL TESTS
 * This section contains normal "make check" tests
 * for building/packaging. These are built when
 * LONG_TEST is _not_ defined.
 * ==============================================
 */

/*
 * Check that after construction, the fcntl array _fcntl_arr is empty and the is_init() function returns false.
 */
QPID_AUTO_TEST_CASE(default_constructor)
{
    string test_name = get_test_name(test_filename, "default_constructor");
    try
    {
        lpmgr lm;
        BOOST_CHECK_EQUAL(lm.is_init(), false);
        BOOST_CHECK_EQUAL(lm.is_ae(), false);
        BOOST_CHECK_EQUAL(lm.ae_max_jfiles(), u_int16_t(0));
        BOOST_CHECK_EQUAL(lm.num_jfiles(), u_int16_t(0));
        BOOST_CHECK_EQUAL(lm.get_fcntlp(0), (void*)0);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "done" << endl;
}

/*
 * Check that initialize() correctly creates an ordered fcntl array _fcntl_arr.
 */
QPID_AUTO_TEST_CASE(initialize)
{
    string test_name = get_test_name(test_filename, "initialize");
    const u_int16_t num_jfiles = 8;
    try
    {
        jdir::create_dir(test_dir); // Check test dir exists; create it if not
        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        {
            lpmgr lm;
            lpmgr_test_helper::initialize(lm, jc, num_jfiles, false, 0);
        }
        {
            lpmgr lm;
            lpmgr_test_helper::initialize(lm, jc, num_jfiles, true, 0);
        }
        {
            lpmgr lm;
            lpmgr_test_helper::initialize(lm, jc, num_jfiles, true, 5 * num_jfiles);
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "done" << endl;
}

/*
 * Check that recover() correctly sets up the specified pfid list order.
 */
QPID_AUTO_TEST_CASE(recover)
{
    string test_name = get_test_name(test_filename, "recover");
    ::srand48(1); // init random gen for repeatable tests when using lpmgr_test_helper::prepare_recover()
    try
    {
        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        lfid_pfid_map lfm(test_name, test_name);

        {
            lpmgr lm;
            lpmgr_test_helper::prepare_recover(lfm, 8);
            lpmgr_test_helper::recover(lfm, lm, jc, false, 0);
            lfm.destroy_journal();
        }
        {
            lpmgr lm;
            lpmgr_test_helper::prepare_recover(lfm, 8);
            lpmgr_test_helper::recover(lfm, lm, jc, true, 0);
            lfm.destroy_journal();
        }
        {
            lpmgr lm;
            lpmgr_test_helper::prepare_recover(lfm, 8);
            lpmgr_test_helper::recover(lfm, lm, jc, true, 5 * lfm.size());
            lfm.destroy_journal();
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "done" << endl;
}

/*
 * Check that finalize() after an initialize() empties _fcntl_arr and that afterwards is_init() returns false.
 */
QPID_AUTO_TEST_CASE(initialize_finalize)
{
    string test_name = get_test_name(test_filename, "initialize_finalize");
    const u_int16_t num_jfiles = 8;
    try
    {
        jdir::create_dir(test_dir); // Check test dir exists; create it if not
        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        {
            lpmgr lm;
            lpmgr_test_helper::initialize(lm, jc, num_jfiles, false, 0);
            lpmgr_test_helper::finalize(lm);
        }
        {
            lpmgr lm;
            lpmgr_test_helper::initialize(lm, jc, num_jfiles, true, 0);
            lpmgr_test_helper::finalize(lm);
        }
        {
            lpmgr lm;
            lpmgr_test_helper::initialize(lm, jc, num_jfiles, true, 5 * num_jfiles);
            lpmgr_test_helper::finalize(lm);
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "done" << endl;
}

/*
 * Check that finalize() after a recover() empties _fcntl_arr and that afterwards is_init() returns false.
 */
QPID_AUTO_TEST_CASE(recover_finalize)
{
    string test_name = get_test_name(test_filename, "recover_finalize");
    const u_int16_t num_jfiles = 8;
    ::srand48(1); // init random gen for repeatable tests when using lpmgr_test_helper::prepare_recover()
    try
    {
        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        lfid_pfid_map lfm(test_name, test_name);

        {
            lpmgr lm;
            lpmgr_test_helper::prepare_recover(lfm, num_jfiles);
            lpmgr_test_helper::recover(lfm, lm, jc, false, 0);
            lpmgr_test_helper::finalize(lm);
            lfm.destroy_journal();
        }
        {
            lpmgr lm;
            lpmgr_test_helper::prepare_recover(lfm, num_jfiles);
            lpmgr_test_helper::recover(lfm, lm, jc, true, 0);
            lpmgr_test_helper::finalize(lm);
            lfm.destroy_journal();
        }
        {
            lpmgr lm;
            lpmgr_test_helper::prepare_recover(lfm, num_jfiles);
            lpmgr_test_helper::recover(lfm, lm, jc, true, 5 * lfm.size());
            lpmgr_test_helper::finalize(lm);
            lfm.destroy_journal();
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "done" << endl;
}

/*
 * Check that 0 and/or null and other extreme/boundary parameters behave as expected.
 */
QPID_AUTO_TEST_CASE(zero_null_params)
{
    string test_name = get_test_name(test_filename, "zero_null_params");
    const u_int16_t num_jfiles = 8;
    try
    {
        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        lfid_pfid_map lfm(test_name, test_name);
        lpmgr lm;
        lpmgr_test_helper::initialize(lfm, lm, jc, num_jfiles, true, 0);

        // Check that inserting 0 files works ok
        lpmgr_test_helper::insert(lfm, lm, jc, 0, 0);
        lpmgr_test_helper::insert(lfm, lm, jc, 2, 0);
        lpmgr_test_helper::insert(lfm, lm, jc, num_jfiles - 1, 0);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "done" << endl;
}

/*
 * Check that initialize()/recover() works correctly after a previous initialize()/recover() with/without an intervening
 * finalize().
 */
QPID_AUTO_TEST_CASE(multiple_initialization_recover)
{
    string test_name = get_test_name(test_filename, "multiple_initialization_recover");
    ::srand48(1); // init random gen for repeatable tests when using lpmgr_test_helper::prepare_recover()

    // Set combinations of value pairs to be used for number of journal files in first and second init
    u_int16_t num_jfiles_arr[][2] = {{8, 12}, {4, 7}, {0, 0}}; // end with zeros
    try
    {
        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        lfid_pfid_map lfm(test_name, test_name);
        for (unsigned p = 0; p < 8; p++)
        {
            const bool i_0 = p & 0x01; // first bit
            const bool i_1 = p & 0x02; // second bit
            const bool f = p & 0x04;   // third bit
            lpmgr_test_helper::check_multiple_initialization_recover(lfm, jc, num_jfiles_arr, i_0, f, i_1);
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "done" << endl;
}

/*
 * Check that insert() works correctly after initialize() and shifts the pfid sequence beyond the insert point correctly:
 *
 * The following sequence is tested:
 * initialize 4            pfids=[0,1,2,3]           lfids=[0,1,2,3]
 * insert 1 after lfid 0   pfids=[0,4,1,2,3]         lfids=[0,2,3,4,1]
 * insert 2 after lfid 2   pfids=[0,4,1,5,6,2,3]     lfids=[0,2,5,6,1,3,4]
 * insert 1 after lfid 6   pfids=[0,4,1,5,6,2,3,7]   lfids=[0,2,5,6,1,3,4,7]
 * issert 1 after lfid 3   pfids=[0,4,1,5,8,6,2,3,7] lfids=[0,2,6,7,1,3,5,8,4]
 */
QPID_AUTO_TEST_CASE(initialize_insert)
{
    string test_name = get_test_name(test_filename, "initialize_insert");
    const u_int16_t initial_num_jfiles = 8;
    try
    {
        jdir::create_dir(test_dir); // Check test dir exists; create it if not
        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        lfid_pfid_map lfm(test_name, test_name);
        lpmgr lm;
        lpmgr_test_helper::initialize(lfm, lm, jc, initial_num_jfiles, true, 0);

        lpmgr_test_helper::insert(lfm, lm, jc, 0);
        lpmgr_test_helper::insert(lfm, lm, jc, 2, 2);
        lpmgr_test_helper::insert(lfm, lm, jc, 6);
        lpmgr_test_helper::insert(lfm, lm, jc, 3);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "done" << endl;
}

/*
 * Check that insert() works correctly after recover() and shifts the pfid sequence beyond the insert point correctly:
 *
 * The following sequence is tested:
 * recover 4               pfids=[0,2,3,1]           lfids=[0,3,1,2]
 * insert 1 after lfid 0   pfids=[0,4,2,3,1]         lfids=[0,4,2,3,1]
 * insert 2 after lfid 2   pfids=[0,4,2,5,6,3,1]     lfids=[0,6,2,5,1,3,4]
 * insert 1 after lfid 6   pfids=[0,4,2,5,6,3,1,7]   lfids=[0,6,2,5,1,3,4,7]
 * issert 1 after lfid 3   pfids=[0,4,2,5,8,6,3,1,7] lfids=[0,7,2,6,1,3,5,8,4]
 */
QPID_AUTO_TEST_CASE(recover_insert)
{
    string test_name = get_test_name(test_filename, "recover_insert");
    const u_int16_t initial_num_jfiles = 4;
    ::srand48(1); // init random gen for repeatable tests when using lpmgr_test_helper::prepare_recover()
    try
    {
        jdir::create_dir(test_dir); // Check test dir exists; create it if not
        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        lfid_pfid_map lfm(test_name, test_name);
        lpmgr lm;
        lpmgr_test_helper::prepare_recover(lfm, initial_num_jfiles);
        lpmgr_test_helper::recover(lfm, lm, jc, true, 0);

        lpmgr_test_helper::insert(lfm, lm, jc, 0);
        lpmgr_test_helper::insert(lfm, lm, jc, 2, 2);
        lpmgr_test_helper::insert(lfm, lm, jc, 6);
        lpmgr_test_helper::insert(lfm, lm, jc, 3);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "done" << endl;
}

/*
 * Check that illegal ae parameter combinations are caught and result in an exception being thrown.
 */
QPID_AUTO_TEST_CASE(ae_parameters)
{
    string test_name = get_test_name(test_filename, "ae_parameters");
    ::srand48(1); // init random gen for repeatable tests when using lpmgr_test_helper::prepare_recover()
    try
    {
        jdir::create_dir(test_dir); // Check test dir exists; create it if not
        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        lfid_pfid_map lfm(test_name, test_name);
        const u_int16_t num_jfiles = 8;
        lpmgr lm;

        for (unsigned i = 0; i < 2; i++)
        {
            if (i)
                lpmgr_test_helper::initialize(lfm, lm, jc, num_jfiles, false, 0);
            else
            {
                lpmgr_test_helper::prepare_recover(lfm, num_jfiles);
                lpmgr_test_helper::recover(lfm, lm, jc, false, 0);
            }

            lpmgr_test_helper::check_ae_max_jfiles(lm, num_jfiles, num_jfiles - 2);
            lpmgr_test_helper::check_ae_max_jfiles(lm, num_jfiles, 0);
            lpmgr_test_helper::check_ae_max_jfiles(lm, num_jfiles, 2 * num_jfiles);
            lpmgr_test_helper::check_ae_max_jfiles(lm, num_jfiles, num_jfiles);
            lfm.destroy_journal();
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "done" << endl;
}

/*
 * Check that initialized or recovered journals with auto-expand disabled will not allow either inserts or appends.
 */
QPID_AUTO_TEST_CASE(ae_disabled)
{
    string test_name = get_test_name(test_filename, "ae_disabled");
    ::srand48(1); // init random gen for repeatable tests when using lpmgr_test_helper::prepare_recover()
    try
    {
        jdir::create_dir(test_dir); // Check test dir exists; create it if not
        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        lfid_pfid_map lfm(test_name, test_name);
        lpmgr_test_helper::check_limit(lfm, jc, false, 8, 0);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "done" << endl;
}

/*
 * Check that initialized or recovered journals with auto-expand enabled and a file limit set will enforce the correct
 * limits on inserts and appends.
 */
QPID_AUTO_TEST_CASE(ae_enabled_limit)
{
    string test_name = get_test_name(test_filename, "ae_enabled_limit");
    ::srand48(1); // init random gen for repeatable tests when using lpmgr_test_helper::prepare_recover()
    try
    {
        jdir::create_dir(test_dir); // Check test dir exists; create it if not
        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        lfid_pfid_map lfm(test_name, test_name);
        lpmgr_test_helper::check_limit(lfm, jc, true, 8, 32);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "done" << endl;
}

/*
 * Check that initialized or recovered journals with auto-expand enabled and no file limit set (0) will allow inserts and
 * appends up to the file limit JRNL_MAX_NUM_FILES.
 */
QPID_AUTO_TEST_CASE(ae_enabled_unlimited)
{
    string test_name = get_test_name(test_filename, "ae_enabled_unlimited");
    ::srand48(1); // init random gen for repeatable tests when using lpmgr_test_helper::prepare_recover()
    try
    {
        jdir::create_dir(test_dir); // Check test dir exists; create it if not
        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        lfid_pfid_map lfm(test_name, test_name);
        lpmgr_test_helper::check_limit(lfm, jc, true, 8, 0);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "done" << endl;
}

#else
/*
 * ==============================================
 *                  LONG TESTS
 * This section contains long tests and soak tests,
 * and are run using target check-long (ie "make
 * check-long"). These are built when LONG_TEST is
 * defined.
 * ==============================================
 */

/*
 * Tests randomized combinations of initialization/recovery, initial size, number, size and location of inserts.
 *
 * To reproduce a specific test, comment out the get_seed() statement and uncomment the literal below, adjusting the seed
 * value to that required.
 */
QPID_AUTO_TEST_CASE(randomized_tests)
{
    string test_name = get_test_name(test_filename, "randomized_tests");
    const long seed = get_seed();
    // const long seed = 0x2d9b69d32;
    cout << "seed=0x" << hex << seed << dec << " " << flush;
    ::srand48(seed);

    lfid_pfid_map lfm(test_name, test_name);
    flist pfidl;
    flist lfidl;
    rcvdat rd;
    u_int16_t curr_ae_max_jfiles = 0;
    jdir::create_dir(test_dir); // Check test dir exists; create it if not

    for (int test_num = 0; test_num < 250; test_num++)
    {
        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        lpmgr lm;
        // 50% chance of recovery except first run and if there is still ae space left
        const bool recover_flag = test_num > 0 &&
                                  curr_ae_max_jfiles > lfm.size() &&
                                  2.0 * ::drand48() < 1.0;
        if (recover_flag)
        {
            // Recover from previous iteration
            lfm.get_pfid_list(pfidl);
            lfm.get_lfid_list(lfidl);
            lfm.write_journal(true, curr_ae_max_jfiles, JFSIZE_SBLKS);
            lpmgr_test_helper::rcvdat_init(rd, pfidl, true, curr_ae_max_jfiles);
            lm.recover(rd, &jc, &jc.new_fcntl);
            lpmgr_test_helper::check_pfids_lfids(lm, pfidl, lfidl);
        }
        else
        {
            // Initialize from scratch
            const u_int16_t num_jfiles = 4 + u_int16_t(21.0 * ::drand48()); // size: 4 - 25 files
            curr_ae_max_jfiles = u_int16_t(4 * num_jfiles * ::drand48()); // size: 0 - 100 files
            if (curr_ae_max_jfiles > JRNL_MAX_NUM_FILES) curr_ae_max_jfiles = JRNL_MAX_NUM_FILES;
            else if (curr_ae_max_jfiles <= num_jfiles) curr_ae_max_jfiles = 0;
            lfm.destroy_journal();
            lfm.journal_create(num_jfiles, num_jfiles);
            lfm.get_pfid_list(pfidl);
            lfm.get_lfid_list(lfidl);
            lm.initialize(num_jfiles, true, curr_ae_max_jfiles, &jc, &jc.new_fcntl);
            lpmgr_test_helper::check_linear_pfids_lfids(lm, num_jfiles);
        }

        // Loop to insert pfids
        const int num_inserts = 1 + int(lfm.size() * ::drand48());
        for (int i = 0; i < num_inserts; i++)
        {
            const u_int16_t size = lm.num_jfiles();
            const u_int16_t after_lfid = u_int16_t(1.0 * size * ::drand48());
            const u_int16_t num_jfiles = 1 + u_int16_t(4.0 * ::drand48());
            const bool legal = lm.ae_max_jfiles()
                               ? size + num_jfiles <= lm.ae_max_jfiles()
                               : size + num_jfiles <= JRNL_MAX_NUM_FILES;
            if (legal)
            {
                lfm.journal_insert(after_lfid, num_jfiles);
                lfm.get_pfid_list(pfidl);
                lfm.get_lfid_list(lfidl);

                lm.insert(after_lfid, &jc, &jc.new_fcntl, num_jfiles);
                lpmgr_test_helper::check_pfids_lfids(lm, pfidl, lfidl);
            }
            else
            {
                try
                {
                    lm.insert(after_lfid, &jc, &jc.new_fcntl, num_jfiles);
                    BOOST_FAIL("lpmgr::insert() succeeded and exceeded limit");
                }
                catch (const jexception& e)
                {
                    BOOST_CHECK_EQUAL(e.err_code(), jerrno::JERR_LFMGR_AEFNUMLIMIT);
                    break; // no more inserts...
                }
            }
        }
        lm.finalize();
        BOOST_CHECK_EQUAL(lm.is_init(), false);
        BOOST_CHECK_EQUAL(lm.num_jfiles(), u_int16_t(0));
        BOOST_CHECK_EQUAL(lm.get_fcntlp(0), (void*)0);
    }
    cout << "done" << endl;
}

#endif

QPID_AUTO_TEST_SUITE_END()
