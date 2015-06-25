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
 * \file jcntl.cpp
 *
 * Qpid asynchronous store plugin library
 *
 * Messaging journal top-level control and interface class
 * mrg::journal::jcntl.  See comments in file jcntl.h for details.
 *
 * \author Kim van der Riet
 */


#include "qpid/legacystore/jrnl/jcntl.h"

#include <algorithm>
#include <cassert>
#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <iomanip>
#include <iostream>
#include "qpid/legacystore/jrnl/file_hdr.h"
#include "qpid/legacystore/jrnl/jerrno.h"
#include "qpid/legacystore/jrnl/jinf.h"
#include <limits>
#include <sstream>
#include <unistd.h>

namespace mrg
{
namespace journal
{

#define AIO_CMPL_TIMEOUT_SEC   5
#define AIO_CMPL_TIMEOUT_NSEC  0
#define FINAL_AIO_CMPL_TIMEOUT_SEC   15
#define FINAL_AIO_CMPL_TIMEOUT_NSEC  0

// Static
timespec jcntl::_aio_cmpl_timeout; ///< Timeout for blocking libaio returns
timespec jcntl::_final_aio_cmpl_timeout; ///< Timeout for blocking libaio returns when stopping or finalizing
bool jcntl::_init = init_statics();
bool jcntl::init_statics()
{
    _aio_cmpl_timeout.tv_sec = AIO_CMPL_TIMEOUT_SEC;
    _aio_cmpl_timeout.tv_nsec = AIO_CMPL_TIMEOUT_NSEC;
    _final_aio_cmpl_timeout.tv_sec = FINAL_AIO_CMPL_TIMEOUT_SEC;
    _final_aio_cmpl_timeout.tv_nsec = FINAL_AIO_CMPL_TIMEOUT_NSEC;
    return true;
}


// Functions

jcntl::jcntl(const std::string& jid, const std::string& jdir, const std::string& base_filename):
    _jid(jid),
    _jdir(jdir, base_filename),
    _base_filename(base_filename),
    _init_flag(false),
    _stop_flag(false),
    _readonly_flag(false),
    _autostop(true),
    _jfsize_sblks(0),
    _lpmgr(),
    _emap(),
    _tmap(),
    _rrfc(&_lpmgr),
    _wrfc(&_lpmgr),
    _rmgr(this, _emap, _tmap, _rrfc),
    _wmgr(this, _emap, _tmap, _wrfc),
    _rcvdat()
{}

jcntl::~jcntl()
{
    if (_init_flag && !_stop_flag)
        try { stop(true); }
        catch (const jexception& e) { std::cerr << e << std::endl; }
    _lpmgr.finalize();
}

void
jcntl::initialize(const u_int16_t num_jfiles, const bool ae, const u_int16_t ae_max_jfiles,
        const u_int32_t jfsize_sblks, const u_int16_t wcache_num_pages, const u_int32_t wcache_pgsize_sblks,
        aio_callback* const cbp)
{
    _init_flag = false;
    _stop_flag = false;
    _readonly_flag = false;

    _emap.clear();
    _tmap.clear();

    _lpmgr.finalize();

    // Set new file geometry parameters
    assert(num_jfiles >= JRNL_MIN_NUM_FILES);
    assert(num_jfiles <= JRNL_MAX_NUM_FILES);
    _emap.set_num_jfiles(num_jfiles);
    _tmap.set_num_jfiles(num_jfiles);

    assert(jfsize_sblks >= JRNL_MIN_FILE_SIZE);
    assert(jfsize_sblks <= JRNL_MAX_FILE_SIZE);
    _jfsize_sblks = jfsize_sblks;

    // Clear any existing journal files
    _jdir.clear_dir();
    _lpmgr.initialize(num_jfiles, ae, ae_max_jfiles, this, &new_fcntl);

    _wrfc.initialize(_jfsize_sblks);
    _rrfc.initialize();
    _rrfc.set_findex(0);
    _rmgr.initialize(cbp);
    _wmgr.initialize(cbp, wcache_pgsize_sblks, wcache_num_pages, JRNL_WMGR_MAXDTOKPP, JRNL_WMGR_MAXWAITUS);

    // Write info file (<basename>.jinf) to disk
    write_infofile();

    _init_flag = true;
}

void
jcntl::recover(const u_int16_t num_jfiles, const bool ae, const u_int16_t ae_max_jfiles,
        const u_int32_t jfsize_sblks, const u_int16_t wcache_num_pages, const u_int32_t wcache_pgsize_sblks,
//         const rd_aio_cb rd_cb, const wr_aio_cb wr_cb, const std::vector<std::string>* prep_txn_list_ptr,
        aio_callback* const cbp, const std::vector<std::string>* prep_txn_list_ptr,
        u_int64_t& highest_rid)
{
    _init_flag = false;
    _stop_flag = false;
    _readonly_flag = false;

    _emap.clear();
    _tmap.clear();

    _lpmgr.finalize();

    assert(num_jfiles >= JRNL_MIN_NUM_FILES);
    assert(num_jfiles <= JRNL_MAX_NUM_FILES);
    assert(jfsize_sblks >= JRNL_MIN_FILE_SIZE);
    assert(jfsize_sblks <= JRNL_MAX_FILE_SIZE);
    _jfsize_sblks = jfsize_sblks;

    // Verify journal dir and journal files
    _jdir.verify_dir();
    _rcvdat.reset(num_jfiles, ae, ae_max_jfiles);

    rcvr_janalyze(_rcvdat, prep_txn_list_ptr);
    highest_rid = _rcvdat._h_rid;
    if (_rcvdat._jfull)
        throw jexception(jerrno::JERR_JCNTL_RECOVERJFULL, "jcntl", "recover");
    this->log(LOG_DEBUG, _rcvdat.to_log(_jid));

    _lpmgr.recover(_rcvdat, this, &new_fcntl);

    _wrfc.initialize(_jfsize_sblks, &_rcvdat);
    _rrfc.initialize();
    _rrfc.set_findex(_rcvdat.ffid());
    _rmgr.initialize(cbp);
    _wmgr.initialize(cbp, wcache_pgsize_sblks, wcache_num_pages, JRNL_WMGR_MAXDTOKPP, JRNL_WMGR_MAXWAITUS,
            (_rcvdat._lffull ? 0 : _rcvdat._eo));

    _readonly_flag = true;
    _init_flag = true;
}

void
jcntl::recover_complete()
{
    if (!_readonly_flag)
        throw jexception(jerrno::JERR_JCNTL_NOTRECOVERED, "jcntl", "recover_complete");
    for (u_int16_t i=0; i<_lpmgr.num_jfiles(); i++)
        _lpmgr.get_fcntlp(i)->reset(&_rcvdat);
    _wrfc.initialize(_jfsize_sblks, &_rcvdat);
    _rrfc.initialize();
    _rrfc.set_findex(_rcvdat.ffid());
    _rmgr.recover_complete();
    _readonly_flag = false;
}

void
jcntl::delete_jrnl_files()
{
    stop(true); // wait for AIO to complete
    _jdir.delete_dir();
}


iores
jcntl::enqueue_data_record(const void* const data_buff, const std::size_t tot_data_len,
        const std::size_t this_data_len, data_tok* dtokp, const bool transient)
{
    iores r;
    check_wstatus("enqueue_data_record");
    {
        slock s(_wr_mutex);
        while (handle_aio_wait(_wmgr.enqueue(data_buff, tot_data_len, this_data_len, dtokp, 0, 0, transient, false), r,
                        dtokp)) ;
    }
    return r;
}

iores
jcntl::enqueue_extern_data_record(const std::size_t tot_data_len, data_tok* dtokp, const bool transient)
{
    iores r;
    check_wstatus("enqueue_extern_data_record");
    {
        slock s(_wr_mutex);
        while (handle_aio_wait(_wmgr.enqueue(0, tot_data_len, 0, dtokp, 0, 0, transient, true), r, dtokp)) ;
    }
    return r;
}

iores
jcntl::enqueue_txn_data_record(const void* const data_buff, const std::size_t tot_data_len,
        const std::size_t this_data_len, data_tok* dtokp, const std::string& xid,
        const bool transient)
{
    iores r;
    check_wstatus("enqueue_tx_data_record");
    {
        slock s(_wr_mutex);
        while (handle_aio_wait(_wmgr.enqueue(data_buff, tot_data_len, this_data_len, dtokp, xid.data(), xid.size(),
                        transient, false), r, dtokp)) ;
    }
    return r;
}

iores
jcntl::enqueue_extern_txn_data_record(const std::size_t tot_data_len, data_tok* dtokp,
        const std::string& xid, const bool transient)
{
    iores r;
    check_wstatus("enqueue_extern_txn_data_record");
    {
        slock s(_wr_mutex);
        while (handle_aio_wait(_wmgr.enqueue(0, tot_data_len, 0, dtokp, xid.data(), xid.size(), transient, true), r,
                        dtokp)) ;
    }
    return r;
}

/* TODO
iores
jcntl::get_data_record(const u_int64_t& rid, const std::size_t& dsize, const std::size_t& dsize_avail,
        const void** const data, bool auto_discard)
{
    check_rstatus("get_data_record");
    return _rmgr.get(rid, dsize, dsize_avail, data, auto_discard);
} */

/* TODO
iores
jcntl::discard_data_record(data_tok* const dtokp)
{
    check_rstatus("discard_data_record");
    return _rmgr.discard(dtokp);
} */

iores
jcntl::read_data_record(void** const datapp, std::size_t& dsize, void** const xidpp, std::size_t& xidsize,
        bool& transient, bool& external, data_tok* const dtokp, bool ignore_pending_txns)
{
    check_rstatus("read_data");
    iores res = _rmgr.read(datapp, dsize, xidpp, xidsize, transient, external, dtokp, ignore_pending_txns);
    if (res == RHM_IORES_RCINVALID)
    {
        get_wr_events(0); // check for outstanding write events
        iores sres = _rmgr.synchronize(); // flushes all outstanding read events
        if (sres != RHM_IORES_SUCCESS)
            return sres;
        _rmgr.wait_for_validity(&_aio_cmpl_timeout, true); // throw if timeout occurs
        res = _rmgr.read(datapp, dsize, xidpp, xidsize, transient, external, dtokp, ignore_pending_txns);
    }
    return res;
}

iores
jcntl::dequeue_data_record(data_tok* const dtokp, const bool txn_coml_commit)
{
    iores r;
    check_wstatus("dequeue_data");
    {
        slock s(_wr_mutex);
        while (handle_aio_wait(_wmgr.dequeue(dtokp, 0, 0, txn_coml_commit), r, dtokp)) ;
    }
    return r;
}

iores
jcntl::dequeue_txn_data_record(data_tok* const dtokp, const std::string& xid, const bool txn_coml_commit)
{
    iores r;
    check_wstatus("dequeue_data");
    {
        slock s(_wr_mutex);
        while (handle_aio_wait(_wmgr.dequeue(dtokp, xid.data(), xid.size(), txn_coml_commit), r, dtokp)) ;
    }
    return r;
}

iores
jcntl::txn_abort(data_tok* const dtokp, const std::string& xid)
{
    iores r;
    check_wstatus("txn_abort");
    {
        slock s(_wr_mutex);
        while (handle_aio_wait(_wmgr.abort(dtokp, xid.data(), xid.size()), r, dtokp)) ;
    }
    return r;
}

iores
jcntl::txn_commit(data_tok* const dtokp, const std::string& xid)
{
    iores r;
    check_wstatus("txn_commit");
    {
        slock s(_wr_mutex);
        while (handle_aio_wait(_wmgr.commit(dtokp, xid.data(), xid.size()), r, dtokp)) ;
    }
    return r;
}

bool
jcntl::is_txn_synced(const std::string& xid)
{
    slock s(_wr_mutex);
    bool res = _wmgr.is_txn_synced(xid);
    return res;
}

int32_t
jcntl::get_wr_events(timespec* const timeout)
{
    stlock t(_wr_mutex);
    if (!t.locked())
        return jerrno::LOCK_TAKEN;
    int32_t res = _wmgr.get_events(pmgr::UNUSED, timeout);
    return res;
}

int32_t
jcntl::get_rd_events(timespec* const timeout)
{
    return _rmgr.get_events(pmgr::AIO_COMPLETE, timeout);
}

void
jcntl::stop(const bool block_till_aio_cmpl)
{
    if (_readonly_flag)
        check_rstatus("stop");
    else
        check_wstatus("stop");
    _stop_flag = true;
    if (!_readonly_flag)
        flush(block_till_aio_cmpl);
    _rrfc.finalize();
    _lpmgr.finalize();
}

u_int16_t
jcntl::get_earliest_fid()
{
    u_int16_t ffid = _wrfc.earliest_index();
    u_int16_t fid = _wrfc.index();
    while ( _emap.get_enq_cnt(ffid) == 0 && _tmap.get_txn_pfid_cnt(ffid) == 0 && ffid != fid)
    {
        if (++ffid >= _lpmgr.num_jfiles())
            ffid = 0;
    }
    if (!_rrfc.is_active())
        _rrfc.set_findex(ffid);
    return ffid;
}

iores
jcntl::flush(const bool block_till_aio_cmpl)
{
    if (!_init_flag)
        return RHM_IORES_SUCCESS;
    if (_readonly_flag)
        throw jexception(jerrno::JERR_JCNTL_READONLY, "jcntl", "flush");
    iores res;
    {
        slock s(_wr_mutex);
        res = _wmgr.flush();
    }
    if (block_till_aio_cmpl)
        aio_cmpl_wait();
    return res;
}

void
jcntl::log(log_level ll, const std::string& log_stmt) const
{
    log(ll, log_stmt.c_str());
}

void
jcntl::log(log_level ll, const char* const log_stmt) const
{
    if (ll > LOG_INFO)
    {
        std::cout << log_level_str(ll) << ": Journal \"" << _jid << "\": " << log_stmt << std::endl;
    }
}

void
jcntl::chk_wr_frot()
{
    if (_wrfc.index() == _rrfc.index())
        _rmgr.invalidate();
}

void
jcntl::fhdr_wr_sync(const u_int16_t lid)
{
    fcntl* fcntlp = _lpmgr.get_fcntlp(lid);
    while (fcntlp->wr_fhdr_aio_outstanding())
    {
        if (get_wr_events(&_aio_cmpl_timeout) == jerrno::AIO_TIMEOUT)
            throw jexception(jerrno::JERR_JCNTL_AIOCMPLWAIT, "jcntl", "fhdr_wr_sync");
    }
}

fcntl*
jcntl::new_fcntl(jcntl* const jcp, const u_int16_t lid, const u_int16_t fid, const rcvdat* const rdp)
{
    if (!jcp) return 0;
    std::ostringstream oss;
    oss << jcp->jrnl_dir() << "/" << jcp->base_filename();
    return new fcntl(oss.str(), fid, lid, jcp->jfsize_sblks(), rdp);
}

// Protected/Private functions

void
jcntl::check_wstatus(const char* fn_name) const
{
    if (!_init_flag)
        throw jexception(jerrno::JERR__NINIT, "jcntl", fn_name);
    if (_readonly_flag)
        throw jexception(jerrno::JERR_JCNTL_READONLY, "jcntl", fn_name);
    if (_stop_flag)
        throw jexception(jerrno::JERR_JCNTL_STOPPED, "jcntl", fn_name);
}

void
jcntl::check_rstatus(const char* fn_name) const
{
    if (!_init_flag)
        throw jexception(jerrno::JERR__NINIT, "jcntl", fn_name);
    if (_stop_flag)
        throw jexception(jerrno::JERR_JCNTL_STOPPED, "jcntl", fn_name);
}

void
jcntl::write_infofile() const
{
    timespec ts;
    if (::clock_gettime(CLOCK_REALTIME, &ts))
    {
        std::ostringstream oss;
        oss << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR__RTCLOCK, oss.str(), "jcntl", "write_infofile");
    }
    jinf ji(_jid, _jdir.dirname(), _base_filename, _lpmgr.num_jfiles(), _lpmgr.is_ae(), _lpmgr.ae_max_jfiles(),
            _jfsize_sblks, _wmgr.cache_pgsize_sblks(), _wmgr.cache_num_pages(), ts);
    ji.write();
}

void
jcntl::aio_cmpl_wait()
{
    //while (_wmgr.get_aio_evt_rem())
    while (true)
    {
        u_int32_t aer;
        {
            slock s(_wr_mutex);
            aer = _wmgr.get_aio_evt_rem();
        }
        if (aer == 0) break; // no events left
        if (get_wr_events(&_aio_cmpl_timeout) == jerrno::AIO_TIMEOUT)
            throw jexception(jerrno::JERR_JCNTL_AIOCMPLWAIT, "jcntl", "aio_cmpl_wait");
    }
}

bool
jcntl::handle_aio_wait(const iores res, iores& resout, const data_tok* dtp)
{
    resout = res;
    if (res == RHM_IORES_PAGE_AIOWAIT)
    {
        while (_wmgr.curr_pg_blocked())
        {
            if (_wmgr.get_events(pmgr::UNUSED, &_aio_cmpl_timeout) == jerrno::AIO_TIMEOUT)
            {
                std::ostringstream oss;
                oss << "get_events() returned JERR_JCNTL_AIOCMPLWAIT; wmgr_status: " << _wmgr.status_str();
                this->log(LOG_CRITICAL, oss.str());
                throw jexception(jerrno::JERR_JCNTL_AIOCMPLWAIT, "jcntl", "handle_aio_wait");
            }
        }
        return true;
    }
    else if (res == RHM_IORES_FILE_AIOWAIT)
    {
        while (_wmgr.curr_file_blocked())
        {
            if (_wmgr.get_events(pmgr::UNUSED, &_aio_cmpl_timeout) == jerrno::AIO_TIMEOUT)
            {
                std::ostringstream oss;
                oss << "get_events() returned JERR_JCNTL_AIOCMPLWAIT; wmgr_status: " << _wmgr.status_str();
                this->log(LOG_CRITICAL, oss.str());
                throw jexception(jerrno::JERR_JCNTL_AIOCMPLWAIT, "jcntl", "handle_aio_wait");
            }
        }
        _wrfc.wr_reset();
        resout = RHM_IORES_SUCCESS;
        data_tok::write_state ws = dtp->wstate();
        return ws == data_tok::ENQ_PART || ws == data_tok::DEQ_PART || ws == data_tok::ABORT_PART ||
                ws == data_tok::COMMIT_PART;
    }
    return false;
}

void
jcntl::rcvr_janalyze(rcvdat& rd, const std::vector<std::string>* prep_txn_list_ptr)
{
    jinf ji(_jdir.dirname() + "/" + _base_filename + "." + JRNL_INFO_EXTENSION, true);

    // If the number of files does not tie up with the jinf file from the journal being recovered,
    // use the jinf data.
    if (rd._njf != ji.num_jfiles())
    {
        std::ostringstream oss;
        oss << "Recovery found " << ji.num_jfiles() <<
                " files (different from --num-jfiles value of " << rd._njf << ").";
        this->log(LOG_INFO, oss.str());
        rd._njf = ji.num_jfiles();
        _rcvdat._enq_cnt_list.resize(rd._njf);
    }
    _emap.set_num_jfiles(rd._njf);
    _tmap.set_num_jfiles(rd._njf);
    if (_jfsize_sblks != ji.jfsize_sblks())
    {
        std::ostringstream oss;
        oss << "Recovery found file size = " << (ji.jfsize_sblks() / JRNL_RMGR_PAGE_SIZE) <<
                " (different from --jfile-size-pgs value of " <<
                (_jfsize_sblks / JRNL_RMGR_PAGE_SIZE) << ").";
        this->log(LOG_INFO, oss.str());
        _jfsize_sblks = ji.jfsize_sblks();
    }
    if (_jdir.dirname().compare(ji.jdir()))
    {
        std::ostringstream oss;
        oss << "Journal file location change: original = \"" << ji.jdir() <<
                "\"; current = \"" << _jdir.dirname() << "\"";
        this->log(LOG_WARN, oss.str());
        ji.set_jdir(_jdir.dirname());
    }

    try
    {
        rd._ffid = ji.get_first_pfid();
        rd._lfid = ji.get_last_pfid();
        rd._owi = ji.get_initial_owi();
        rd._frot = ji.get_frot();
        rd._jempty = false;
        ji.get_normalized_pfid_list(rd._fid_list); // _pfid_list
    }
    catch (const jexception& e)
    {
        if (e.err_code() != jerrno::JERR_JINF_JDATEMPTY) throw;
    }

    // Restore all read and write pointers and transactions
    if (!rd._jempty)
    {
        u_int16_t fid = rd._ffid;
        std::ifstream ifs;
        bool lowi = rd._owi; // local copy of owi to be used during analysis
        while (rcvr_get_next_record(fid, &ifs, lowi, rd)) ;
        if (ifs.is_open()) ifs.close();

        // Remove all txns from tmap that are not in the prepared list
        if (prep_txn_list_ptr)
        {
            std::vector<std::string> xid_list;
            _tmap.xid_list(xid_list);
            for (std::vector<std::string>::iterator itr = xid_list.begin(); itr != xid_list.end(); itr++)
            {
                std::vector<std::string>::const_iterator pitr =
                        std::find(prep_txn_list_ptr->begin(), prep_txn_list_ptr->end(), *itr);
                if (pitr == prep_txn_list_ptr->end()) // not found in prepared list
                {
                    txn_data_list tdl = _tmap.get_remove_tdata_list(*itr); // tdl will be empty if xid not found
                    // Unlock any affected enqueues in emap
                    for (tdl_itr i=tdl.begin(); i<tdl.end(); i++)
                    {
                        if (i->_enq_flag) // enq op - decrement enqueue count
                            rd._enq_cnt_list[i->_pfid]--;
                        else if (_emap.is_enqueued(i->_drid, true)) // deq op - unlock enq record
                        {
                            int16_t ret = _emap.unlock(i->_drid);
                            if (ret < enq_map::EMAP_OK) // fail
                            {
                                // enq_map::unlock()'s only error is enq_map::EMAP_RID_NOT_FOUND
                                std::ostringstream oss;
                                oss << std::hex << "_emap.unlock(): drid=0x\"" << i->_drid;
                                throw jexception(jerrno::JERR_MAP_NOTFOUND, oss.str(), "jcntl", "rcvr_janalyze");
                            }
                        }
                    }
                }
            }
        }

        // Check for file full condition - add one to _jfsize_sblks to account for file header
        rd._lffull = rd._eo == (1 + _jfsize_sblks) * JRNL_SBLK_SIZE * JRNL_DBLK_SIZE;

        // Check for journal full condition
        u_int16_t next_wr_fid = (rd._lfid + 1) % rd._njf;
        rd._jfull = rd._ffid == next_wr_fid && rd._enq_cnt_list[next_wr_fid] && rd._lffull;
    }
}

bool
jcntl::rcvr_get_next_record(u_int16_t& fid, std::ifstream* ifsp, bool& lowi, rcvdat& rd)
{
    std::size_t cum_size_read = 0;
    void* xidp = 0;
    rec_hdr h;

    bool hdr_ok = false;
    std::streampos file_pos;
    while (!hdr_ok)
    {
        if (!ifsp->is_open())
        {
            if (!jfile_cycle(fid, ifsp, lowi, rd, true))
                return false;
        }
        file_pos = ifsp->tellg();
        ifsp->read((char*)&h, sizeof(rec_hdr));
        if (ifsp->gcount() == sizeof(rec_hdr))
            hdr_ok = true;
        else
        {
            if (!jfile_cycle(fid, ifsp, lowi, rd, true))
                return false;
        }
    }

    switch(h._magic)
    {
        case RHM_JDAT_ENQ_MAGIC:
            {
                enq_rec er;
                u_int16_t start_fid = fid; // fid may increment in decode() if record folds over file boundary
                if (!decode(er, fid, ifsp, cum_size_read, h, lowi, rd, file_pos))
                    return false;
                if (!er.is_transient()) // Ignore transient msgs
                {
                    rd._enq_cnt_list[start_fid]++;
                    if (er.xid_size())
                    {
                        er.get_xid(&xidp);
                        assert(xidp != 0);
                        std::string xid((char*)xidp, er.xid_size());
                        _tmap.insert_txn_data(xid, txn_data(h._rid, 0, start_fid, true));
                        if (_tmap.set_aio_compl(xid, h._rid) < txn_map::TMAP_OK) // fail - xid or rid not found
                        {
                            std::ostringstream oss;
                            oss << std::hex << "_tmap.set_aio_compl: txn_enq xid=\"" << xid << "\" rid=0x" << h._rid;
                            throw jexception(jerrno::JERR_MAP_NOTFOUND, oss.str(), "jcntl", "rcvr_get_next_record");
                        }
                        std::free(xidp);
                    }
                    else
                    {
                        if (_emap.insert_pfid(h._rid, start_fid) < enq_map::EMAP_OK) // fail
                        {
                            // The only error code emap::insert_pfid() returns is enq_map::EMAP_DUP_RID.
                            std::ostringstream oss;
                            oss << std::hex << "rid=0x" << h._rid << " _pfid=0x" << start_fid;
                            throw jexception(jerrno::JERR_MAP_DUPLICATE, oss.str(), "jcntl", "rcvr_get_next_record");
                        }
                    }
                }
            }
            break;
        case RHM_JDAT_DEQ_MAGIC:
            {
                deq_rec dr;
                u_int16_t start_fid = fid; // fid may increment in decode() if record folds over file boundary
                if (!decode(dr, fid, ifsp, cum_size_read, h, lowi, rd, file_pos))
                    return false;
                if (dr.xid_size())
                {
                    // If the enqueue is part of a pending txn, it will not yet be in emap
                    _emap.lock(dr.deq_rid()); // ignore not found error
                    dr.get_xid(&xidp);
                    assert(xidp != 0);
                    std::string xid((char*)xidp, dr.xid_size());
                    _tmap.insert_txn_data(xid, txn_data(dr.rid(), dr.deq_rid(), start_fid, false,
                            dr.is_txn_coml_commit()));
                    if (_tmap.set_aio_compl(xid, dr.rid()) < txn_map::TMAP_OK) // fail - xid or rid not found
                    {
                        std::ostringstream oss;
                        oss << std::hex << "_tmap.set_aio_compl: txn_deq xid=\"" << xid << "\" rid=0x" << dr.rid();
                        throw jexception(jerrno::JERR_MAP_NOTFOUND, oss.str(), "jcntl", "rcvr_get_next_record");
                    }
                    std::free(xidp);
                }
                else
                {
                    int16_t enq_fid = _emap.get_remove_pfid(dr.deq_rid(), true);
                    if (enq_fid >= enq_map::EMAP_OK) // ignore not found error
                        rd._enq_cnt_list[enq_fid]--;
                }
            }
            break;
        case RHM_JDAT_TXA_MAGIC:
            {
                txn_rec ar;
                if (!decode(ar, fid, ifsp, cum_size_read, h, lowi, rd, file_pos))
                    return false;
                // Delete this txn from tmap, unlock any locked records in emap
                ar.get_xid(&xidp);
                assert(xidp != 0);
                std::string xid((char*)xidp, ar.xid_size());
                txn_data_list tdl = _tmap.get_remove_tdata_list(xid); // tdl will be empty if xid not found
                for (tdl_itr itr = tdl.begin(); itr != tdl.end(); itr++)
                {
                    if (itr->_enq_flag)
                        rd._enq_cnt_list[itr->_pfid]--;
                    else
                        _emap.unlock(itr->_drid); // ignore not found error
                }
                std::free(xidp);
            }
            break;
        case RHM_JDAT_TXC_MAGIC:
            {
                txn_rec cr;
                if (!decode(cr, fid, ifsp, cum_size_read, h, lowi, rd, file_pos))
                    return false;
                // Delete this txn from tmap, process records into emap
                cr.get_xid(&xidp);
                assert(xidp != 0);
                std::string xid((char*)xidp, cr.xid_size());
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
                            throw jexception(jerrno::JERR_MAP_DUPLICATE, oss.str(), "jcntl", "rcvr_get_next_record");
                        }
                    }
                    else // txn dequeue
                    {
                        int16_t enq_fid = _emap.get_remove_pfid(itr->_drid, true);
                        if (enq_fid >= enq_map::EMAP_OK)
                            rd._enq_cnt_list[enq_fid]--;
                    }
                }
                std::free(xidp);
            }
            break;
        case RHM_JDAT_EMPTY_MAGIC:
            {
                u_int32_t rec_dblks = jrec::size_dblks(sizeof(rec_hdr));
                ifsp->ignore(rec_dblks * JRNL_DBLK_SIZE - sizeof(rec_hdr));
                assert(!ifsp->fail() && !ifsp->bad());
                if (!jfile_cycle(fid, ifsp, lowi, rd, false))
                    return false;
            }
            break;
        case 0:
            check_journal_alignment(fid, file_pos, rd);
            return false;
        default:
            // Stop as this is the overwrite boundary.
            check_journal_alignment(fid, file_pos, rd);
            return false;
    }
    return true;
}

