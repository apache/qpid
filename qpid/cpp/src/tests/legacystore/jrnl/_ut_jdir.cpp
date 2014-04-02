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

#include <cerrno>
#include <cstring>
#include <dirent.h>
#include <fstream>
#include <iomanip>
#include <iostream>
#include "qpid/legacystore/jrnl/file_hdr.h"
#include "qpid/legacystore/jrnl/jcfg.h"
#include "qpid/legacystore/jrnl/jdir.h"
#include "qpid/legacystore/jrnl/jerrno.h"
#include "qpid/legacystore/jrnl/jexception.h"
#include <sys/stat.h>

#define NUM_JFILES 4
#define JFSIZE_SBLKS 128

#define ERRORSTR(e) std::strerror(e) << " (" << e << ")"
#define NUM_CLEAR_OPS 20

using namespace boost::unit_test;
using namespace mrg::journal;
using namespace std;

QPID_AUTO_TEST_SUITE(jdir_suite)

const string test_filename("_ut_jdir");
const char* tdp = getenv("TMP_DATA_DIR");
const string test_dir(tdp && strlen(tdp) > 0 ? string(tdp) + "/_ut_jdir" : "/var/tmp/_ut_jdir");

// === Helper functions ===

void create_file(const char* filename, mode_t fmode = S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH)
{
    ofstream of(filename, ofstream::out | ofstream::trunc);
    if (!of.good())
        BOOST_FAIL("Unable to open file " << filename << " for writing.");
    of.write(filename, std::strlen(filename));
    of.close();
    ::chmod(filename, fmode);
}

void create_file(const string filename, mode_t fmode = S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH)
{
    create_file(filename.c_str(), fmode);
}

void create_jdat_file(const char* dirname, const char* base_filename, u_int32_t fid,
        u_int64_t first_rid)
{
    stringstream fn;
    fn << dirname << "/" << base_filename << ".";
    fn << setfill('0') << hex << setw(4) << fid << ".jdat";
    file_hdr fh(RHM_JDAT_FILE_MAGIC, RHM_JDAT_VERSION, 0, first_rid, fid, 0x200, true);
    ofstream of(fn.str().c_str(), ofstream::out | ofstream::trunc);
    if (!of.good())
        BOOST_FAIL("Unable to open journal data file " << fn.str() << " for writing.");
    of.write((const char*)&fh, sizeof(file_hdr));
    of.close();
}

void create_jinf_file(const char* dirname, const char* base_filename)
{
    timespec ts;
    ::clock_gettime(CLOCK_REALTIME, &ts);
    jinf ji("test journal id", dirname, base_filename, NUM_JFILES, false, 0, JFSIZE_SBLKS,
            JRNL_WMGR_DEF_PAGE_SIZE, JRNL_WMGR_DEF_PAGES, ts);
    ji.write();
}

void create_jrnl_fileset(const char* dirname, const char* base_filename)
{
    create_jinf_file(dirname, base_filename);
    for (u_int32_t fid = 0; fid < NUM_JFILES; fid++)
    {
        u_int64_t rid = 0x12340000 + (fid * 0x25);
        create_jdat_file(dirname, base_filename, fid, rid);
    }
}

unsigned count_dir_contents(const char* dirname, bool incl_files, bool incl_dirs = true)
{
    struct dirent* entry;
    struct stat s;
    unsigned file_cnt = 0;
    unsigned dir_cnt = 0;
    unsigned other_cnt = 0;
    DIR* dir = ::opendir(dirname);
    if (!dir)
        BOOST_FAIL("Unable to open directory " << dirname);
    while ((entry = ::readdir(dir)) != NULL)
    {
        // Ignore . and ..
        if (std::strcmp(entry->d_name, ".") != 0 && std::strcmp(entry->d_name, "..") != 0)
        {
            stringstream fn;
            fn << dirname << "/" << entry->d_name;
            if (::stat(fn.str().c_str(), &s))
                BOOST_FAIL("Unable to stat dir entry " << entry->d_name << "; err=" <<
                        ERRORSTR(errno));
            if (S_ISREG(s.st_mode))
                file_cnt++;
            else if (S_ISDIR(s.st_mode))
                dir_cnt++;
            else
                other_cnt++;
        }
    }
    ::closedir(dir);
    if (incl_files)
    {
        if (incl_dirs)
            return file_cnt + dir_cnt;
        return file_cnt;
    }
    else if (incl_dirs)
        return dir_cnt;
    return other_cnt;
}

