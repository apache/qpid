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

#include "qpid/linearstore/jrnl/jcntl.h"

#include <algorithm>
#include <cassert>
#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <qpid/linearstore/jrnl/EmptyFilePool.h>
#include <qpid/linearstore/jrnl/EmptyFilePoolManager.h>
#include "qpid/linearstore/jrnl/jerrno.h"
#include "qpid/linearstore/jrnl/JournalLog.h"
#include "qpid/linearstore/jrnl/utils/enq_hdr.h"
#include <limits>
#include <sstream>
#include <unistd.h>

namespace qpid
{
namespace qls_jrnl
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

jcntl::jcntl(const std::string& jid,
             const std::string& jdir,
             JournalLog& jrnl_log):
    _jid(jid),
    _jdir(jdir),
    _init_flag(false),
    _stop_flag(false),
    _readonly_flag(false),
    _jrnl_log(jrnl_log),
    _linearFileController(*this),
    _emptyFilePoolPtr(0),
    _emap(),
    _tmap(),
    _wmgr(this, _emap, _tmap, _linearFileController),
    _recoveryManager(_jdir.dirname(), _jid, _emap, _tmap, jrnl_log)
{}

jcntl::~jcntl()
{
    if (_init_flag && !_stop_flag)
        try { stop(true); }
        catch (const jexception& e) { std::cerr << e << std::endl; }
    _linearFileController.finalize();
}

void
jcntl::initialize(EmptyFilePool* efpp,
                  const uint16_t wcache_num_pages,
                  const uint32_t wcache_pgsize_sblks,
                  aio_callback* const cbp)
{
    _init_flag = false;
    _stop_flag = false;
    _readonly_flag = false;

    _emap.clear();
    _tmap.clear();

    _linearFileController.finalize();

//    _lpmgr.finalize();

    // Set new file geometry parameters
//    assert(num_jfiles >= JRNL_MIN_NUM_FILES);
//    assert(num_jfiles <= JRNL_MAX_NUM_FILES);
//    _emap.set_num_jfiles(num_jfiles);
//    _tmap.set_num_jfiles(num_jfiles);

//    assert(jfsize_sblks >= JRNL_MIN_FILE_SIZE);
//    assert(jfsize_sblks <= JRNL_MAX_FILE_SIZE);
//    _jfsize_sblks = jfsize_sblks;

    // Clear any existing journal files
    _jdir.clear_dir();
//    _lpmgr.initialize(num_jfiles, ae, ae_max_jfiles, this, &new_fcntl); // Creates new journal files

    _linearFileController.initialize(_jdir.dirname(), efpp, 0ULL);
    _linearFileController.pullEmptyFileFromEfp();
//    std::cout << _linearFileController.status(2);
//    _wrfc.initialize(_jfsize_sblks);
//    _rrfc.initialize();
//    _rrfc.set_findex(0);
//    _rmgr.initialize(cbp);
    _wmgr.initialize(cbp, wcache_pgsize_sblks, wcache_num_pages, QLS_WMGR_MAXDTOKPP, QLS_WMGR_MAXWAITUS);

    // Write info file (<basename>.jinf) to disk
//    write_infofile();

    _init_flag = true;
}

void
jcntl::recover(EmptyFilePoolManager* efpmp,
               const uint16_t wcache_num_pages,
               const uint32_t wcache_pgsize_sblks,
               aio_callback* const cbp,
               const std::vector<std::string>* prep_txn_list_ptr,
               uint64_t& highest_rid)
{
    _init_flag = false;
    _stop_flag = false;
    _readonly_flag = false;

    _emap.clear();
    _tmap.clear();

    _linearFileController.finalize();

//    _lpmgr.finalize();

//    assert(num_jfiles >= JRNL_MIN_NUM_FILES);
//    assert(num_jfiles <= JRNL_MAX_NUM_FILES);
//    assert(jfsize_sblks >= JRNL_MIN_FILE_SIZE);
//    assert(jfsize_sblks <= JRNL_MAX_FILE_SIZE);
//    _jfsize_sblks = jfsize_sblks;

    // Verify journal dir and journal files
    _jdir.verify_dir();
//    _rcvdat.reset(num_jfiles/*, ae, ae_max_jfiles*/);

//    rcvr_janalyze(prep_txn_list_ptr, efpm);
    efpIdentity_t efpIdentity;
    _recoveryManager.analyzeJournals(prep_txn_list_ptr, efpmp, &_emptyFilePoolPtr);

    highest_rid = _recoveryManager.getHighestRecordId();
//    if (_rcvdat._jfull)
//        throw jexception(jerrno::JERR_JCNTL_RECOVERJFULL, "jcntl", "recover");
    _jrnl_log.log(/*LOG_DEBUG*/JournalLog::LOG_INFO, _jid, _recoveryManager.toString(_jid, true));

//    _lpmgr.recover(_rcvdat, this, &new_fcntl);

    _linearFileController.initialize(_jdir.dirname(), _emptyFilePoolPtr, _recoveryManager.getHighestFileNumber());
//    _linearFileController.setFileNumberCounter(_recoveryManager.getHighestFileNumber());
    _recoveryManager.setLinearFileControllerJournals(&qpid::qls_jrnl::LinearFileController::addJournalFile, &_linearFileController);
//    _wrfc.initialize(_jfsize_sblks, &_rcvdat);
//    _rrfc.initialize();
//    _rrfc.set_findex(_rcvdat.ffid());
//    _rmgr.initialize(cbp);
    _wmgr.initialize(cbp, wcache_pgsize_sblks, wcache_num_pages, QLS_WMGR_MAXDTOKPP, QLS_WMGR_MAXWAITUS,
            (_recoveryManager.isLastFileFull() ? 0 : _recoveryManager.getEndOffset()));

    _readonly_flag = true;
    _init_flag = true;
}

void
jcntl::recover_complete()
{
    if (!_readonly_flag)
        throw jexception(jerrno::JERR_JCNTL_NOTRECOVERED, "jcntl", "recover_complete");
//    for (uint16_t i=0; i<_lpmgr.num_jfiles(); i++)
//        _lpmgr.get_fcntlp(i)->reset(&_rcvdat);
//    _wrfc.initialize(_jfsize_sblks, &_rcvdat);
//    _rrfc.initialize();
//    _rrfc.set_findex(_rcvdat.ffid());
//    _rmgr.recover_complete();
    _readonly_flag = false;
}

void
jcntl::delete_jrnl_files()
{
    stop(true); // wait for AIO to complete
    _linearFileController.purgeFilesToEfp();
    _jdir.delete_dir();
}


iores
jcntl::enqueue_data_record(const void* const data_buff,
                           const std::size_t tot_data_len,
                           const std::size_t this_data_len,
                           data_tok* dtokp,
                           const bool transient)
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
jcntl::enqueue_extern_data_record(const std::size_t tot_data_len,
                                  data_tok* dtokp,
                                  const bool transient)
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
jcntl::enqueue_txn_data_record(const void* const data_buff,
                               const std::size_t tot_data_len,
                               const std::size_t this_data_len,
                               data_tok* dtokp,
                               const std::string& xid,
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
jcntl::enqueue_extern_txn_data_record(const std::size_t tot_data_len,
                                      data_tok* dtokp,
                                      const std::string& xid,
                                      const bool transient)
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

iores
jcntl::read_data_record(void** const datapp,
                        std::size_t& dsize,
                        void** const xidpp,
                        std::size_t& xidsize,
                        bool& transient,
                        bool& external,
                        data_tok* const dtokp,
                        bool ignore_pending_txns)
{
    check_rstatus("read_data");
    if (_recoveryManager.readNextRemainingRecord(datapp, dsize, xidpp, xidsize, transient, external, dtokp, ignore_pending_txns))
        return RHM_IORES_SUCCESS;
    return RHM_IORES_EMPTY;
/*
    if (!dtokp->is_readable()) {
        std::ostringstream oss;
        oss << std::hex << std::setfill('0');
        oss << "dtok_id=0x" << std::setw(8) << dtokp->id();
        oss << "; dtok_rid=0x" << std::setw(16) << dtokp->rid();
        oss << "; dtok_wstate=" << dtokp->wstate_str();
        throw jexception(jerrno::JERR_JCNTL_ENQSTATE, oss.str(), "jcntl", "read_data_record");
    }
    std::vector<uint64_t> ridl;
    _emap.rid_list(ridl);
    enq_map::emap_data_struct_t eds;
    for (std::vector<uint64_t>::const_iterator i=ridl.begin(); i!=ridl.end(); ++i) {
        short res = _emap.get_data(*i, eds);
        if (res == enq_map::EMAP_OK) {
            std::ifstream ifs(_recoveryManager._fm[eds._pfid].c_str(), std::ifstream::in | std::ifstream::binary);
            if (!ifs.good()) {
                std::ostringstream oss;
                oss << "rid=" << (*i) << " pfid=" << eds._pfid << " file=" << _recoveryManager._fm[eds._pfid] << " file_posn=" << eds._file_posn;
                throw jexception(jerrno::JERR_RCVM_OPENRD, oss.str(), "jcntl", "read_data_record");
            }
            ifs.seekg(eds._file_posn, std::ifstream::beg);
            ::enq_hdr_t eh;
            ifs.read((char*)&eh, sizeof(::enq_hdr_t));
            if (!::validate_enq_hdr(&eh, QLS_ENQ_MAGIC, QLS_JRNL_VERSION, *i)) {
                std::ostringstream oss;
                oss << "rid=" << (*i) << " pfid=" << eds._pfid << " file=" << _recoveryManager._fm[eds._pfid] << " file_posn=" << eds._file_posn;
                throw jexception(jerrno::JERR_JCNTL_INVALIDENQHDR, oss.str(), "jcntl", "read_data_record");
            }
            dsize = eh._dsize;
            xidsize = eh._xidsize;
            transient = ::is_enq_transient(&eh);
            external = ::is_enq_external(&eh);
            if (xidsize) {
                *xidpp = ::malloc(xidsize);
                ifs.read((char*)(*xidpp), xidsize);
            } else {
                *xidpp = 0;
            }
            if (dsize) {
                *datapp = ::malloc(dsize);
                ifs.read((char*)(*datapp), dsize);
            } else {
                *datapp = 0;
            }
        }
    }
*/
/*
    check_rstatus("read_data");
    iores res = _rmgr.read(datapp, dsize, xidpp, xidsize, transient, external, dtokp, ignore_pending_txns);
    if (res == RHM_IORES_RCINVALID)
    {
        get_wr_events(0); // check for outstanding write events
        iores sres = _rmgr.synchronize(); // flushes all outstanding read events
        if (sres != RHM_IORES_SUCCESS)
            return sres;
        // TODO: Does linear store need this?
//        _rmgr.wait_for_validity(&_aio_cmpl_timeout, true); // throw if timeout occurs
        res = _rmgr.read(datapp, dsize, xidpp, xidsize, transient, external, dtokp, ignore_pending_txns);
    }
    return res;
*/
    return RHM_IORES_SUCCESS;
}

iores
jcntl::dequeue_data_record(data_tok* const dtokp,
                           const bool txn_coml_commit)
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
jcntl::dequeue_txn_data_record(data_tok* const dtokp,
                               const std::string& xid,
                               const bool txn_coml_commit)
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
jcntl::txn_abort(data_tok* const dtokp,
                 const std::string& xid)
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
jcntl::txn_commit(data_tok* const dtokp,
                  const std::string& xid)
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
    return _wmgr.get_events(timeout, false);
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
    _linearFileController.finalize();
}

LinearFileController&
jcntl::getLinearFileControllerRef() {
    return _linearFileController;
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
jcntl::aio_cmpl_wait()
{
    //while (_wmgr.get_aio_evt_rem())
    while (true)
    {
        uint32_t aer;
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
            if (_wmgr.get_aio_evt_rem() == 0) {
std::cout << "&&&&&& jcntl::handle_aio_wait() " << _wmgr.status_str() << std::endl; // DEBUG
                throw jexception("_wmgr.curr_pg_blocked() with no events remaining"); // TODO - complete exception
            }
            if (_wmgr.get_events(&_aio_cmpl_timeout, false) == jerrno::AIO_TIMEOUT)
            {
                std::ostringstream oss;
                oss << "get_events() returned JERR_JCNTL_AIOCMPLWAIT; wmgr_status: " << _wmgr.status_str();
                _jrnl_log.log(JournalLog::LOG_CRITICAL, _jid, oss.str());
                throw jexception(jerrno::JERR_JCNTL_AIOCMPLWAIT, "jcntl", "handle_aio_wait");
            }
        }
        return true;
    }
    else if (res == RHM_IORES_FILE_AIOWAIT)
    {
//        while (_wmgr.curr_file_blocked())
//        {
//            if (_wmgr.get_events(pmgr::UNUSED, &_aio_cmpl_timeout) == jerrno::AIO_TIMEOUT)
//            {
//                std::ostringstream oss;
//                oss << "get_events() returned JERR_JCNTL_AIOCMPLWAIT; wmgr_status: " << _wmgr.status_str();
//                this->log(LOG_CRITICAL, oss.str());
//                throw jexception(jerrno::JERR_JCNTL_AIOCMPLWAIT, "jcntl", "handle_aio_wait");
//            }
//        }
//        _wrfc.wr_reset();
        resout = RHM_IORES_SUCCESS;
        data_tok::write_state ws = dtp->wstate();
        return ws == data_tok::ENQ_PART || ws == data_tok::DEQ_PART || ws == data_tok::ABORT_PART ||
                ws == data_tok::COMMIT_PART;
    }
    return false;
}

}}
