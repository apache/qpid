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

QPID_AUTO_TEST_SUITE(journal_basic_txn)

const string test_filename("_st_basic_txn");

#include "_st_helper_fns.h"

// === Test suite ===

QPID_AUTO_TEST_CASE(enqueue_commit_dequeue_block)
{
    string test_name = get_test_name(test_filename, "enqueue_commit_dequeue_block");
    try
    {
        string msg;
        string xid;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        create_xid(xid, 0, XID_SIZE);
        for (int m=0; m<NUM_MSGS; m++)
            BOOST_CHECK_EQUAL(enq_txn_msg(jc, m, create_msg(msg, m, MSG_SIZE), xid, false), u_int64_t(m));
        txn_commit(jc, NUM_MSGS, xid);
        for (int m=0; m<NUM_MSGS; m++)
            deq_msg(jc, m, m+NUM_MSGS+1);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(enqueue_abort_dequeue_block)
{
    string test_name = get_test_name(test_filename, "enqueue_abort_dequeue_block");
    try
    {
        string msg;
        string xid;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        create_xid(xid, 0, XID_SIZE);
        for (int m=0; m<NUM_MSGS; m++)
            BOOST_CHECK_EQUAL(enq_txn_msg(jc, m, create_msg(msg, m, MSG_SIZE), xid, false), u_int64_t(m));
        txn_abort(jc, NUM_MSGS, xid);
        for (int m=0; m<NUM_MSGS; m++)
        {
            try
            {
                deq_msg(jc, m, m+NUM_MSGS+1);
                BOOST_ERROR("Expected dequeue to fail with exception JERR_WMGR_DEQRIDNOTENQ.");
            }
            catch (const jexception& e) { if (e.err_code() != jerrno::JERR_WMGR_DEQRIDNOTENQ) throw; }
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(enqueue_commit_dequeue_interleaved)
{
    string test_name = get_test_name(test_filename, "enqueue_commit_dequeue_interleaved");
    try
    {
        string msg;
        string xid;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        for (int m=0; m<NUM_MSGS; m++)
        {
            create_xid(xid, m, XID_SIZE);
            BOOST_CHECK_EQUAL(enq_txn_msg(jc, 3*m, create_msg(msg, m, MSG_SIZE), xid, false), u_int64_t(3*m));
            txn_commit(jc, 3*m+1, xid);
            deq_msg(jc, 3*m, 3*m+2);
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(enqueue_abort_dequeue_interleaved)
{
    string test_name = get_test_name(test_filename, "enqueue_abort_dequeue_interleaved");
    try
    {
        string msg;
        string xid;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        for (int m=0; m<NUM_MSGS; m++)
        {
            create_xid(xid, m, XID_SIZE);
            BOOST_CHECK_EQUAL(enq_txn_msg(jc, 3*m, create_msg(msg, m, MSG_SIZE), xid, false), u_int64_t(3*m));
            txn_abort(jc, 3*m+1, xid);
            try
            {
                deq_msg(jc, 2*m, 2*m+2);
                BOOST_ERROR("Expected dequeue to fail with exception JERR_WMGR_DEQRIDNOTENQ.");
            }
            catch (const jexception& e) { if (e.err_code() != jerrno::JERR_WMGR_DEQRIDNOTENQ) throw; }
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(enqueue_dequeue_commit_block)
{
    string test_name = get_test_name(test_filename, "enqueue_dequeue_commit_block");
    try
    {
        string msg;
        string xid;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        create_xid(xid, 0, XID_SIZE);
        for (int m=0; m<NUM_MSGS; m++)
            BOOST_CHECK_EQUAL(enq_txn_msg(jc, m, create_msg(msg, m, MSG_SIZE), xid, false), u_int64_t(m));
        BOOST_CHECK_EQUAL(jc.get_enq_cnt(), u_int32_t(0));
        for (int m=0; m<NUM_MSGS; m++)
            deq_txn_msg(jc, m, m+NUM_MSGS, xid);
        BOOST_CHECK_EQUAL(jc.get_enq_cnt(), u_int32_t(0));
        txn_commit(jc, 2*NUM_MSGS, xid);
        BOOST_CHECK_EQUAL(jc.get_enq_cnt(), u_int32_t(0));
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(enqueue_dequeue_abort_block)
{
    string test_name = get_test_name(test_filename, "enqueue_dequeue_abort_block");
    try
    {
        string msg;
        string xid;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        create_xid(xid, 0, XID_SIZE);
        for (int m=0; m<NUM_MSGS; m++)
            BOOST_CHECK_EQUAL(enq_txn_msg(jc, m, create_msg(msg, m, MSG_SIZE), xid, false), u_int64_t(m));
        BOOST_CHECK_EQUAL(jc.get_enq_cnt(), u_int32_t(0));
        for (int m=0; m<NUM_MSGS; m++)
            deq_txn_msg(jc, m, m+NUM_MSGS, xid);
        BOOST_CHECK_EQUAL(jc.get_enq_cnt(), u_int32_t(0));
        txn_abort(jc, 2*NUM_MSGS, xid);
        BOOST_CHECK_EQUAL(jc.get_enq_cnt(), u_int32_t(0));
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(enqueue_dequeue_commit_interleaved)
{
    string test_name = get_test_name(test_filename, "enqueue_dequeue_commit_interleaved");
    try
    {
        string msg;
        string xid;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        for (int m=0; m<NUM_MSGS; m++)
        {
            create_xid(xid, m, XID_SIZE);
            BOOST_CHECK_EQUAL(enq_txn_msg(jc, 3*m, create_msg(msg, m, MSG_SIZE), xid, false), u_int64_t(3*m));
            deq_txn_msg(jc, 3*m, 3*m+1, xid);
            txn_commit(jc, 3*m+2, xid);
            BOOST_CHECK_EQUAL(jc.get_enq_cnt(), u_int32_t(0));
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(enqueue_dequeue_abort_interleaved)
{
    string test_name = get_test_name(test_filename, "enqueue_dequeue_abort_interleaved");
    try
    {
        string msg;
        string xid;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        for (int m=0; m<NUM_MSGS; m++)
        {
            create_xid(xid, m, XID_SIZE);
            BOOST_CHECK_EQUAL(enq_txn_msg(jc, 3*m, create_msg(msg, m, MSG_SIZE), xid, false), u_int64_t(3*m));
            deq_txn_msg(jc, 3*m, 3*m+1, xid);
            txn_abort(jc, 3*m+2, xid);
            BOOST_CHECK_EQUAL(jc.get_enq_cnt(), u_int32_t(0));
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_SUITE_END()
