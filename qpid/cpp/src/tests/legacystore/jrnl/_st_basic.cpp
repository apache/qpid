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

QPID_AUTO_TEST_SUITE(journal_basic)

const string test_filename("_st_basic");

#include "_st_helper_fns.h"

// === Test suite ===

#ifndef LONG_TEST
/*
 * ==============================================
 *                  NORMAL TESTS
 * This section contains normal "make check" tests
 * for building/packaging. These are built when
 * LONG_TEST is _not_ defined.
 * ==============================================
 */

QPID_AUTO_TEST_CASE(instantiation)
{
    string test_name = get_test_name(test_filename, "instantiation");
    try
    {
        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        BOOST_CHECK_EQUAL(jc.is_ready(), false);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(initialization)
{
    string test_name = get_test_name(test_filename, "initialization");
    try
    {
        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        BOOST_CHECK_EQUAL(jc.is_ready(), false);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        BOOST_CHECK_EQUAL(jc.is_ready(), true);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(enqueue_dequeue_block)
{
    string test_name = get_test_name(test_filename, "enqueue_dequeue_block");
    try
    {
        string msg;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        for (int m=0; m<NUM_MSGS; m++)
            BOOST_CHECK_EQUAL(enq_msg(jc, m, create_msg(msg, m, MSG_SIZE), false), u_int64_t(m));
        for (int m=0; m<NUM_MSGS; m++)
            deq_msg(jc, m, m+NUM_MSGS);

        // Again...
        for (int m=2*NUM_MSGS; m<3*NUM_MSGS; m++)
            BOOST_CHECK_EQUAL(enq_msg(jc, m, create_msg(msg, m, MSG_SIZE), false), u_int64_t(m));
        for (int m=2*NUM_MSGS; m<3*NUM_MSGS; m++)
            deq_msg(jc, m, m+3*NUM_MSGS);

        // Disjoint rids
        for (int m=10*NUM_MSGS; m<11*NUM_MSGS; m++)
            BOOST_CHECK_EQUAL(enq_msg(jc, m, create_msg(msg, m, MSG_SIZE), false), u_int64_t(m));
        for (int m=10*NUM_MSGS; m<11*NUM_MSGS; m++)
            deq_msg(jc, m, m+11*NUM_MSGS);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(enqueue_dequeue_interleaved)
{
    string test_name = get_test_name(test_filename, "enqueue_dequeue_interleaved");
    try
    {
        string msg;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        for (int m=0; m<2*NUM_MSGS; m+=2)
        {
            BOOST_CHECK_EQUAL(enq_msg(jc, m, create_msg(msg, m, MSG_SIZE), false), u_int64_t(m));
            deq_msg(jc, m, m+1);
        }

        // Again...
        for (int m=2*NUM_MSGS; m<4*NUM_MSGS; m+=2)
        {
            BOOST_CHECK_EQUAL(enq_msg(jc, m, create_msg(msg, m, MSG_SIZE), false), u_int64_t(m));
            deq_msg(jc, m, m+1);
        }

        // Disjoint rids
        for (int m=10*NUM_MSGS; m<12*NUM_MSGS; m+=2)
        {
            BOOST_CHECK_EQUAL(enq_msg(jc, m, create_msg(msg, m, MSG_SIZE), false), u_int64_t(m));
            deq_msg(jc, m, m+1);
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(enqueue_dequeue_interleaved_file_rollover)
{
    string test_name = get_test_name(test_filename, "enqueue_dequeue_interleaved_file_rollover");
    try
    {
        string msg;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        unsigned n = num_msgs_to_full(NUM_TEST_JFILES, TEST_JFSIZE_SBLKS * JRNL_SBLK_SIZE,
                16*MSG_REC_SIZE_DBLKS, true);
        for (unsigned m=0; m<3*2*n; m+=2) // overwrite files 3 times
        {
            enq_msg(jc, m, create_msg(msg, m, 16*MSG_SIZE), false);
            deq_msg(jc, m, m+1);
        }
        jc.stop(true);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(empty_recover)
{
    string test_name = get_test_name(test_filename, "empty_recover");
    try
    {
        {
            test_jrnl_cb cb;
            test_jrnl jc(test_name, test_dir, test_name, cb);
            BOOST_CHECK_EQUAL(jc.is_ready(), false);
            BOOST_CHECK_EQUAL(jc.is_read_only(), false);
            jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
            BOOST_CHECK_EQUAL(jc.is_ready(), true);
            BOOST_CHECK_EQUAL(jc.is_read_only(), false);
        }
        {
            u_int64_t hrid;

            test_jrnl_cb cb;
            test_jrnl jc(test_name, test_dir, test_name, cb);
            BOOST_CHECK_EQUAL(jc.is_ready(), false);
            BOOST_CHECK_EQUAL(jc.is_read_only(), false);
            jc.recover(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS, 0, hrid);
            BOOST_CHECK_EQUAL(jc.is_ready(), true);
            BOOST_CHECK_EQUAL(jc.is_read_only(), true);
            BOOST_CHECK_EQUAL(hrid, u_int64_t(0));
        }
        {
            u_int64_t hrid;

            test_jrnl_cb cb;
            test_jrnl jc(test_name, test_dir, test_name, cb);
            BOOST_CHECK_EQUAL(jc.is_ready(), false);
            BOOST_CHECK_EQUAL(jc.is_read_only(), false);
            jc.recover(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS, 0, hrid);
            BOOST_CHECK_EQUAL(jc.is_ready(), true);
            BOOST_CHECK_EQUAL(jc.is_read_only(), true);
            BOOST_CHECK_EQUAL(hrid, u_int64_t(0));
            jc.recover_complete();
            BOOST_CHECK_EQUAL(jc.is_ready(), true);
            BOOST_CHECK_EQUAL(jc.is_read_only(), false);
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(enqueue_recover_dequeue_block)
{
    string test_name = get_test_name(test_filename, "enqueue_recover_dequeue_block");
    try
    {
        {
            string msg;

            test_jrnl_cb cb;
            test_jrnl jc(test_name, test_dir, test_name, cb);
            jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
            for (int m=0; m<NUM_MSGS; m++)
                enq_msg(jc, m, create_msg(msg, m, MSG_SIZE), false);
        }
        {
            u_int64_t hrid;

            test_jrnl_cb cb;
            test_jrnl jc(test_name, test_dir, test_name, cb);
            jc.recover(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS, 0, hrid);
            BOOST_CHECK_EQUAL(hrid, u_int64_t(NUM_MSGS - 1));
            jc.recover_complete();
            for (int m=0; m<NUM_MSGS; m++)
                deq_msg(jc, m, m+NUM_MSGS);
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(enqueue_recover_dequeue_interleaved)
{
    string test_name = get_test_name(test_filename, "enqueue_recover_dequeue_interleaved");
    try
    {
        string msg;
        u_int64_t hrid;

        for (int m=0; m<2*NUM_MSGS; m+=2)
        {
            {
                test_jrnl_cb cb;
                test_jrnl jc(test_name, test_dir, test_name, cb);
                if (m == 0)
                    jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS); // First time only
                else
                {
                    jc.recover(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS, 0, hrid);
                    BOOST_CHECK_EQUAL(hrid, u_int64_t(m - 1));
                    jc.recover_complete();
                }
                enq_msg(jc, m, create_msg(msg, m, MSG_SIZE), false);
            }
            {
                test_jrnl_cb cb;
                test_jrnl jc(test_name, test_dir, test_name, cb);
                jc.recover(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS, 0, hrid);
                BOOST_CHECK_EQUAL(hrid, u_int64_t(m));
                jc.recover_complete();
                deq_msg(jc, m, m+1);
            }
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(header_flags)
{
    string test_name = get_test_name(test_filename, "header_flags");
    try
    {
        {
            string msg;

            test_jrnl_cb cb;
            test_jrnl jc(test_name, test_dir, test_name, cb);
            jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
            // Transient msgs - should not recover
            for (int m=0; m<NUM_MSGS; m++)
                enq_msg(jc, m, create_msg(msg, m, MSG_SIZE), true);
            // Persistent msgs
            for (int m=NUM_MSGS; m<NUM_MSGS*2; m++)
                enq_msg(jc, m, create_msg(msg, m, MSG_SIZE), false);
            // Transient extern msgs - should not recover
            for (int m=NUM_MSGS*2; m<NUM_MSGS*3; m++)
                enq_extern_msg(jc, m, MSG_SIZE, true);
            // Persistnet extern msgs
            for (int m=NUM_MSGS*3; m<NUM_MSGS*4; m++)
                enq_extern_msg(jc, m, MSG_SIZE, false);
        }
        {
            string msg;
            u_int64_t hrid;

            test_jrnl_cb cb;
            test_jrnl jc(test_name, test_dir, test_name, cb);
            jc.recover(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS, 0, hrid);
            // Recover non-transient msgs
            for (int m=NUM_MSGS; m<NUM_MSGS*2; m++)
            {
                string rmsg;
                string xid;
                bool transientFlag;
                bool externalFlag;

                read_msg(jc, rmsg, xid, transientFlag, externalFlag);
                BOOST_CHECK_MESSAGE(transientFlag == false, "Transient message recovered.");
                BOOST_CHECK_MESSAGE(externalFlag == false, "External flag incorrect.");
                BOOST_CHECK_MESSAGE(create_msg(msg, m, MSG_SIZE).compare(rmsg) == 0,
                        "Non-transient message corrupt during recover.");
            }
            // Recover non-transient extern msgs
            for (int m=NUM_MSGS*3; m<NUM_MSGS*4; m++)
            {
                string rmsg;
                string xid;
                bool transientFlag;
                bool externalFlag;

                read_msg(jc, rmsg, xid, transientFlag, externalFlag);
                BOOST_CHECK_MESSAGE(transientFlag == false, "Transient message recovered.");
                BOOST_CHECK_MESSAGE(externalFlag == true, "External flag incorrect.");
                BOOST_CHECK_MESSAGE(rmsg.size() == 0, "External message returned non-zero size.");
            }
            jc.recover_complete();
            // Read recovered non-transient msgs
            for (int m=NUM_MSGS; m<NUM_MSGS*2; m++)
            {
                string rmsg;
                string xid;
                bool transientFlag;
                bool externalFlag;

                read_msg(jc, rmsg, xid, transientFlag, externalFlag);
                BOOST_CHECK_MESSAGE(transientFlag == false, "Transient message recovered.");
                BOOST_CHECK_MESSAGE(externalFlag == false, "External flag incorrect.");
                BOOST_CHECK_MESSAGE(create_msg(msg, m, MSG_SIZE).compare(rmsg) == 0,
                        "Non-transient message corrupt during recover.");
            }
            // Read recovered non-transient extern msgs
            for (int m=NUM_MSGS*3; m<NUM_MSGS*4; m++)
            {
                string rmsg;
                string xid;
                bool transientFlag;
                bool externalFlag;

                read_msg(jc, rmsg, xid, transientFlag, externalFlag);
                BOOST_CHECK_MESSAGE(transientFlag == false, "Transient message recovered.");
                BOOST_CHECK_MESSAGE(externalFlag == true, "External flag incorrect.");
                BOOST_CHECK_MESSAGE(rmsg.size() == 0, "External message returned non-zero size.");
            }
            // Dequeue recovered messages
            for (int m=NUM_MSGS; m<NUM_MSGS*2; m++)
                deq_msg(jc, m, m+3*NUM_MSGS);
            for (int m=NUM_MSGS*3; m<NUM_MSGS*4; m++)
                deq_msg(jc, m, m+2*NUM_MSGS);
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(double_dequeue)
{
    string test_name = get_test_name(test_filename, "double_dequeue");
    try
    {
        string msg;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        enq_msg(jc, 0, create_msg(msg, 0, MSG_SIZE), false);
        deq_msg(jc, 0, 1);
        try{ deq_msg(jc, 0, 2); BOOST_ERROR("Did not throw exception on second dequeue."); }
        catch (const jexception& e){ BOOST_CHECK_EQUAL(e.err_code(), jerrno::JERR_WMGR_DEQRIDNOTENQ); }
        enq_msg(jc, 2, create_msg(msg, 1, MSG_SIZE), false);
        deq_msg(jc, 2, 3);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
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

QPID_AUTO_TEST_CASE(journal_overflow)
{
    string test_name = get_test_name(test_filename, "journal_overflow");
    try
    {
        string msg;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_DEFAULT_JFILES, false, 0, DEFAULT_JFSIZE_SBLKS);
        unsigned m;

        // Fill journal to just below threshold
        u_int32_t t = num_msgs_to_threshold(NUM_DEFAULT_JFILES, DEFAULT_JFSIZE_SBLKS * JRNL_SBLK_SIZE,
                MSG_REC_SIZE_DBLKS);
        u_int32_t d = num_dequeues_rem(NUM_DEFAULT_JFILES, DEFAULT_JFSIZE_SBLKS * JRNL_SBLK_SIZE);
        for (m=0; m<t; m++)
            enq_msg(jc, m, create_msg(msg, m, MSG_SIZE), false);
        // This enqueue should exceed the threshold
        enq_msg(jc, m, create_msg(msg, m, MSG_SIZE), false, RHM_IORES_ENQCAPTHRESH);

        // Dequeue as many msgs as possible except first
        for (m=1; m<=d; m++)
            deq_msg(jc, m, m+t);
        deq_msg(jc, d+1, d+2, RHM_IORES_FULL);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(file_cycle_block)
{
    string test_name = get_test_name(test_filename, "file_cycle_block");
    try
    {
        string msg;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_DEFAULT_JFILES, false, 0, DEFAULT_JFSIZE_SBLKS);

        // 5 cycles of enqueue/dequeue blocks of half threshold exception size
        u_int32_t t = num_msgs_to_threshold(NUM_DEFAULT_JFILES, DEFAULT_JFSIZE_SBLKS * JRNL_SBLK_SIZE,
                LARGE_MSG_REC_SIZE_DBLKS)/2;
        for (unsigned i=0; i<5; i++)
        {
            for (unsigned m=2*i*t; m<(2*i+1)*t; m++)
                enq_msg(jc, m, create_msg(msg, m, LARGE_MSG_SIZE), false);
            for (unsigned m=2*i*t; m<(2*i+1)*t; m++)
                deq_msg(jc, m, m+t);
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(file_cycle_interleaved)
{
    string test_name = get_test_name(test_filename, "file_cycle_interleaved");
    try
    {
        string msg;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_DEFAULT_JFILES, false, 0, DEFAULT_JFSIZE_SBLKS);

        // 5 cycles of enqueue/dequeue blocks of half threshold exception size
        u_int32_t t = num_msgs_to_threshold(NUM_DEFAULT_JFILES, DEFAULT_JFSIZE_SBLKS * JRNL_SBLK_SIZE,
                LARGE_MSG_REC_SIZE_DBLKS)/2;
        for (unsigned m=0; m<5*2*t; m+=2)
        {
            enq_msg(jc, m, create_msg(msg, m, LARGE_MSG_SIZE), false);
            deq_msg(jc, m, m+1);
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(recover_file_cycle_block)
{
    string test_name = get_test_name(test_filename, "recover_file_cycle_block");
    try
    {
        string msg;
        u_int64_t hrid;

        // 5 cycles of enqueue/dequeue blocks of half threshold exception size
        u_int32_t t = num_msgs_to_threshold(NUM_DEFAULT_JFILES, DEFAULT_JFSIZE_SBLKS * JRNL_SBLK_SIZE,
                LARGE_MSG_REC_SIZE_DBLKS)/2;
        for (unsigned i=0; i<5; i++)
        {
            test_jrnl_cb cb;
            test_jrnl jc(test_name, test_dir, test_name, cb);
            if (i)
            {
                jc.recover(NUM_DEFAULT_JFILES, false, 0, DEFAULT_JFSIZE_SBLKS, 0, hrid);
                BOOST_CHECK_EQUAL(hrid, u_int64_t(2*i*t - 1));
                jc.recover_complete();
            }
            else
                jc.initialize(NUM_DEFAULT_JFILES, false, 0, DEFAULT_JFSIZE_SBLKS);

            for (unsigned m=2*i*t; m<(2*i+1)*t; m++)
                enq_msg(jc, m, create_msg(msg, m, LARGE_MSG_SIZE), false);
            for (unsigned m=2*i*t; m<(2*i+1)*t; m++)
                deq_msg(jc, m, m+t);
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(recover_file_cycle_interleaved)
{
    string test_name = get_test_name(test_filename, "recover_file_cycle_interleaved");
    try
    {
        string msg;
        u_int64_t hrid;

        // 5 cycles of enqueue/dequeue blocks of half threshold exception size
        u_int32_t t = num_msgs_to_threshold(NUM_DEFAULT_JFILES, DEFAULT_JFSIZE_SBLKS * JRNL_SBLK_SIZE,
                LARGE_MSG_REC_SIZE_DBLKS)/2;
        for (unsigned i=0; i<5; i++)
        {
            test_jrnl_cb cb;
            test_jrnl jc(test_name, test_dir, test_name, cb);
            if (i)
            {
                jc.recover(NUM_DEFAULT_JFILES, false, 0, DEFAULT_JFSIZE_SBLKS, 0, hrid);
                BOOST_CHECK_EQUAL(hrid, u_int64_t(2*i*t - 1));
                jc.recover_complete();
            }
            else
                jc.initialize(NUM_DEFAULT_JFILES, false, 0, DEFAULT_JFSIZE_SBLKS);

            for (unsigned m=2*i*t; m<2*(i+1)*t; m+=2)
            {
                enq_msg(jc, m, create_msg(msg, m, LARGE_MSG_SIZE), false);
                deq_msg(jc, m, m+1);
            }
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

#endif

QPID_AUTO_TEST_SUITE_END()
