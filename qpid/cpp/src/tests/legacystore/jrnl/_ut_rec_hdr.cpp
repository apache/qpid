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

#include <ctime>
#include <iostream>
#include "qpid/legacystore/jrnl/deq_hdr.h"
#include "qpid/legacystore/jrnl/enq_hdr.h"
#include "qpid/legacystore/jrnl/file_hdr.h"
#include "qpid/legacystore/jrnl/jcfg.h"
#include "qpid/legacystore/jrnl/rec_tail.h"
#include "qpid/legacystore/jrnl/txn_hdr.h"

using namespace boost::unit_test;
using namespace mrg::journal;
using namespace std;

QPID_AUTO_TEST_SUITE(rec_hdr_suite)

const string test_filename("_ut_rec_hdr");

QPID_AUTO_TEST_CASE(hdr_class)
{
    cout << test_filename << ".hdr_class: " << flush;
    rec_hdr h1;
    BOOST_CHECK_EQUAL(h1._magic, 0UL);
    BOOST_CHECK_EQUAL(h1._version, 0);
    BOOST_CHECK_EQUAL(h1._eflag, 0);
    BOOST_CHECK_EQUAL(h1._uflag, 0);
    BOOST_CHECK_EQUAL(h1._rid, 0ULL);
    BOOST_CHECK(!h1.get_owi());

    const u_int32_t magic = 0x89abcdefUL;
    const u_int16_t uflag = 0x5537;
    const u_int8_t version = 0xef;
    const u_int64_t rid = 0x123456789abcdef0ULL;
    const bool owi = true;

    rec_hdr h2(magic, version, rid, owi);
    BOOST_CHECK_EQUAL(h2._magic, magic);
    BOOST_CHECK_EQUAL(h2._version, version);
#ifdef JRNL_LITTLE_ENDIAN
    BOOST_CHECK_EQUAL(h2._eflag, RHM_LENDIAN_FLAG);
#else
    BOOST_CHECK_EQUAL(h2._eflag, RHM_BENDIAN_FLAG);
#endif
    BOOST_CHECK_EQUAL(h2._uflag, (const u_int16_t)rec_hdr::HDR_OVERWRITE_INDICATOR_MASK);
    BOOST_CHECK_EQUAL(h2._rid, rid);
    BOOST_CHECK_EQUAL(h2.get_owi(), owi);
    h2._uflag = uflag;
    BOOST_CHECK(h2.get_owi());
    h2.set_owi(true);
    BOOST_CHECK(h2.get_owi());
    BOOST_CHECK_EQUAL(h2._uflag, uflag);
    h2.set_owi(false);
    BOOST_CHECK(!h2.get_owi());
    BOOST_CHECK_EQUAL(h2._uflag, (uflag & ~(const u_int16_t)rec_hdr::HDR_OVERWRITE_INDICATOR_MASK));
    h2.set_owi(true);
    BOOST_CHECK(h2.get_owi());
    BOOST_CHECK_EQUAL(h2._uflag, uflag);

    h1.hdr_copy(h2);
    BOOST_CHECK_EQUAL(h1._magic, magic);
    BOOST_CHECK_EQUAL(h1._version, version);
#ifdef JRNL_LITTLE_ENDIAN
    BOOST_CHECK_EQUAL(h1._eflag, RHM_LENDIAN_FLAG);
#else
    BOOST_CHECK_EQUAL(h1._eflag, RHM_BENDIAN_FLAG);
#endif
    BOOST_CHECK_EQUAL(h1._uflag, uflag);
    BOOST_CHECK_EQUAL(h1._rid, rid);
    BOOST_CHECK(h1.get_owi());
    BOOST_CHECK_EQUAL(h1._uflag, uflag);

    h1.reset();
    BOOST_CHECK_EQUAL(h1._magic, 0UL);
    BOOST_CHECK_EQUAL(h1._version, 0);
    BOOST_CHECK_EQUAL(h1._eflag, 0);
    BOOST_CHECK_EQUAL(h1._uflag, 0);
    BOOST_CHECK_EQUAL(h1._rid, 0ULL);
    BOOST_CHECK(!h1.get_owi());
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(rec_tail_class)
{
    cout << test_filename << ".rec_tail_class: " << flush;
    const u_int32_t magic = 0xfedcba98;
    const u_int64_t rid = 0xfedcba9876543210ULL;
    const u_int32_t xmagic = ~magic;

    {
        rec_tail rt1;
        BOOST_CHECK_EQUAL(rt1._xmagic, 0xffffffffUL);
        BOOST_CHECK_EQUAL(rt1._rid, 0ULL);
    }

    {
        rec_tail rt2(magic, rid);
        BOOST_CHECK_EQUAL(rt2._xmagic, magic);
        BOOST_CHECK_EQUAL(rt2._rid, rid);
    }

    {
        rec_hdr h(magic, RHM_JDAT_VERSION, rid, true);
        rec_tail rt3(h);
        BOOST_CHECK_EQUAL(rt3._xmagic, xmagic);
        BOOST_CHECK_EQUAL(rt3._rid, rid);
    }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(file_hdr_class)
{
    cout << test_filename << ".file_hdr_class: " << flush;
    const u_int32_t magic = 0xfedcba98UL;
    const u_int8_t version = 0xa5;
    const u_int16_t uflag = 0x5537;
    const u_int64_t rid = 0xfedcba9876543210ULL;
    const u_int16_t pfid = 0xfedcU;
    const u_int16_t lfid = 0xf0e1U;
#ifdef JRNL_32_BIT
    const std::size_t fro = 0xfedcba98UL;
#else
    const std::size_t fro = 0xfedcba9876543210ULL;
#endif
    timespec ts;
    const bool owi = true;

    {
        file_hdr fh1;
        BOOST_CHECK_EQUAL(fh1._magic, 0UL);
        BOOST_CHECK_EQUAL(fh1._version, 0);
        BOOST_CHECK_EQUAL(fh1._eflag, 0);
        BOOST_CHECK_EQUAL(fh1._uflag, 0);
        BOOST_CHECK_EQUAL(fh1._rid, 0ULL);
        BOOST_CHECK_EQUAL(fh1._pfid, 0UL);
        BOOST_CHECK_EQUAL(fh1._lfid, 0U);
        BOOST_CHECK_EQUAL(fh1._fro, std::size_t(0));
        BOOST_CHECK_EQUAL(fh1._ts_sec, std::time_t(0));
        BOOST_CHECK_EQUAL(fh1._ts_nsec, u_int32_t(0));
        BOOST_CHECK(!fh1.get_owi());
    }

    {
        file_hdr fh2(magic, version, rid, pfid, lfid, fro, owi, false);
        BOOST_CHECK_EQUAL(fh2._magic, magic);
        BOOST_CHECK_EQUAL(fh2._version, version);
#ifdef JRNL_LITTLE_ENDIAN
        BOOST_CHECK_EQUAL(fh2._eflag, RHM_LENDIAN_FLAG);
#else
        BOOST_CHECK_EQUAL(fh2._eflag, RHM_BENDIAN_FLAG);
#endif
        BOOST_CHECK_EQUAL(fh2._uflag, (const u_int16_t)rec_hdr::HDR_OVERWRITE_INDICATOR_MASK);
        BOOST_CHECK_EQUAL(fh2._rid, rid);
        BOOST_CHECK_EQUAL(fh2._pfid, pfid );
        BOOST_CHECK_EQUAL(fh2._lfid, lfid);
        BOOST_CHECK_EQUAL(fh2._fro, fro);
        BOOST_CHECK_EQUAL(fh2._ts_sec, std::time_t(0));
        BOOST_CHECK_EQUAL(fh2._ts_nsec, u_int32_t(0));
        ::clock_gettime(CLOCK_REALTIME, &ts);
        fh2.set_time(ts);
        BOOST_CHECK_EQUAL(fh2._ts_sec, ts.tv_sec);
        BOOST_CHECK_EQUAL(fh2._ts_nsec, u_int32_t(ts.tv_nsec));
        BOOST_CHECK(fh2.get_owi());

        fh2._uflag = uflag;
        BOOST_CHECK(fh2.get_owi());

        fh2.set_owi(false);
        BOOST_CHECK(!fh2.get_owi());
        BOOST_CHECK_EQUAL(fh2._uflag,
                (uflag & ~(const u_int16_t)rec_hdr::HDR_OVERWRITE_INDICATOR_MASK));

        fh2.set_owi(true);
        BOOST_CHECK(fh2.get_owi());
        BOOST_CHECK_EQUAL(fh2._uflag, uflag);
    }

    {
        file_hdr fh3(magic, version, rid, pfid, lfid, fro, owi, true);
        BOOST_CHECK_EQUAL(fh3._magic, magic);
        BOOST_CHECK_EQUAL(fh3._version, version);
#ifdef JRNL_LITTLE_ENDIAN
        BOOST_CHECK_EQUAL(fh3._eflag, RHM_LENDIAN_FLAG);
#else
        BOOST_CHECK_EQUAL(fh3._eflag, RHM_BENDIAN_FLAG);
#endif
        BOOST_CHECK_EQUAL(fh3._uflag, (const u_int16_t)rec_hdr::HDR_OVERWRITE_INDICATOR_MASK);
        BOOST_CHECK_EQUAL(fh3._rid, rid);
        BOOST_CHECK_EQUAL(fh3._pfid, pfid);
        BOOST_CHECK_EQUAL(fh3._lfid, lfid);
        BOOST_CHECK_EQUAL(fh3._fro, fro);
        BOOST_CHECK(fh3._ts_sec - ts.tv_sec <= 1); // No more than 1 sec difference
    }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(enq_hdr_class)
{
    cout << test_filename << ".enq_hdr_class: " << flush;
    const u_int32_t magic = 0xfedcba98UL;
    const u_int8_t version = 0xa5;
    const u_int64_t rid = 0xfedcba9876543210ULL;
    const u_int16_t uflag = 0x5537;
#ifdef JRNL_32_BIT
    const std::size_t xidsize = 0xfedcba98UL;
    const std::size_t dsize = 0x76543210UL;
#else
    const std::size_t xidsize = 0xfedcba9876543210ULL;
    const std::size_t dsize = 0x76543210fedcba98ULL;
#endif
    const bool owi = true;

    {
        enq_hdr eh1;
        BOOST_CHECK_EQUAL(eh1._magic, 0UL);
        BOOST_CHECK_EQUAL(eh1._version, 0);
        BOOST_CHECK_EQUAL(eh1._eflag, 0);
        BOOST_CHECK_EQUAL(eh1._uflag, 0);
        BOOST_CHECK_EQUAL(eh1._rid, 0ULL);
        BOOST_CHECK_EQUAL(eh1._xidsize, std::size_t(0));
        BOOST_CHECK_EQUAL(eh1._dsize, std::size_t(0));
        BOOST_CHECK(!eh1.get_owi());
    }

    {
        enq_hdr eh2(magic, version, rid, xidsize, dsize, owi, false);
        BOOST_CHECK_EQUAL(eh2._magic, magic);
        BOOST_CHECK_EQUAL(eh2._version, version);
#ifdef JRNL_LITTLE_ENDIAN
        BOOST_CHECK_EQUAL(eh2._eflag, RHM_LENDIAN_FLAG);
#else
        BOOST_CHECK_EQUAL(eh2._eflag, RHM_BENDIAN_FLAG);
#endif
        BOOST_CHECK_EQUAL(eh2._uflag, (const u_int16_t)rec_hdr::HDR_OVERWRITE_INDICATOR_MASK);
        BOOST_CHECK_EQUAL(eh2._rid, rid);
        BOOST_CHECK_EQUAL(eh2._xidsize, xidsize);
        BOOST_CHECK_EQUAL(eh2._dsize, dsize);
        BOOST_CHECK(eh2.get_owi());
        BOOST_CHECK(!eh2.is_transient());
        BOOST_CHECK(!eh2.is_external());

        eh2._uflag = uflag;
        BOOST_CHECK(eh2.get_owi());
        BOOST_CHECK(eh2.is_transient());
        BOOST_CHECK(eh2.is_external());

        eh2.set_owi(false);
        BOOST_CHECK(!eh2.get_owi());
        BOOST_CHECK(eh2.is_transient());
        BOOST_CHECK(eh2.is_external());
        BOOST_CHECK_EQUAL(eh2._uflag,
                (uflag & ~(const u_int16_t)rec_hdr::HDR_OVERWRITE_INDICATOR_MASK));

        eh2.set_owi(true);
        BOOST_CHECK(eh2.get_owi());
        BOOST_CHECK(eh2.is_transient());
        BOOST_CHECK(eh2.is_external());
        BOOST_CHECK_EQUAL(eh2._uflag, uflag);

        eh2.set_transient(false);
        BOOST_CHECK(eh2.get_owi());
        BOOST_CHECK(!eh2.is_transient());
        BOOST_CHECK(eh2.is_external());
        BOOST_CHECK_EQUAL(eh2._uflag, uflag & ~(const u_int16_t)enq_hdr::ENQ_HDR_TRANSIENT_MASK);

        eh2.set_transient(true);
        BOOST_CHECK(eh2.get_owi());
        BOOST_CHECK(eh2.is_transient());
        BOOST_CHECK(eh2.is_external());
        BOOST_CHECK_EQUAL(eh2._uflag, uflag);

        eh2.set_external(false);
        BOOST_CHECK(eh2.get_owi());
        BOOST_CHECK(eh2.is_transient());
        BOOST_CHECK(!eh2.is_external());
        BOOST_CHECK_EQUAL(eh2._uflag, uflag & ~(const u_int16_t)enq_hdr::ENQ_HDR_EXTERNAL_MASK);

        eh2.set_external(true);
        BOOST_CHECK(eh2.get_owi());
        BOOST_CHECK(eh2.is_transient());
        BOOST_CHECK(eh2.is_external());
        BOOST_CHECK_EQUAL(eh2._uflag, uflag);
    }

    {
        enq_hdr eh3(magic, version, rid, xidsize, dsize, owi, true);
        BOOST_CHECK_EQUAL(eh3._magic, magic);
        BOOST_CHECK_EQUAL(eh3._version, version);
#ifdef JRNL_LITTLE_ENDIAN
        BOOST_CHECK_EQUAL(eh3._eflag, RHM_LENDIAN_FLAG);
#else
        BOOST_CHECK_EQUAL(eh3._eflag, RHM_BENDIAN_FLAG);
#endif
        BOOST_CHECK_EQUAL(eh3._uflag, (const u_int16_t)enq_hdr::ENQ_HDR_TRANSIENT_MASK |
                (const u_int16_t)rec_hdr::HDR_OVERWRITE_INDICATOR_MASK);
        BOOST_CHECK_EQUAL(eh3._rid, rid);
        BOOST_CHECK_EQUAL(eh3._xidsize, xidsize);
        BOOST_CHECK_EQUAL(eh3._dsize, dsize);
        BOOST_CHECK(eh3.get_owi());
        BOOST_CHECK(eh3.is_transient());
        BOOST_CHECK(!eh3.is_external());
    }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(deq_hdr_class)
{
    cout << test_filename << ".deq_hdr_class: " << flush;
    const u_int32_t magic = 0xfedcba98UL;
    const u_int8_t version = 0xa5;
    const u_int16_t uflag = 0x5537;
    const u_int64_t rid = 0xfedcba9876543210ULL;
    const u_int64_t drid = 0x76543210fedcba98ULL;
#ifdef JRNL_32_BIT
    const std::size_t xidsize = 0xfedcba98UL;
#else
    const std::size_t xidsize = 0xfedcba9876543210ULL;
#endif
    const bool owi = true;

    {
        deq_hdr dh1;
        BOOST_CHECK_EQUAL(dh1._magic, 0UL);
        BOOST_CHECK_EQUAL(dh1._version, 0);
        BOOST_CHECK_EQUAL(dh1._eflag, 0);
        BOOST_CHECK_EQUAL(dh1._uflag, 0);
        BOOST_CHECK_EQUAL(dh1._rid, 0ULL);
        BOOST_CHECK_EQUAL(dh1._deq_rid, 0ULL);
        BOOST_CHECK_EQUAL(dh1._xidsize, std::size_t(0));
        BOOST_CHECK(!dh1.get_owi());
    }

    {
        deq_hdr dh2(magic, version, rid, drid, xidsize, owi);
        BOOST_CHECK_EQUAL(dh2._magic, magic);
        BOOST_CHECK_EQUAL(dh2._version, version);
#ifdef JRNL_LITTLE_ENDIAN
        BOOST_CHECK_EQUAL(dh2._eflag, RHM_LENDIAN_FLAG);
#else
        BOOST_CHECK_EQUAL(dh2._eflag, RHM_BENDIAN_FLAG);
#endif
        BOOST_CHECK_EQUAL(dh2._uflag, (const u_int16_t)rec_hdr::HDR_OVERWRITE_INDICATOR_MASK);
        BOOST_CHECK_EQUAL(dh2._rid, rid);
        BOOST_CHECK_EQUAL(dh2._deq_rid, drid);
        BOOST_CHECK_EQUAL(dh2._xidsize, xidsize);
        BOOST_CHECK(dh2.get_owi());

        dh2._uflag = uflag;
        BOOST_CHECK(dh2.get_owi());

        dh2.set_owi(false);
        BOOST_CHECK(!dh2.get_owi());
        BOOST_CHECK_EQUAL(dh2._uflag,
                (uflag & ~(const u_int16_t)rec_hdr::HDR_OVERWRITE_INDICATOR_MASK));

        dh2.set_owi(true);
        BOOST_CHECK(dh2.get_owi());
        BOOST_CHECK_EQUAL(dh2._uflag, uflag);
    }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(txn_hdr_class)
{
    cout << test_filename << ".txn_hdr_class: " << flush;
    const u_int32_t magic = 0xfedcba98UL;
    const u_int8_t version = 0xa5;
    const u_int16_t uflag = 0x5537;
    const u_int64_t rid = 0xfedcba9876543210ULL;
#ifdef JRNL_32_BIT
    const std::size_t xidsize = 0xfedcba98UL;
#else
    const std::size_t xidsize = 0xfedcba9876543210ULL;
#endif
    const bool owi = true;

    {
        txn_hdr th1;
        BOOST_CHECK_EQUAL(th1._magic, 0UL);
        BOOST_CHECK_EQUAL(th1._version, 0);
        BOOST_CHECK_EQUAL(th1._eflag, 0);
        BOOST_CHECK_EQUAL(th1._uflag, 0);
        BOOST_CHECK_EQUAL(th1._rid, 0ULL);
        BOOST_CHECK_EQUAL(th1._xidsize, std::size_t(0));
        BOOST_CHECK(!th1.get_owi());
    }

    {
        txn_hdr th2(magic, version, rid, xidsize, owi);
        BOOST_CHECK_EQUAL(th2._magic, magic);
        BOOST_CHECK_EQUAL(th2._version, version);
#ifdef JRNL_LITTLE_ENDIAN
        BOOST_CHECK_EQUAL(th2._eflag, RHM_LENDIAN_FLAG);
#else
        BOOST_CHECK_EQUAL(th2._eflag, RHM_BENDIAN_FLAG);
#endif
        BOOST_CHECK_EQUAL(th2._uflag, (const u_int16_t)rec_hdr::HDR_OVERWRITE_INDICATOR_MASK);
        BOOST_CHECK_EQUAL(th2._rid, rid);
        BOOST_CHECK_EQUAL(th2._xidsize, xidsize);
        BOOST_CHECK(th2.get_owi());

        th2._uflag = uflag;
        BOOST_CHECK(th2.get_owi());

        th2.set_owi(false);
        BOOST_CHECK(!th2.get_owi());
        BOOST_CHECK_EQUAL(th2._uflag,
                (uflag & ~(const u_int16_t)rec_hdr::HDR_OVERWRITE_INDICATOR_MASK));

        th2.set_owi(true);
        BOOST_CHECK(th2.get_owi());
        BOOST_CHECK_EQUAL(th2._uflag, uflag);
    }
    cout << "ok" << endl;
}

QPID_AUTO_TEST_SUITE_END()
