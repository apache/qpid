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

#include "jrnl_instance.h"

#include <cstdlib>
#include "data_src.h"
#include "qpid/legacystore/jrnl/data_tok.h"
#include "qpid/legacystore/jrnl/jerrno.h"
#include "test_case_result.h"

#define MAX_WR_WAIT             10 // in ms
#define MAX_RD_WAIT             100 // in ms
#define MAX_ENQCAPTHRESH_CNT    1000 // 10s if MAX_WR_WAIT is 10 ms

namespace mrg
{
namespace jtt
{

jrnl_instance::jrnl_instance(const std::string& jid, const std::string& jdir, const std::string& base_filename,
        const u_int16_t num_jfiles, const bool ae, const u_int16_t ae_max_jfiles, const u_int32_t jfsize_sblks,
        const u_int16_t wcache_num_pages, const u_int32_t wcache_pgsize_sblks):
        mrg::journal::jcntl(jid, jdir, base_filename),
        _jpp(new jrnl_init_params(jid, jdir, base_filename, num_jfiles, ae, ae_max_jfiles, jfsize_sblks,
                wcache_num_pages, wcache_pgsize_sblks)),
        _args_ptr(0),
        _dtok_master_enq_list(),
        _dtok_master_txn_list(),
        _dtok_rd_list(),
        _dtok_deq_list(),
        _rd_aio_cv(_rd_aio_mutex),
        _wr_full_cv(_wr_full_mutex),
        _rd_list_cv(_rd_list_mutex),
        _deq_list_cv(_deq_list_mutex),
        _tcp(),
        _tcrp()
{}

jrnl_instance::jrnl_instance(const jrnl_init_params::shared_ptr& p):
        mrg::journal::jcntl(p->jid(), p->jdir(), p->base_filename()),
        _jpp(p),
        _args_ptr(0),
        _dtok_master_enq_list(),
        _dtok_master_txn_list(),
        _dtok_rd_list(),
        _dtok_deq_list(),
        _rd_aio_cv(_rd_aio_mutex),
        _wr_full_cv(_wr_full_mutex),
        _rd_list_cv(_rd_list_mutex),
        _deq_list_cv(_deq_list_mutex),
        _tcp(),
        _tcrp()
{}

jrnl_instance::~jrnl_instance() {}


void
jrnl_instance::init_tc(test_case::shared_ptr& tcp, const args* const args_ptr) throw ()
{
    test_case_result::shared_ptr p(new test_case_result(_jpp->jid()));
    _tcrp = p;
    _args_ptr = args_ptr;
    try
    {
        _tcp = tcp;
        _dtok_master_enq_list.clear();
        _dtok_master_txn_list.clear();
        _dtok_rd_list.clear();
        _dtok_deq_list.clear();

        if (_args_ptr->recover_mode)
        {
            try
            {
            u_int64_t highest_rid;
            recover(_jpp->num_jfiles(), _jpp->is_ae(), _jpp->ae_max_jfiles(), _jpp->jfsize_sblks(),
                    _jpp->wcache_num_pages(), _jpp->wcache_pgsize_sblks(), this,
                    0, highest_rid);
            recover_complete();
            }
            catch (const mrg::journal::jexception& e)
            {
                if (e.err_code() == mrg::journal::jerrno::JERR_JDIR_STAT)
                    initialize(_jpp->num_jfiles(), _jpp->is_ae(), _jpp->ae_max_jfiles(), _jpp->jfsize_sblks(),
                            _jpp->wcache_num_pages(), _jpp->wcache_pgsize_sblks(), this);
                else
                    throw;
            }
        }
        else
            initialize(_jpp->num_jfiles(), _jpp->is_ae(), _jpp->ae_max_jfiles(), _jpp->jfsize_sblks(),
                    _jpp->wcache_num_pages(), _jpp->wcache_pgsize_sblks(), this);
    }
    catch (const mrg::journal::jexception& e) { _tcrp->add_exception(e); }
    catch (const std::exception& e) { _tcrp->add_exception(e.what()); }
    catch (...) { _tcrp->add_exception("Unknown exception"); }
}

void
jrnl_instance::run_tc() throw ()
{
    _tcrp->set_start_time();
    ::pthread_create(&_enq_thread, 0, run_enq, this);
    ::pthread_create(&_read_thread, 0, run_read, this);
    ::pthread_create(&_deq_thread, 0, run_deq, this);
}

void
jrnl_instance::tc_wait_compl() throw ()
{
    try
    {
        ::pthread_join(_deq_thread, 0);
        ::pthread_join(_read_thread, 0);
        ::pthread_join(_enq_thread, 0);
        stop(true);
    }
    catch (const mrg::journal::jexception& e) { _tcrp->add_exception(e); panic(); }
    catch (const std::exception& e) { _tcrp->add_exception(e.what()); panic(); }
    catch (...) { _tcrp->add_exception("Unknown exception"); panic(); }
    _lpmgr.finalize();
    _tcrp->set_stop_time();
    _tcp->add_result(_tcrp);
}

void
jrnl_instance::run_enq() throw ()
{
    try
    {
        unsigned sleep_cnt = 0U;
        while(_tcrp->num_enq() < _tcp->num_msgs() && !_tcrp->exception())
        {
            dtok_ptr p(new mrg::journal::data_tok);
            _dtok_master_enq_list.push_back(p);
            const char* msgp = data_src::get_data(_tcrp->num_enq() % 10);
            const std::size_t msg_size = _tcp->this_data_size();
            const std::size_t xid_size = _tcp->this_xid_size();
            const std::string xid(data_src::get_xid(xid_size));
            const bool external = _tcp->this_external();
            const bool transient = _tcp->this_transience();
            mrg::journal::iores res;
            if (xid_size)
            {
                if (external)
                    res = enqueue_extern_txn_data_record(msg_size, p.get(), xid, transient);
                else
                    res = enqueue_txn_data_record(msgp, msg_size, msg_size, p.get(), xid,
                            transient);
            }
            else
            {
                if (external)
                    res = enqueue_extern_data_record(msg_size, p.get(), transient);
                else
                    res = enqueue_data_record(msgp, msg_size, msg_size, p.get(), transient);
            }
            switch (res)
            {
            case mrg::journal::RHM_IORES_SUCCESS:
                sleep_cnt = 0U;
                _tcrp->incr_num_enq();
                if (p->has_xid() && !_tcp->auto_deq())
                    commit(p.get());
                break;
            case mrg::journal::RHM_IORES_ENQCAPTHRESH:
                if (++sleep_cnt > MAX_ENQCAPTHRESH_CNT)
                {
                    _tcrp->add_exception("Timeout waiting for RHM_IORES_ENQCAPTHRESH to clear.");
                    panic();
                }
                else if (get_wr_events(0) == 0) // *** GEV2
                {
                    mrg::journal::slock sl(_wr_full_mutex);
                    _wr_full_cv.waitintvl(MAX_WR_WAIT * 1000000); // MAX_WR_WAIT in ms
                }
                break;
            default:
                std::ostringstream oss;
                oss << "ERROR: enqueue operation in journal \"" << _jid << "\" returned ";
                oss << mrg::journal::iores_str(res) << ".";
                _tcrp->add_exception(oss.str());
            }
        }
        flush(true);
    }
    catch (const mrg::journal::jexception& e) { _tcrp->add_exception(e); panic(); }
    catch (const std::exception& e) { _tcrp->add_exception(e.what()); panic(); }
    catch (...) { _tcrp->add_exception("Unknown exception"); panic(); }
}

void
jrnl_instance::run_read() throw ()
{
    try
    {
        read_arg::read_mode_t rd_mode = _args_ptr->read_mode.val();
        if (rd_mode != read_arg::NONE)
        {
            while (_tcrp->num_rproc() < _tcp->num_msgs() && !_tcrp->exception())
            {
                journal::data_tok* dtokp = 0;
                {
                    mrg::journal::slock sl(_rd_list_mutex);
                    if (_dtok_rd_list.empty())
                        _rd_list_cv.wait();
                    if (!_dtok_rd_list.empty())
                    {
                        dtokp = _dtok_rd_list.front();
                        _dtok_rd_list.pop_front();
                    }
                }
                if (dtokp)
                {
                    _tcrp->incr_num_rproc();

                    bool do_read = true;
                    if (rd_mode == read_arg::RANDOM)
                        do_read = 1.0 * std::rand() / RAND_MAX <  _args_ptr->read_prob / 100.0;
                    else if (rd_mode == read_arg::LAZYLOAD)
                        do_read = _tcrp->num_rproc() >= _args_ptr->lld_skip_num &&
                                        _tcrp->num_read() < _args_ptr->lld_rd_num;
                    bool read_compl = false;
                    while (do_read && !read_compl && !_tcrp->exception())
                    {
                        void* dptr = 0;
                        std::size_t dsize = 0;
                        void* xptr = 0;
                        std::size_t xsize = 0;
                        bool tr = false;
                        bool ext = false;
                        mrg::journal::iores res = read_data_record(&dptr, dsize, &xptr, xsize, tr,
                                ext, dtokp);
                        switch (res)
                        {
                        case mrg::journal::RHM_IORES_SUCCESS:
                            {
                                mrg::journal::slock sl(_deq_list_mutex);
                                _dtok_deq_list.push_back(dtokp);
                                _deq_list_cv.broadcast();
                            }
                            read_compl = true;
                            _tcrp->incr_num_read();

                            // clean up
                            if (xsize)
                                std::free(xptr);
                            else if (dsize)
                                std::free(dptr);
                            dptr = 0;
                            xptr = 0;
                            break;
                        case mrg::journal::RHM_IORES_PAGE_AIOWAIT:
                            if (get_rd_events(0) == 0)
                            {
                                mrg::journal::slock sl(_rd_aio_mutex);
                                _rd_aio_cv.waitintvl(MAX_RD_WAIT * 1000000); // MAX_RD_WAIT in ms
                            }
                            break;
                        default:
                            std::ostringstream oss;
                            oss << "ERROR: read operation in journal \"" << _jid;
                            oss << "\" returned " << mrg::journal::iores_str(res) << ".";
                            _tcrp->add_exception(oss.str());
                            {
                                mrg::journal::slock sl(_deq_list_mutex);
                                _deq_list_cv.broadcast(); // wake up deq thread
                            }
                        }
                    }
                }
            }
        }
    }
    catch (const mrg::journal::jexception& e) { _tcrp->add_exception(e); panic(); }
    catch (const std::exception& e) { _tcrp->add_exception(e.what()); panic(); }
    catch (...) { _tcrp->add_exception("Unknown exception"); panic(); }
}

void
jrnl_instance::run_deq() throw ()
{
    try
    {
        if (_tcp->auto_deq())
        {
            while(_tcrp->num_deq() < _tcp->num_msgs() && !_tcrp->exception())
            {
                journal::data_tok* dtokp = 0;
                {
                    mrg::journal::slock sl(_deq_list_mutex);
                    if (_dtok_deq_list.empty())
                        _deq_list_cv.wait();
                    if (!_dtok_deq_list.empty())
                    {
                        dtokp = _dtok_deq_list.front();
                        _dtok_deq_list.pop_front();
                    }
                }
                if (dtokp)
                {
                    mrg::journal::iores res;
                    if (dtokp->has_xid())
                        res = dequeue_txn_data_record(dtokp, dtokp->xid());
                    else
                        res = dequeue_data_record(dtokp);
                    if (res == mrg::journal::RHM_IORES_SUCCESS)
                    {
                        _tcrp->incr_num_deq();
                        commit(dtokp);
                    }
                    else
                    {
                        std::ostringstream oss;
                        oss << "ERROR: dequeue operation in journal \"" << _jid;
                        oss << "\" returned " << mrg::journal::iores_str(res) << ".";
                        _tcrp->add_exception(oss.str());
                    }
                }
            }
            flush(true);
        }
    }
    catch (const mrg::journal::jexception& e) { _tcrp->add_exception(e); panic(); }
    catch (const std::exception& e) { _tcrp->add_exception(e.what()); panic(); }
    catch (...) { _tcrp->add_exception("Unknown exception"); panic(); }
}

void
jrnl_instance::abort(const mrg::journal::data_tok* dtokp)
{
    txn(dtokp, false);
}

void
jrnl_instance::commit(const mrg::journal::data_tok* dtokp)
{
    txn(dtokp, true);
}

void
jrnl_instance::txn(const mrg::journal::data_tok* dtokp, const bool commit)
{
    if (dtokp->has_xid())
    {
        mrg::journal::data_tok* p = prep_txn_dtok(dtokp);
        mrg::journal::iores res = commit ? txn_commit(p, p->xid()) : txn_abort(p, p->xid());
        if (res != mrg::journal::RHM_IORES_SUCCESS)
        {
            std::ostringstream oss;
            oss << "ERROR: " << (commit ? "commit" : "abort") << " operation in journal \"";
            oss << _jid << "\" returned " << mrg::journal::iores_str(res) << ".";
            _tcrp->add_exception(oss.str());
        }
    }
}

mrg::journal::data_tok*
jrnl_instance::prep_txn_dtok(const mrg::journal::data_tok* dtokp)
{
    dtok_ptr p(new mrg::journal::data_tok);
    _dtok_master_txn_list.push_back(p);
    p->set_xid(dtokp->xid());
    return p.get();
}

void
jrnl_instance::panic()
{
    // In the event of a panic or exception condition, release all waiting CVs
    _rd_aio_cv.broadcast();
    _wr_full_cv.broadcast();
    _rd_list_cv.broadcast();
    _deq_list_cv.broadcast();
}

// AIO callbacks

void
jrnl_instance::wr_aio_cb(std::vector<journal::data_tok*>& dtokl)
{
    for (std::vector<journal::data_tok*>::const_iterator i=dtokl.begin(); i!=dtokl.end(); i++)
    {
        if ((*i)->wstate() == journal::data_tok::ENQ || (*i)->wstate() == journal::data_tok::DEQ)
        {
            journal::data_tok* dtokp = *i;
            if (dtokp->wstate() == journal::data_tok::ENQ)
            {
                if (_args_ptr->read_mode.val() == read_arg::NONE)
                {
                    mrg::journal::slock sl(_deq_list_mutex);
                    _dtok_deq_list.push_back(dtokp);
                    _deq_list_cv.broadcast();
                }
                else
                {
                    mrg::journal::slock sl(_rd_list_mutex);
                    _dtok_rd_list.push_back(dtokp);
                    _rd_list_cv.broadcast();
                }
            }
            else // DEQ
            {
                mrg::journal::slock sl(_wr_full_mutex);
                _wr_full_cv.broadcast();
            }
        }
    }
}

void
jrnl_instance::rd_aio_cb(std::vector<u_int16_t>& /*pil*/)
{
    mrg::journal::slock sl(_rd_aio_mutex);
    _rd_aio_cv.broadcast();
}

} // namespace jtt
} // namespace mrg
