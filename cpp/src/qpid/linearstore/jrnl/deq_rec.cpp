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

#include "qpid/linearstore/jrnl/deq_rec.h"

#include <cassert>
#include <cstring>
#include <iomanip>
#include "qpid/linearstore/jrnl/jexception.h"

namespace qpid {
namespace linearstore {
namespace journal {

deq_rec::deq_rec():
        _xidp(0),
        _buff(0)
{
    ::deq_hdr_init(&_deq_hdr, QLS_DEQ_MAGIC, QLS_JRNL_VERSION, 0, 0, 0, 0, 0);
    ::rec_tail_copy(&_deq_tail, &_deq_hdr._rhdr, 0);
}

deq_rec::~deq_rec()
{
    clean();
}

void
deq_rec::reset(const uint64_t serial, const uint64_t rid, const  uint64_t drid, const void* const xidp,
               const std::size_t xidlen, const bool txn_coml_commit)
{
    _deq_hdr._rhdr._serial = serial;
    _deq_hdr._rhdr._rid = rid;
    ::set_txn_coml_commit(&_deq_hdr, txn_coml_commit);
    _deq_hdr._deq_rid = drid;
    _deq_hdr._xidsize = xidlen;
    _xidp = xidp;
    _buff = 0;
    _deq_tail._serial = serial;
    _deq_tail._rid = rid;
}

uint32_t
deq_rec::encode(void* wptr, uint32_t rec_offs_dblks, uint32_t max_size_dblks)
{
    assert(wptr != 0);
    assert(max_size_dblks > 0);
    if (_xidp == 0)
        assert(_deq_hdr._xidsize == 0);

    std::size_t rec_offs = rec_offs_dblks * QLS_DBLK_SIZE_BYTES;
    std::size_t rem = max_size_dblks * QLS_DBLK_SIZE_BYTES;
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
#ifdef QLS_CLEAN
                std::size_t rec_offs = rec_offs_dblks * QLS_DBLK_SIZE_BYTES;
                std::size_t dblk_rec_size = size_dblks(rec_size() - rec_offs) * QLS_DBLK_SIZE_BYTES;
                std::memset((char*)wptr + wr_cnt, QLS_CLEAN_CHAR, dblk_rec_size - wr_cnt);
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
#ifdef QLS_CLEAN
            std::size_t dblk_rec_size = size_dblks(rec_size()) * QLS_DBLK_SIZE_BYTES;
            std::memset((char*)wptr + wr_cnt, QLS_CLEAN_CHAR, dblk_rec_size - wr_cnt);
#endif
        }
    }
    return size_dblks(wr_cnt);
}

bool
deq_rec::decode(::rec_hdr_t& h, std::ifstream* ifsp, std::size_t& rec_offs)
{
    uint32_t checksum = 0UL; // TODO: Add checksum math
    if (rec_offs == 0)
    {
        //_deq_hdr.hdr_copy(h);
        ::rec_hdr_copy(&_deq_hdr._rhdr, &h);
        ifsp->read((char*)&_deq_hdr._deq_rid, sizeof(_deq_hdr._deq_rid));
        ifsp->read((char*)&_deq_hdr._xidsize, sizeof(_deq_hdr._xidsize));
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
            (_deq_hdr._xidsize ? _deq_hdr._xidsize + sizeof(rec_tail_t) : 0))
    {
        // Read tail (or continue reading tail)
        std::size_t offs = rec_offs - sizeof(_deq_hdr) - _deq_hdr._xidsize;
        ifsp->read((char*)&_deq_tail + offs, sizeof(rec_tail_t) - offs);
        std::size_t size_read = ifsp->gcount();
        rec_offs += size_read;
        if (size_read < sizeof(rec_tail_t) - offs)
        {
            assert(ifsp->eof());
            // As we may have read past eof, turn off fail bit
            ifsp->clear(ifsp->rdstate()&(~std::ifstream::failbit));
            assert(!ifsp->fail() && !ifsp->bad());
            return false;
        }
    }
    ifsp->ignore(rec_size_dblks() * QLS_DBLK_SIZE_BYTES - rec_size());
    assert(!ifsp->fail() && !ifsp->bad());
    if (_deq_hdr._xidsize) {
        int res = ::rec_tail_check(&_deq_tail, &_deq_hdr._rhdr, checksum);
        if (res != 0) {
            std::stringstream oss;
            switch (res) {
              case 1: oss << std::hex << "Magic: expected 0x" << ~_deq_hdr._rhdr._magic << "; found 0x" << _deq_tail._xmagic; break;
              case 2: oss << std::hex << "Serial: expected 0x" << _deq_hdr._rhdr._serial << "; found 0x" << _deq_tail._serial; break;
              case 3: oss << std::hex << "Record Id: expected 0x" << _deq_hdr._rhdr._rid << "; found 0x" << _deq_tail._rid; break;
              case 4: oss << std::hex << "Checksum: expected 0x" << checksum << "; found 0x" << _deq_tail._checksum; break;
              default: oss << "Unknown error " << res;
            }
            throw jexception(jerrno::JERR_JREC_BADRECTAIL, oss.str(), "deq_rec", "decode"); // TODO: Don't throw exception, log info
        }
    }
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
    oss << "deq_rec: m=" << _deq_hdr._rhdr._magic;
    oss << " v=" << (int)_deq_hdr._rhdr._version;
    oss << " rid=" << _deq_hdr._rhdr._rid;
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
    return sizeof(deq_hdr_t) + (_deq_hdr._xidsize ? _deq_hdr._xidsize + sizeof(rec_tail_t) : 0);
}

void
deq_rec::clean()
{
    // clean up allocated memory here
}

}}}
