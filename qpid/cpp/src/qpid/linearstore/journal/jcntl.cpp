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

#include "qpid/linearstore/journal/jcntl.h"

#include <iomanip>
#include "qpid/linearstore/journal/data_tok.h"
#include "qpid/linearstore/journal/JournalLog.h"

namespace qpid {
namespace linearstore {
namespace journal {

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
    _jdir.clear_dir(); // Clear any existing journal files
    _linearFileController.initialize(_jdir.dirname(), efpp, 0ULL);
    _linearFileController.getNextJournalFile();
    _wmgr.initialize(cbp, wcache_pgsize_sblks, wcache_num_pages, QLS_WMGR_MAXDTOKPP, QLS_WMGR_MAXWAITUS, 0);
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

    // Verify journal dir and journal files
    _jdir.verify_dir();
    _recoveryManager.analyzeJournals(prep_txn_list_ptr, efpmp, &_emptyFilePoolPtr);
    assert(_emptyFilePoolPtr != 0);

    highest_rid = _recoveryManager.getHighestRecordId();
    _jrnl_log.log(/*LOG_DEBUG*/JournalLog::LOG_INFO, _jid, _recoveryManager.toString(_jid, 5U));
    _linearFileController.initialize(_jdir.dirname(), _emptyFilePoolPtr, _recoveryManager.getHighestFileNumber());
    _recoveryManager.setLinearFileControllerJournals(&qpid::linearstore::journal::LinearFileController::addJournalFile, &_linearFileController);
    if (_recoveryManager.isLastFileFull()) {
        _linearFileController.getNextJournalFile();
    }
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
    _recoveryManager.recoveryComplete();
    _readonly_flag = false;
}

void
jcntl::delete_jrnl_files()
{
    stop(true); // wait for AIO to complete
    _linearFileController.purgeEmptyFilesToEfp();
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
        while (handle_aio_wait(_wmgr.enqueue(data_buff, tot_data_len, this_data_len, dtokp, 0, 0, false, transient, false), r,
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
        while (handle_aio_wait(_wmgr.enqueue(0, tot_data_len, 0, dtokp, 0, 0, false, transient, true), r, dtokp)) ;
    }
    return r;
}

iores
jcntl::enqueue_txn_data_record(const void* const data_buff,
                               const std::size_t tot_data_len,
                               const std::size_t this_data_len,
                               data_tok* dtokp,
                               const std::string& xid,
                               const bool tpc_flag,
                               const bool transient)
{
    iores r;
    check_wstatus("enqueue_tx_data_record");
    {
        slock s(_wr_mutex);
        while (handle_aio_wait(_wmgr.enqueue(data_buff, tot_data_len, this_data_len, dtokp, xid.data(), xid.size(),
                        tpc_flag, transient, false), r, dtokp)) ;
    }
    return r;
}

iores
jcntl::enqueue_extern_txn_data_record(const std::size_t tot_data_len,
                                      data_tok* dtokp,
                                      const std::string& xid,
                                      const bool tpc_flag,
                                      const bool transient)
{
    iores r;
    check_wstatus("enqueue_extern_txn_data_record");
    {
        slock s(_wr_mutex);
        while (handle_aio_wait(_wmgr.enqueue(0, tot_data_len, 0, dtokp, xid.data(), xid.size(), tpc_flag, transient,
                        true), r, dtokp)) ;
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
    if (_recoveryManager.readNextRemainingRecord(datapp, dsize, xidpp, xidsize, transient, external, dtokp, ignore_pending_txns)) {
        return RHM_IORES_SUCCESS;
    }
    return RHM_IORES_EMPTY;
}

iores
jcntl::dequeue_data_record(data_tok* const dtokp,
                           const bool txn_coml_commit)
{
    iores r;
    check_wstatus("dequeue_data");
    {
        slock s(_wr_mutex);
        while (handle_aio_wait(_wmgr.dequeue(dtokp, 0, 0, false, txn_coml_commit), r, dtokp)) ;
    }
    return r;
}

iores
jcntl::dequeue_txn_data_record(data_tok* const dtokp,
                               const std::string& xid,
                               const bool tpc_flag,
                               const bool txn_coml_commit)
{
    iores r;
    check_wstatus("dequeue_data");
    {
        slock s(_wr_mutex);
        while (handle_aio_wait(_wmgr.dequeue(dtokp, xid.data(), xid.size(), tpc_flag, txn_coml_commit), r, dtokp)) ;
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
}

LinearFileController&
jcntl::getLinearFileControllerRef() {
    return _linearFileController;
}

// static
std::string
jcntl::str2hexnum(const std::string& str) {
    if (str.empty()) {
        return "<null>";
    }
    std::ostringstream oss;
    oss << "(" << str.size() << ")0x" << std::hex;
    for (unsigned i=str.size(); i>0; --i) {
        oss << std::setfill('0') << std::setw(2) << (uint16_t)(uint8_t)str[i-1];
    }
    return oss.str();
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
//std::cout << "&&&&&& jcntl::handle_aio_wait() " << _wmgr.status_str() << std::endl; // DEBUG
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

}}}
