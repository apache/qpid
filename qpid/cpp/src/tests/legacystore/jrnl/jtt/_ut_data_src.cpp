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
#include <cstddef>
#include "data_src.h"
#include <iomanip>
#include <iostream>

using namespace boost::unit_test;
using namespace mrg::jtt;
using namespace std;

QPID_AUTO_TEST_SUITE(jtt_data_src)

const string test_filename("_ut_data_src");

long
get_seed()
{
    timespec ts;
    if (::clock_gettime(CLOCK_REALTIME, &ts))
        BOOST_FAIL("Unable to read clock to generate seed.");
    long tenths = ts.tv_nsec / 100000000;
    return long(10 * ts.tv_sec + tenths); // time in tenths of a second
}

#ifndef LONG_TEST
/*
 * ==============================================
 *                  NORMAL TESTS
 * This section contains normal "make check" tests
 * for building/packaging. These are built when
 * LONG_TEST is _not_ defined.
 * ==============================================
 */

QPID_AUTO_TEST_CASE(data)
{
    cout << test_filename << ".data: " << flush;
    BOOST_CHECK(data_src::max_dsize > 0);
    for (std::size_t i=0; i<1024; i++)
    {
        const char* dp = data_src::get_data(i);
        BOOST_CHECK_EQUAL(*dp, static_cast<char>('0' + ((i + 1) % 10)));
    }
    for (std::size_t i=data_src::max_dsize-1024; i<data_src::max_dsize; i++)
    {
        const char* dp = data_src::get_data(i);
        BOOST_CHECK_EQUAL(*dp, static_cast<char>('0' + ((i + 1) % 10)));
    }
    const char* dp1 = data_src::get_data(data_src::max_dsize);
    BOOST_CHECK_EQUAL(dp1,(char*) 0);
    const char* dp2 = data_src::get_data(data_src::max_dsize + 0x1000);
    BOOST_CHECK_EQUAL(dp2, (char*)0);
    cout << "ok" << endl;
}