bool
jcntl::decode(jrec& rec, u_int16_t& fid, std::ifstream* ifsp, std::size_t& cum_size_read,
        rec_hdr& h, bool& lowi, rcvdat& rd, std::streampos& file_offs)
{
    u_int16_t start_fid = fid;
    std::streampos start_file_offs = file_offs;
    if (!check_owi(fid, h, lowi, rd, file_offs))
        return false;
    bool done = false;
    while (!done)
    {
        try { done = rec.rcv_decode(h, ifsp, cum_size_read); }
        catch (const jexception& e)
        {
// TODO - review this logic and tidy up how rd._lfid is assigned. See new jinf.get_end_file() fn.
// Original
//             if (e.err_code() != jerrno::JERR_JREC_BADRECTAIL ||
//                     fid != (rd._ffid ? rd._ffid - 1 : _num_jfiles - 1)) throw;
// Tried this, but did not work
//             if (e.err_code() != jerrno::JERR_JREC_BADRECTAIL || h._magic != 0) throw;
            check_journal_alignment(start_fid, start_file_offs, rd);
//             rd._lfid = start_fid;
            return false;
        }
        if (!done && !jfile_cycle(fid, ifsp, lowi, rd, false))
        {
            check_journal_alignment(start_fid, start_file_offs, rd);
            return false;
        }
    }
    return true;
}

