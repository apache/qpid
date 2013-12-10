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

/**
 * \file jrec.cpp
 *
 * Qpid asynchronous store plugin library
 *
 * File containing source code for class mrg::journal::jrec (abstract journal
 * jrecord). See comments in file jrec.h for details.
 *
 * \author Kim van der Riet
 */

#include "qpid/legacystore/jrnl/jrec.h"

#include <iomanip>
#include "qpid/legacystore/jrnl/jerrno.h"
#include "qpid/legacystore/jrnl/jexception.h"
#include <sstream>

namespace mrg
{
namespace journal
{

jrec::jrec() {}
jrec::~jrec() {}

void
jrec::chk_hdr(const rec_hdr& hdr)
{
    if (hdr._magic == 0)
    {
        std::ostringstream oss;
        oss << std::hex << std::setfill('0');
        oss << "enq magic NULL: rid=0x" << hdr._rid;
        throw jexception(jerrno::JERR_JREC_BADRECHDR, oss.str(), "jrec", "chk_hdr");
    }
    if (hdr._version != RHM_JDAT_VERSION)
    {
        std::ostringstream oss;
        oss << std::hex << std::setfill('0');
        oss << "version: rid=0x" << hdr._rid;
        oss << ": expected=0x" << std::setw(2) << (int)RHM_JDAT_VERSION;
        oss << " read=0x" << std::setw(2) << (int)hdr._version;
        throw jexception(jerrno::JERR_JREC_BADRECHDR, oss.str(), "jrec", "chk_hdr");
    }
#if defined (JRNL_LITTLE_ENDIAN)
    u_int8_t endian_flag = RHM_LENDIAN_FLAG;
#else
    u_int8_t endian_flag = RHM_BENDIAN_FLAG;
#endif
    if (hdr._eflag != endian_flag)
    {
        std::ostringstream oss;
        oss << std::hex << std::setfill('0');
        oss << "endian_flag: rid=" << hdr._rid;
        oss << ": expected=0x" << std::setw(2) << (int)endian_flag;
        oss << " read=0x" << std::setw(2) << (int)hdr._eflag;
        throw jexception(jerrno::JERR_JREC_BADRECHDR, oss.str(), "jrec", "chk_hdr");
    }
}

void
jrec::chk_rid(const rec_hdr& hdr, const u_int64_t rid)
{
    if (hdr._rid != rid)
    {
        std::ostringstream oss;
        oss << std::hex << std::setfill('0');
        oss << "rid mismatch: expected=0x" << rid;
        oss << " read=0x" << hdr._rid;
        throw jexception(jerrno::JERR_JREC_BADRECHDR, oss.str(), "jrec", "chk_hdr");
    }
}

void
jrec::chk_tail(const rec_tail& tail, const rec_hdr& hdr)
{
    if (tail._xmagic != ~hdr._magic)
    {
        std::ostringstream oss;
        oss << std::hex << std::setfill('0');
        oss << "magic: rid=0x" << hdr._rid;
        oss << ": expected=0x" << ~hdr._magic;
        oss << " read=0x" << tail._xmagic;
        throw jexception(jerrno::JERR_JREC_BADRECTAIL, oss.str(), "jrec", "chk_tail");
    }
    if (tail._rid != hdr._rid)
    {
        std::ostringstream oss;
        oss << std::hex << std::setfill('0');
        oss << "rid: rid=0x" << hdr._rid;
        oss << ": read=0x" << tail._rid;
        throw jexception(jerrno::JERR_JREC_BADRECTAIL, oss.str(), "jrec", "chk_tail");
    }
}

} // namespace journal
} // namespace mrg