// There is a long version of this test in _ut_long_data_src.cpp
QPID_AUTO_TEST_CASE(xid_data_xid)
{
    const std::size_t num = 64;
    cout << test_filename << ".xid_data_xid: " << flush;
    BOOST_CHECK_EQUAL(data_src::get_xid(1),  "0");
    BOOST_CHECK_EQUAL(data_src::get_xid(2),  "01");
    BOOST_CHECK_EQUAL(data_src::get_xid(3),  "002");
    BOOST_CHECK_EQUAL(data_src::get_xid(4),  "0003");
    BOOST_CHECK_EQUAL(data_src::get_xid(5),  "00004");
    BOOST_CHECK_EQUAL(data_src::get_xid(6),  "000005");
    BOOST_CHECK_EQUAL(data_src::get_xid(7),  "0000006");
    BOOST_CHECK_EQUAL(data_src::get_xid(8),  "00000007");
    BOOST_CHECK_EQUAL(data_src::get_xid(9),  "xid:00008");
    BOOST_CHECK_EQUAL(data_src::get_xid(10), "xid:000009");
    BOOST_CHECK_EQUAL(data_src::get_xid(11), "xid:0000010");
    BOOST_CHECK_EQUAL(data_src::get_xid(12), "xid:00000011");
    BOOST_CHECK_EQUAL(data_src::get_xid(13), "xid:00000012:");
    BOOST_CHECK_EQUAL(data_src::get_xid(14), "xid:00000013:n");
    BOOST_CHECK_EQUAL(data_src::get_xid(15), "xid:00000014:no");
    std::size_t i = 15;
    for (; i<num; i++)
    {
        string xid(data_src::get_xid(i));

        ostringstream oss;
        oss << setfill('0') << "xid:" << setw(8) << i << ":";

        BOOST_CHECK_EQUAL(xid.size(), i);
        BOOST_CHECK_EQUAL(xid.substr(0, 13), oss.str());
        BOOST_CHECK_EQUAL(xid[13], 'n');
        BOOST_CHECK_EQUAL(xid[i-1], (char)('a' + ((i-1)%26)));
    }
    for (std::size_t j=data_src::max_xsize-num; j<data_src::max_xsize; j++,i++)
    {
        string xid(data_src::get_xid(j));

        ostringstream oss;
        oss << setfill('0') << "xid:" << setw(8) << i << ":";

        BOOST_CHECK_EQUAL(xid.size(), j);
        BOOST_CHECK_EQUAL(xid.substr(0, 13), oss.str());
        BOOST_CHECK_EQUAL(xid[13], 'n');
        BOOST_CHECK_EQUAL(xid[j-1], (char)('a' + ((j-1)%26)));
    }
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

/*
 * To reproduce a specific test, comment out the get_seed() statement and uncomment the literal below, adjusting the seed
 * value to that required.
 */
QPID_AUTO_TEST_CASE(xid_data_xid)
{
    const long seed = get_seed();
    // const long seed = 0x2d9b69d32;
    ::srand48(seed);

    const std::size_t num = 1024;
    cout << test_filename << ".xid_data_xid seed=0x" << std::hex << seed << std::dec <<  ": " << flush;
    BOOST_CHECK_EQUAL(data_src::get_xid(1),  "0");
    BOOST_CHECK_EQUAL(data_src::get_xid(2),  "01");
    BOOST_CHECK_EQUAL(data_src::get_xid(3),  "002");
    BOOST_CHECK_EQUAL(data_src::get_xid(4),  "0003");
    BOOST_CHECK_EQUAL(data_src::get_xid(5),  "00004");
    BOOST_CHECK_EQUAL(data_src::get_xid(6),  "000005");
    BOOST_CHECK_EQUAL(data_src::get_xid(7),  "0000006");
    BOOST_CHECK_EQUAL(data_src::get_xid(8),  "00000007");
    BOOST_CHECK_EQUAL(data_src::get_xid(9),  "xid:00008");
    BOOST_CHECK_EQUAL(data_src::get_xid(10), "xid:000009");
    BOOST_CHECK_EQUAL(data_src::get_xid(11), "xid:0000010");
    BOOST_CHECK_EQUAL(data_src::get_xid(12), "xid:00000011");
    BOOST_CHECK_EQUAL(data_src::get_xid(13), "xid:00000012:");
    BOOST_CHECK_EQUAL(data_src::get_xid(14), "xid:00000013:n");
    BOOST_CHECK_EQUAL(data_src::get_xid(15), "xid:00000014:no");
    std::size_t i = 15;
    for (; i<num; i++)
    {
        string xid(data_src::get_xid(i));

        ostringstream oss;
        oss << setfill('0') << "xid:" << setw(8) << i << ":";

        BOOST_CHECK_EQUAL(xid.size(), i);
        BOOST_CHECK_EQUAL(xid.substr(0, 13), oss.str());
        BOOST_CHECK_EQUAL(xid[13], 'n');
        BOOST_CHECK_EQUAL(xid[i-1], (char)('a' + ((i-1)%26)));
    }
    for (std::size_t j=data_src::max_xsize-num; j<data_src::max_xsize; j++,i++)
    {
        string xid(data_src::get_xid(j));

        ostringstream oss;
        oss << setfill('0') << "xid:" << setw(8) << i << ":";

        BOOST_CHECK_EQUAL(xid.size(), j);
        BOOST_CHECK_EQUAL(xid.substr(0, 13), oss.str());
        BOOST_CHECK_EQUAL(xid[13], 'n');
        BOOST_CHECK_EQUAL(xid[j-1], (char)('a' + ((j-1)%26)));
    }
    std::srand(seed);
    for (int cnt=0; cnt<1000; cnt++,i++)
    {
        std::size_t k = 1 + ::lrand48() % (data_src::max_xsize - 1);
        string xid(data_src::get_xid(k));

        ostringstream oss;
        oss << setfill('0') << "xid:" << setw(8) << i << ":";

        BOOST_CHECK_EQUAL(xid.size(), k);
        BOOST_CHECK_EQUAL(xid.substr(0, 13), oss.str());
        BOOST_CHECK_EQUAL(xid[13], 'n');
        BOOST_CHECK_EQUAL(xid[k-1], (char)('a' + ((k-1)%26)));
    }
    cout << "ok" << endl;
}

#endif

QPID_AUTO_TEST_SUITE_END()
