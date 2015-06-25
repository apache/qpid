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

using namespace boost::unit_test;
using namespace mrg::journal;
using namespace std;

QPID_AUTO_TEST_SUITE(jinf_suite)

const string test_filename("_ut_jinf");

#include "_st_helper_fns.h"

timespec ts;

QPID_AUTO_TEST_CASE(write_constructor)
{
    string test_name = get_test_name(test_filename, "write_constructor");
    const string jid = test_name + "_jid";
    const string base_filename = test_name + "_bfn";
    jdir::create_dir(test_dir); // Check test dir exists; create it if not
    ::clock_gettime(CLOCK_REALTIME, &ts);
    jinf ji(jid, test_dir, base_filename, NUM_JFILES, false, 0, JFSIZE_SBLKS, JRNL_WMGR_DEF_PAGE_SIZE, JRNL_WMGR_DEF_PAGES, ts);
    BOOST_CHECK_EQUAL(ji.jver(), RHM_JDAT_VERSION);
    BOOST_CHECK(ji.jid().compare(jid) == 0);
    BOOST_CHECK(ji.jdir().compare(test_dir) == 0);
    BOOST_CHECK(ji.base_filename().compare(base_filename) == 0);
    const timespec this_ts = ji.ts();
    BOOST_CHECK_EQUAL(this_ts.tv_sec, ts.tv_sec);
    BOOST_CHECK_EQUAL(this_ts.tv_nsec, ts.tv_nsec);
    BOOST_CHECK_EQUAL(ji.num_jfiles(), u_int16_t(NUM_JFILES));
    BOOST_CHECK_EQUAL(ji.is_ae(), false);
    BOOST_CHECK_EQUAL(ji.ae_max_jfiles(), u_int16_t(0));
    BOOST_CHECK_EQUAL(ji.jfsize_sblks(), u_int32_t(JFSIZE_SBLKS));
    BOOST_CHECK_EQUAL(ji.sblk_size_dblks(), u_int16_t(JRNL_SBLK_SIZE));
    BOOST_CHECK_EQUAL(ji.dblk_size(), u_int32_t(JRNL_DBLK_SIZE));
    BOOST_CHECK_EQUAL(ji.wcache_pgsize_sblks(), u_int32_t(JRNL_WMGR_DEF_PAGE_SIZE));
    BOOST_CHECK_EQUAL(ji.wcache_num_pages(), u_int16_t(JRNL_WMGR_DEF_PAGES));
    BOOST_CHECK_EQUAL(ji.rcache_pgsize_sblks(), u_int32_t(JRNL_RMGR_PAGE_SIZE));
    BOOST_CHECK_EQUAL(ji.rcache_num_pages(), u_int16_t(JRNL_RMGR_PAGES));
    ji.write();
    cout << "done" << endl;
}

QPID_AUTO_TEST_CASE(read_constructor)
{
    string test_name = get_test_name(test_filename, "read_constructor");
    const string jid = test_name + "_jid";
    const string base_filename = test_name + "_bfn";
    lfid_pfid_map::create_new_jinf(jid, base_filename, false);

    stringstream fn;
    fn << test_dir << "/" <<base_filename  << "." << JRNL_INFO_EXTENSION;
    jinf ji(fn.str(), false);
    BOOST_CHECK_EQUAL(ji.jver(), RHM_JDAT_VERSION);
    BOOST_CHECK(ji.jid().compare(jid) == 0);
    BOOST_CHECK(ji.jdir().compare(test_dir) == 0);
    BOOST_CHECK(ji.base_filename().compare(base_filename) == 0);
//     const timespec this_ts = ji.ts();
//     BOOST_CHECK_EQUAL(this_ts.tv_sec, ts.tv_sec);
//     BOOST_CHECK_EQUAL(this_ts.tv_nsec, ts.tv_nsec);
    BOOST_CHECK_EQUAL(ji.num_jfiles(), u_int16_t(NUM_JFILES));
    BOOST_CHECK_EQUAL(ji.is_ae(), false);
    BOOST_CHECK_EQUAL(ji.ae_max_jfiles(), u_int16_t(0));
    BOOST_CHECK_EQUAL(ji.jfsize_sblks(), u_int32_t(JFSIZE_SBLKS));
    BOOST_CHECK_EQUAL(ji.sblk_size_dblks(), u_int16_t(JRNL_SBLK_SIZE));
    BOOST_CHECK_EQUAL(ji.dblk_size(), u_int32_t(JRNL_DBLK_SIZE));
    BOOST_CHECK_EQUAL(ji.wcache_pgsize_sblks(), u_int32_t(JRNL_WMGR_DEF_PAGE_SIZE));
    BOOST_CHECK_EQUAL(ji.wcache_num_pages(), u_int16_t(JRNL_WMGR_DEF_PAGES));
    BOOST_CHECK_EQUAL(ji.rcache_pgsize_sblks(), u_int32_t(JRNL_RMGR_PAGE_SIZE));
    BOOST_CHECK_EQUAL(ji.rcache_num_pages(), u_int16_t(JRNL_RMGR_PAGES));

    cout << "done" << endl;
}

