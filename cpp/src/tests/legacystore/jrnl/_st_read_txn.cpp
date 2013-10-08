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

QPID_AUTO_TEST_SUITE(journal_read_txn)

const string test_filename("_st_read_txn");

#include "_st_helper_fns.h"

// === Test suite ===

QPID_AUTO_TEST_CASE(tx_enqueue_commit_block)
{
    string test_name = get_test_name(test_filename, "tx_enqueue_commit_block");
    try
    {
        string msg;
        string xid;
        string rmsg;
        string rxid;
        bool transientFlag;
        bool externalFlag;
        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        create_xid(xid, 0, XID_SIZE);
        for (int m=0; m<NUM_MSGS; m++)
            enq_txn_msg(jc, m, create_msg(msg, m, MSG_SIZE), xid, false);
        jc.flush();
        read_msg(jc, rmsg, rxid, transientFlag, externalFlag, RHM_IORES_TXPENDING);
        txn_commit(jc, NUM_MSGS, xid);
        jc.flush();
        for (int m=0; m<NUM_MSGS; m++)
        {
            read_msg(jc, rmsg, rxid, transientFlag, externalFlag);
            BOOST_CHECK_EQUAL(create_msg(msg, m, MSG_SIZE), rmsg);
            BOOST_CHECK_EQUAL(rxid, xid);
            BOOST_CHECK_EQUAL(transientFlag, false);
            BOOST_CHECK_EQUAL(externalFlag, false);
        }
        read_msg(jc, rmsg, rxid, transientFlag, externalFlag, RHM_IORES_EMPTY);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(tx_enqueue_commit_interleaved)
{
    string test_name = get_test_name(test_filename, "tx_enqueue_commit_interleaved");
    try
    {
        string msg;
        string xid;
        string rmsg;
        string rxid;
        bool transientFlag;
        bool externalFlag;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        for (int m=0; m<NUM_MSGS; m++)
        {
            create_xid(xid, 2*m, XID_SIZE);
            enq_txn_msg(jc, 2*m, create_msg(msg, 2*m, MSG_SIZE), xid, false);
            jc.flush();
            read_msg(jc, rmsg, rxid, transientFlag, externalFlag, RHM_IORES_TXPENDING);
            txn_commit(jc, 2*m+1, xid);
            jc.flush();
            read_msg(jc, rmsg, rxid, transientFlag, externalFlag);
            BOOST_CHECK_EQUAL(create_msg(msg, 2*m, MSG_SIZE), rmsg);
            BOOST_CHECK_EQUAL(rxid, xid);
            BOOST_CHECK_EQUAL(transientFlag, false);
            BOOST_CHECK_EQUAL(externalFlag, false);
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(tx_enqueue_abort_block)
{
    string test_name = get_test_name(test_filename, "tx_enqueue_abort_block");
    try
    {
        string msg;
        string xid;
        string rmsg;
        string rxid;
        bool transientFlag;
        bool externalFlag;
        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        create_xid(xid, 1, XID_SIZE);
        for (int m=0; m<NUM_MSGS; m++)
            enq_txn_msg(jc, m, create_msg(msg, m, MSG_SIZE), xid, false);
        jc.flush();
        read_msg(jc, rmsg, rxid, transientFlag, externalFlag, RHM_IORES_TXPENDING);
        txn_abort(jc, NUM_MSGS, xid);
        jc.flush();
        read_msg(jc, rmsg, rxid, transientFlag, externalFlag, RHM_IORES_EMPTY);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(tx_enqueue_abort_interleaved)
{
    string test_name = get_test_name(test_filename, "tx_enqueue_abort_interleaved");
    try
    {
        string msg;
        string xid;
        string rmsg;
        string rxid;
        bool transientFlag;
        bool externalFlag;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        for (int m=0; m<NUM_MSGS; m++)
        {
            create_xid(xid, 2*m, XID_SIZE);
            enq_txn_msg(jc, 2*m, create_msg(msg, 2*m, MSG_SIZE), xid, false);
            jc.flush();
            read_msg(jc, rmsg, rxid, transientFlag, externalFlag, RHM_IORES_TXPENDING);
            txn_abort(jc, 2*m+1, xid);
            jc.flush();
            read_msg(jc, rmsg, rxid, transientFlag, externalFlag, RHM_IORES_EMPTY);
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(tx_enqueue_commit_dequeue_block)
{
    string test_name = get_test_name(test_filename, "tx_enqueue_commit_dequeue_block");
    try
    {
        string msg;
        string xid;
        string rmsg;
        string rxid;
        bool transientFlag;
        bool externalFlag;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        create_xid(xid, 2, XID_SIZE);
        for (int m=0; m<NUM_MSGS; m++)
            enq_txn_msg(jc, m, create_msg(msg, m, MSG_SIZE), xid, false);
        txn_commit(jc, NUM_MSGS, xid);
        for (int m=0; m<NUM_MSGS; m++)
            deq_msg(jc, m, m+NUM_MSGS+1);
        jc.flush();
        read_msg(jc, rmsg, rxid, transientFlag, externalFlag, RHM_IORES_EMPTY);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(tx_enqueue_commit_dequeue_interleaved)
{
    string test_name = get_test_name(test_filename, "tx_enqueue_commit_dequeue_interleaved");
    try
    {
        string msg;
        string xid;
        string rmsg;
        string rxid;
        bool transientFlag;
        bool externalFlag;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        for (int m=0; m<NUM_MSGS; m++)
        {
            create_xid(xid, 3*m, XID_SIZE);
            enq_txn_msg(jc, 3*m, create_msg(msg, m, MSG_SIZE), xid, false);
            txn_commit(jc, 3*m+1, xid);
            deq_msg(jc, 3*m, 3*m+2);
            jc.flush();
            read_msg(jc, rmsg, rxid, transientFlag, externalFlag, RHM_IORES_EMPTY);
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(enqueue_tx_dequeue_commit_block)
{
    string test_name = get_test_name(test_filename, "enqueue_tx_dequeue_commit_block");
    try
    {
        string msg;
        string xid;
        string rmsg;
        string rxid;
        bool transientFlag;
        bool externalFlag;

        create_xid(xid, 3, XID_SIZE);
        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        for (int m=0; m<NUM_MSGS; m++)
            enq_msg(jc, m, create_msg(msg, m, MSG_SIZE), false);
        for (int m=0; m<NUM_MSGS; m++)
            deq_txn_msg(jc, m, m+NUM_MSGS, xid);
        jc.flush();
        read_msg(jc, rmsg, rxid, transientFlag, externalFlag, RHM_IORES_TXPENDING);
        txn_commit(jc, 2*NUM_MSGS, xid);
        jc.flush();
        read_msg(jc, rmsg, rxid, transientFlag, externalFlag, RHM_IORES_EMPTY);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(enqueue_tx_dequeue_commit_interleaved)
{
    string test_name = get_test_name(test_filename, "enqueue_tx_dequeue_commit_interleaved");
    try
    {
        string msg;
        string xid;
        string rmsg;
        string rxid;
        bool transientFlag;
        bool externalFlag;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        for (int m=0; m<NUM_MSGS; m++)
        {
            enq_msg(jc, 3*m, create_msg(msg, 3*m, MSG_SIZE), false);
            create_xid(xid, 3*m, XID_SIZE);
            deq_txn_msg(jc, 3*m, 3*m+1, xid);
            jc.flush();
            read_msg(jc, rmsg, rxid, transientFlag, externalFlag, RHM_IORES_TXPENDING);
            txn_commit(jc, 3*m+2, xid);
            jc.flush();
            read_msg(jc, rmsg, rxid, transientFlag, externalFlag, RHM_IORES_EMPTY);
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(enqueue_tx_dequeue_abort_block)
{
    string test_name = get_test_name(test_filename, "enqueue_tx_dequeue_abort_block");
    try
    {
        string msg;
        string xid;
        string rmsg;
        string rxid;
        bool transientFlag;
        bool externalFlag;

        create_xid(xid, 4, XID_SIZE);
        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        for (int m=0; m<NUM_MSGS; m++)
            enq_msg(jc, m, create_msg(msg, m, MSG_SIZE), false);
        for (int m=0; m<NUM_MSGS; m++)
            deq_txn_msg(jc, m, m+NUM_MSGS, xid);
        jc.flush();
        read_msg(jc, rmsg, rxid, transientFlag, externalFlag, RHM_IORES_TXPENDING);
        txn_abort(jc, 2*NUM_MSGS, xid);
        jc.flush();
        for (int m=0; m<NUM_MSGS; m++)
        {
            read_msg(jc, rmsg, rxid, transientFlag, externalFlag);
            BOOST_CHECK_EQUAL(create_msg(msg, m, MSG_SIZE), rmsg);
            BOOST_CHECK_EQUAL(rxid.length(), std::size_t(0));
            BOOST_CHECK_EQUAL(transientFlag, false);
            BOOST_CHECK_EQUAL(externalFlag, false);
        }
        read_msg(jc, rmsg, rxid, transientFlag, externalFlag, RHM_IORES_EMPTY);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(enqueue_tx_dequeue_abort_interleaved)
{
    string test_name = get_test_name(test_filename, "enqueue_tx_dequeue_abort_interleaved");
    try
    {
        string msg;
        string xid;
        string rmsg;
        string rxid;
        bool transientFlag;
        bool externalFlag;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_TEST_JFILES, false, 0, TEST_JFSIZE_SBLKS);
        for (int m=0; m<NUM_MSGS; m++)
        {
            enq_msg(jc, 3*m, create_msg(msg, 3*m, MSG_SIZE), false);
            create_xid(xid, 3*m, XID_SIZE);
            deq_txn_msg(jc, 3*m, 3*m+1, xid);
            jc.flush();
            read_msg(jc, rmsg, rxid, transientFlag, externalFlag, RHM_IORES_TXPENDING);
            txn_abort(jc, 3*m+2, xid);
            jc.flush();
            read_msg(jc, rmsg, rxid, transientFlag, externalFlag);
            read_msg(jc, rmsg, rxid, transientFlag, externalFlag, RHM_IORES_EMPTY);
        }
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_SUITE_END()
