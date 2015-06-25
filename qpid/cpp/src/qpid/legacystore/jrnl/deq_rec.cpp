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
 * \file deq_rec.cpp
 *
 * Qpid asynchronous store plugin library
 *
 * This file contains the code for the mrg::journal::deq_rec (journal dequeue
 * record) class. See comments in file deq_rec.h for details.
 *
 * \author Kim van der Riet
 */

#include "qpid/legacystore/jrnl/deq_rec.h"

#include <cassert>
#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <iomanip>
#include "qpid/legacystore/jrnl/jerrno.h"
#include "qpid/legacystore/jrnl/jexception.h"
#include <sstream>

namespace mrg
{
namespace journal
{

deq_rec::deq_rec():
        _deq_hdr(RHM_JDAT_DEQ_MAGIC, RHM_JDAT_VERSION, 0, 0, 0, false),
        _xidp(0),
        _buff(0),
        _deq_tail(_deq_hdr)
{}

deq_rec::deq_rec(const u_int64_t rid, const u_int64_t drid, const void* const xidp,
        const std::size_t xidlen, const bool owi, const bool txn_coml_commit):
        _deq_hdr(RHM_JDAT_DEQ_MAGIC, RHM_JDAT_VERSION, rid, drid, xidlen, owi, txn_coml_commit),
        _xidp(xidp),
        _buff(0),
        _deq_tail(_deq_hdr)
{}

deq_rec::~deq_rec()
{
    clean();
}

void
deq_rec::reset()
{
    _deq_hdr._rid = 0;
    _deq_hdr.set_owi(false);
    _deq_hdr.set_txn_coml_commit(false);
    _deq_hdr._deq_rid = 0;
    _deq_hdr._xidsize = 0;
    _deq_tail._rid = 0;
    _xidp = 0;
    _buff = 0;
}

void
deq_rec::reset(const  u_int64_t rid, const  u_int64_t drid, const void* const xidp,
        const std::size_t xidlen, const bool owi, const bool txn_coml_commit)
{
    _deq_hdr._rid = rid;
    _deq_hdr.set_owi(owi);
    _deq_hdr.set_txn_coml_commit(txn_coml_commit);
    _deq_hdr._deq_rid = drid;
    _deq_hdr._xidsize = xidlen;
    _deq_tail._rid = rid;
    _xidp = xidp;
    _buff = 0;
}

u_int32_t
deq_rec::encode(void* wptr, u_int32_t rec_offs_dblks, u_int32_t max_size_dblks)
{
    assert(wptr != 0);
    assert(max_size_dblks > 0);
    if (_xidp == 0)
        assert(_deq_hdr._xidsize == 0);

    std::size_t rec_offs = rec_offs_dblks * JRNL_DBLK_SIZE;
    std::size_t rem = max_size_dblks * JRNL_DBLK_SIZE;
    std::size_t wr_cnt = 0;
    if (rec_offs_dblks) // Continuation of split dequeue record (over 2 or more pages)
    {
        if (size_dblks(rec_size()) - rec_offs_dblks > max_size_dblks) // Further split required
        {
            rec_offs -= sizeof(_deq_hdr);
            std::size_t wsize = _deq_hdr._xidsize > rec_offs ? _deq_hdr._xidsize - rec_offs : 0;
            std::size_t wsize2 = wsize;
            if (wsize)
            {
                if (wsize > rem)
                    wsize = rem;
                std::memcpy(wptr, (const char*)_xidp + rec_offs, wsize);
                wr_cnt += wsize;
                rem -= wsize;
            }
            rec_offs -= _deq_hdr._xidsize - wsize2;
            if (rem)
            {
                wsize = sizeof(_deq_tail) > rec_offs ? sizeof(_deq_tail) - rec_offs : 0;
                wsize2 = wsize;
                if (wsize)
                {
                    if (wsize > rem)
                        wsize = rem;
                    std::memcpy((char*)wptr + wr_cnt, (char*)&_deq_tail + rec_offs, wsize);
                    wr_cnt += wsize;
                    rem -= wsize;
                }
                rec_offs -= sizeof(_deq_tail) - wsize2;
            }
            assert(rem == 0);
            assert(rec_offs == 0);
        }
        else // No further split required
        {
            rec_offs -= sizeof(_deq_hdr);
            std::size_t wsize = _deq_hdr._xidsize > rec_offs ? _deq_hdr._xidsize - rec_offs : 0;
            if (wsize)
            {
                std::memcpy(wptr, (const char*)_xidp + rec_offs, wsize);
                wr_cnt += wsize;
            }
            rec_offs -= _deq_hdr._xidsize - wsize;
            wsize = sizeof(_deq_tail) > rec_offs ? sizeof(_deq_tail) - rec_offs : 0;
            if (wsize)
            {
                std::memcpy((char*)wptr + wr_cnt, (char*)&_deq_tail + rec_offs, wsize);
                wr_cnt += wsize;
#ifdef RHM_CLEAN
                std::size_t rec_offs = rec_offs_dblks * JRNL_DBLK_SIZE;
                std::size_t dblk_rec_size = size_dblks(rec_size() - rec_offs) * JRNL_DBLK_SIZE;
                std::memset((char*)wptr + wr_cnt, RHM_CLEAN_CHAR, dblk_rec_size - wr_cnt);
#endif
            }
            rec_offs -= sizeof(_deq_tail) - wsize;
            assert(rec_offs == 0);
        }
    }
    else // Start at beginning of data record
    {
        // Assumption: the header will always fit into the first dblk
        std::memcpy(wptr, (void*)&_deq_hdr, sizeof(_deq_hdr));
        wr_cnt = sizeof(_deq_hdr);
        if (size_dblks(rec_size()) > max_size_dblks) // Split required - can only occur with xid
        {
            std::size_t wsize;
            rem -= sizeof(_deq_hdr);
            if (rem)
            {
                wsize = rem >= _deq_hdr._xidsize ? _deq_hdr._xidsize : rem;
                std::memcpy((char*)wptr + wr_cnt, _xidp, wsize);
                wr_cnt += wsize;
                rem -= wsize;
            }
            if (rem)
            {
                wsize = rem >= sizeof(_deq_tail) ? sizeof(_deq_tail) : rem;
                std::memcpy((char*)wptr + wr_cnt, (void*)&_deq_tail, wsize);
                wr_cnt += wsize;
                rem -= wsize;
            }
            assert(rem == 0);
        }
        else // No split required
        {
            if (_deq_hdr._xidsize)
            {
                std::memcpy((char*)wptr + wr_cnt, _xidp, _deq_hdr._xidsize);
                wr_cnt += _deq_hdr._xidsize;
                std::memcpy((char*)wptr + wr_cnt, (void*)&_deq_tail, sizeof(_deq_tail));
                wr_cnt += sizeof(_deq_tail);
            }
#ifdef RHM_CLEAN
            std::size_t dblk_rec_size = size_dblks(rec_size()) * JRNL_DBLK_SIZE;
            std::memset((char*)wptr + wr_cnt, RHM_CLEAN_CHAR, dblk_rec_size - wr_cnt);
#endif
        }
    }
    return size_dblks(wr_cnt);
}

u_int32_t
deq_rec::decode(rec_hdr& h, void* rptr, u_int32_t rec_offs_dblks, u_int32_t max_size_dblks)
{
    assert(rptr != 0);
    assert(max_size_dblks > 0);

    std::size_t rd_cnt = 0;
    if (rec_offs_dblks) // Continuation of record on new page
    {
        const u_int32_t hdr_xid_dblks = size_dblks(deq_hdr::size() + _deq_hdr._xidsize);
        const u_int32_t hdr_xid_tail_dblks = size_dblks(deq_hdr::size() + _deq_hdr._xidsize +
                rec_tail::size());
        const std::size_t rec_offs = rec_offs_dblks * JRNL_DBLK_SIZE;

        if (hdr_xid_tail_dblks - rec_offs_dblks <= max_size_dblks)
        {
            // Remainder of xid fits within this page
            if (rec_offs - deq_hdr::size() < _deq_hdr._xidsize)
            {
                // Part of xid still outstanding, copy remainder of xid and tail
                const std::size_t xid_offs = rec_offs - deq_hdr::size();
                const std::size_t xid_rem = _deq_hdr._xidsize - xid_offs;
                std::memcpy((char*)_buff + xid_offs, rptr, xid_rem);
                rd_cnt = xid_rem;
                std::memcpy((void*)&_deq_tail, ((char*)rptr + rd_cnt), sizeof(_deq_tail));
                chk_tail();
                rd_cnt += sizeof(_deq_tail);
            }
            else
            {
                // Tail or part of tail only outstanding, complete tail
                const std::size_t tail_offs = rec_offs - deq_hdr::size() - _deq_hdr._xidsize;
                const std::size_t tail_rem = rec_tail::size() - tail_offs;
                std::memcpy((char*)&_deq_tail + tail_offs, rptr, tail_rem);
                chk_tail();
                rd_cnt = tail_rem;
            }
        }
        else if (hdr_xid_dblks - rec_offs_dblks <= max_size_dblks)
        {
            // Remainder of xid fits within this page, tail split
            const std::size_t xid_offs = rec_offs - deq_hdr::size();
            const std::size_t xid_rem = _deq_hdr._xidsize - xid_offs;
            std::memcpy((char*)_buff + xid_offs, rptr, xid_rem);
            rd_cnt += xid_rem;
            const std::size_t tail_rem = (max_size_dblks * JRNL_DBLK_SIZE) - rd_cnt;
            if (tail_rem)
            {
                std::memcpy((void*)&_deq_tail, ((char*)rptr + xid_rem), tail_rem);
                rd_cnt += tail_rem;
            }
        }
        else
        {
            // Remainder of xid split
            const std::size_t xid_cp_size = (max_size_dblks * JRNL_DBLK_SIZE);
            std::memcpy((char*)_buff + rec_offs - deq_hdr::size(), rptr, xid_cp_size);
            rd_cnt += xid_cp_size;
        }
    }
    else // Start of record
    {
        // Get and check header
        _deq_hdr.hdr_copy(h);
        rd_cnt = sizeof(rec_hdr);
        //_deq_hdr._deq_rid = *(u_int64_t*)((char*)rptr + rd_cnt);
        std::memcpy((void*)&_deq_hdr._deq_rid, (char*)rptr + rd_cnt, sizeof(u_int64_t));
        rd_cnt += sizeof(u_int64_t);
#if defined(JRNL_BIG_ENDIAN) && defined(JRNL_32_BIT)
        rd_cnt += sizeof(u_int32_t); // Filler 0
#endif
        //_deq_hdr._xidsize = *(std::size_t*)((char*)rptr + rd_cnt);
        std::memcpy((void*)&_deq_hdr._xidsize, (char*)rptr + rd_cnt, sizeof(std::size_t));
        rd_cnt = _deq_hdr.size();
        chk_hdr();
        if (_deq_hdr._xidsize)
        {
            _buff = std::malloc(_deq_hdr._xidsize);
            MALLOC_CHK(_buff, "_buff", "deq_rec", "decode");
            const u_int32_t hdr_xid_dblks = size_dblks(deq_hdr::size() + _deq_hdr._xidsize);
            const u_int32_t hdr_xid_tail_dblks = size_dblks(deq_hdr::size() +  _deq_hdr._xidsize +
                    rec_tail::size());

            // Check if record (header + xid + tail) fits within this page, we can check the
            // tail before the expense of copying data to memory
            if (hdr_xid_tail_dblks <= max_size_dblks)
            {
                // Entire header, xid and tail fits within this page
                std::memcpy(_buff, (char*)rptr + rd_cnt, _deq_hdr._xidsize);
                rd_cnt += _deq_hdr._xidsize;
                std::memcpy((void*)&_deq_tail, (char*)rptr + rd_cnt, sizeof(_deq_tail));
                rd_cnt += sizeof(_deq_tail);
                chk_tail();
            }
            else if (hdr_xid_dblks <= max_size_dblks)
            {
                // Entire header and xid fit within this page, tail split
                std::memcpy(_buff, (char*)rptr + rd_cnt, _deq_hdr._xidsize);
                rd_cnt += _deq_hdr._xidsize;
                const std::size_t tail_rem = (max_size_dblks * JRNL_DBLK_SIZE) - rd_cnt;
                if (tail_rem)
                {
                    std::memcpy((void*)&_deq_tail, (char*)rptr + rd_cnt, tail_rem);
                    rd_cnt += tail_rem;
                }
            }
            else
            {
                // Header fits within this page, xid split
                const std::size_t xid_cp_size = (max_size_dblks * JRNL_DBLK_SIZE) - rd_cnt;
                std::memcpy(_buff, (char*)rptr + rd_cnt, xid_cp_size);
                rd_cnt += xid_cp_size;
            }
        }
    }
    return size_dblks(rd_cnt);
}

bool
deq_rec::rcv_decode(rec_hdr h, std::ifstream* ifsp, std::size_t& rec_offs)
{
    if (rec_offs == 0)
    {
        _deq_hdr.hdr_copy(h);
        ifsp->read((char*)&_deq_hdr._deq_rid, sizeof(u_int64_t));
#if defined(JRNL_BIG_ENDIAN) && defined(JRNL_32_BIT)
        ifsp->ignore(sizeof(u_int32_t)); // _filler0
#endif
        ifsp->read((char*)&_deq_hdr._xidsize, sizeof(std::size_t));
#if defined(JRNL_LITTLE_ENDIAN) && defined(JRNL_32_BIT)
        ifsp->ignore(sizeof(u_int32_t)); // _filler0
#endif
        rec_offs = sizeof(_deq_hdr);
        // Read header, allocate (if req'd) for xid
        if (_deq_hdr._xidsize)
        {
            _buff = std::malloc(_deq_hdr._xidsize);
            MALLOC_CHK(_buff, "_buff", "enq_rec", "rcv_decode");
        }
    }
    if (rec_offs < sizeof(_deq_hdr) + _deq_hdr._xidsize)
    {
        // Read xid (or continue reading xid)
        std::size_t offs = rec_offs - sizeof(_deq_hdr);
        ifsp->read((char*)_buff + offs, _deq_hdr._xidsize - offs);
        std::size_t size_read = ifsp->gcount();
        rec_offs += size_read;
        if (size_read < _deq_hdr._xidsize - offs)
        {
            assert(ifsp->eof());
            // As we may have read past eof, turn off fail bit
            ifsp->clear(ifsp->rdstate()&(~std::ifstream::failbit));
            assert(!ifsp->fail() && !ifsp->bad());
            return false;
        }
    }
    if (rec_offs < sizeof(_deq_hdr) +
            (_deq_hdr._xidsize ? _deq_hdr._xidsize + sizeof(rec_tail) : 0))
    {
        // Read tail (or continue reading tail)
        std::size_t offs = rec_offs - sizeof(_deq_hdr) - _deq_hdr._xidsize;
        ifsp->read((char*)&_deq_tail + offs, sizeof(rec_tail) - offs);
        std::size_t size_read = ifsp->gcount();
        rec_offs += size_read;
        if (size_read < sizeof(rec_tail) - offs)
        {
            assert(ifsp->eof());
            // As we may have read past eof, turn off fail bit
            ifsp->clear(ifsp->rdstate()&(~std::ifstream::failbit));
            assert(!ifsp->fail() && !ifsp->bad());
            return false;
        }
    }
    ifsp->ignore(rec_size_dblks() * JRNL_DBLK_SIZE - rec_size());
    if (_deq_hdr._xidsize)
        chk_tail(); // Throws if tail invalid or record incomplete
    assert(!ifsp->fail() && !ifsp->bad());
    return true;
}

std::size_t
deq_rec::get_xid(void** const xidpp)
{
    if (!_buff)
    {
        *xidpp = 0;
        return 0;
    }
    *xidpp = _buff;
    return _deq_hdr._xidsize;
}

std::string&
deq_rec::str(std::string& str) const
{
    std::ostringstream oss;
    oss << "deq_rec: m=" << _deq_hdr._magic;
    oss << " v=" << (int)_deq_hdr._version;
    oss << " rid=" << _deq_hdr._rid;
    oss << " drid=" << _deq_hdr._deq_rid;
    if (_xidp)
        oss << " xid=\"" << _xidp << "\"";
    str.append(oss.str());
    return str;
}

std::size_t
deq_rec::xid_size() const
{
    return _deq_hdr._xidsize;
}

std::size_t
deq_rec::rec_size() const
{
    return deq_hdr::size() + (_deq_hdr._xidsize ? _deq_hdr._xidsize + rec_tail::size() : 0);
}

void
deq_rec::chk_hdr() const
{
    jrec::chk_hdr(_deq_hdr);
    if (_deq_hdr._magic != RHM_JDAT_DEQ_MAGIC)
    {
        std::ostringstream oss;
        oss << std::hex << std::setfill('0');
        oss << "deq magic: rid=0x" << std::setw(16) << _deq_hdr._rid;
        oss << ": expected=0x" << std::setw(8) << RHM_JDAT_DEQ_MAGIC;
        oss << " read=0x" << std::setw(2) << (int)_deq_hdr._magic;
        throw jexception(jerrno::JERR_JREC_BADRECHDR, oss.str(), "deq_rec", "chk_hdr");
    }
}

void
deq_rec::chk_hdr(u_int64_t rid) const
{
    chk_hdr();
    jrec::chk_rid(_deq_hdr, rid);
}

void
deq_rec::chk_tail() const
{
    jrec::chk_tail(_deq_tail, _deq_hdr);
}

void
deq_rec::clean()
{
    // clean up allocated memory here
}

} // namespace journal
} // namespace mrg