bool
jcntl::jfile_cycle(u_int16_t& fid, std::ifstream* ifsp, bool& lowi, rcvdat& rd, const bool jump_fro)
{
    if (ifsp->is_open())
    {
        if (ifsp->eof() || !ifsp->good())
        {
            ifsp->clear();
            rd._eo = ifsp->tellg(); // remember file offset before closing
            assert(rd._eo != std::numeric_limits<std::size_t>::max()); // Check for error code -1
            ifsp->close();
            if (++fid >= rd._njf)
            {
                fid = 0;
                lowi = !lowi; // Flip local owi
            }
            if (fid == rd._ffid) // used up all journal files
                return false;
        }
    }
    if (!ifsp->is_open())
    {
        std::ostringstream oss;
        oss << _jdir.dirname() << "/" << _base_filename << ".";
        oss << std::hex << std::setfill('0') << std::setw(4) << fid << "." << JRNL_DATA_EXTENSION;
        ifsp->clear(); // clear eof flag, req'd for older versions of c++
        ifsp->open(oss.str().c_str(), std::ios_base::in | std::ios_base::binary);
        if (!ifsp->good())
            throw jexception(jerrno::JERR__FILEIO, oss.str(), "jcntl", "jfile_cycle");

        // Read file header
        file_hdr fhdr;
        ifsp->read((char*)&fhdr, sizeof(fhdr));
        assert(ifsp->good());
        if (fhdr._magic == RHM_JDAT_FILE_MAGIC)
        {
            assert(fhdr._lfid == fid);
            if (!rd._fro)
                rd._fro = fhdr._fro;
            std::streamoff foffs = jump_fro ? fhdr._fro : JRNL_DBLK_SIZE * JRNL_SBLK_SIZE;
            ifsp->seekg(foffs);
        }
        else
        {
            ifsp->close();
            return false;
        }
    }
    return true;
}

