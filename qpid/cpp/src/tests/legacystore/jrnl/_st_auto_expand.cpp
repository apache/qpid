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

QPID_AUTO_TEST_SUITE(journal_auto_expand)

const string test_filename("_st_auto_expand");

#include "_st_helper_fns.h"

// === Test suite ===

QPID_AUTO_TEST_CASE(no_ae_threshold)
{
    string test_name = get_test_name(test_filename, "no_ae_threshold");
    try
    {
        string msg;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_DEFAULT_JFILES, false, 0, DEFAULT_JFSIZE_SBLKS);
        unsigned m;

        // Fill journal to just below threshold
        u_int32_t t = num_msgs_to_threshold(NUM_DEFAULT_JFILES,
                DEFAULT_JFSIZE_SBLKS * JRNL_SBLK_SIZE, LARGE_MSG_REC_SIZE_DBLKS);
        for (m=0; m<t; m++)
            enq_msg(jc, m, create_msg(msg, m, LARGE_MSG_SIZE), false);
        // This enqueue should exceed the threshold
        BOOST_CHECK_EQUAL(jc.get_enq_cnt(), t);
        enq_msg(jc, m, create_msg(msg, m, LARGE_MSG_SIZE), false, RHM_IORES_ENQCAPTHRESH);
        BOOST_CHECK_EQUAL(jc.get_enq_cnt(), t);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(no_ae_threshold_dequeue_some)
{
    string test_name = get_test_name(test_filename, "no_ae_threshold_dequeue_some");
    try
    {
        string msg;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_DEFAULT_JFILES, false, 0, DEFAULT_JFSIZE_SBLKS);
        unsigned m;

        // Fill journal to just below threshold
        u_int32_t t = num_msgs_to_threshold(NUM_DEFAULT_JFILES,
                DEFAULT_JFSIZE_SBLKS * JRNL_SBLK_SIZE, LARGE_MSG_REC_SIZE_DBLKS);
        for (m=0; m<t; m++)
            enq_msg(jc, m, create_msg(msg, m, LARGE_MSG_SIZE), false);
        // This enqueue should exceed the threshold
        BOOST_CHECK_EQUAL(jc.get_enq_cnt(), t);
        enq_msg(jc, m, create_msg(msg, m, LARGE_MSG_SIZE), false, RHM_IORES_ENQCAPTHRESH);
        BOOST_CHECK_EQUAL(jc.get_enq_cnt(), t);

        // Dequeue 25 msgs
        #define NUM_MSGS_DEQ 25
        for (m=0; m<NUM_MSGS_DEQ; m++)
            deq_msg(jc, m, m+t);
        BOOST_CHECK_EQUAL(jc.get_enq_cnt(), u_int32_t(t-NUM_MSGS_DEQ));

        // Check we can still enqueue and dequeue
        for (m=t+NUM_MSGS_DEQ; m<t+NUM_MSGS_DEQ+NUM_MSGS; m++)
            enq_msg(jc, m, create_msg(msg, m, LARGE_MSG_SIZE), false);
        for (m=t+NUM_MSGS_DEQ; m<t+NUM_MSGS_DEQ+NUM_MSGS; m++)
            deq_msg(jc, m, m+NUM_MSGS_DEQ+NUM_MSGS);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(no_ae_threshold_dequeue_all)
{
    string test_name = get_test_name(test_filename, "no_ae_threshold_dequeue_all");
    try
    {
        string msg;

        test_jrnl_cb cb;
        test_jrnl jc(test_name, test_dir, test_name, cb);
        jc.initialize(NUM_DEFAULT_JFILES, false, 0, DEFAULT_JFSIZE_SBLKS);
        unsigned m;

        // Fill journal to just below threshold
        u_int32_t t = num_msgs_to_threshold(NUM_DEFAULT_JFILES,
                DEFAULT_JFSIZE_SBLKS * JRNL_SBLK_SIZE, LARGE_MSG_REC_SIZE_DBLKS);
        for (m=0; m<t; m++)
            enq_msg(jc, m, create_msg(msg, m, LARGE_MSG_SIZE), false);
        // This enqueue should exceed the threshold
        BOOST_CHECK_EQUAL(jc.get_enq_cnt(), t);
        enq_msg(jc, m, create_msg(msg, m, LARGE_MSG_SIZE), false, RHM_IORES_ENQCAPTHRESH);
        BOOST_CHECK_EQUAL(jc.get_enq_cnt(), t);

        // Dequeue all msgs
        for (m=0; m<t; m++)
            deq_msg(jc, m, m+t);
        BOOST_CHECK_EQUAL(jc.get_enq_cnt(), u_int32_t(0));

        // Check we can still enqueue and dequeue
        for (m=2*t; m<2*t + NUM_MSGS; m++)
            enq_msg(jc, m, create_msg(msg, m, LARGE_MSG_SIZE), false);
        for (m=2*t; m<2*t + NUM_MSGS; m++)
            deq_msg(jc, m, m+2*t+NUM_MSGS);
    }
    catch(const exception& e) { BOOST_FAIL(e.what()); }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_SUITE_END()
