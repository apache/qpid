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
 * \file rmgr.cpp
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::rmgr (read manager). See
 * comments in file rmgr.h for details.
 *
 * \author Kim van der Riet
 */

#include "qpid/legacystore/jrnl/rmgr.h"

#include <cassert>
#include <cerrno>
#include <cstdlib>
#include "qpid/legacystore/jrnl/jcntl.h"
#include "qpid/legacystore/jrnl/jerrno.h"
#include <sstream>

namespace mrg
{
namespace journal
{

rmgr::rmgr(jcntl* jc, enq_map& emap, txn_map& tmap, rrfc& rrfc):
        pmgr(jc, emap, tmap),
        _rrfc(rrfc),
        _hdr(),
        _fhdr_buffer(0),
        _fhdr_aio_cb_ptr(0),
        _fhdr_rd_outstanding(false)
{}

rmgr::~rmgr()
{
    rmgr::clean();
}

void
rmgr::initialize(aio_callback* const cbp)
{
    pmgr::initialize(cbp, JRNL_RMGR_PAGE_SIZE, JRNL_RMGR_PAGES);
    clean();
    // Allocate memory for reading file header
    if (::posix_memalign(&_fhdr_buffer, _sblksize, _sblksize))
    {
        std::ostringstream oss;
        oss << "posix_memalign(): blksize=" << _sblksize << " size=" << _sblksize;
        oss << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR__MALLOC, oss.str(), "rmgr", "initialize");
    }
    _fhdr_aio_cb_ptr = new aio_cb;
    std::memset(_fhdr_aio_cb_ptr, 0, sizeof(aio_cb));
}

void
rmgr::clean()
{
    std::free(_fhdr_buffer);
    _fhdr_buffer = 0;

    if (_fhdr_aio_cb_ptr)
    {
        delete _fhdr_aio_cb_ptr;
        _fhdr_aio_cb_ptr = 0;
    }
}

iores
rmgr::read(void** const datapp, std::size_t& dsize, void** const xidpp, std::size_t& xidsize,
        bool& transient, bool& external, data_tok* dtokp,  bool ignore_pending_txns)
{
    iores res = pre_read_check(dtokp);
    if (res != RHM_IORES_SUCCESS)
    {
        set_params_null(datapp, dsize, xidpp, xidsize);
        return res;
    }

    if (dtokp->rstate() == data_tok::SKIP_PART)
    {
        if (_page_cb_arr[_pg_index]._state != AIO_COMPLETE)
        {
            aio_cycle();   // check if rd AIOs returned; initiate new reads if possible
            return RHM_IORES_PAGE_AIOWAIT;
        }
        const iores res = skip(dtokp);
        if (res != RHM_IORES_SUCCESS)
        {
            set_params_null(datapp, dsize, xidpp, xidsize);
            return res;
        }
    }
    if (dtokp->rstate() == data_tok::READ_PART)
    {
        assert(dtokp->rid() == _hdr._rid);
        void* rptr = (void*)((char*)_page_ptr_arr[_pg_index] + (_pg_offset_dblks * JRNL_DBLK_SIZE));
        const iores res = read_enq(_hdr, rptr, dtokp);
        dsize = _enq_rec.get_data(datapp);
        xidsize = _enq_rec.get_xid(xidpp);
        transient = _enq_rec.is_transient();
        external = _enq_rec.is_external();
        return res;
    }

    set_params_null(datapp, dsize, xidpp, xidsize);
    _hdr.reset();
    // Read header, determine next record type
    while (true)
    {
        if(dblks_rem() == 0 && _rrfc.is_compl() && !_rrfc.is_wr_aio_outstanding())
        {
            aio_cycle();   // check if rd AIOs returned; initiate new reads if possible
            if(dblks_rem() == 0 && _rrfc.is_compl() && !_rrfc.is_wr_aio_outstanding())
            {
                if (_jc->unflushed_dblks() > 0)
                    _jc->flush();
                else if (!_aio_evt_rem)
                    return RHM_IORES_EMPTY;
            }
        }
        if (_page_cb_arr[_pg_index]._state != AIO_COMPLETE)
        {
            aio_cycle();
            return RHM_IORES_PAGE_AIOWAIT;
        }
        void* rptr = (void*)((char*)_page_ptr_arr[_pg_index] + (_pg_offset_dblks * JRNL_DBLK_SIZE));
        std::memcpy(&_hdr, rptr, sizeof(rec_hdr));
        switch (_hdr._magic)
        {
            case RHM_JDAT_ENQ_MAGIC:
            {
                _enq_rec.reset(); // sets enqueue rec size
                // Check if RID of this rec is still enqueued, if so read it, else skip
                bool is_enq = false;
                int16_t fid = _emap.get_pfid(_hdr._rid);
                if (fid < enq_map::EMAP_OK)
                {
                    bool enforce_txns = !_jc->is_read_only() && !ignore_pending_txns;
                    // Block read for transactionally locked record (only when not recovering)
                    if (fid == enq_map::EMAP_LOCKED && enforce_txns)
                        return RHM_IORES_TXPENDING;

                    // (Recover mode only) Ok, not in emap - now search tmap, if present then read
                    is_enq = _tmap.is_enq(_hdr._rid);
                    if (enforce_txns && is_enq)
                        return RHM_IORES_TXPENDING;
                }
                else
                    is_enq = true;

                if (is_enq) // ok, this record is enqueued, check it, then read it...
                {
                    if (dtokp->rid())
                    {
                        if (_hdr._rid != dtokp->rid())
                        {
                            std::ostringstream oss;
                            oss << std::hex << "rid=0x" << _hdr._rid << "; dtok_rid=0x" << dtokp->rid()
                                << "; dtok_id=0x" << dtokp->id();
                            throw jexception(jerrno::JERR_RMGR_RIDMISMATCH, oss.str(), "rmgr", "read");
                        }
                    }
                    else
                        dtokp->set_rid(_hdr._rid);

// TODO: Add member _fid to pmgr::page_cb which indicates the fid from which this page was
// populated. When this value is set in wmgr::flush() somewehere, then uncomment the following
// check:
//                     if (fid != _page_cb_arr[_pg_index]._fid)
//                     {
//                         std::ostringstream oss;
//                         oss << std::hex << std::setfill('0');
//                         oss << "rid=0x" << std::setw(16) << _hdr._rid;
//                         oss << "; emap_fid=0x" << std::setw(4) << fid;
//                         oss << "; current_fid=" << _rrfc.fid();
//                         throw jexception(jerrno::JERR_RMGR_FIDMISMATCH, oss.str(), "rmgr",
//                              "read");
//                     }

                    const iores res = read_enq(_hdr, rptr, dtokp);
                    dsize = _enq_rec.get_data(datapp);
                    xidsize = _enq_rec.get_xid(xidpp);
                    transient = _enq_rec.is_transient();
                    external = _enq_rec.is_external();
                    return res;
                }
                else // skip this record, it is already dequeued
                    consume_xid_rec(_hdr, rptr, dtokp);
                break;
            }
            case RHM_JDAT_DEQ_MAGIC:
                consume_xid_rec(_hdr, rptr, dtokp);
                break;
            case RHM_JDAT_TXA_MAGIC:
                consume_xid_rec(_hdr, rptr, dtokp);
                break;
            case RHM_JDAT_TXC_MAGIC:
                consume_xid_rec(_hdr, rptr, dtokp);
                break;
            case RHM_JDAT_EMPTY_MAGIC:
                consume_filler();
                break;
            default:
                return RHM_IORES_EMPTY;
        }
    }
}

int32_t
rmgr::get_events(page_state state, timespec* const timeout, bool flush)
{
    if (_aio_evt_rem == 0) // no events to get
        return 0;

    int32_t ret;
    if ((ret = aio::getevents(_ioctx, flush ? _aio_evt_rem : 1, _aio_evt_rem/*_cache_num_pages + _jc->num_jfiles()*/, _aio_event_arr, timeout)) < 0)
    {
        if (ret == -EINTR) // Interrupted by signal
            return 0;
        std::ostringstream oss;
        oss << "io_getevents() failed: " << std::strerror(-ret) << " (" << ret << ")";
        throw jexception(jerrno::JERR__AIO, oss.str(), "rmgr", "get_events");
    }
    if (ret == 0 && timeout)
        return jerrno::AIO_TIMEOUT;

    std::vector<u_int16_t> pil;
    pil.reserve(ret);
    for (int i=0; i<ret; i++) // Index of returned AIOs
    {
        if (_aio_evt_rem == 0)
        {
            std::ostringstream oss;
            oss << "_aio_evt_rem; evt " << (i + 1) << " of " << ret;
            throw jexception(jerrno::JERR__UNDERFLOW, oss.str(), "rmgr", "get_events");
        }
        _aio_evt_rem--;
        aio_cb* aiocbp = _aio_event_arr[i].obj; // This I/O control block (iocb)
        page_cb* pcbp = (page_cb*)(aiocbp->data); // This page control block (pcb)
        long aioret = (long)_aio_event_arr[i].res;
        if (aioret < 0)
        {
            std::ostringstream oss;
            oss << "AIO read operation failed: " << std::strerror(-aioret) << " (" << aioret << ")";
            oss << " [pg=" << pcbp->_index << " buf=" << aiocbp->u.c.buf;
            oss << " rsize=0x" << std::hex << aiocbp->u.c.nbytes;
            oss << " offset=0x" << aiocbp->u.c.offset << std::dec;
            oss << " fh=" << aiocbp->aio_fildes << "]";
            throw jexception(jerrno::JERR__AIO, oss.str(), "rmgr", "get_events");
        }

        if (pcbp) // Page reads have pcb
        {
            if (pcbp->_rfh->rd_subm_cnt_dblks() >= JRNL_SBLK_SIZE) // Detects if write reset of this fcntl obj has occurred.
            {
                // Increment the completed read offset
                // NOTE: We cannot use _rrfc here, as it may have rotated since submitting count.
                // Use stored pointer to fcntl in the pcb instead.
                pcbp->_rdblks = aiocbp->u.c.nbytes / JRNL_DBLK_SIZE;
                pcbp->_rfh->add_rd_cmpl_cnt_dblks(pcbp->_rdblks);
                pcbp->_state = state;
                pil[i] = pcbp->_index;
            }
        }
        else // File header reads have no pcb
        {
            std::memcpy(&_fhdr, _fhdr_buffer, sizeof(file_hdr));
            _rrfc.add_cmpl_cnt_dblks(JRNL_SBLK_SIZE);

            u_int32_t fro_dblks = (_fhdr._fro / JRNL_DBLK_SIZE) - JRNL_SBLK_SIZE;
            // Check fro_dblks does not exceed the write pointers which can happen in some corrupted journal recoveries
            if (fro_dblks > _jc->wr_subm_cnt_dblks(_fhdr._pfid) - JRNL_SBLK_SIZE)
                fro_dblks = _jc->wr_subm_cnt_dblks(_fhdr._pfid) - JRNL_SBLK_SIZE;
            _pg_cntr = fro_dblks / (JRNL_RMGR_PAGE_SIZE * JRNL_SBLK_SIZE);
            u_int32_t tot_pg_offs_dblks = _pg_cntr * JRNL_RMGR_PAGE_SIZE * JRNL_SBLK_SIZE;
            _pg_index = _pg_cntr % JRNL_RMGR_PAGES;
            _pg_offset_dblks = fro_dblks - tot_pg_offs_dblks;
            _rrfc.add_subm_cnt_dblks(tot_pg_offs_dblks);
            _rrfc.add_cmpl_cnt_dblks(tot_pg_offs_dblks);

            _fhdr_rd_outstanding = false;
            _rrfc.set_valid();
        }
    }

    // Perform AIO return callback
    if (_cbp && ret)
        _cbp->rd_aio_cb(pil);
    return ret;
}

void
rmgr::recover_complete()
{}

void
rmgr::invalidate()
{
    if (_rrfc.is_valid())
        _rrfc.set_invalid();
}

void
rmgr::flush(timespec* timeout)
{
    // Wait for any outstanding AIO read operations to complete before synchronizing
    while (_aio_evt_rem)
    {
        if (get_events(AIO_COMPLETE, timeout) == jerrno::AIO_TIMEOUT) // timed out, nothing returned
        {
            throw jexception(jerrno::JERR__TIMEOUT,
                            "Timed out waiting for outstanding read aio to return", "rmgr", "init_validation");
        }
    }

    // Reset all read states and pointers
    for (int i=0; i<_cache_num_pages; i++)
        _page_cb_arr[i]._state = UNUSED;
    _rrfc.unset_findex();
    _pg_index = 0;
    _pg_offset_dblks = 0;
}

bool
rmgr::wait_for_validity(timespec* timeout, const bool throw_on_timeout)
{
    bool timed_out = false;
    while (!_rrfc.is_valid() && !timed_out)
    {
        timed_out = get_events(AIO_COMPLETE, timeout) == jerrno::AIO_TIMEOUT;
        if (timed_out && throw_on_timeout)
            throw jexception(jerrno::JERR__TIMEOUT, "Timed out waiting for read validity", "rmgr", "wait_for_validity");
    }
    return _rrfc.is_valid();
}

iores
rmgr::pre_read_check(data_tok* dtokp)
{
    if (_aio_evt_rem)
        get_events(AIO_COMPLETE, 0);

    if (!_rrfc.is_valid())
        return RHM_IORES_RCINVALID;

    // block reads until outstanding file header read completes as fro is needed to read
    if (_fhdr_rd_outstanding)
        return RHM_IORES_PAGE_AIOWAIT;

    if(dblks_rem() == 0 && _rrfc.is_compl() && !_rrfc.is_wr_aio_outstanding())
    {
        aio_cycle();   // check if any AIOs have returned
        if(dblks_rem() == 0 && _rrfc.is_compl() && !_rrfc.is_wr_aio_outstanding())
        {
            if (_jc->unflushed_dblks() > 0)
                _jc->flush();
            else if (!_aio_evt_rem)
                return RHM_IORES_EMPTY;
        }
    }

    // Check write state of this token is ENQ - required for read
    if (dtokp)
    {
        if (!dtokp->is_readable())
        {
            std::ostringstream oss;
            oss << std::hex << std::setfill('0');
            oss << "dtok_id=0x" << std::setw(8) << dtokp->id();
            oss << "; dtok_rid=0x" << std::setw(16) << dtokp->rid();
            oss << "; dtok_wstate=" << dtokp->wstate_str();
            throw jexception(jerrno::JERR_RMGR_ENQSTATE, oss.str(), "rmgr", "pre_read_check");
        }
    }

    return RHM_IORES_SUCCESS;
}

iores
rmgr::read_enq(rec_hdr& h, void* rptr, data_tok* dtokp)
{
    if (_page_cb_arr[_pg_index]._state != AIO_COMPLETE)
    {
        aio_cycle();   // check if any AIOs have returned
        return RHM_IORES_PAGE_AIOWAIT;
    }

    // Read data from this page, first block will have header and data size.
    u_int32_t dblks_rd = _enq_rec.decode(h, rptr, dtokp->dblocks_read(), dblks_rem());
    dtokp->incr_dblocks_read(dblks_rd);

    _pg_offset_dblks += dblks_rd;

    // If data still incomplete, move to next page and decode again
    while (dtokp->dblocks_read() < _enq_rec.rec_size_dblks())
    {
        rotate_page();
        if (_page_cb_arr[_pg_index]._state != AIO_COMPLETE)
        {
            dtokp->set_rstate(data_tok::READ_PART);
            dtokp->set_dsize(_enq_rec.data_size());
            return RHM_IORES_PAGE_AIOWAIT;
        }

        rptr = (void*)((char*)_page_ptr_arr[_pg_index]);
        dblks_rd = _enq_rec.decode(h, rptr, dtokp->dblocks_read(), dblks_rem());
        dtokp->incr_dblocks_read(dblks_rd);
        _pg_offset_dblks += dblks_rd;
    }

    // If we have finished with this page, rotate it
    if (dblks_rem() == 0)
        rotate_page();

    // Set the record size in dtokp
    dtokp->set_rstate(data_tok::READ);
    dtokp->set_dsize(_enq_rec.data_size());
    return RHM_IORES_SUCCESS;
}

void
rmgr::consume_xid_rec(rec_hdr& h, void* rptr, data_tok* dtokp)
{
    if (h._magic == RHM_JDAT_ENQ_MAGIC)
    {
        enq_hdr ehdr;
        std::memcpy(&ehdr, rptr, sizeof(enq_hdr));
        if (ehdr.is_external())
            dtokp->set_dsize(ehdr._xidsize + sizeof(enq_hdr) + sizeof(rec_tail));
        else
            dtokp->set_dsize(ehdr._xidsize + ehdr._dsize + sizeof(enq_hdr) + sizeof(rec_tail));
    }
    else if (h._magic == RHM_JDAT_DEQ_MAGIC)
    {
        deq_hdr dhdr;
        std::memcpy(&dhdr, rptr, sizeof(deq_hdr));
        if (dhdr._xidsize)
            dtokp->set_dsize(dhdr._xidsize + sizeof(deq_hdr) + sizeof(rec_tail));
        else
            dtokp->set_dsize(sizeof(deq_hdr));
    }
    else if (h._magic == RHM_JDAT_TXA_MAGIC || h._magic == RHM_JDAT_TXC_MAGIC)
    {
        txn_hdr thdr;
        std::memcpy(&thdr, rptr, sizeof(txn_hdr));
        dtokp->set_dsize(thdr._xidsize + sizeof(txn_hdr) + sizeof(rec_tail));
    }
    else
    {
        std::ostringstream oss;
        oss << "Record type found = \"" << (char*)&h._magic << "\"";
        throw jexception(jerrno::JERR_RMGR_BADRECTYPE, oss.str(), "rmgr", "consume_xid_rec");
    }
    dtokp->set_dblocks_read(0);
    skip(dtokp);
}

void
rmgr::consume_filler()
{
    // Filler (Magic "RHMx") is one dblk by definition
    _pg_offset_dblks++;
    if (dblks_rem() == 0)
        rotate_page();
}

iores
rmgr::skip(data_tok* dtokp)
{
    u_int32_t dsize_dblks = jrec::size_dblks(dtokp->dsize());
    u_int32_t tot_dblk_cnt = dtokp->dblocks_read();
    while (true)
    {
        u_int32_t this_dblk_cnt = 0;
        if (dsize_dblks - tot_dblk_cnt > dblks_rem())
            this_dblk_cnt = dblks_rem();
        else
            this_dblk_cnt = dsize_dblks - tot_dblk_cnt;
        if (this_dblk_cnt)
        {
            dtokp->incr_dblocks_read(this_dblk_cnt);
            _pg_offset_dblks += this_dblk_cnt;
            tot_dblk_cnt += this_dblk_cnt;
        }
        // If skip still incomplete, move to next page and decode again
        if (tot_dblk_cnt < dsize_dblks)
        {
            if (dblks_rem() == 0)
                rotate_page();
            if (_page_cb_arr[_pg_index]._state != AIO_COMPLETE)
            {
                dtokp->set_rstate(data_tok::SKIP_PART);
                return RHM_IORES_PAGE_AIOWAIT;
            }
        }
        else
        {
            // Skip complete, put state back to unread
            dtokp->set_rstate(data_tok::UNREAD);
            dtokp->set_dsize(0);
            dtokp->set_dblocks_read(0);

            // If we have finished with this page, rotate it
            if (dblks_rem() == 0)
                rotate_page();
            return RHM_IORES_SUCCESS;
        }
    }
}

iores
rmgr::aio_cycle()
{
    // Perform validity checks
    if (_fhdr_rd_outstanding) // read of file header still outstanding in aio
        return RHM_IORES_SUCCESS;
    if (!_rrfc.is_valid())
    {
        // Flush and reset all read states and pointers
        flush(&jcntl::_aio_cmpl_timeout);

        _jc->get_earliest_fid(); // determine initial file to read; calls _rrfc.set_findex() to set value
        // If this file has not yet been written to, return RHM_IORES_EMPTY
        if (_rrfc.is_void() && !_rrfc.is_wr_aio_outstanding())
            return RHM_IORES_EMPTY;
        init_file_header_read(); // send off AIO read request for file header
        return RHM_IORES_SUCCESS;
    }

    int16_t first_uninit = -1;
    u_int16_t num_uninit = 0;
    u_int16_t num_compl = 0;
    bool outstanding = false;
    // Index must start with current buffer and cycle around so that first
    // uninitialized buffer is initialized first
    for (u_int16_t i=_pg_index; i<_pg_index+_cache_num_pages; i++)
    {
        int16_t ci = i % _cache_num_pages;
        switch (_page_cb_arr[ci]._state)
        {
            case UNUSED:
                if (first_uninit < 0)
                    first_uninit = ci;
                num_uninit++;
                break;
            case IN_USE:
                break;
            case AIO_PENDING:
                outstanding = true;
                break;
            case AIO_COMPLETE:
                num_compl++;
                break;
            default:;
        }
    }
    iores res = RHM_IORES_SUCCESS;
    if (num_uninit)
        res = init_aio_reads(first_uninit, num_uninit);
    else if (num_compl == _cache_num_pages) // This condition exists after invalidation
        res = init_aio_reads(0, _cache_num_pages);
    if (outstanding)
        get_events(AIO_COMPLETE, 0);
    return res;
}

iores
rmgr::init_aio_reads(const int16_t first_uninit, const u_int16_t num_uninit)
{
    for (int16_t i=0; i<num_uninit; i++)
    {
        if (_rrfc.is_void()) // Nothing to do; this file not yet written to
            break;

        if (_rrfc.subm_offs() == 0)
        {
            _rrfc.add_subm_cnt_dblks(JRNL_SBLK_SIZE);
            _rrfc.add_cmpl_cnt_dblks(JRNL_SBLK_SIZE);
        }

        // TODO: Future perf improvement: Do a single AIO read for all available file
        // space into all contiguous empty pages in one AIO operation.

        u_int32_t file_rem_dblks = _rrfc.remaining_dblks();
        file_rem_dblks -= file_rem_dblks % JRNL_SBLK_SIZE; // round down to closest sblk boundary
        u_int32_t pg_size_dblks = JRNL_RMGR_PAGE_SIZE * JRNL_SBLK_SIZE;
        u_int32_t rd_size = file_rem_dblks > pg_size_dblks ? pg_size_dblks : file_rem_dblks;
        if (rd_size)
        {
            int16_t pi = (i + first_uninit) % _cache_num_pages;
            // TODO: For perf, combine contiguous pages into single read
            //   1 or 2 AIOs needed depending on whether read block folds
            aio_cb* aiocbp = &_aio_cb_arr[pi];
            aio::prep_pread_2(aiocbp, _rrfc.fh(), _page_ptr_arr[pi], rd_size * JRNL_DBLK_SIZE, _rrfc.subm_offs());
            if (aio::submit(_ioctx, 1, &aiocbp) < 0)
                throw jexception(jerrno::JERR__AIO, "rmgr", "init_aio_reads");
            _rrfc.add_subm_cnt_dblks(rd_size);
            _aio_evt_rem++;
            _page_cb_arr[pi]._state = AIO_PENDING;
            _page_cb_arr[pi]._rfh = _rrfc.file_controller();
        }
        else // If there is nothing to read for this page, neither will there be for the others...
            break;
        if (_rrfc.file_rotate())
            _rrfc.rotate();
    }
    return RHM_IORES_SUCCESS;
}

void
rmgr::rotate_page()
{
    _page_cb_arr[_pg_index]._rdblks = 0;
    _page_cb_arr[_pg_index]._state = UNUSED;
    if (_pg_offset_dblks >= JRNL_RMGR_PAGE_SIZE * JRNL_SBLK_SIZE)
    {
        _pg_offset_dblks = 0;
        _pg_cntr++;
    }
    if (++_pg_index >= _cache_num_pages)
        _pg_index = 0;
    aio_cycle();
    _pg_offset_dblks = 0;
    // This counter is for bookkeeping only, page rotates are handled directly in init_aio_reads()
    // FIXME: _pg_cntr should be sync'd with aio ops, not use of page as it is now...
    // Need to move reset into if (_rrfc.file_rotate()) above.
    if (_pg_cntr >= (_jc->jfsize_sblks() / JRNL_RMGR_PAGE_SIZE))
        _pg_cntr = 0;
}

u_int32_t
rmgr::dblks_rem() const
{
    return _page_cb_arr[_pg_index]._rdblks - _pg_offset_dblks;
}

void
rmgr::set_params_null(void** const datapp, std::size_t& dsize, void** const xidpp, std::size_t& xidsize)
{
    *datapp = 0;
    dsize = 0;
    *xidpp = 0;
    xidsize = 0;
}

void
rmgr::init_file_header_read()
{
    _jc->fhdr_wr_sync(_rrfc.index()); // wait if the file header write is outstanding
    int rfh = _rrfc.fh();
    aio::prep_pread_2(_fhdr_aio_cb_ptr, rfh, _fhdr_buffer, _sblksize, 0);
    if (aio::submit(_ioctx, 1, &_fhdr_aio_cb_ptr) < 0)
        throw jexception(jerrno::JERR__AIO, "rmgr", "init_file_header_read");
    _aio_evt_rem++;
    _rrfc.add_subm_cnt_dblks(JRNL_SBLK_SIZE);
    _fhdr_rd_outstanding = true;
}

/* TODO (sometime in the future)
const iores
rmgr::get(const u_int64_t& rid, const std::size_t& dsize, const std::size_t& dsize_avail,
        const void** const data, bool auto_discard)
{
    return RHM_IORES_SUCCESS;
}

const iores
rmgr::discard(data_tok* dtokp)
{
    return RHM_IORES_SUCCESS;
}
*/

} // namespace journal
} // namespace mrg