void check_dir_contents(const char* dirname, const char* base_filename, unsigned num_subdirs,
        bool jrnl_present)
{
    if (jdir::is_dir(dirname))
    {
        // Subdir count
        BOOST_CHECK_EQUAL(count_dir_contents(dirname, false, true), num_subdirs);

        // Journal file count
        unsigned num_jrnl_files = jrnl_present ? NUM_JFILES + 1 : 0;
        BOOST_CHECK_EQUAL(count_dir_contents(dirname, true, false), num_jrnl_files);

        // Check journal files are present
        if (jrnl_present)
            try { jdir::verify_dir(dirname, base_filename); }
            catch(const jexception& e) { BOOST_ERROR(e); }
        for (unsigned subdir_num = 1; subdir_num <= num_subdirs; subdir_num++)
        {
            stringstream subdir_name;
            subdir_name << dirname << "/_" << base_filename << ".bak.";
            subdir_name << hex << setfill('0') << setw(4) << subdir_num;
            try { jdir::verify_dir(subdir_name.str().c_str(), base_filename); }
            catch(const jexception& e) { BOOST_ERROR(e); }
        }
    }
    else
        BOOST_ERROR(dirname << " is not a directory");
}

void check_dir_not_existing(const char* dirname)
{
    if (jdir::exists(dirname) && jdir::is_dir(dirname))
        jdir::delete_dir(dirname);
    if (jdir::exists(dirname))
        BOOST_FAIL("Unable to remove directory " << dirname);
}

void check_dir_not_existing(const string dirname)
{
    check_dir_not_existing(dirname.c_str());
}

// === Test suite ===

