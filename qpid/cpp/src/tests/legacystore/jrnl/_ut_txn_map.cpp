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

#include <iomanip>
#include <iostream>
#include "qpid/legacystore/jrnl/txn_map.h"
#include <sstream>

using namespace boost::unit_test;
using namespace mrg::journal;
using namespace std;

QPID_AUTO_TEST_SUITE(txn_map_suite)

const string test_filename("_ut_txn_map");

// === Helper functions ===

const string make_xid(u_int64_t rid)
{
    stringstream ss;
    ss << "XID-" << setfill('0') << setw(16) << hex << rid;
    ss << "-0123456789abcdef";
    return ss.str();
}

void check_td_equal(txn_data& td1, txn_data& td2)
{
    BOOST_CHECK_EQUAL(td1._rid,       td2._rid);
    BOOST_CHECK_EQUAL(td1._drid,      td2._drid);
    BOOST_CHECK_EQUAL(td1._pfid,      td2._pfid);
    BOOST_CHECK_EQUAL(td1._enq_flag,  td2._enq_flag);
    BOOST_CHECK_EQUAL(td1._aio_compl, td2._aio_compl);
}

// === Test suite ===

QPID_AUTO_TEST_CASE(constructor)
{
    cout << test_filename << ".constructor: " << flush;
    const u_int64_t rid = 0x123456789abcdef0ULL;
    const u_int64_t drid = 0xfedcba9876543210ULL;
    const u_int16_t pfid = 0xfedcU;
    const bool enq_flag = true;
    txn_data td(rid, drid, pfid, enq_flag);
    BOOST_CHECK_EQUAL(td._rid, rid);
    BOOST_CHECK_EQUAL(td._drid, drid);
    BOOST_CHECK_EQUAL(td._pfid, pfid);
    BOOST_CHECK_EQUAL(td._enq_flag, enq_flag);
    BOOST_CHECK_EQUAL(td._aio_compl, false);

    txn_map t1;
    BOOST_CHECK(t1.empty());
    BOOST_CHECK_EQUAL(t1.size(), u_int32_t(0));
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(insert_get)
{
    cout << test_filename << ".insert_get: " << flush;
    u_int16_t fid;
    u_int64_t rid;
    u_int16_t pfid_start = 0x2000U;
    u_int64_t rid_begin = 0xffffffff00000000ULL;
    u_int64_t rid_end   = 0xffffffff00000200ULL;

    // insert with no dups
    u_int64_t rid_incr_1 = 4ULL;
    txn_map t2;
    t2.set_num_jfiles(pfid_start + (rid_end - rid_begin)/rid_incr_1);
    for (rid = rid_begin, fid = pfid_start; rid < rid_end; rid += rid_incr_1, fid++)
        t2.insert_txn_data(make_xid(rid), txn_data(rid, ~rid, fid, false));
    BOOST_CHECK(!t2.empty());
    BOOST_CHECK_EQUAL(t2.size(), u_int32_t(128));

    // get
    u_int64_t rid_incr_2 = 6ULL;
    for (u_int64_t rid = rid_begin; rid < rid_end; rid += rid_incr_2)
    {
        string xid = make_xid(rid);
        BOOST_CHECK_EQUAL(t2.in_map(xid), (rid%rid_incr_1 ? false : true));
    }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_SUITE_END()