QPID_AUTO_TEST_CASE(set_functions)
{
    string test_name = get_test_name(test_filename, "set_functions");
    const string jid = test_name + "_jid";
    const string base_filename = test_name + "_bfn";
    lfid_pfid_map::create_new_jinf(jid, base_filename, false);

    stringstream fn;
    fn << test_dir << "/" << base_filename << "." << JRNL_INFO_EXTENSION;
    jinf ji(fn.str(), false);

    ji.set_jdir("abc123");
    BOOST_CHECK(ji.jdir().compare("abc123") == 0);
    ji.set_jdir(test_dir);
    BOOST_CHECK(ji.jdir().compare(test_dir) == 0);
    ji.incr_num_jfiles();
    BOOST_CHECK_EQUAL(ji.num_jfiles(), u_int16_t(NUM_JFILES+1));
    ji.incr_num_jfiles();
    BOOST_CHECK_EQUAL(ji.num_jfiles(), u_int16_t(NUM_JFILES+2));

    lfid_pfid_map::clean_journal_info_file(test_dir);
    cout << "done" << endl;
}

QPID_AUTO_TEST_CASE(validate)
{
    string test_name = get_test_name(test_filename, "validate");
    const string jid = test_name + "_jid";
    const string base_filename = test_name + "_bfn";
    lfid_pfid_map::create_new_jinf(jid, base_filename, false);

    stringstream fn;
    fn << test_dir << "/" << base_filename << "." << JRNL_INFO_EXTENSION;
    jinf ji(fn.str(), true);
    // TODO: Check validation picks up conflict, but need to be friend to jinf to do it

    lfid_pfid_map::clean_journal_info_file(test_dir);
    cout << "done" << endl;
}

QPID_AUTO_TEST_CASE(analyze_empty_journal)
{
    string test_name = get_test_name(test_filename, "analyze_empty_journal");
    const string jid = test_name + "_jid";
    const string base_filename = test_name + "_bfn";
    jdir::create_dir(test_dir); // Check test dir exists; create it if not

    lfid_pfid_map m(jid, base_filename);
    m.journal_create(NUM_JFILES, 0, 0);
    m.write_journal(false, 0);

    stringstream fn;
    fn << test_dir << "/" << base_filename << "." << JRNL_INFO_EXTENSION;
    jinf ji(fn.str(), false);
    try { ji.analyze(); }
    catch (const jexception& e)
    {
        if (e.err_code() != jerrno::JERR_JINF_JDATEMPTY)
            BOOST_ERROR("Failed to throw expected exception jerrno::JERR_JINF_JDATEMPTY");
    }

    m.destroy_journal();
    cout << "done" << endl;
}

QPID_AUTO_TEST_CASE(analyze_part_full_journal)
{
    string test_name = get_test_name(test_filename, "analyze_part_full_journal");
    const string jid = test_name + "_jid";
    const string base_filename = test_name + "_bfn";
    lfid_pfid_map m(jid, base_filename);
    for (u_int16_t num_files = 1; num_files < NUM_JFILES; num_files++)
    {
        m.journal_create(NUM_JFILES, num_files, 0);
        m.write_journal(false, 0);

        stringstream fn;
        fn << test_dir << "/" << base_filename << "." << JRNL_INFO_EXTENSION;
        jinf ji(fn.str(), false);
        ji.analyze();
        m.check_analysis(ji);

        m.destroy_journal();
    }
    cout << "done" << endl;
}