bool
jcntl::check_owi(const u_int16_t fid, rec_hdr& h, bool& lowi, rcvdat& rd, std::streampos& file_pos)
{
    if (rd._ffid ? h.get_owi() == lowi : h.get_owi() != lowi) // Overwrite indicator changed
    {
        u_int16_t expected_fid = rd._ffid ? rd._ffid - 1 : rd._njf - 1;
        if (fid == expected_fid)
        {
            check_journal_alignment(fid, file_pos, rd);
            return false;
        }
        std::ostringstream oss;
        oss << std::hex << std::setfill('0') << "Magic=0x" << std::setw(8) << h._magic;
        oss << " fid=0x" << std::setw(4) << fid << " rid=0x" << std::setw(8) << h._rid;
        oss << " foffs=0x" << std::setw(8) << file_pos;
        oss << " expected_fid=0x" << std::setw(4) << expected_fid;
        throw jexception(jerrno::JERR_JCNTL_OWIMISMATCH, oss.str(), "jcntl",
                "check_owi");
    }
    if (rd._h_rid == 0)
        rd._h_rid = h._rid;
    else if (h._rid - rd._h_rid < 0x8000000000000000ULL) // RFC 1982 comparison for unsigned 64-bit
        rd._h_rid = h._rid;
    return true;
}


