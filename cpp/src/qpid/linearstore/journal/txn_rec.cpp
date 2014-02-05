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

#include "qpid/linearstore/journal/txn_rec.h"

#include <cassert>
#include <cstring>
#include "qpid/linearstore/journal/Checksum.h"
#include "qpid/linearstore/journal/jexception.h"

namespace qpid {
namespace linearstore {
namespace journal {

txn_rec::txn_rec():
        _xidp(0),
        _xid_buff(0)
{
    ::txn_hdr_init(&_txn_hdr, 0, QLS_JRNL_VERSION, 0, 0, 0, 0);
    ::rec_tail_init(&_txn_tail, 0, 0, 0, 0);
}

txn_rec::~txn_rec()
{
    clean();
}

void
txn_rec::reset(const bool commitFlag, const uint64_t serial, const  uint64_t rid, const void* const xidp,
        const std::size_t xidlen)
{
    _txn_hdr._rhdr._magic = commitFlag ? QLS_TXC_MAGIC : QLS_TXA_MAGIC;
    _txn_hdr._rhdr._serial = serial;
    _txn_hdr._rhdr._rid = rid;
    _txn_hdr._xidsize = xidlen;
    _xidp = xidp;
    _xid_buff = 0;
    _txn_tail._xmagic = ~_txn_hdr._rhdr._magic;
    _txn_tail._serial = serial;
    _txn_tail._rid = rid;
    _txn_tail._checksum = 0UL;
}

uint32_t
txn_rec::encode(void* wptr, uint32_t rec_offs_dblks, uint32_t max_size_dblks, Checksum& checksum)
{
    assert(wptr != 0);
    assert(max_size_dblks > 0);
    assert(_xidp != 0 && _txn_hdr._xidsize > 0);

    std::size_t rec_offs = rec_offs_dblks * QLS_DBLK_SIZE_BYTES;
    std::size_t rem = max_size_dblks * QLS_DBLK_SIZE_BYTES;
    std::size_t wr_cnt = 0;
    if (rec_offs_dblks) // Continuation of split dequeue record (over 2 or more pages)
    {
        if (size_dblks(rec_size()) - rec_offs_dblks > max_size_dblks) // Further split required
        {
            rec_offs -= sizeof(txn_hdr_t);
            std::size_t wsize = _txn_hdr._xidsize > rec_offs ? _txn_hdr._xidsize - rec_offs : 0;
            std::size_t wsize2 = wsize;
            if (wsize)
            {
                if (wsize > rem)
                    wsize = rem;
                std::memcpy(wptr, (const char*)_xidp + rec_offs, wsize);
                wr_cnt += wsize;
                rem -= wsize;
            }
            rec_offs -= _txn_hdr._xidsize - wsize2;
            checksum.addData((unsigned char*)wptr, wr_cnt);
            if (rem)
            {
                _txn_tail._checksum = checksum.getChecksum();
                wsize = sizeof(_txn_tail) > rec_offs ? sizeof(_txn_tail) - rec_offs : 0;
                wsize2 = wsize;
                if (wsize)
                {
                    if (wsize > rem)
                        wsize = rem;
                    std::memcpy((char*)wptr + wr_cnt, (char*)&_txn_tail + rec_offs, wsize);
                    wr_cnt += wsize;
                    rem -= wsize;
                }
                rec_offs -= sizeof(_txn_tail) - wsize2;
            }
            assert(rem == 0);
            assert(rec_offs == 0);
        }
        else // No further split required
        {
            rec_offs -= sizeof(txn_hdr_t);
            std::size_t wsize = _txn_hdr._xidsize > rec_offs ? _txn_hdr._xidsize - rec_offs : 0;
            if (wsize)
            {
                std::memcpy(wptr, (const char*)_xidp + rec_offs, wsize);
                wr_cnt += wsize;
                checksum.addData((unsigned char*)wptr, wr_cnt);
            }
            rec_offs -= _txn_hdr._xidsize - wsize;
            _txn_tail._checksum = checksum.getChecksum();
            wsize = sizeof(_txn_tail) > rec_offs ? sizeof(_txn_tail) - rec_offs : 0;
            if (wsize)
            {
                std::memcpy((char*)wptr + wr_cnt, (char*)&_txn_tail + rec_offs, wsize);
                wr_cnt += wsize;
#ifdef QLS_CLEAN
                std::size_t rec_offs = rec_offs_dblks * QLS_DBLK_SIZE_BYTES;
                std::size_t dblk_rec_size = size_dblks(rec_size() - rec_offs) * QLS_DBLK_SIZE_BYTES;
                std::memset((char*)wptr + wr_cnt, QLS_CLEAN_CHAR, dblk_rec_size - wr_cnt);
#endif
            }
            rec_offs -= sizeof(_txn_tail) - wsize;
            assert(rec_offs == 0);
        }
    }
    else // Start at beginning of data record
    {
        // Assumption: the header will always fit into the first dblk
        std::memcpy(wptr, (void*)&_txn_hdr, sizeof(txn_hdr_t));
        wr_cnt = sizeof(txn_hdr_t);
        if (size_dblks(rec_size()) > max_size_dblks) // Split required
        {
            std::size_t wsize;
            rem -= sizeof(txn_hdr_t);
            if (rem)
            {
                wsize = rem >= _txn_hdr._xidsize ? _txn_hdr._xidsize : rem;
                std::memcpy((char*)wptr + wr_cnt, _xidp, wsize);
                wr_cnt += wsize;
                rem -= wsize;
            }
            checksum.addData((unsigned char*)wptr, wr_cnt);
            if (rem)
            {
                _txn_tail._checksum = checksum.getChecksum();
                wsize = rem >= sizeof(_txn_tail) ? sizeof(_txn_tail) : rem;
                std::memcpy((char*)wptr + wr_cnt, (void*)&_txn_tail, wsize);
                wr_cnt += wsize;
                rem -= wsize;
            }
            assert(rem == 0);
        }
        else // No split required
        {
            std::memcpy((char*)wptr + wr_cnt, _xidp, _txn_hdr._xidsize);
            wr_cnt += _txn_hdr._xidsize;
            checksum.addData((unsigned char*)wptr, wr_cnt);
            _txn_tail._checksum = checksum.getChecksum();
            std::memcpy((char*)wptr + wr_cnt, (void*)&_txn_tail, sizeof(_txn_tail));
            wr_cnt += sizeof(_txn_tail);
#ifdef QLS_CLEAN
            std::size_t dblk_rec_size = size_dblks(rec_size()) * QLS_DBLK_SIZE_BYTES;
            std::memset((char*)wptr + wr_cnt, QLS_CLEAN_CHAR, dblk_rec_size - wr_cnt);
#endif
        }
    }
    return size_dblks(wr_cnt);
}

bool
txn_rec::decode(::rec_hdr_t& h, std::ifstream* ifsp, std::size_t& rec_offs, const std::streampos rec_start)
{
    if (rec_offs == 0)
    {
        // Read header, allocate for xid
        ::rec_hdr_copy(&_txn_hdr._rhdr, &h);
        ifsp->read((char*)&_txn_hdr._xidsize, sizeof(_txn_hdr._xidsize));
        rec_offs = sizeof(::txn_hdr_t);
        _xid_buff = std::malloc(_txn_hdr._xidsize);
        MALLOC_CHK(_xid_buff, "_buff", "txn_rec", "rcv_decode");
    }
    if (rec_offs < sizeof(txn_hdr_t) + _txn_hdr._xidsize)
    {
        // Read xid (or continue reading xid)
        std::size_t offs = rec_offs - sizeof(txn_hdr_t);
        ifsp->read((char*)_xid_buff + offs, _txn_hdr._xidsize - offs);
        std::size_t size_read = ifsp->gcount();
        rec_offs += size_read;
        if (size_read < _txn_hdr._xidsize - offs)
        {
            assert(ifsp->eof());
            // As we may have read past eof, turn off fail bit
            ifsp->clear(ifsp->rdstate()&(~std::ifstream::failbit));
            assert(!ifsp->fail() && !ifsp->bad());
            return false;
        }
    }
    if (rec_offs < sizeof(txn_hdr_t) + _txn_hdr._xidsize + sizeof(rec_tail_t))
    {
        // Read tail (or continue reading tail)
        std::size_t offs = rec_offs - sizeof(txn_hdr_t) - _txn_hdr._xidsize;
        ifsp->read((char*)&_txn_tail + offs, sizeof(rec_tail_t) - offs);
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
        check_rec_tail(rec_start);
    }
    ifsp->ignore(rec_size_dblks() * QLS_DBLK_SIZE_BYTES - rec_size());
    assert(!ifsp->fail() && !ifsp->bad());
    assert(_txn_hdr._xidsize > 0);
    return true;
}

std::size_t
txn_rec::get_xid(void** const xidpp)
{
    if (!_xid_buff)
    {
        *xidpp = 0;
        return 0;
    }
    *xidpp = _xid_buff;
    return _txn_hdr._xidsize;
}

std::string&
txn_rec::str(std::string& str) const
{
    std::ostringstream oss;
    if (_txn_hdr._rhdr._magic == QLS_TXA_MAGIC)
        oss << "dtxa_rec: m=" << _txn_hdr._rhdr._magic;
    else
        oss << "dtxc_rec: m=" << _txn_hdr._rhdr._magic;
    oss << " v=" << (int)_txn_hdr._rhdr._version;
    oss << " rid=" << _txn_hdr._rhdr._rid;
    oss << " xid=\"" << _xidp << "\"";
    str.append(oss.str());
    return str;
}

std::size_t
txn_rec::xid_size() const
{
    return _txn_hdr._xidsize;
}

std::size_t
txn_rec::rec_size() const
{
    return sizeof(txn_hdr_t) + _txn_hdr._xidsize + sizeof(rec_tail_t);
}

void
txn_rec::check_rec_tail(const std::streampos rec_start) const {
    Checksum checksum;
    checksum.addData((const unsigned char*)&_txn_hdr, sizeof(::txn_hdr_t));
    if (_txn_hdr._xidsize > 0) {
        checksum.addData((const unsigned char*)_xid_buff, _txn_hdr._xidsize);
    }
    uint32_t cs = checksum.getChecksum();
    uint16_t res = ::rec_tail_check(&_txn_tail, &_txn_hdr._rhdr, cs);
    if (res != 0) {
        std::stringstream oss;
        oss << std::endl << "  Record offset: 0x" << std::hex << rec_start;
        if (res & ::REC_TAIL_MAGIC_ERR_MASK) {
            oss << std::endl << "  Magic: expected 0x" << ~_txn_hdr._rhdr._magic << "; found 0x" << _txn_tail._xmagic;
        }
        if (res & ::REC_TAIL_SERIAL_ERR_MASK) {
            oss << std::endl << "  Serial: expected 0x" << _txn_hdr._rhdr._serial << "; found 0x" << _txn_tail._serial;
        }
        if (res & ::REC_TAIL_RID_ERR_MASK) {
            oss << std::endl << "  Record Id: expected 0x" << _txn_hdr._rhdr._rid << "; found 0x" << _txn_tail._rid;
        }
        if (res & ::REC_TAIL_CHECKSUM_ERR_MASK) {
            oss << std::endl << "  Checksum: expected 0x" << cs << "; found 0x" << _txn_tail._checksum;
        }
        throw jexception(jerrno::JERR_JREC_BADRECTAIL, oss.str(), "txn_rec", "check_rec_tail");
    }
}

void
txn_rec::clean()
{
    if (_xid_buff) {
        std::free(_xid_buff);
        _xid_buff = 0;
    }
}

}}}
