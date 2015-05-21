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

#include "qpid/linearstore/journal/wmgr.h"

#include <cassert>
#include "qpid/linearstore/journal/aio_callback.h"
#include "qpid/linearstore/journal/Checksum.h"
#include "qpid/linearstore/journal/data_tok.h"
#include "qpid/linearstore/journal/jcntl.h"
#include "qpid/linearstore/journal/JournalFile.h"
#include "qpid/linearstore/journal/LinearFileController.h"
#include "qpid/linearstore/journal/utils/file_hdr.h"

namespace qpid {
namespace linearstore {
namespace journal {

wmgr::wmgr(jcntl* jc,
           enq_map& emap,
           txn_map& tmap,
           LinearFileController& lfc):
        pmgr(jc, emap, tmap),
        _lfc(lfc),
        _max_dtokpp(0),
        _max_io_wait_us(0),
        _cached_offset_dblks(0),
        _enq_busy(false),
        _deq_busy(false),
        _abort_busy(false),
        _commit_busy(false),
        _txn_pending_map()
{}

wmgr::wmgr(jcntl* jc,
           enq_map& emap,
           txn_map& tmap,
           LinearFileController& lfc,
           const uint32_t max_dtokpp,
           const uint32_t max_iowait_us):
        pmgr(jc, emap, tmap),
        _lfc(lfc),
        _max_dtokpp(max_dtokpp),
        _max_io_wait_us(max_iowait_us),
        _cached_offset_dblks(0),
        _enq_busy(false),
        _deq_busy(false),
        _abort_busy(false),
        _commit_busy(false),
        _txn_pending_map()
{}

wmgr::~wmgr()
{
    wmgr::clean();
}

void
wmgr::initialize(aio_callback* const cbp,
                 const uint32_t wcache_pgsize_sblks,
                 const uint16_t wcache_num_pages,
                 const uint32_t max_dtokpp,
                 const uint32_t max_iowait_us,
                 std::size_t end_offset)
{
    _enq_busy = false;
    _deq_busy = false;
    _abort_busy = false;
    _commit_busy = false;
    _max_dtokpp = max_dtokpp;
    _max_io_wait_us = max_iowait_us;

    initialize(cbp, wcache_pgsize_sblks, wcache_num_pages);

    if (end_offset)
    {
        if(!aio::is_aligned((const void*)end_offset, QLS_AIO_ALIGN_BOUNDARY_BYTES)) {
            std::ostringstream oss;
            oss << "Recovery using misaligned end_offset (0x" << std::hex << end_offset << std::dec << ")" << std::endl;
            throw jexception(jerrno::JERR_WMGR_NOTSBLKALIGNED, oss.str(), "wmgr", "initialize");
        }
        const uint32_t wr_pg_size_dblks = _cache_pgsize_sblks * QLS_SBLK_SIZE_DBLKS;
        uint32_t data_dblks = (end_offset / QLS_DBLK_SIZE_BYTES) - (QLS_JRNL_FHDR_RES_SIZE_SBLKS * QLS_SBLK_SIZE_DBLKS); // exclude file header
        _pg_cntr = data_dblks / wr_pg_size_dblks; // Must be set to get file rotation synchronized (this is determined by value of _pg_cntr)
        _pg_offset_dblks = data_dblks - (_pg_cntr * wr_pg_size_dblks);
    }
}

iores
wmgr::enqueue(const void* const data_buff,
              const std::size_t tot_data_len,
              const std::size_t this_data_len,
              data_tok* dtokp,
              const void* const xid_ptr,
              const std::size_t xid_len,
              const bool tpc_flag,
              const bool transient,
              const bool external)
{
//std::cout << _lfc.status(10) << std::endl;
    if (xid_len)
        assert(xid_ptr != 0);

    if (_deq_busy || _abort_busy || _commit_busy) {
        std::ostringstream oss;
        oss << "RHM_IORES_BUSY: enqueue while part way through another op:";
        oss << " _deq_busy=" << (_deq_busy?"T":"F");
        oss << " _abort_busy=" << (_abort_busy?"T":"F");
        oss << " _commit_busy=" << (_commit_busy?"T":"F");
        throw jexception(oss.str()); // TODO: complete exception
    }

    if (this_data_len != tot_data_len && !external) {
        throw jexception("RHM_IORES_NOTIMPL: partial enqueues not implemented"); // TODO: complete exception;
    }

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

    uint64_t rid = (dtokp->external_rid() | cont) ? dtokp->rid() : _lfc.getNextRecordId();
    _enq_rec.reset(_lfc.getCurrentSerial(), rid, data_buff, tot_data_len, xid_ptr, xid_len, transient, external);
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
//std::cout << "---+++ wmgr::enqueue() ENQ rid=0x" << std::hex << rid << " po=0x" << _pg_offset_dblks << " cs=0x" << (_cache_pgsize_sblks * QLS_SBLK_SIZE_DBLKS) << " " << std::dec << std::flush; // DEBUG
    bool done = false;
    Checksum checksum;
    while (!done)
    {
//std::cout << "*" << std::flush; // DEBUG
        assert(_pg_offset_dblks < _cache_pgsize_sblks * QLS_SBLK_SIZE_DBLKS);
        void* wptr = (void*)((char*)_page_ptr_arr[_pg_index] + _pg_offset_dblks * QLS_DBLK_SIZE_BYTES);
        uint32_t data_offs_dblks = dtokp->dblocks_written();
        uint32_t ret = _enq_rec.encode(wptr, data_offs_dblks,
                (_cache_pgsize_sblks * QLS_SBLK_SIZE_DBLKS) - _pg_offset_dblks, checksum);

        // Remember fid which contains the record header in case record is split over several files
        if (data_offs_dblks == 0) {
            dtokp->set_fid(_lfc.getCurrentFileSeqNum());
        }
        _pg_offset_dblks += ret;
        _cached_offset_dblks += ret;
        dtokp->incr_dblocks_written(ret);
        dtokp->incr_pg_cnt();
        _page_cb_arr[_pg_index]._pdtokl->push_back(dtokp);

        // Is the encoding of this record complete?
        if (dtokp->dblocks_written() >= _enq_rec.rec_size_dblks())
        {
//std::cout << "!" << std::flush; // DEBUG
            // TODO: Incorrect - must set state to ENQ_CACHED; ENQ_SUBM is set when AIO returns.
            dtokp->set_wstate(data_tok::ENQ_SUBM);
            dtokp->set_dsize(tot_data_len);
            // Only add this data token to page token list when submit is complete, this way
            // long multi-page messages have their token on the page containing the END of the
            // message. AIO callbacks will then only process this token when entire message is
            // enqueued.
            _lfc.incrEnqueuedRecordCount(dtokp->fid());
//std::cout << "[0x" << std::hex << _lfc.getEnqueuedRecordCount(dtokp->fid()) << std::dec << std::flush; // DEBUG

            if (xid_len) // If part of transaction, add to transaction map
            {
                std::string xid((const char*)xid_ptr, xid_len);
                _tmap.insert_txn_data(xid, txn_data_t(rid, 0, dtokp->fid(), 0, true, tpc_flag, false));
            }
            else
            {
                if (_emap.insert_pfid(rid, dtokp->fid(), 0) < enq_map::EMAP_OK) // fail
                {
                    // The only error code emap::insert_pfid() returns is enq_map::EMAP_DUP_RID.
                    std::ostringstream oss;
                    oss << std::hex << "rid=0x" << rid << " _pfid=0x" << dtokp->fid();
                    throw jexception(jerrno::JERR_MAP_DUPLICATE, oss.str(), "wmgr", "enqueue");
                }
            }

            done = true;
        } else {
//std::cout << "$" << std::flush; // DEBUG
            dtokp->set_wstate(data_tok::ENQ_PART);
        }

        file_header_check(rid, cont, _enq_rec.rec_size_dblks() - data_offs_dblks);
        flush_check(res, cont, done, rid);
    }
    if (dtokp->wstate() >= data_tok::ENQ_SUBM)
        _enq_busy = false;
//std::cout << " res=" << iores_str(res) << " _enq_busy=" << (_enq_busy?"T":"F") << std::endl << std::flush; // DEBUG
    return res;
}

iores
wmgr::dequeue(data_tok* dtokp,
              const void* const xid_ptr,
              const std::size_t xid_len,
              const bool tpc_flag,
              const bool txn_coml_commit)
{
    if (xid_len)
        assert(xid_ptr != 0);

    if (_enq_busy || _abort_busy || _commit_busy) {
        std::ostringstream oss;
        oss << "RHM_IORES_BUSY: dequeue while part way through another op:";
        oss << " _enq_busy=" << (_enq_busy?"T":"F");
        oss << " _abort_busy=" << (_abort_busy?"T":"F");
        oss << " _commit_busy=" << (_commit_busy?"T":"F");
        throw jexception(oss.str()); // TODO: complete exception
    }

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
    uint64_t rid = (ext_rid | cont) ? dtokp->rid() : _lfc.getNextRecordId();
    uint64_t dequeue_rid = (ext_rid | cont) ? dtokp->dequeue_rid() : dtokp->rid();
    _deq_rec.reset(_lfc.getCurrentSerial(), rid, dequeue_rid, xid_ptr, xid_len, txn_coml_commit);
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
//std::cout << "---+++ wmgr::dequeue() DEQ rid=0x" << std::hex << rid << " drid=0x" << dequeue_rid << " " << std::dec << std::flush; // DEBUG
    std::string xid((const char*)xid_ptr, xid_len);
    bool done = false;
    Checksum checksum;
    while (!done)
    {
//std::cout << "*" << std::flush; // DEBUG
        assert(_pg_offset_dblks < _cache_pgsize_sblks * QLS_SBLK_SIZE_DBLKS);
        void* wptr = (void*)((char*)_page_ptr_arr[_pg_index] + _pg_offset_dblks * QLS_DBLK_SIZE_BYTES);
        uint32_t data_offs_dblks = dtokp->dblocks_written();
        uint32_t ret = _deq_rec.encode(wptr, data_offs_dblks,
                (_cache_pgsize_sblks * QLS_SBLK_SIZE_DBLKS) - _pg_offset_dblks, checksum);

        if (data_offs_dblks == 0) {
            uint64_t fid;
            short eres = _emap.get_pfid(dtokp->dequeue_rid(), fid);
            if (eres == enq_map::EMAP_OK) {
                dtokp->set_fid(fid);
            } else if (xid_len > 0) {
                txn_data_list_t tdl = _tmap.get_tdata_list(xid);
                bool found = false;
                for (tdl_const_itr_t i=tdl.begin(); i!=tdl.end() && !found; ++i) {
                    if (i->rid_ == dtokp->dequeue_rid()) {
                        found = true;
                        dtokp->set_fid(i->fid_);
                        break;
                    }
                }
                if (!found) {
                    throw jexception("rid found in neither emap nor tmap, transactional");
                }
            } else {
                throw jexception("rid not found in emap, non-transactional");
            }
        }
        _pg_offset_dblks += ret;
        _cached_offset_dblks += ret;
        dtokp->incr_dblocks_written(ret);
        dtokp->incr_pg_cnt();
        _page_cb_arr[_pg_index]._pdtokl->push_back(dtokp);

        // Is the encoding of this record complete?
        if (dtokp->dblocks_written() >= _deq_rec.rec_size_dblks())
        {
//std::cout << "!" << std::flush; // DEBUG
            // TODO: Incorrect - must set state to ENQ_CACHED; ENQ_SUBM is set when AIO returns.
            dtokp->set_wstate(data_tok::DEQ_SUBM);

            if (xid_len) // If part of transaction, add to transaction map
            {
                // If the enqueue is part of a pending txn, it will not yet be in emap
                _emap.lock(dequeue_rid); // ignore rid not found error
                std::string xid((const char*)xid_ptr, xid_len);
                _tmap.insert_txn_data(xid, txn_data_t(rid, dequeue_rid, dtokp->fid(), 0, false, tpc_flag, false));
            }
            else
            {
                uint64_t fid;
                short eres = _emap.get_remove_pfid(dtokp->dequeue_rid(), fid);
                if (eres < enq_map::EMAP_OK) // fail
                {
                    if (eres == enq_map::EMAP_RID_NOT_FOUND)
                    {
                        std::ostringstream oss;
                        oss << std::hex << "emap: rid=0x" << rid;
                        throw jexception(jerrno::JERR_MAP_NOTFOUND, oss.str(), "wmgr", "dequeue");
                    }
                    if (eres == enq_map::EMAP_LOCKED)
                    {
                        std::ostringstream oss;
                        oss << std::hex << "rid=0x" << rid;
                        throw jexception(jerrno::JERR_MAP_LOCKED, oss.str(), "wmgr", "dequeue");
                    }
                }
            }

            done = true;
        } else {
//std::cout << "$" << std::flush; // DEBUG
            dtokp->set_wstate(data_tok::DEQ_PART);
        }

        file_header_check(rid, cont, _deq_rec.rec_size_dblks() - data_offs_dblks);
        flush_check(res, cont, done, rid);
    }
    if (dtokp->wstate() >= data_tok::DEQ_SUBM)
        _deq_busy = false;
//std::cout << " res=" << iores_str(res) << " _deq_busy=" << (_deq_busy?"T":"F") << std::endl << std::flush; // DEBUG
    return res;
}

iores
wmgr::abort(data_tok* dtokp,
            const void* const xid_ptr,
            const std::size_t xid_len)
{
    // commit and abort MUST have a valid xid
    assert(xid_ptr != 0 && xid_len > 0);

    if (_enq_busy || _deq_busy || _commit_busy) {
        std::ostringstream oss;
        oss << "RHM_IORES_BUSY: abort while part way through another op:";
        oss << " _enq_busy=" << (_enq_busy?"T":"F");
        oss << " _deq_busy=" << (_deq_busy?"T":"F");
        oss << " _commit_busy=" << (_commit_busy?"T":"F");
        throw jexception(oss.str()); // TODO: complete exception
    }

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

    uint64_t rid = (dtokp->external_rid() | cont) ? dtokp->rid() : _lfc.getNextRecordId();
    _txn_rec.reset(false, _lfc.getCurrentSerial(), rid, xid_ptr, xid_len);
    if (!cont)
    {
        dtokp->set_rid(rid);
        dtokp->set_dequeue_rid(0);
        dtokp->set_xid(xid_ptr, xid_len);
        dtokp->set_dblocks_written(0); // Reset dblks_written from previous op
        _abort_busy = true;
    }
    bool done = false;
    Checksum checksum;
    while (!done)
    {
        assert(_pg_offset_dblks < _cache_pgsize_sblks * QLS_SBLK_SIZE_DBLKS);
        void* wptr = (void*)((char*)_page_ptr_arr[_pg_index] + _pg_offset_dblks * QLS_DBLK_SIZE_BYTES);
        uint32_t data_offs_dblks = dtokp->dblocks_written();
        uint32_t ret = _txn_rec.encode(wptr, data_offs_dblks,
                (_cache_pgsize_sblks * QLS_SBLK_SIZE_DBLKS) - _pg_offset_dblks, checksum);

        // Remember fid which contains the record header in case record is split over several files
        if (data_offs_dblks == 0)
            dtokp->set_fid(_lfc.getCurrentFileSeqNum());
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
            txn_data_list_t tdl = _tmap.get_remove_tdata_list(xid); // tdl will be empty if xid not found
            fidl_t fidl;
            for (tdl_itr_t itr = tdl.begin(); itr != tdl.end(); itr++)
            {
				if (!itr->enq_flag_)
				    _emap.unlock(itr->drid_); // ignore rid not found error
                if (itr->enq_flag_) {
                    fidl.push_back(itr->fid_);
                }
            }
            std::pair<pending_txn_map_itr_t, bool> res = _txn_pending_map.insert(std::pair<std::string, fidl_t>(xid, fidl));
            if (!res.second)
            {
                std::ostringstream oss;
                oss << std::hex << "_txn_pending_set: xid=\"" << xid << "\"";
                throw jexception(jerrno::JERR_MAP_DUPLICATE, oss.str(), "wmgr", "abort");
            }

            done = true;
        } else {
            dtokp->set_wstate(data_tok::ABORT_PART);
        }

        file_header_check(rid, cont, _txn_rec.rec_size_dblks() - data_offs_dblks);
        flush_check(res, cont, done, rid);
    }
    if (dtokp->wstate() >= data_tok::ABORT_SUBM)
        _abort_busy = false;
    return res;
}

iores
wmgr::commit(data_tok* dtokp,
             const void* const xid_ptr,
             const std::size_t xid_len)
{
    // commit and abort MUST have a valid xid
    assert(xid_ptr != 0 && xid_len > 0);

    if (_enq_busy || _deq_busy || _abort_busy) {
        std::ostringstream oss;
        oss << "RHM_IORES_BUSY: commit while part way through another op:";
        oss << " _enq_busy=" << (_enq_busy?"T":"F");
        oss << " _deq_busy=" << (_deq_busy?"T":"F");
        oss << " _abort_busy=" << (_abort_busy?"T":"F");
        throw jexception(oss.str()); // TODO: complete exception
    }

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

    uint64_t rid = (dtokp->external_rid() | cont) ? dtokp->rid() : _lfc.getNextRecordId();
    _txn_rec.reset(true, _lfc.getCurrentSerial(), rid, xid_ptr, xid_len);
    if (!cont)
    {
        dtokp->set_rid(rid);
        dtokp->set_dequeue_rid(0);
        dtokp->set_xid(xid_ptr, xid_len);
        dtokp->set_dblocks_written(0); // Reset dblks_written from previous op
        _commit_busy = true;
    }
    bool done = false;
    Checksum checksum;
    while (!done)
    {
        assert(_pg_offset_dblks < _cache_pgsize_sblks * QLS_SBLK_SIZE_DBLKS);
        void* wptr = (void*)((char*)_page_ptr_arr[_pg_index] + _pg_offset_dblks * QLS_DBLK_SIZE_BYTES);
        uint32_t data_offs_dblks = dtokp->dblocks_written();
        uint32_t ret = _txn_rec.encode(wptr, data_offs_dblks,
                (_cache_pgsize_sblks * QLS_SBLK_SIZE_DBLKS) - _pg_offset_dblks, checksum);

        // Remember fid which contains the record header in case record is split over several files
        if (data_offs_dblks == 0)
            dtokp->set_fid(_lfc.getCurrentFileSeqNum());
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
            txn_data_list_t tdl = _tmap.get_remove_tdata_list(xid); // tdl will be empty if xid not found
            fidl_t fidl;
            for (tdl_itr_t itr = tdl.begin(); itr != tdl.end(); itr++)
            {
                if (itr->enq_flag_) // txn enqueue
                {
                    if (_emap.insert_pfid(itr->rid_, itr->fid_, 0) < enq_map::EMAP_OK) // fail
                    {
                        // The only error code emap::insert_pfid() returns is enq_map::EMAP_DUP_RID.
                        std::ostringstream oss;
                        oss << std::hex << "rid=0x" << itr->rid_ << " _pfid=0x" << itr->fid_;
                        throw jexception(jerrno::JERR_MAP_DUPLICATE, oss.str(), "wmgr", "commit");
                    }
                }
                else // txn dequeue
                {
                    uint64_t fid;
                    short eres = _emap.get_remove_pfid(itr->drid_, fid, true);
                    if (eres < enq_map::EMAP_OK) // fail
                    {
                        if (eres == enq_map::EMAP_RID_NOT_FOUND)
                        {
                            std::ostringstream oss;
                            oss << std::hex << "emap: rid=0x" << itr->drid_;
                            throw jexception(jerrno::JERR_MAP_NOTFOUND, oss.str(), "wmgr", "commit");
                        }
                        if (eres == enq_map::EMAP_LOCKED)
                        {
                            std::ostringstream oss;
                            oss << std::hex << "rid=0x" << itr->drid_;
                            throw jexception(jerrno::JERR_MAP_LOCKED, oss.str(), "wmgr", "commit");
                        }
                    }
                    fidl.push_back(fid);
                }
            }
            std::pair<pending_txn_map_itr_t, bool> res = _txn_pending_map.insert(std::pair<std::string, fidl_t>(xid, fidl));
            if (!res.second)
            {
                std::ostringstream oss;
                oss << std::hex << "_txn_pending_set: xid=\"" << xid << "\"";
                throw jexception(jerrno::JERR_MAP_DUPLICATE, oss.str(), "wmgr", "commit");
            }

            done = true;
        } else {
            dtokp->set_wstate(data_tok::COMMIT_PART);
        }

        file_header_check(rid, cont, _txn_rec.rec_size_dblks() - data_offs_dblks);
        flush_check(res, cont, done, rid);
    }
    if (dtokp->wstate() >= data_tok::COMMIT_SUBM)
        _commit_busy = false;
    return res;
}

void
wmgr::file_header_check(const uint64_t rid,
                        const bool cont,
                        const uint32_t rec_dblks_rem)
{
    if (_lfc.isEmpty()) // File never written (i.e. no header or data)
    {
//std::cout << "e" << std::flush;
        std::size_t fro = 0;
        if (cont) {
            bool file_fit = rec_dblks_rem <= _lfc.dataSize_sblks() * QLS_SBLK_SIZE_DBLKS; // Will fit within this journal file
            bool file_full = rec_dblks_rem == _lfc.dataSize_sblks() * QLS_SBLK_SIZE_DBLKS; // Will exactly fill this journal file
            if (file_fit && !file_full) {
                fro = (rec_dblks_rem + (QLS_JRNL_FHDR_RES_SIZE_SBLKS * QLS_SBLK_SIZE_DBLKS)) * QLS_DBLK_SIZE_BYTES;
            }
        } else {
            fro = QLS_JRNL_FHDR_RES_SIZE_SBLKS * QLS_SBLK_SIZE_BYTES;
        }
        _lfc.asyncFileHeaderWrite(_ioctx, 0, rid, fro);
        _aio_evt_rem++;
    }
}

void
wmgr::flush_check(iores& res,
                  bool& cont,
                  bool& done, const uint64_t /*rid*/) // DEBUG
{
    // Is page is full, flush
    if (_pg_offset_dblks >= _cache_pgsize_sblks * QLS_SBLK_SIZE_DBLKS)
    {
//std::cout << "^" << _pg_offset_dblks << ">=" << (_cache_pgsize_sblks * QLS_SBLK_SIZE_DBLKS) << std::flush;
        res = write_flush();
        assert(res == RHM_IORES_SUCCESS);

        if (_page_cb_arr[_pg_index]._state == AIO_PENDING && !done)
        {
            res = RHM_IORES_PAGE_AIOWAIT;
            done = true;
        }

        // If file is full, rotate to next file
        uint32_t dataSize_pgs = _lfc.dataSize_sblks() / _cache_pgsize_sblks;
        if (_pg_cntr >= dataSize_pgs)
        {
//std::cout << _pg_cntr << ">=" << fileSize_pgs << std::flush;
            get_next_file();
            if (!done) {
                cont = true;
            }
//std::cout << "***** wmgr::flush_check(): GET NEXT FILE: rid=0x" << std::hex << rid << std::dec << " res=" << iores_str(res) << " cont=" << (cont?"T":"F") << " done=" << (done?"T":"F") << std::endl; // DEBUG
        }
    }
}

iores
wmgr::flush()
{
    iores res = write_flush();
    uint32_t dataSize_pgs = _lfc.dataSize_sblks() / _cache_pgsize_sblks;
    if (res == RHM_IORES_SUCCESS && _pg_cntr >= dataSize_pgs) {
        get_next_file();
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
        if (_page_cb_arr[_pg_index]._state == AIO_PENDING) {
//std::cout << "#" << std::flush; // DEBUG
            res = RHM_IORES_PAGE_AIOWAIT;
        } else {
            if (_page_cb_arr[_pg_index]._state != IN_USE)
            {
                std::ostringstream oss;
                oss << "pg_index=" << _pg_index << " state=" << _page_cb_arr[_pg_index].state_str();
                throw jexception(jerrno::JERR_WMGR_BADPGSTATE, oss.str(), "wmgr", "write_flush");
            }

            // Send current page using AIO

            // In manual flushes, dblks may not coincide with sblks, add filler records ("RHMx") if necessary.
            dblk_roundup();

            std::size_t pg_offs = (_pg_offset_dblks - _cached_offset_dblks) * QLS_DBLK_SIZE_BYTES;
            aio_cb* aiocbp = &_aio_cb_arr[_pg_index];
            _lfc.asyncPageWrite(_ioctx, aiocbp, (char*)_page_ptr_arr[_pg_index] + pg_offs, _cached_offset_dblks);
            _page_cb_arr[_pg_index]._state = AIO_PENDING;
            _aio_evt_rem++;
//std::cout << "." << _aio_evt_rem << std::flush; // DEBUG
            _cached_offset_dblks = 0;
            _jc->instr_incr_outstanding_aio_cnt();

           rotate_page(); // increments _pg_index, resets _pg_offset_dblks if req'd
           if (_page_cb_arr[_pg_index]._state == UNUSED)
               _page_cb_arr[_pg_index]._state = IN_USE;
        }
    }
    get_events(0, false);
    if (_page_cb_arr[_pg_index]._state == UNUSED)
        _page_cb_arr[_pg_index]._state = IN_USE;
    return res;
}

void
wmgr::get_next_file()
{
    _pg_cntr = 0;
//std::cout << "&&&&& wmgr::get_next_file(): " << status_str() << std::flush << std::endl; // DEBUG
    _lfc.getNextJournalFile();
}

int32_t
wmgr::get_events(timespec* const timeout,
                 bool flush)
{
    if (_aio_evt_rem == 0) // no events to get
        return 0;

    int ret = 0;
    if ((ret = aio::getevents(_ioctx, flush ? _aio_evt_rem : 1, _aio_evt_rem, _aio_event_arr, timeout)) < 0)
    {
        if (ret == -EINTR) // Interrupted by signal
            return 0;
        std::ostringstream oss;
        oss << "io_getevents() failed: " << std::strerror(-ret) << " (" << ret << ") ctx_id=" << _ioctx;
        oss << " min_nr=" << (flush ? _aio_evt_rem : 1) << " nr=" << _aio_evt_rem;
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
//std::cout << "'" << _aio_evt_rem; // DEBUG
        aio_cb* aiocbp = _aio_event_arr[i].obj; // This I/O control block (iocb)
        page_cb* pcbp = (page_cb*)(aiocbp->data); // This page control block (pcb)
        long aioret = (long)_aio_event_arr[i].res;
        if (aioret < 0) {
            std::ostringstream oss;
            oss << "AIO write operation failed: " << std::strerror(-aioret) << " (" << aioret << ")" << std::endl;
            oss << "  data=" << _aio_event_arr[i].data << std::endl;
            oss << "  obj=" << _aio_event_arr[i].obj << std::endl;
            oss << "  res=" << _aio_event_arr[i].res << std::endl;
            oss << "  res2=" << _aio_event_arr[i].res2 << std::endl;
            oss << "  iocb->data=" << aiocbp->data << std::endl;
            oss << "  iocb->key=" << aiocbp->key << std::endl;
            oss << "  iocb->aio_lio_opcode=" << aiocbp->aio_lio_opcode << std::endl;
            oss << "  iocb->aio_reqprio=" << aiocbp->aio_reqprio << std::endl;
            oss << "  iocb->aio_fildes=" << aiocbp->aio_fildes << std::endl;
            oss << "  iocb->u.c.buf=" << aiocbp->u.c.buf << std::endl;
            oss << "  iocb->u.c.nbytes=0x" << std::hex <<  aiocbp->u.c.nbytes << std::dec << " (" << aiocbp->u.c.nbytes << ")" << std::endl;
            oss << "  iocb->u.c.offset=0x" << std::hex << aiocbp->u.c.offset << std::dec << " (" << aiocbp->u.c.offset << ")" << std::endl;
            oss << "  iocb->u.c.flags=0x" << std::hex << aiocbp->u.c.flags << std::dec << " (" << aiocbp->u.c.flags << ")" << std::endl;
            oss << "  iocb->u.c.resfd=" << aiocbp->u.c.resfd << std::endl;
            if (pcbp) {
                oss << "  Page Control Block: (iocb->data):" << std::endl;
                oss << "    pcb.index=" << pcbp->_index << std::endl;
                oss << "    pcb.state=" << pcbp->_state << " (" << pmgr::page_state_str(pcbp->_state) << ")" << std::endl;
                oss << "    pcb.frid=0x" << std::hex << pcbp->_frid << std::dec << std::endl;
                oss << "    pcb.wdblks=0x" << std::hex << pcbp->_wdblks << std::dec << std::endl;
                oss << "    pcb.pdtokl.size=" << pcbp->_pdtokl->size() << std::endl;
                oss << "    pcb.pbuff=" << pcbp->_pbuff << std::endl;
                oss << "    JournalFile (pcb.jfp):" << std::endl;
                oss << pcbp->_jfp->status_str(6) << std::endl;
            } else {
                file_hdr_t* fhp = (file_hdr_t*)aiocbp->u.c.buf;
                oss << "fnum=" << fhp->_file_number;
                oss << " qname=" << std::string((char*)fhp + sizeof(file_hdr_t), fhp->_queue_name_len);
            }
            throw jexception(jerrno::JERR__AIO, oss.str(), "wmgr", "get_events");
        }
        if (pcbp) // Page writes have pcb
        {
//std::cout << "p"; // DEBUG
            uint32_t s = pcbp->_pdtokl->size();
            std::vector<data_tok*> dtokl;
            dtokl.reserve(s);
            for (uint32_t k=0; k<s; k++)
            {
                data_tok* dtokp = pcbp->_pdtokl->at(k);
                if (dtokp->decr_pg_cnt() == 0)
                {
                    pending_txn_map_itr_t it;
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
                        if (!dtokp->has_xid()) {
                            _lfc.decrEnqueuedRecordCount(dtokp->fid());
                        }
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
                        it = _txn_pending_map.find(dtokp->xid());
                        if (it == _txn_pending_map.end())
                        {
                            std::ostringstream oss;
                            oss << std::hex << "_txn_pending_set: abort xid=\""
                                            << qpid::linearstore::journal::jcntl::str2hexnum(dtokp->xid()) << "\"";
                            throw jexception(jerrno::JERR_MAP_NOTFOUND, oss.str(), "wmgr", "get_events");
                        }
                        for (fidl_itr_t i=it->second.begin(); i!=it->second.end(); ++i) {
                            _lfc.decrEnqueuedRecordCount(*i);
                        }
                        _txn_pending_map.erase(it);
                        break;
                    case data_tok::COMMIT_SUBM:
                        dtokl.push_back(dtokp);
                        tot_data_toks++;
                        dtokp->set_wstate(data_tok::COMMITTED);
                        it = _txn_pending_map.find(dtokp->xid());
                        if (it == _txn_pending_map.end())
                        {
                            std::ostringstream oss;
                            oss << std::hex << "_txn_pending_set: commit xid=\""
                                            << qpid::linearstore::journal::jcntl::str2hexnum(dtokp->xid()) << "\"";
                            throw jexception(jerrno::JERR_MAP_NOTFOUND, oss.str(), "wmgr", "get_events");
                        }
                        for (fidl_itr_t i=it->second.begin(); i!=it->second.end(); ++i) {
                            _lfc.decrEnqueuedRecordCount(*i);
                        }
                        _txn_pending_map.erase(it);
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
                    }
                }
            }

            // Increment the completed write offset
            // NOTE: We cannot use _wrfc here, as it may have rotated since submitting count.
            // Use stored pointer to fcntl in the pcb instead.
            pcbp->_jfp->addCompletedDblkCount(pcbp->_wdblks);
            pcbp->_jfp->decrOutstandingAioOperationCount();
            _jc->instr_decr_outstanding_aio_cnt();

            // Clean up this pcb's data_tok list
            pcbp->_pdtokl->clear();
            pcbp->_state = UNUSED;
//std::cout << "c" << pcbp->_index << pcbp->state_str(); // DEBUG

            // Perform AIO return callback
            if (_cbp && tot_data_toks)
                _cbp->wr_aio_cb(dtokl);
        }
        else // File header writes have no pcb
        {
//std::cout << "f"; // DEBUG
            file_hdr_t* fhp = (file_hdr_t*)aiocbp->u.c.buf;
            _lfc.addWriteCompletedDblkCount(fhp->_file_number, QLS_JRNL_FHDR_RES_SIZE_SBLKS * QLS_SBLK_SIZE_DBLKS);
            _lfc.decrOutstandingAioOperationCount(fhp->_file_number);
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
    pending_txn_map_itr_t it = _txn_pending_map.find(xid);
    return it == _txn_pending_map.end();
}

void
wmgr::initialize(aio_callback* const cbp,
                 const uint32_t wcache_pgsize_sblks,
                 const uint16_t wcache_num_pages)
{

    pmgr::initialize(cbp, wcache_pgsize_sblks, wcache_num_pages);
    wmgr::clean();
    _page_cb_arr[0]._state = IN_USE;
    _cached_offset_dblks = 0;
    _enq_busy = false;
}

iores
wmgr::pre_write_check(const _op_type op,
                      const data_tok* const dtokp,
                      const std::size_t /*xidsize*/,
                      const std::size_t /*dsize*/,
                      const bool /*external*/) const
{
    // Check status of current file
    // TODO: Replace for LFC
/*
    if (!_wrfc.is_wr_reset())
    {
        if (!_wrfc.wr_reset())
            return RHM_IORES_FULL;
    }
*/

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
wmgr::dequeue_check(const std::string& xid,
                    const uint64_t drid)
{
    // First check emap
    bool found = false;
    uint64_t fid;
    short eres = _emap.get_pfid(drid, fid);
    if (eres < enq_map::EMAP_OK) { // fail
        if (eres == enq_map::EMAP_RID_NOT_FOUND) {
            if (xid.size()) {
                found = _tmap.data_exists(xid, drid);
            }
        } else if (eres == enq_map::EMAP_LOCKED) {
            std::ostringstream oss;
            oss << std::hex << "drid=0x" << drid;
            throw jexception(jerrno::JERR_MAP_LOCKED, oss.str(), "wmgr", "dequeue_check");
        }
    } else {
        found = true;
    }
    if (!found) {
        std::ostringstream oss;
        oss << "jrnl=" << _jc->id() << " drid=0x" << std::hex << drid;
        throw jexception(jerrno::JERR_WMGR_DEQRIDNOTENQ, oss.str(), "wmgr", "dequeue_check");
    }
}

void
wmgr::dblk_roundup()
{
    const uint32_t xmagic = QLS_EMPTY_MAGIC;
    uint32_t wdblks = jrec::size_blks(_cached_offset_dblks, QLS_SBLK_SIZE_DBLKS) * QLS_SBLK_SIZE_DBLKS;
    while (_cached_offset_dblks < wdblks)
    {
//std::cout << "^0x" << std::hex << _cached_offset_dblks << "<0x" << wdblks << std::dec << std::flush;
        void* wptr = (void*)((char*)_page_ptr_arr[_pg_index] + _pg_offset_dblks * QLS_DBLK_SIZE_BYTES);
        std::memcpy(wptr, (const void*)&xmagic, sizeof(xmagic));
#ifdef QLS_CLEAN
        std::memset((char*)wptr + sizeof(xmagic), QLS_CLEAN_CHAR, QLS_DBLK_SIZE_BYTES - sizeof(xmagic));
#endif
        _pg_offset_dblks++;
        _cached_offset_dblks++;
    }
}

void
wmgr::rotate_page()
{
//std::cout << "^^^^^ wmgr::rotate_page() " << status_str() << " pi=" << _pg_index; // DEBUG
    if (_pg_offset_dblks >= _cache_pgsize_sblks * QLS_SBLK_SIZE_DBLKS)
    {
        _pg_offset_dblks = 0;
        _pg_cntr++;
    }
    if (++_pg_index >= _cache_num_pages)
        _pg_index = 0;
//std::cout << "->" << _pg_index << std::endl; // DEBUG
}

void
wmgr::clean() {
    // Clean up allocated memory here
}

const std::string
wmgr::status_str() const
{
    std::ostringstream oss;
    oss << "wmgr: pi=" << _pg_index << " pc=" << _pg_cntr;
    oss << " po=" << _pg_offset_dblks << " aer=" << _aio_evt_rem;
    oss << " edac=" << (_enq_busy?"T":"F") << (_deq_busy?"T":"F");
    oss << (_abort_busy?"T":"F") << (_commit_busy?"T":"F");
    oss << " ps=[";
    for (int i=0; i<_cache_num_pages; i++)
    {
        switch (_page_cb_arr[i]._state)
        {
            case UNUSED:        oss << "-"; break;
            case IN_USE:        oss << "U"; break;
            case AIO_PENDING:   oss << "A"; break;
            default:            oss << _page_cb_arr[i]._state;
        }
    }
    oss << "] ";
    return oss.str();
}

// static

const char* wmgr::_op_str[] = {"enqueue", "dequeue", "abort", "commit"};

}}}