void
jcntl::check_journal_alignment(const u_int16_t fid, std::streampos& file_pos, rcvdat& rd)
{
    unsigned sblk_offs = file_pos % (JRNL_DBLK_SIZE * JRNL_SBLK_SIZE);
    if (sblk_offs)
    {
        {
            std::ostringstream oss;
            oss << std::hex << "Bad record alignment found at fid=0x" << fid;
            oss << " offs=0x" << file_pos << " (likely journal overwrite boundary); " << std::dec;
            oss << (JRNL_SBLK_SIZE - (sblk_offs/JRNL_DBLK_SIZE)) << " filler record(s) required.";
            this->log(LOG_WARN, oss.str());
        }
        const u_int32_t xmagic = RHM_JDAT_EMPTY_MAGIC;
        std::ostringstream oss;
        oss << _jdir.dirname() << "/" << _base_filename << ".";
        oss << std::hex << std::setfill('0') << std::setw(4) << fid << "." << JRNL_DATA_EXTENSION;
        std::ofstream ofsp(oss.str().c_str(),
                std::ios_base::in | std::ios_base::out | std::ios_base::binary);
        if (!ofsp.good())
            throw jexception(jerrno::JERR__FILEIO, oss.str(), "jcntl", "check_journal_alignment");
        ofsp.seekp(file_pos);
        void* buff = std::malloc(JRNL_DBLK_SIZE);
        assert(buff != 0);
        std::memcpy(buff, (const void*)&xmagic, sizeof(xmagic));
        // Normally, RHM_CLEAN must be set before these fills are done, but this is a recover
        // situation (i.e. performance is not an issue), and it makes the location of the write
        // clear should inspection of the file be required.
        std::memset((char*)buff + sizeof(xmagic), RHM_CLEAN_CHAR, JRNL_DBLK_SIZE - sizeof(xmagic));

        while (file_pos % (JRNL_DBLK_SIZE * JRNL_SBLK_SIZE))
        {
            ofsp.write((const char*)buff, JRNL_DBLK_SIZE);
            assert(!ofsp.fail());
            std::ostringstream oss;
            oss << std::hex << "Recover phase write: Wrote filler record: fid=0x" << fid << " offs=0x" << file_pos;
            this->log(LOG_NOTICE, oss.str());
            file_pos = ofsp.tellp();
        }
        ofsp.close();
        std::free(buff);
        rd._lfid = fid;
        if (!rd._frot)
            rd._ffid = (fid + 1) % rd._njf;
        this->log(LOG_INFO, "Bad record alignment fixed.");
    }
    rd._eo = file_pos;
}

} // namespace journal
} // namespace mrg