QPID_AUTO_TEST_CASE(analyze_full_journal)
{
    string test_name = get_test_name(test_filename, "analyze_full_journal");
    const string jid = test_name + "_jid";
    const string base_filename = test_name + "_bfn";
    lfid_pfid_map m(jid, base_filename);
    for (u_int16_t file_num = 0; file_num < NUM_JFILES; file_num++)
    {
        m.journal_create(NUM_JFILES, NUM_JFILES, file_num);
        m.write_journal(false, 0);

        stringstream fn;
        fn << test_dir << "/" << base_filename << "." << JRNL_INFO_EXTENSION;
        jinf ji(fn.str(), false);
        ji.analyze();
        m.check_analysis(ji);

        m.destroy_journal();
    }
    cout << "done" << endl;
}

QPID_AUTO_TEST_CASE(analyze_single_appended_journal)
{
    string test_name = get_test_name(test_filename, "analyze_single_appended_journal");
    const string jid = test_name + "_jid";
    const string base_filename = test_name + "_bfn";
    lfid_pfid_map m(jid, base_filename);
    for (u_int16_t oldest_lid = 0; oldest_lid < NUM_JFILES; oldest_lid++)
       for (u_int16_t after_lid = 0; after_lid < NUM_JFILES; after_lid++)
            for (u_int16_t num_files = 1; num_files <= 5; num_files++)
            {
                m.journal_create(NUM_JFILES, NUM_JFILES, oldest_lid);
                m.journal_insert(after_lid, num_files);
                m.write_journal(true, 16);

                stringstream fn;
                fn << test_dir << "/" << base_filename << "." << JRNL_INFO_EXTENSION;
                jinf ji(fn.str(), false);
                ji.analyze();
                m.check_analysis(ji);

                m.destroy_journal();
            }
    cout << "done" << endl;
}

QPID_AUTO_TEST_CASE(analyze_multi_appended_journal)
{
    string test_name = get_test_name(test_filename, "analyze_multi_appended_journal");
    const string jid = test_name + "_jid";
    const string base_filename = test_name + "_bfn";
    lfid_pfid_map m(jid, base_filename);
    ::srand48(1);

    for (u_int16_t num_appends = 1; num_appends <= 2*NUM_JFILES; num_appends++)
    {
        const u_int16_t oldest_lid = u_int16_t(NUM_JFILES * ::drand48());
        m.journal_create(NUM_JFILES, NUM_JFILES, oldest_lid);
        for (u_int16_t a = 0; a < num_appends; a++)
        {
            const u_int16_t num_files = u_int16_t(1 + (NUM_JFILES * ::drand48()));
            const u_int16_t after_lid = u_int16_t(m.size() * ::drand48());
            m.journal_insert(after_lid, num_files);
        }
        m.write_journal(true, 24);

        stringstream fn;
        fn << test_dir << "/" << base_filename << "." << JRNL_INFO_EXTENSION;
        jinf ji(fn.str(), false);
        ji.analyze();
        m.check_analysis(ji);

        m.destroy_journal();
    }

    cout << "done" << endl;
}

QPID_AUTO_TEST_CASE(analyze_multi_appended_then_failed_journal)
{
    string test_name = get_test_name(test_filename, "analyze_multi_appended_then_failed_journal");
    const string jid = test_name + "_jid";
    const string base_filename = test_name + "_bfn";
    lfid_pfid_map m(jid, base_filename);
    ::srand48(1);

    // As this test relies on repeatable but random sequences, use many iterations for coverage
    for (int c = 1; c <= 100; c++)
    {
        for (u_int16_t num_appends = 1; num_appends <= 2*NUM_JFILES; num_appends++)
        {
            u_int16_t oldest_lid = u_int16_t(NUM_JFILES * ::drand48());
            m.journal_create(NUM_JFILES, NUM_JFILES, oldest_lid);
            for (u_int16_t a = 0; a < num_appends-1; a++)
            {
                const u_int16_t num_files = u_int16_t(1 + (NUM_JFILES * ::drand48()));
                const u_int16_t after_lid = u_int16_t(m.size() * ::drand48());
                m.journal_insert(after_lid, num_files);
                if (after_lid < oldest_lid)
                    oldest_lid += num_files;
            }
            const u_int16_t num_files = u_int16_t(1 + (NUM_JFILES * ::drand48()));
            const u_int16_t after_lid = oldest_lid == 0 ? m.size() - 1 : oldest_lid - 1;
            m.journal_insert(after_lid, num_files, false);
            m.write_journal(true, 32);

            stringstream fn;
            fn << test_dir << "/" << base_filename << "." << JRNL_INFO_EXTENSION;
            jinf ji(fn.str(), false);
            ji.analyze();
            m.check_analysis(ji);

            m.destroy_journal();
        }
    }

    cout << "done" << endl;
}

