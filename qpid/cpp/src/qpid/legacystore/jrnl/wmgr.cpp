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
 * \file wmgr.cpp
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::wmgr (write manager). See
 * comments in file wmgr.h for details.
 *
 * \author Kim van der Riet
 */

#include "qpid/legacystore/jrnl/wmgr.h"

#include <cassert>
#include <cerrno>
#include <cstdlib>
#include <cstring>
#include "qpid/legacystore/jrnl/file_hdr.h"
#include "qpid/legacystore/jrnl/jcntl.h"
#include "qpid/legacystore/jrnl/jerrno.h"
#include <sstream>

namespace mrg
{
namespace journal
{

wmgr::wmgr(jcntl* jc, enq_map& emap, txn_map& tmap, wrfc& wrfc):
        pmgr(jc, emap, tmap),
        _wrfc(wrfc),
        _max_dtokpp(0),
        _max_io_wait_us(0),
        _fhdr_base_ptr(0),
        _fhdr_ptr_arr(0),
        _fhdr_aio_cb_arr(0),
        _cached_offset_dblks(0),
        _jfsize_dblks(0),
        _jfsize_pgs(0),
        _num_jfiles(0),
        _enq_busy(false),
        _deq_busy(false),
        _abort_busy(false),
        _commit_busy(false),
        _txn_pending_set()
{}

wmgr::wmgr(jcntl* jc, enq_map& emap, txn_map& tmap, wrfc& wrfc,
        const u_int32_t max_dtokpp, const u_int32_t max_iowait_us):
        pmgr(jc, emap, tmap /* , dtoklp */),
        _wrfc(wrfc),
        _max_dtokpp(max_dtokpp),
        _max_io_wait_us(max_iowait_us),
        _fhdr_base_ptr(0),
        _fhdr_ptr_arr(0),
        _fhdr_aio_cb_arr(0),
        _cached_offset_dblks(0),
        _jfsize_dblks(0),
        _jfsize_pgs(0),
        _num_jfiles(0),
        _enq_busy(false),
        _deq_busy(false),
        _abort_busy(false),
        _commit_busy(false),
        _txn_pending_set()
{}

wmgr::~wmgr()
{
    wmgr::clean();
}

void
wmgr::initialize(aio_callback* const cbp, const u_int32_t wcache_pgsize_sblks,
        const u_int16_t wcache_num_pages, const u_int32_t max_dtokpp, const u_int32_t max_iowait_us,
        std::size_t eo)
{
    _enq_busy = false;
    _deq_busy = false;
    _abort_busy = false;
    _commit_busy = false;
    _max_dtokpp = max_dtokpp;
    _max_io_wait_us = max_iowait_us;

    initialize(cbp, wcache_pgsize_sblks, wcache_num_pages);

    _jfsize_dblks = _jc->jfsize_sblks() * JRNL_SBLK_SIZE;
    _jfsize_pgs = _jc->jfsize_sblks() / _cache_pgsize_sblks;
    assert(_jc->jfsize_sblks() % JRNL_RMGR_PAGE_SIZE == 0);

    if (eo)
    {
        const u_int32_t wr_pg_size_dblks = _cache_pgsize_sblks * JRNL_SBLK_SIZE;
        u_int32_t data_dblks = (eo / JRNL_DBLK_SIZE) - 4; // 4 dblks for file hdr
        _pg_cntr = data_dblks / wr_pg_size_dblks;
        _pg_offset_dblks = data_dblks - (_pg_cntr * wr_pg_size_dblks);
    }
}

iores
wmgr::enqueue(const void* const data_buff, const std::size_t tot_data_len,
        const std::size_t this_data_len, data_tok* dtokp, const void* const xid_ptr,
        const std::size_t xid_len, const bool transient, const bool external)
{
    if (xid_len)
        assert(xid_ptr != 0);

    if (_deq_busy || _abort_busy || _commit_busy)
        return RHM_IORES_BUSY;

    if (this_data_len != tot_data_len && !external)
        return RHM_IORES_NOTIMPL;

    iores res = pre_write_check(WMGR_ENQUEUE, dtokp, xid_len, tot_data_len, external);
    if (res != RHM_IORES_SUCCESS)
        return res;

    bool cont = false;
    if (_enq_busy) // If enqueue() exited last time with RHM_IORES_FULL or RHM_IORES_PAGE_AIOWAIT
    {
        if (dtokp->wstate() == data_tok::ENQ_PART)
            cont = true;
        else
        {
            std::ostringstream oss;
            oss << "This data_tok: id=" << dtokp->id() << " state=" << dtokp->wstate_str();
            throw jexception(jerrno::JERR_WMGR_ENQDISCONT, oss.str(), "wmgr", "enqueue");
        }
    }

    u_int64_t rid = (dtokp->external_rid() | cont) ? dtokp->rid() : _wrfc.get_incr_rid();
    _enq_rec.reset(rid, data_buff, tot_data_len, xid_ptr, xid_len, _wrfc.owi(), transient,
            external);
    if (!cont)
    {
        dtokp->set_rid(rid);
        dtokp->set_dequeue_rid(0);
        if (xid_len)
            dtokp->set_xid(xid_ptr, xid_len);
        else
            dtokp->clear_xid();
        _enq_busy = true;
    }
    bool done = false;
    while (!done)
    {
        assert(_pg_offset_dblks < _cache_pgsize_sblks * JRNL_SBLK_SIZE);
        void* wptr = (void*)((char*)_page_ptr_arr[_pg_index] + _pg_offset_dblks * JRNL_DBLK_SIZE);
        u_int32_t data_offs_dblks = dtokp->dblocks_written();
        u_int32_t ret = _enq_rec.encode(wptr, data_offs_dblks,
                (_cache_pgsize_sblks * JRNL_SBLK_SIZE) - _pg_offset_dblks);

        // Remember fid which contains the record header in case record is split over several files
        if (data_offs_dblks == 0)
            dtokp->set_fid(_wrfc.index());
        _pg_offset_dblks += ret;
        _cached_offset_dblks += ret;
        dtokp->incr_dblocks_written(ret);
        dtokp->incr_pg_cnt();
        _page_cb_arr[_pg_index]._pdtokl->push_back(dtokp);

        // Is the encoding of this record complete?
        if (dtokp->dblocks_written() >= _enq_rec.rec_size_dblks())
        {
            // TODO: Incorrect - must set state to ENQ_CACHED; ENQ_SUBM is set when AIO returns.
            dtokp->set_wstate(data_tok::ENQ_SUBM);
            dtokp->set_dsize(tot_data_len);
            // Only add this data token to page token list when submit is complete, this way
            // long multi-page messages have their token on the page containing the END of the
            // message. AIO callbacks will then only process this token when entire message is
            // enqueued.
            _wrfc.incr_enqcnt(dtokp->fid());

            if (xid_len) // If part of transaction, add to transaction map
            {
                std::string xid((const char*)xid_ptr, xid_len);
                _tmap.insert_txn_data(xid, txn_data(rid, 0, dtokp->fid(), true));
            }
            else
            {
                if (_emap.insert_pfid(rid, dtokp->fid()) < enq_map::EMAP_OK) // fail
                {
                    // The only error code emap::insert_pfid() returns is enq_map::EMAP_DUP_RID.
                    std::ostringstream oss;
                    oss << std::hex << "rid=0x" << rid << " _pfid=0x" << dtokp->fid();
                    throw jexception(jerrno::JERR_MAP_DUPLICATE, oss.str(), "wmgr", "enqueue");
                }
            }

            done = true;
        }
        else
            dtokp->set_wstate(data_tok::ENQ_PART);

        file_header_check(rid, cont, _enq_rec.rec_size_dblks() - data_offs_dblks);
        flush_check(res, cont, done);
    }
    if (dtokp->wstate() >= data_tok::ENQ_SUBM)
        _enq_busy = false;
    return res;
}

iores
wmgr::dequeue(data_tok* dtokp, const void* const xid_ptr, const std::size_t xid_len, const bool txn_coml_commit)
{
    if (xid_len)
        assert(xid_ptr != 0);

    if (_enq_busy || _abort_busy || _commit_busy)
        return RHM_IORES_BUSY;

    iores res = pre_write_check(WMGR_DEQUEUE, dtokp);
    if (res != RHM_IORES_SUCCESS)
        return res;

    bool cont = false;
    if (_deq_busy) // If dequeue() exited last time with RHM_IORES_FULL or RHM_IORES_PAGE_AIOWAIT
    {
        if (dtokp->wstate() == data_tok::DEQ_PART)
            cont = true;
        else
        {
            std::ostringstream oss;
            oss << "This data_tok: id=" << dtokp->id() << " state=" << dtokp->wstate_str();
            throw jexception(jerrno::JERR_WMGR_DEQDISCONT, oss.str(), "wmgr", "dequeue");
        }
    }

    const bool ext_rid = dtokp->external_rid();
    u_int64_t rid = (ext_rid | cont) ? dtokp->rid() : _wrfc.get_incr_rid();
    u_int64_t dequeue_rid = (ext_rid | cont) ? dtokp->dequeue_rid() : dtokp->rid();
    _deq_rec.reset(rid, dequeue_rid, xid_ptr, xid_len, _wrfc.owi(), txn_coml_commit);
    if (!cont)
    {
	    if (!ext_rid)
	    {
		    dtokp->set_rid(rid);
		    dtokp->set_dequeue_rid(dequeue_rid);
	    }
        if (xid_len)
            dtokp->set_xid(xid_ptr, xid_len);
        else
            dtokp->clear_xid();
        dequeue_check(dtokp->xid(), dequeue_rid);
        dtokp->set_dblocks_written(0); // Reset dblks_written from previous op
        _deq_busy = true;
    }
    bool done = false;
    while (!done)
    {
        assert(_pg_offset_dblks < _cache_pgsize_sblks * JRNL_SBLK_SIZE);
        void* wptr = (void*)((char*)_page_ptr_arr[_pg_index] + _pg_offset_dblks * JRNL_DBLK_SIZE);
        u_int32_t data_offs_dblks = dtokp->dblocks_written();
        u_int32_t ret = _deq_rec.encode(wptr, data_offs_dblks,
                (_cache_pgsize_sblks * JRNL_SBLK_SIZE) - _pg_offset_dblks);

        // Remember fid which contains the record header in case record is split over several files
        if (data_offs_dblks == 0)
            dtokp->set_fid(_wrfc.index());
        _pg_offset_dblks += ret;
        _cached_offset_dblks += ret;
        dtokp->incr_dblocks_written(ret);
        dtokp->incr_pg_cnt();
        _page_cb_arr[_pg_index]._pdtokl->push_back(dtokp);

        // Is the encoding of this record complete?
        if (dtokp->dblocks_written() >= _deq_rec.rec_size_dblks())
        {
            // TODO: Incorrect - must set state to ENQ_CACHED; ENQ_SUBM is set when AIO returns.
            dtokp->set_wstate(data_tok::DEQ_SUBM);

            if (xid_len) // If part of transaction, add to transaction map
            {
                // If the enqueue is part of a pending txn, it will not yet be in emap
                _emap.lock(dequeue_rid); // ignore rid not found error
                std::string xid((const char*)xid_ptr, xid_len);
                _tmap.insert_txn_data(xid, txn_data(rid, dequeue_rid, dtokp->fid(), false));
            }
            else
            {
                int16_t fid = _emap.get_remove_pfid(dtokp->dequeue_rid());
                if (fid < enq_map::EMAP_OK) // fail
                {
                    if (fid == enq_map::EMAP_RID_NOT_FOUND)
                    {
                        std::ostringstream oss;
                        oss << std::hex << "rid=0x" << rid;
                        throw jexception(jerrno::JERR_MAP_NOTFOUND, oss.str(), "wmgr", "dequeue");
                    }
                    if (fid == enq_map::EMAP_LOCKED)
                    {
                        std::ostringstream oss;
                        oss << std::hex << "rid=0x" << rid;
                        throw jexception(jerrno::JERR_MAP_LOCKED, oss.str(), "wmgr", "dequeue");
                    }
                }
                _wrfc.decr_enqcnt(fid);
            }

            done = true;
        }
        else
            dtokp->set_wstate(data_tok::DEQ_PART);

        file_header_check(rid, cont, _deq_rec.rec_size_dblks() - data_offs_dblks);
        flush_check(res, cont, done);
    }
    if (dtokp->wstate() >= data_tok::DEQ_SUBM)
        _deq_busy = false;
    return res;
}

iores
wmgr::abort(data_tok* dtokp, const void* const xid_ptr, const std::size_t xid_len)
{
    // commit and abort MUST have a valid xid
    assert(xid_ptr != 0 && xid_len > 0);

    if (_enq_busy || _deq_busy || _commit_busy)
        return RHM_IORES_BUSY;

    iores res = pre_write_check(WMGR_ABORT, dtokp);
    if (res != RHM_IORES_SUCCESS)
        return res;

    bool cont = false;
    if (_abort_busy) // If abort() exited last time with RHM_IORES_FULL or RHM_IORES_PAGE_AIOWAIT
    {
        if (dtokp->wstate() == data_tok::ABORT_PART)
            cont = true;
        else
        {
            std::ostringstream oss;
            oss << "This data_tok: id=" << dtokp->id() << " state=" << dtokp->wstate_str();
            throw jexception(jerrno::JERR_WMGR_DEQDISCONT, oss.str(), "wmgr", "abort");
        }
    }

    u_int64_t rid = (dtokp->external_rid() | cont) ? dtokp->rid() : _wrfc.get_incr_rid();
    _txn_rec.reset(RHM_JDAT_TXA_MAGIC, rid, xid_ptr, xid_len, _wrfc.owi());
    if (!cont)
    {
        dtokp->set_rid(rid);
        dtokp->set_dequeue_rid(0);
        dtokp->set_xid(xid_ptr, xid_len);
        dtokp->set_dblocks_written(0); // Reset dblks_written from previous op
        _abort_busy = true;
    }
    bool done = false;
    while (!done)
    {
        assert(_pg_offset_dblks < _cache_pgsize_sblks * JRNL_SBLK_SIZE);
        void* wptr = (void*)((char*)_page_ptr_arr[_pg_index] + _pg_offset_dblks * JRNL_DBLK_SIZE);
        u_int32_t data_offs_dblks = dtokp->dblocks_written();
        u_int32_t ret = _txn_rec.encode(wptr, data_offs_dblks,
                (_cache_pgsize_sblks * JRNL_SBLK_SIZE) - _pg_offset_dblks);

        // Remember fid which contains the record header in case record is split over several files
        if (data_offs_dblks == 0)
            dtokp->set_fid(_wrfc.index());
        _pg_offset_dblks += ret;
        _cached_offset_dblks += ret;
        dtokp->incr_dblocks_written(ret);
        dtokp->incr_pg_cnt();
        _page_cb_arr[_pg_index]._pdtokl->push_back(dtokp);

        // Is the encoding of this record complete?
        if (dtokp->dblocks_written() >= _txn_rec.rec_size_dblks())
        {
            dtokp->set_wstate(data_tok::ABORT_SUBM);

            // Delete this txn from tmap, unlock any locked records in emap
            std::string xid((const char*)xid_ptr, xid_len);
            txn_data_list tdl = _tmap.get_remove_tdata_list(xid); // tdl will be empty if xid not found
            for (tdl_itr itr = tdl.begin(); itr != tdl.end(); itr++)
            {
				if (!itr->_enq_flag)
				    _emap.unlock(itr->_drid); // ignore rid not found error
                if (itr->_enq_flag)
                    _wrfc.decr_enqcnt(itr->_pfid);
            }
            std::pair<std::set<std::string>::iterator, bool> res = _txn_pending_set.insert(xid);
            if (!res.second)
            {
                std::ostringstream oss;
                oss << std::hex << "_txn_pending_set: xid=\"" << xid << "\"";
                throw jexception(jerrno::JERR_MAP_DUPLICATE, oss.str(), "wmgr", "abort");
            }

            done = true;
        }
        else
            dtokp->set_wstate(data_tok::ABORT_PART);

        file_header_check(rid, cont, _txn_rec.rec_size_dblks() - data_offs_dblks);
        flush_check(res, cont, done);
    }
    if (dtokp->wstate() >= data_tok::ABORT_SUBM)
        _abort_busy = false;
    return res;
}

iores
wmgr::commit(data_tok* dtokp, const void* const xid_ptr, const std::size_t xid_len)
{
    // commit and abort MUST have a valid xid
    assert(xid_ptr != 0 && xid_len > 0);

    if (_enq_busy || _deq_busy || _abort_busy)
        return RHM_IORES_BUSY;

    iores res = pre_write_check(WMGR_COMMIT, dtokp);
    if (res != RHM_IORES_SUCCESS)
        return res;

    bool cont = false;
    if (_commit_busy) // If commit() exited last time with RHM_IORES_FULL or RHM_IORES_PAGE_AIOWAIT
    {
        if (dtokp->wstate() == data_tok::COMMIT_PART)
            cont = true;
        else
        {
            std::ostringstream oss;
            oss << "This data_tok: id=" << dtokp->id() << " state=" << dtokp->wstate_str();
            throw jexception(jerrno::JERR_WMGR_DEQDISCONT, oss.str(), "wmgr", "commit");
        }
    }

    u_int64_t rid = (dtokp->external_rid() | cont) ? dtokp->rid() : _wrfc.get_incr_rid();
    _txn_rec.reset(RHM_JDAT_TXC_MAGIC, rid, xid_ptr, xid_len, _wrfc.owi());
    if (!cont)
    {
        dtokp->set_rid(rid);
        dtokp->set_dequeue_rid(0);
        dtokp->set_xid(xid_ptr, xid_len);
        dtokp->set_dblocks_written(0); // Reset dblks_written from previous op
        _commit_busy = true;
    }
    bool done = false;
    while (!done)
    {
        assert(_pg_offset_dblks < _cache_pgsize_sblks * JRNL_SBLK_SIZE);
        void* wptr = (void*)((char*)_page_ptr_arr[_pg_index] + _pg_offset_dblks * JRNL_DBLK_SIZE);
        u_int32_t data_offs_dblks = dtokp->dblocks_written();
        u_int32_t ret = _txn_rec.encode(wptr, data_offs_dblks,
                (_cache_pgsize_sblks * JRNL_SBLK_SIZE) - _pg_offset_dblks);

        // Remember fid which contains the record header in case record is split over several files
        if (data_offs_dblks == 0)
            dtokp->set_fid(_wrfc.index());
        _pg_offset_dblks += ret;
        _cached_offset_dblks += ret;
        dtokp->incr_dblocks_written(ret);
        dtokp->incr_pg_cnt();
        _page_cb_arr[_pg_index]._pdtokl->push_back(dtokp);

        // Is the encoding of this record complete?
        if (dtokp->dblocks_written() >= _txn_rec.rec_size_dblks())
        {
            dtokp->set_wstate(data_tok::COMMIT_SUBM);

            // Delete this txn from tmap, process records into emap
            std::string xid((const char*)xid_ptr, xid_len);
            txn_data_list tdl = _tmap.get_remove_tdata_list(xid); // tdl will be empty if xid not found
            for (tdl_itr itr = tdl.begin(); itr != tdl.end(); itr++)
            {
                if (itr->_enq_flag) // txn enqueue
                {
                    if (_emap.insert_pfid(itr->_rid, itr->_pfid) < enq_map::EMAP_OK) // fail
                    {
                        // The only error code emap::insert_pfid() returns is enq_map::EMAP_DUP_RID.
                        std::ostringstream oss;
                        oss << std::hex << "rid=0x" << itr->_rid << " _pfid=0x" << itr->_pfid;
                        throw jexception(jerrno::JERR_MAP_DUPLICATE, oss.str(), "wmgr", "commit");
                    }
                }
                else // txn dequeue
                {
                    int16_t fid = _emap.get_remove_pfid(itr->_drid, true);
                    if (fid < enq_map::EMAP_OK) // fail
                    {
                        if (fid == enq_map::EMAP_RID_NOT_FOUND)
                        {
                            std::ostringstream oss;
                            oss << std::hex << "rid=0x" << rid;
                            throw jexception(jerrno::JERR_MAP_NOTFOUND, oss.str(), "wmgr", "dequeue");
                        }
                        if (fid == enq_map::EMAP_LOCKED)
                        {
                            std::ostringstream oss;
                            oss << std::hex << "rid=0x" << rid;
                            throw jexception(jerrno::JERR_MAP_LOCKED, oss.str(), "wmgr", "dequeue");
                        }
                    }
                    _wrfc.decr_enqcnt(fid);
                }
            }
            std::pair<std::set<std::string>::iterator, bool> res = _txn_pending_set.insert(xid);
            if (!res.second)
            {
                std::ostringstream oss;
                oss << std::hex << "_txn_pending_set: xid=\"" << xid << "\"";
                throw jexception(jerrno::JERR_MAP_DUPLICATE, oss.str(), "wmgr", "commit");
            }

            done = true;
        }
        else
            dtokp->set_wstate(data_tok::COMMIT_PART);

        file_header_check(rid, cont, _txn_rec.rec_size_dblks() - data_offs_dblks);
        flush_check(res, cont, done);
    }
    if (dtokp->wstate() >= data_tok::COMMIT_SUBM)
        _commit_busy = false;
    return res;
}

void
wmgr::file_header_check(const u_int64_t rid, const bool cont, const u_int32_t rec_dblks_rem)
{
    // Has the file header been written (i.e. write pointers still at 0)?
    if (_wrfc.is_void())
    {
        bool file_fit = rec_dblks_rem <= _jfsize_dblks;
        bool file_full = rec_dblks_rem == _jfsize_dblks;
        std::size_t fro = 0;
        if (cont)
        {
            if (file_fit && !file_full)
                fro = (rec_dblks_rem + JRNL_SBLK_SIZE) * JRNL_DBLK_SIZE;
        }
        else
            fro = JRNL_SBLK_SIZE * JRNL_DBLK_SIZE;
        write_fhdr(rid, _wrfc.index(), _wrfc.index(), fro);
    }
}

void
wmgr::flush_check(iores& res, bool& cont, bool& done)
{
    // Is page is full, flush
    if (_pg_offset_dblks >= _cache_pgsize_sblks * JRNL_SBLK_SIZE)
    {
        res = write_flush();
        assert(res == RHM_IORES_SUCCESS);

        if (_page_cb_arr[_pg_index]._state == AIO_PENDING && !done)
        {
            res = RHM_IORES_PAGE_AIOWAIT;
            done = true;
        }

        // If file is full, rotate to next file
        if (_pg_cntr >= _jfsize_pgs)
        {
            iores rfres = rotate_file();
            if (rfres != RHM_IORES_SUCCESS)
                res = rfres;
            if (!done)
            {
                if (rfres == RHM_IORES_SUCCESS)
                    cont = true;
                else
                    done = true;
            }
        }
    }
}

iores
wmgr::flush()
{
    iores res = write_flush();
    if (_pg_cntr >= _jfsize_pgs)
    {
        iores rfres = rotate_file();
        if (rfres != RHM_IORES_SUCCESS)
            res = rfres;
    }
    return res;
}

iores
wmgr::write_flush()
{
    iores res = RHM_IORES_SUCCESS;
    // Don't bother flushing an empty page or one that is still in state AIO_PENDING
    if (_cached_offset_dblks)
    {
        if (_page_cb_arr[_pg_index]._state == AIO_PENDING)
            res = RHM_IORES_PAGE_AIOWAIT;
        else
        {
            if (_page_cb_arr[_pg_index]._state != IN_USE)
            {
                std::ostringstream oss;
                oss << "pg_index=" << _pg_index << " state=" << _page_cb_arr[_pg_index].state_str();
                throw jexception(jerrno::JERR_WMGR_BADPGSTATE, oss.str(), "wmgr",
                        "write_flush");
            }

            // Send current page using AIO

            // In manual flushes, dblks may not coincide with sblks, add filler records ("RHMx")
            // if necessary.
            dblk_roundup();

            std::size_t pg_offs = (_pg_offset_dblks - _cached_offset_dblks) * JRNL_DBLK_SIZE;
            aio_cb* aiocbp = &_aio_cb_arr[_pg_index];
            aio::prep_pwrite_2(aiocbp, _wrfc.fh(),
                (char*)_page_ptr_arr[_pg_index] + pg_offs, _cached_offset_dblks * JRNL_DBLK_SIZE,
                _wrfc.subm_offs());
            page_cb* pcbp = (page_cb*)(aiocbp->data); // This page control block (pcb)
            pcbp->_wdblks = _cached_offset_dblks;
            pcbp->_wfh = _wrfc.file_controller();
            if (aio::submit(_ioctx, 1, &aiocbp) < 0)
                throw jexception(jerrno::JERR__AIO, "wmgr", "write_flush");
            _wrfc.add_subm_cnt_dblks(_cached_offset_dblks);
            _wrfc.incr_aio_cnt();
            _aio_evt_rem++;
            _cached_offset_dblks = 0;
            _jc->instr_incr_outstanding_aio_cnt();

           rotate_page(); // increments _pg_index, resets _pg_offset_dblks if req'd
           if (_page_cb_arr[_pg_index]._state == UNUSED)
               _page_cb_arr[_pg_index]._state = IN_USE;
        }
    }
    get_events(UNUSED, 0);
    if (_page_cb_arr[_pg_index]._state == UNUSED)
        _page_cb_arr[_pg_index]._state = IN_USE;
    return res;
}

iores
wmgr::rotate_file()
{
    _pg_cntr = 0;
    iores res = _wrfc.rotate();
    _jc->chk_wr_frot();
    return res;
}

int32_t
wmgr::get_events(page_state state, timespec* const timeout, bool flush)
{
    if (_aio_evt_rem == 0) // no events to get
        return 0;

    int ret = 0;
    if ((ret = aio::getevents(_ioctx, flush ? _aio_evt_rem : 1, _aio_evt_rem/*_cache_num_pages + _jc->num_jfiles()*/, _aio_event_arr, timeout)) < 0)
    {
        if (ret == -EINTR) // Interrupted by signal
            return 0;
        std::ostringstream oss;
        oss << "io_getevents() failed: " << std::strerror(-ret) << " (" << ret << ")";
        throw jexception(jerrno::JERR__AIO, oss.str(), "wmgr", "get_events");
    }

    if (ret == 0 && timeout)
        return jerrno::AIO_TIMEOUT;

    int32_t tot_data_toks = 0;
    for (int i=0; i<ret; i++) // Index of returned AIOs
    {
        if (_aio_evt_rem == 0)
        {
            std::ostringstream oss;
            oss << "_aio_evt_rem; evt " << (i + 1) << " of " << ret;
            throw jexception(jerrno::JERR__UNDERFLOW, oss.str(), "wmgr", "get_events");
        }
        _aio_evt_rem--;
        aio_cb* aiocbp = _aio_event_arr[i].obj; // This I/O control block (iocb)
        page_cb* pcbp = (page_cb*)(aiocbp->data); // This page control block (pcb)
        long aioret = (long)_aio_event_arr[i].res;
        if (aioret < 0)
        {
            std::ostringstream oss;
            oss << "AIO write operation failed: " << std::strerror(-aioret) << " (" << aioret << ") [";
            if (pcbp)
                oss << "pg=" << pcbp->_index;
            else
            {
                file_hdr* fhp = (file_hdr*)aiocbp->u.c.buf;
                oss << "fid=" << fhp->_pfid;
            }
            oss << " size=" << aiocbp->u.c.nbytes;
            oss << " offset=" << aiocbp->u.c.offset << " fh=" << aiocbp->aio_fildes << "]";
            throw jexception(jerrno::JERR__AIO, oss.str(), "wmgr", "get_events");
        }
        if (pcbp) // Page writes have pcb
        {
            u_int32_t s = pcbp->_pdtokl->size();
            std::vector<data_tok*> dtokl;
            dtokl.reserve(s);
            for (u_int32_t k=0; k<s; k++)
            {
                data_tok* dtokp = pcbp->_pdtokl->at(k);
                if (dtokp->decr_pg_cnt() == 0)
                {
                    std::set<std::string>::iterator it;
                    switch (dtokp->wstate())
                    {
                    case data_tok::ENQ_SUBM:
                        dtokl.push_back(dtokp);
                        tot_data_toks++;
                        dtokp->set_wstate(data_tok::ENQ);
                        if (dtokp->has_xid())
                            // Ignoring return value here. A non-zero return can signify that the transaction
                            // has committed or aborted, and which was completed prior to the aio returning.
                            _tmap.set_aio_compl(dtokp->xid(), dtokp->rid());
                        break;
                    case data_tok::DEQ_SUBM:
                        dtokl.push_back(dtokp);
                        tot_data_toks++;
                        dtokp->set_wstate(data_tok::DEQ);
                        if (dtokp->has_xid())
                            // Ignoring return value - see note above.
                            _tmap.set_aio_compl(dtokp->xid(), dtokp->rid());
                        break;
                    case data_tok::ABORT_SUBM:
                        dtokl.push_back(dtokp);
                        tot_data_toks++;
                        dtokp->set_wstate(data_tok::ABORTED);
                        it = _txn_pending_set.find(dtokp->xid());
                        if (it == _txn_pending_set.end())
                        {
                            std::ostringstream oss;
                            oss << std::hex << "_txn_pending_set: abort xid=\"";
                            oss << dtokp->xid() << "\"";
                            throw jexception(jerrno::JERR_MAP_NOTFOUND, oss.str(), "wmgr",
                                    "get_events");
                        }
                        _txn_pending_set.erase(it);
                        break;
                    case data_tok::COMMIT_SUBM:
                        dtokl.push_back(dtokp);
                        tot_data_toks++;
                        dtokp->set_wstate(data_tok::COMMITTED);
                        it = _txn_pending_set.find(dtokp->xid());
                        if (it == _txn_pending_set.end())
                        {
                            std::ostringstream oss;
                            oss << std::hex << "_txn_pending_set: commit xid=\"";
                            oss << dtokp->xid() << "\"";
                            throw jexception(jerrno::JERR_MAP_NOTFOUND, oss.str(), "wmgr",
                                    "get_events");
                        }
                        _txn_pending_set.erase(it);
                        break;
                    case data_tok::ENQ_PART:
                    case data_tok::DEQ_PART:
                    case data_tok::ABORT_PART:
                    case data_tok::COMMIT_PART:
                        // ignore these
                        break;
                    default:
                        // throw for anything else
                        std::ostringstream oss;
                        oss << "dtok_id=" << dtokp->id() << " dtok_state=" << dtokp->wstate_str();
                        throw jexception(jerrno::JERR_WMGR_BADDTOKSTATE, oss.str(), "wmgr",
                                "get_events");
                    } // switch
                } // if
            } // for

            // Increment the completed write offset
            // NOTE: We cannot use _wrfc here, as it may have rotated since submitting count.
            // Use stored pointer to fcntl in the pcb instead.
            pcbp->_wfh->add_wr_cmpl_cnt_dblks(pcbp->_wdblks);
            pcbp->_wfh->decr_aio_cnt();
            _jc->instr_decr_outstanding_aio_cnt();

            // Clean up this pcb's data_tok list
            pcbp->_pdtokl->clear();
            pcbp->_state = state;

            // Perform AIO return callback
            if (_cbp && tot_data_toks)
                _cbp->wr_aio_cb(dtokl);
        }
        else // File header writes have no pcb
        {
            // get lfid from original file header record, update info for that lfid
            file_hdr* fhp = (file_hdr*)aiocbp->u.c.buf;
            u_int32_t lfid = fhp->_lfid;
            fcntl* fcntlp = _jc->get_fcntlp(lfid);
            fcntlp->add_wr_cmpl_cnt_dblks(JRNL_SBLK_SIZE);
            fcntlp->decr_aio_cnt();
            fcntlp->set_wr_fhdr_aio_outstanding(false);
        }
    }

    return tot_data_toks;
}

bool
wmgr::is_txn_synced(const std::string& xid)
{
    // Ignore xid not found error here
    if (_tmap.is_txn_synced(xid) == txn_map::TMAP_NOT_SYNCED)
        return false;
    // Check for outstanding commit/aborts
    std::set<std::string>::iterator it = _txn_pending_set.find(xid);
    return it == _txn_pending_set.end();
}

void
wmgr::initialize(aio_callback* const cbp, const u_int32_t wcache_pgsize_sblks, const u_int16_t wcache_num_pages)
{
    pmgr::initialize(cbp, wcache_pgsize_sblks, wcache_num_pages);
    wmgr::clean();
    _num_jfiles = _jc->num_jfiles();
    if (::posix_memalign(&_fhdr_base_ptr, _sblksize, _sblksize * _num_jfiles))
    {
        wmgr::clean();
        std::ostringstream oss;
        oss << "posix_memalign(): blksize=" << _sblksize << " size=" << _sblksize;
        oss << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR__MALLOC, oss.str(), "wmgr", "initialize");
    }
    _fhdr_ptr_arr = (void**)std::malloc(_num_jfiles * sizeof(void*));
    MALLOC_CHK(_fhdr_ptr_arr, "_fhdr_ptr_arr", "wmgr", "initialize");
    _fhdr_aio_cb_arr = (aio_cb**)std::malloc(sizeof(aio_cb*) * _num_jfiles);
    MALLOC_CHK(_fhdr_aio_cb_arr, "_fhdr_aio_cb_arr", "wmgr", "initialize");
    std::memset(_fhdr_aio_cb_arr, 0, sizeof(aio_cb*) * _num_jfiles);
    for (u_int16_t i=0; i<_num_jfiles; i++)
    {
        _fhdr_ptr_arr[i] = (void*)((char*)_fhdr_base_ptr + _sblksize * i);
        _fhdr_aio_cb_arr[i] = new aio_cb;
    }
    _page_cb_arr[0]._state = IN_USE;
    _ddtokl.clear();
    _cached_offset_dblks = 0;
    _enq_busy = false;
}

iores
wmgr::pre_write_check(const _op_type op, const data_tok* const dtokp,
        const std::size_t xidsize, const std::size_t dsize, const bool external
        ) const
{
    // Check status of current file
    if (!_wrfc.is_wr_reset())
    {
        if (!_wrfc.wr_reset())
            return RHM_IORES_FULL;
    }

    // Check status of current page is ok for writing
    if (_page_cb_arr[_pg_index]._state != IN_USE)
    {
        if (_page_cb_arr[_pg_index]._state == UNUSED)
            _page_cb_arr[_pg_index]._state = IN_USE;
        else if (_page_cb_arr[_pg_index]._state == AIO_PENDING)
            return RHM_IORES_PAGE_AIOWAIT;
        else
        {
            std::ostringstream oss;
            oss << "jrnl=" << _jc->id()  << " op=" << _op_str[op];
            oss << " index=" << _pg_index << " pg_state=" << _page_cb_arr[_pg_index].state_str();
            throw jexception(jerrno::JERR_WMGR_BADPGSTATE, oss.str(), "wmgr", "pre_write_check");
        }
    }

    // operation-specific checks
    switch (op)
    {
        case WMGR_ENQUEUE:
            {
                // Check for enqueue reaching cutoff threshold
                u_int32_t size_dblks = jrec::size_dblks(enq_rec::rec_size(xidsize, dsize,
                        external));
                if (!_enq_busy && _wrfc.enq_threshold(_cached_offset_dblks + size_dblks))
                    return RHM_IORES_ENQCAPTHRESH;
                if (!dtokp->is_writable())
                {
                    std::ostringstream oss;
                    oss << "jrnl=" << _jc->id() << " op=" << _op_str[op];
                    oss << " dtok_id=" << dtokp->id() << " dtok_state=" << dtokp->wstate_str();
                    throw jexception(jerrno::JERR_WMGR_BADDTOKSTATE, oss.str(), "wmgr",
                        "pre_write_check");
                }
            }
            break;
        case WMGR_DEQUEUE:
            if (!dtokp->is_dequeueable())
            {
                std::ostringstream oss;
                oss << "jrnl=" << _jc->id()  << " op=" << _op_str[op];
                oss << " dtok_id=" << dtokp->id() << " dtok_state=" << dtokp->wstate_str();
                throw jexception(jerrno::JERR_WMGR_BADDTOKSTATE, oss.str(), "wmgr",
                        "pre_write_check");
            }
            break;
        case WMGR_ABORT:
            break;
        case WMGR_COMMIT:
            break;
    }

    return RHM_IORES_SUCCESS;
}

void
wmgr::dequeue_check(const std::string& xid, const u_int64_t drid)
{
    // First check emap
    bool found = false;
    int16_t fid = _emap.get_pfid(drid);
    if (fid < enq_map::EMAP_OK) // fail
    {
        if (fid == enq_map::EMAP_RID_NOT_FOUND)
        {
            if (xid.size())
                found = _tmap.data_exists(xid, drid);
        }
        else if (fid == enq_map::EMAP_LOCKED)
        {
            std::ostringstream oss;
            oss << std::hex << "drid=0x" << drid;
            throw jexception(jerrno::JERR_MAP_LOCKED, oss.str(), "wmgr", "dequeue_check");
        }
    }
    else
        found = true;
    if (!found)
    {
        std::ostringstream oss;
        oss << "jrnl=" << _jc->id() << " drid=0x" << std::hex << drid;
        throw jexception(jerrno::JERR_WMGR_DEQRIDNOTENQ, oss.str(), "wmgr", "dequeue_check");
    }
}

void
wmgr::dblk_roundup()
{
    const u_int32_t xmagic = RHM_JDAT_EMPTY_MAGIC;
    u_int32_t wdblks = jrec::size_blks(_cached_offset_dblks, JRNL_SBLK_SIZE) * JRNL_SBLK_SIZE;
    while (_cached_offset_dblks < wdblks)
    {
        void* wptr = (void*)((char*)_page_ptr_arr[_pg_index] + _pg_offset_dblks * JRNL_DBLK_SIZE);
        std::memcpy(wptr, (const void*)&xmagic, sizeof(xmagic));
#ifdef RHM_CLEAN
        std::memset((char*)wptr + sizeof(xmagic), RHM_CLEAN_CHAR, JRNL_DBLK_SIZE - sizeof(xmagic));
#endif
        _pg_offset_dblks++;
        _cached_offset_dblks++;
    }
}

void
wmgr::write_fhdr(u_int64_t rid, u_int16_t fid, u_int16_t lid, std::size_t fro)
{
    file_hdr fhdr(RHM_JDAT_FILE_MAGIC, RHM_JDAT_VERSION, rid, fid, lid, fro, _wrfc.owi(), true);
    std::memcpy(_fhdr_ptr_arr[fid], &fhdr, sizeof(fhdr));
#ifdef RHM_CLEAN
    std::memset((char*)_fhdr_ptr_arr[fid] + sizeof(fhdr), RHM_CLEAN_CHAR, _sblksize - sizeof(fhdr));
#endif
    aio_cb* aiocbp = _fhdr_aio_cb_arr[fid];
    aio::prep_pwrite(aiocbp, _wrfc.fh(), _fhdr_ptr_arr[fid], _sblksize, 0);
    if (aio::submit(_ioctx, 1, &aiocbp) < 0)
        throw jexception(jerrno::JERR__AIO, "wmgr", "write_fhdr");
    _aio_evt_rem++;
    _wrfc.add_subm_cnt_dblks(JRNL_SBLK_SIZE);
    _wrfc.incr_aio_cnt();
    _wrfc.file_controller()->set_wr_fhdr_aio_outstanding(true);
}

void
wmgr::rotate_page()
{
    _page_cb_arr[_pg_index]._state = AIO_PENDING;
    if (_pg_offset_dblks >= _cache_pgsize_sblks * JRNL_SBLK_SIZE)
    {
        _pg_offset_dblks = 0;
        _pg_cntr++;
    }
    if (++_pg_index >= _cache_num_pages)
        _pg_index = 0;
}

void
wmgr::clean()
{
    std::free(_fhdr_base_ptr);
    _fhdr_base_ptr = 0;

    std::free(_fhdr_ptr_arr);
    _fhdr_ptr_arr = 0;

    if (_fhdr_aio_cb_arr)
    {
        for (u_int32_t i=0; i<_num_jfiles; i++)
            delete _fhdr_aio_cb_arr[i];
        std::free(_fhdr_aio_cb_arr);
        _fhdr_aio_cb_arr = 0;
    }
}

const std::string
wmgr::status_str() const
{
    std::ostringstream oss;
    oss << "wmgr: pi=" << _pg_index << " pc=" << _pg_cntr;
    oss << " po=" << _pg_offset_dblks << " aer=" << _aio_evt_rem;
    oss << " edac:" << (_enq_busy?"T":"F") << (_deq_busy?"T":"F");
    oss << (_abort_busy?"T":"F") << (_commit_busy?"T":"F");
    oss << " ps=[";
    for (int i=0; i<_cache_num_pages; i++)
    {
        switch (_page_cb_arr[i]._state)
        {
            case UNUSED:        oss << "-"; break;
            case IN_USE:        oss << "U"; break;
            case AIO_PENDING:   oss << "A"; break;
            case AIO_COMPLETE:  oss << "*"; break;
            default:            oss << _page_cb_arr[i]._state;
        }
    }
    oss << "] " << _wrfc.status_str();
    return oss.str();
}

// static

const char* wmgr::_op_str[] = {"enqueue", "dequeue", "abort", "commit"};

} // namespace journal
} // namespace mrg