QPID_AUTO_TEST_CASE(constructor)
{
    cout << test_filename << ".constructor: " << flush;
    string dir(test_dir + "/A/B/C/D/E/F");
    string bfn("test_base");
    jdir dir1(dir, bfn);
    BOOST_CHECK(dir1.dirname().compare(dir) == 0);
    BOOST_CHECK(dir1.base_filename().compare(bfn) == 0);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(create_delete_dir)
{
    cout << test_filename << ".create_delete_dir: " << flush;
    // Use instance
    string dir_A(test_dir + "/A");
    string dir_Ats(test_dir + "/A/"); // trailing '/'
    check_dir_not_existing(test_dir + "/A");
    jdir dir1(dir_A, "test_base");
    dir1.create_dir();
    // check all combos of jdir::exists and jdir::is_dir()
    BOOST_CHECK(jdir::exists(dir_A));
    BOOST_CHECK(jdir::exists(dir_Ats));
    BOOST_CHECK(jdir::exists(dir_A.c_str()));
    BOOST_CHECK(jdir::exists(dir_Ats.c_str()));
    BOOST_CHECK(jdir::is_dir(dir_A));
    BOOST_CHECK(jdir::is_dir(dir_Ats));
    BOOST_CHECK(jdir::is_dir(dir_Ats.c_str()));
    BOOST_CHECK(jdir::is_dir(dir_Ats.c_str()));
    // do it a second time when dir exists
    dir1.create_dir();
    BOOST_CHECK(jdir::is_dir(dir_A));
    dir1.delete_dir();
    BOOST_CHECK(!jdir::exists(dir_A));

    // Use static fn
    check_dir_not_existing(test_dir + "/B");
    jdir::create_dir(test_dir + "/B");
    BOOST_CHECK(jdir::is_dir(test_dir + "/B"));
    jdir::create_dir(test_dir + "/B");
    BOOST_CHECK(jdir::is_dir(test_dir + "/B"));
    jdir::delete_dir(test_dir + "/B");
    BOOST_CHECK(!jdir::exists(test_dir + "/B"));

    // Non-empty dirs
    check_dir_not_existing(test_dir + "/C");
    jdir::create_dir(test_dir + "/C");
    BOOST_CHECK(jdir::is_dir(test_dir + "/C"));
    create_file(test_dir + "/C/test_file_1.txt"); // mode 644 (default)
    create_file(test_dir + "/C/test_file_2.txt", S_IRWXU | S_IRWXG | S_IRWXO); // mode 777
    create_file(test_dir + "/C/test_file_3.txt", S_IRUSR | S_IRGRP | S_IROTH); // mode 444 (read-only)
    create_file(test_dir + "/C/test_file_4.txt", 0); // mode 000 (no permissions)
    BOOST_CHECK(jdir::is_dir(test_dir + "/C"));
    jdir::create_dir(test_dir + "/C");
    BOOST_CHECK(jdir::is_dir(test_dir + "/C"));
    jdir::delete_dir(test_dir + "/C");
    BOOST_CHECK(!jdir::exists(test_dir + "/C"));

    // Check non-existent dirs fail
    check_dir_not_existing(test_dir + "/D");
    try
    {
        jdir::is_dir(test_dir + "/D");
        BOOST_ERROR("jdir::is_dir() failed to throw jexeption for non-existent directory.");
    }
    catch(const jexception& e)
    {
        BOOST_CHECK_EQUAL(e.err_code(), jerrno::JERR_JDIR_STAT);
    }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(create_delete_dir_recursive)
{
    cout << test_filename << ".create_delete_dir_recursive: " << flush;
    // Use instances
    check_dir_not_existing(test_dir + "/E");
    jdir dir1(test_dir + "/E/F/G/H", "test_base");
    dir1.create_dir();
    BOOST_CHECK(jdir::is_dir(test_dir + "/E/F/G/H"));
    dir1.delete_dir();
    BOOST_CHECK(!jdir::exists(test_dir + "/E/F/G/H")); // only H deleted, E/F/G remain
    BOOST_CHECK(jdir::exists(test_dir + "/E/F/G"));
    jdir::delete_dir(test_dir + "/E"); // delete remaining dirs
    BOOST_CHECK(!jdir::exists(test_dir + "/E"));

    check_dir_not_existing(test_dir + "/F");
    jdir dir2(test_dir + "/F/G/H/I/", "test_base"); // trailing '/'
    dir2.create_dir();
    BOOST_CHECK(jdir::is_dir(test_dir + "/F/G/H/I/"));
    dir2.delete_dir();
    BOOST_CHECK(!jdir::exists(test_dir + "/F/G/H/I/"));
    BOOST_CHECK(jdir::exists(test_dir + "/F/G/H/"));
    jdir::delete_dir(test_dir + "/F");
    BOOST_CHECK(!jdir::exists(test_dir + "/F"));

    check_dir_not_existing(test_dir + "/G");
    jdir dir3(test_dir + "/G/H//I//J", "test_base"); // extra '/' in path
    dir3.create_dir();
    BOOST_CHECK(jdir::is_dir(test_dir + "/G/H//I//J"));
    dir3.delete_dir();
    BOOST_CHECK(!jdir::exists(test_dir + "/G/H//I//J"));
    BOOST_CHECK(jdir::exists(test_dir + "/G/H//I"));
    jdir::delete_dir(test_dir + "/F");
    BOOST_CHECK(!jdir::exists(test_dir + "/F"));

    // Use static fn
    check_dir_not_existing(test_dir + "/H");
    jdir::create_dir(test_dir + "/H/I/J/K");
    BOOST_CHECK(jdir::is_dir(test_dir + "/H/I/J/K"));
    jdir::delete_dir(test_dir + "/H/I/J/K");
    BOOST_CHECK(!jdir::exists(test_dir + "/H/I/J/K")); // only J deleted, H/I/J remain
    BOOST_CHECK(jdir::exists(test_dir + "/H/I/J"));
    jdir::delete_dir(test_dir + "/H");
    BOOST_CHECK(!jdir::exists(test_dir + "/H"));

    check_dir_not_existing(test_dir + "/I");
    jdir::create_dir(test_dir + "/I/J/K/L/"); // trailing '/'
    BOOST_CHECK(jdir::is_dir(test_dir + "/I/J/K/L/"));
    jdir::delete_dir(test_dir + "/I/J/K/L/");
    BOOST_CHECK(!jdir::exists(test_dir + "/I/J/K/L/"));
    BOOST_CHECK(jdir::exists(test_dir + "/I/J/K/"));
    jdir::delete_dir(test_dir + "/I");
    BOOST_CHECK(!jdir::exists(test_dir + "/I"));

    check_dir_not_existing(test_dir + "//J");
    jdir::create_dir(test_dir + "//J//K//L//M"); // extra '/' in path
    BOOST_CHECK(jdir::is_dir(test_dir + "//J//K//L//M"));
    jdir::delete_dir(test_dir + "//J//K//L//M");
    BOOST_CHECK(!jdir::exists(test_dir + "//J//K//L//M"));
    BOOST_CHECK(jdir::exists(test_dir + "//J//K//L"));
    jdir::delete_dir(test_dir + "//J");
    BOOST_CHECK(!jdir::exists(test_dir + "//J"));

    // Non-empty dirs
    check_dir_not_existing(test_dir + "/K");
    jdir::create_dir(test_dir + "/K/L/M1/N1");
    jdir::create_dir(test_dir + "/K/L/M1/N2");
    jdir::create_dir(test_dir + "/K/L/M1/N3");
    jdir::create_dir(test_dir + "/K/L/M1/N4");
    create_file(test_dir + "/K/L/M1/N4/test_file_1.txt"); // mode 644 (default)
    create_file(test_dir + "/K/L/M1/N4/test_file_2.txt", S_IRWXU | S_IRWXG | S_IRWXO); // mode 777
    create_file(test_dir + "/K/L/M1/N4/test_file_3.txt", S_IRUSR | S_IRGRP | S_IROTH); // mode 444
    create_file(test_dir + "/K/L/M1/N4/test_file_4.txt", 0); // mode 000 (no permissions)
    jdir::create_dir(test_dir + "/K/L/M2");
    jdir::create_dir(test_dir + "/K/L/M3/N5");
    jdir::create_dir(test_dir + "/K/L/M3/N6");
    BOOST_CHECK(jdir::is_dir(test_dir + "/K/L/M1/N1"));
    BOOST_CHECK(jdir::is_dir(test_dir + "/K/L/M1/N2"));
    BOOST_CHECK(jdir::is_dir(test_dir + "/K/L/M1/N3"));
    BOOST_CHECK(jdir::is_dir(test_dir + "/K/L/M1/N4"));
    BOOST_CHECK(jdir::is_dir(test_dir + "/K/L/M2"));
    BOOST_CHECK(jdir::is_dir(test_dir + "/K/L/M3/N5"));
    BOOST_CHECK(jdir::is_dir(test_dir + "/K/L/M3/N6"));
    jdir::delete_dir(test_dir + "/K");
    BOOST_CHECK(!jdir::exists(test_dir + "/K"));
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(clear_verify_dir)
{
    cout << test_filename << ".clear_verify_dir: " << flush;
    // Use instances
    const char* jrnl_dir = "/var/tmp/test_dir_1";
    const char* bfn = "test_base";
    check_dir_not_existing(jrnl_dir);
    jdir test_dir_1(jrnl_dir, bfn);
    test_dir_1.create_dir();
    BOOST_CHECK(jdir::is_dir(jrnl_dir));
    // add journal files, check they exist, then clear them
    unsigned cnt = 0;
    while (cnt < NUM_CLEAR_OPS)
    {
        create_jrnl_fileset(jrnl_dir, bfn);
        check_dir_contents(jrnl_dir, bfn, cnt, true);
        test_dir_1.clear_dir();
        check_dir_contents(jrnl_dir, bfn, ++cnt, false);
    }
    // clean up
    test_dir_1.delete_dir();
    BOOST_CHECK(!jdir::exists(jrnl_dir));

    // Non-existent dir with auto-create true
    jrnl_dir = "/var/tmp/test_dir_2";
    check_dir_not_existing(jrnl_dir);
    jdir test_dir_2(jrnl_dir, bfn);
    // clear dir
    test_dir_2.clear_dir(); // create flag is true by default
    check_dir_contents(jrnl_dir, bfn, 0, false);
    // clear empty dir, should not create subdir
    test_dir_2.clear_dir(); // create flag is true by default
    check_dir_contents(jrnl_dir, bfn, 0, false);
    // clean up
    test_dir_2.delete_dir();
    BOOST_CHECK(!jdir::exists(jrnl_dir));

    // non-existent dir with auto-create false
    jrnl_dir = "/var/tmp/test_dir_3";
    check_dir_not_existing(jrnl_dir);
    jdir test_dir_3(jrnl_dir, bfn);
    try
    {
        test_dir_3.clear_dir(false);
        BOOST_ERROR("jdir::clear_dir(flase) failed to throw jexeption for non-existent directory.");
    }
    catch(const jexception& e)
    {
        BOOST_CHECK_EQUAL(e.err_code(), jerrno::JERR_JDIR_OPENDIR);
    }

    // Use static fn
    jrnl_dir = "/var/tmp/test_dir_4";
    check_dir_not_existing(jrnl_dir);
    jdir::clear_dir(jrnl_dir, bfn); // should create dir if it does not exist
    // add journal files, check they exist, then clear them
    cnt = 0;
    while (cnt < NUM_CLEAR_OPS)
    {
        create_jrnl_fileset(jrnl_dir, bfn);
        check_dir_contents(jrnl_dir, bfn, cnt, true);
        jdir::clear_dir(jrnl_dir, bfn);
        check_dir_contents(jrnl_dir, bfn, ++cnt, false);
    }
    // clean up
    jdir::delete_dir(jrnl_dir);
    BOOST_CHECK(!jdir::exists(jrnl_dir));
    cout << "ok" << endl;
}

QPID_AUTO_TEST_SUITE_END()