QPID_AUTO_TEST_CASE(analyze_inconsistent_jdat_file_size_in_journal)
{
    string test_name = get_test_name(test_filename, "analyze_inconsistent_jdat_file_size_in_journal");
    const string jid = test_name + "_jid";
    const string base_filename = test_name + "_bfn";
    lfid_pfid_map m(jid, base_filename);
    ::srand48(1);

    for (u_int16_t pfid = 1; pfid < NUM_JFILES; pfid++)
    {
        m.journal_create(NUM_JFILES, NUM_JFILES, 0);
        m.write_journal(false, 0);

        const std::string filename = m.create_journal_filename(pfid, base_filename);
        std::ofstream of(filename.c_str(), ofstream::out | ofstream::app);
        if (!of.good())
            BOOST_FAIL("Unable to open test journal file \"" << filename << "\" for writing.");
        std::size_t expand_size = std::size_t(10 * JRNL_DBLK_SIZE * JRNL_SBLK_SIZE * ::drand48());
        std::vector<char> sblk_buffer(expand_size, 0);
        of.write(&sblk_buffer[0], expand_size);
        of.close();

        stringstream fn;
        fn << test_dir << "/" << base_filename << "." << JRNL_INFO_EXTENSION;
        jinf ji(fn.str(), false);
        try
        {
            ji.analyze();
            BOOST_FAIL("Failed to detect irregular journal file size in file \"" << filename << "\"");
        }
        catch (const jexception& e) {} // ignore - expected

        m.destroy_journal();
    }
    cout << "done" << endl;
}

QPID_AUTO_TEST_CASE(analyze_owi_in_non_ae_journal)
{
    string test_name = get_test_name(test_filename, "analyze_owi_in_non_ae_journal");
    const string jid = test_name + "_jid";
    const string base_filename = test_name + "_bfn";
    lfid_pfid_map m(jid, base_filename);
    for (u_int16_t oldest_file = 1; oldest_file < NUM_DEFAULT_JFILES-1; oldest_file++)
    {
        for (u_int16_t bad_owi_file = oldest_file + 1; bad_owi_file < NUM_DEFAULT_JFILES; bad_owi_file++)
        {
            m.journal_create(NUM_DEFAULT_JFILES, NUM_DEFAULT_JFILES, oldest_file, bad_owi_file);
            m.write_journal(false, 0);

            stringstream fn;
            fn << test_dir << "/" << base_filename << "." << JRNL_INFO_EXTENSION;
            jinf ji(fn.str(), false);
            try
            {
                ji.analyze();
                BOOST_FAIL("Failed to detect irregular OWI flag in non-ae journal file \"" << fn.str() << "\"");
            }
            catch (const jexception& e) {} // ignore - expected

            m.destroy_journal();
        }
    }
    cout << "done" << endl;
}

QPID_AUTO_TEST_CASE(analyze_owi_in_ae_min_size_journal)
{
    string test_name = get_test_name(test_filename, "analyze_owi_in_ae_min_size_journal");
    const string jid = test_name + "_jid";
    const string base_filename = test_name + "_bfn";
    lfid_pfid_map m(jid, base_filename);
    for (u_int16_t oldest_file = 1; oldest_file < NUM_JFILES-1; oldest_file++)
    {
        for (u_int16_t bad_owi_file = oldest_file + 1; bad_owi_file < NUM_JFILES; bad_owi_file++)
        {
            m.journal_create(NUM_JFILES, NUM_JFILES, oldest_file, bad_owi_file);
            m.write_journal(true, 16);

            stringstream fn;
            fn << test_dir << "/" << base_filename << "." << JRNL_INFO_EXTENSION;
            jinf ji(fn.str(), false);
            try
            {
                ji.analyze();
                BOOST_FAIL("Failed to detect irregular OWI flag in min-sized ae journal file \"" << fn.str() << "\"");
            }
            catch (const jexception& e) {} // ignore - expected

            m.destroy_journal();
        }
    }
    cout << "done" << endl;
}

QPID_AUTO_TEST_SUITE_END()
