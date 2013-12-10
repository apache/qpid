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

#include "qpid/legacystore/JournalImpl.h"

#include "qpid/legacystore/jrnl/jerrno.h"
#include "qpid/legacystore/jrnl/jexception.h"
#include "qpid/log/Statement.h"
#include "qpid/management/ManagementAgent.h"
#include "qmf/org/apache/qpid/legacystore/ArgsJournalExpand.h"
#include "qmf/org/apache/qpid/legacystore/EventCreated.h"
#include "qmf/org/apache/qpid/legacystore/EventEnqThresholdExceeded.h"
#include "qmf/org/apache/qpid/legacystore/EventFull.h"
#include "qmf/org/apache/qpid/legacystore/EventRecovered.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Timer.h"
#include "qpid/legacystore/StoreException.h"

using namespace mrg::msgstore;
using namespace mrg::journal;
using qpid::management::ManagementAgent;
namespace _qmf = qmf::org::apache::qpid::legacystore;

InactivityFireEvent::InactivityFireEvent(JournalImpl* p, const qpid::sys::Duration timeout):
    qpid::sys::TimerTask(timeout, "JournalInactive:"+p->id()), _parent(p) {}

void InactivityFireEvent::fire() { qpid::sys::Mutex::ScopedLock sl(_ife_lock); if (_parent) _parent->flushFire(); }

GetEventsFireEvent::GetEventsFireEvent(JournalImpl* p, const qpid::sys::Duration timeout):
    qpid::sys::TimerTask(timeout, "JournalGetEvents:"+p->id()), _parent(p) {}

void GetEventsFireEvent::fire() { qpid::sys::Mutex::ScopedLock sl(_gefe_lock); if (_parent) _parent->getEventsFire(); }

JournalImpl::JournalImpl(qpid::sys::Timer& timer_,
                         const std::string& journalId,
                         const std::string& journalDirectory,
                         const std::string& journalBaseFilename,
                         const qpid::sys::Duration getEventsTimeout,
                         const qpid::sys::Duration flushTimeout,
                         qpid::management::ManagementAgent* a,
                         DeleteCallback onDelete):
                         jcntl(journalId, journalDirectory, journalBaseFilename),
                         timer(timer_),
                         getEventsTimerSetFlag(false),
                         lastReadRid(0),
                         writeActivityFlag(false),
                         flushTriggeredFlag(true),
                         _xidp(0),
                         _datap(0),
                         _dlen(0),
                         _dtok(),
                         _external(false),
                         deleteCallback(onDelete)
{
    getEventsFireEventsPtr = new GetEventsFireEvent(this, getEventsTimeout);
    inactivityFireEventPtr = new InactivityFireEvent(this, flushTimeout);
    {
        timer.start();
        timer.add(inactivityFireEventPtr);
    }

    initManagement(a);

    log(LOG_NOTICE, "Created");
    std::ostringstream oss;
    oss << "Journal directory = \"" << journalDirectory << "\"; Base file name = \"" << journalBaseFilename << "\"";
    log(LOG_DEBUG, oss.str());
}

JournalImpl::~JournalImpl()
{
    if (deleteCallback) deleteCallback(*this);
    if (_init_flag && !_stop_flag){
    	try { stop(true); } // NOTE: This will *block* until all outstanding disk aio calls are complete!
        catch (const jexception& e) { log(LOG_ERROR, e.what()); }
	}
    getEventsFireEventsPtr->cancel();
    inactivityFireEventPtr->cancel();
    free_read_buffers();

    if (_mgmtObject.get() != 0) {
        _mgmtObject->resourceDestroy();
	_mgmtObject.reset();
    }

    log(LOG_NOTICE, "Destroyed");
}

void
JournalImpl::initManagement(qpid::management::ManagementAgent* a)
{
    _agent = a;
    if (_agent != 0)
    {
        _mgmtObject = _qmf::Journal::shared_ptr (
            new _qmf::Journal(_agent, this));

        _mgmtObject->set_name(_jid);
        _mgmtObject->set_directory(_jdir.dirname());
        _mgmtObject->set_baseFileName(_base_filename);
        _mgmtObject->set_readPageSize(JRNL_RMGR_PAGE_SIZE * JRNL_SBLK_SIZE * JRNL_DBLK_SIZE);
        _mgmtObject->set_readPages(JRNL_RMGR_PAGES);

        // The following will be set on initialize(), but being properties, these must be set to 0 in the meantime
        _mgmtObject->set_initialFileCount(0);
        _mgmtObject->set_dataFileSize(0);
        _mgmtObject->set_currentFileCount(0);
        _mgmtObject->set_writePageSize(0);
        _mgmtObject->set_writePages(0);

        _agent->addObject(_mgmtObject, 0, true);
    }
}


void
JournalImpl::initialize(const u_int16_t num_jfiles,
                        const bool auto_expand,
                        const u_int16_t ae_max_jfiles,
                        const u_int32_t jfsize_sblks,
                        const u_int16_t wcache_num_pages,
                        const u_int32_t wcache_pgsize_sblks,
                        mrg::journal::aio_callback* const cbp)
{
    std::ostringstream oss;
    oss << "Initialize; num_jfiles=" << num_jfiles << " jfsize_sblks=" << jfsize_sblks;
    oss << " wcache_pgsize_sblks=" << wcache_pgsize_sblks;
    oss << " wcache_num_pages=" << wcache_num_pages;
    log(LOG_DEBUG, oss.str());
    jcntl::initialize(num_jfiles, auto_expand, ae_max_jfiles, jfsize_sblks, wcache_num_pages, wcache_pgsize_sblks, cbp);
    log(LOG_DEBUG, "Initialization complete");

    if (_mgmtObject.get() != 0)
    {
        _mgmtObject->set_initialFileCount(_lpmgr.num_jfiles());
        _mgmtObject->set_autoExpand(_lpmgr.is_ae());
        _mgmtObject->set_currentFileCount(_lpmgr.num_jfiles());
        _mgmtObject->set_maxFileCount(_lpmgr.ae_max_jfiles());
        _mgmtObject->set_dataFileSize(_jfsize_sblks * JRNL_SBLK_SIZE * JRNL_DBLK_SIZE);
        _mgmtObject->set_writePageSize(wcache_pgsize_sblks * JRNL_SBLK_SIZE * JRNL_DBLK_SIZE);
        _mgmtObject->set_writePages(wcache_num_pages);
    }
    if (_agent != 0)
        _agent->raiseEvent(qmf::org::apache::qpid::legacystore::EventCreated(_jid, _jfsize_sblks * JRNL_SBLK_SIZE * JRNL_DBLK_SIZE, _lpmgr.num_jfiles()),
                           qpid::management::ManagementAgent::SEV_NOTE);
}

void
JournalImpl::recover(const u_int16_t num_jfiles,
                     const bool auto_expand,
                     const u_int16_t ae_max_jfiles,
                     const u_int32_t jfsize_sblks,
                     const u_int16_t wcache_num_pages,
                     const u_int32_t wcache_pgsize_sblks,
                     mrg::journal::aio_callback* const cbp,
                     boost::ptr_list<msgstore::PreparedTransaction>* prep_tx_list_ptr,
                     u_int64_t& highest_rid,
                     u_int64_t queue_id)
{
    std::ostringstream oss1;
    oss1 << "Recover; num_jfiles=" << num_jfiles << " jfsize_sblks=" << jfsize_sblks;
    oss1 << " queue_id = 0x" << std::hex << queue_id << std::dec;
    oss1 << " wcache_pgsize_sblks=" << wcache_pgsize_sblks;
    oss1 << " wcache_num_pages=" << wcache_num_pages;
    log(LOG_DEBUG, oss1.str());

    if (_mgmtObject.get() != 0)
    {
        _mgmtObject->set_initialFileCount(_lpmgr.num_jfiles());
        _mgmtObject->set_autoExpand(_lpmgr.is_ae());
        _mgmtObject->set_currentFileCount(_lpmgr.num_jfiles());
        _mgmtObject->set_maxFileCount(_lpmgr.ae_max_jfiles());
        _mgmtObject->set_dataFileSize(_jfsize_sblks * JRNL_SBLK_SIZE * JRNL_DBLK_SIZE);
        _mgmtObject->set_writePageSize(wcache_pgsize_sblks * JRNL_SBLK_SIZE * JRNL_DBLK_SIZE);
        _mgmtObject->set_writePages(wcache_num_pages);
    }

    if (prep_tx_list_ptr) {
        // Create list of prepared xids
        std::vector<std::string> prep_xid_list;
        for (msgstore::PreparedTransaction::list::iterator i = prep_tx_list_ptr->begin(); i != prep_tx_list_ptr->end(); i++) {
            prep_xid_list.push_back(i->xid);
        }

        jcntl::recover(num_jfiles, auto_expand, ae_max_jfiles, jfsize_sblks, wcache_num_pages, wcache_pgsize_sblks,
                cbp, &prep_xid_list, highest_rid);
    } else {
        jcntl::recover(num_jfiles, auto_expand, ae_max_jfiles, jfsize_sblks, wcache_num_pages, wcache_pgsize_sblks,
                cbp, 0, highest_rid);
    }

    // Populate PreparedTransaction lists from _tmap
    if (prep_tx_list_ptr)
    {
        for (msgstore::PreparedTransaction::list::iterator i = prep_tx_list_ptr->begin(); i != prep_tx_list_ptr->end(); i++) {
            txn_data_list tdl = _tmap.get_tdata_list(i->xid); // tdl will be empty if xid not found
            for (tdl_itr tdl_itr = tdl.begin(); tdl_itr < tdl.end(); tdl_itr++) {
                if (tdl_itr->_enq_flag) { // enqueue op
                    i->enqueues->add(queue_id, tdl_itr->_rid);
                } else { // dequeue op
                    i->dequeues->add(queue_id, tdl_itr->_drid);
                }
            }
        }
    }
    std::ostringstream oss2;
    oss2 << "Recover phase 1 complete; highest rid found = 0x" << std::hex << highest_rid;
    oss2 << std::dec << "; emap.size=" << _emap.size() << "; tmap.size=" << _tmap.size();
    oss2 << "; journal now read-only.";
    log(LOG_DEBUG, oss2.str());

    if (_mgmtObject.get() != 0)
    {
        _mgmtObject->inc_recordDepth(_emap.size());
        _mgmtObject->inc_enqueues(_emap.size());
        _mgmtObject->inc_txn(_tmap.size());
        _mgmtObject->inc_txnEnqueues(_tmap.enq_cnt());
        _mgmtObject->inc_txnDequeues(_tmap.deq_cnt());
    }
}

void
JournalImpl::recover_complete()
{
    jcntl::recover_complete();
    log(LOG_DEBUG, "Recover phase 2 complete; journal now writable.");
    if (_agent != 0)
        _agent->raiseEvent(qmf::org::apache::qpid::legacystore::EventRecovered(_jid, _jfsize_sblks * JRNL_SBLK_SIZE * JRNL_DBLK_SIZE, _lpmgr.num_jfiles(),
                        _emap.size(), _tmap.size(), _tmap.enq_cnt(), _tmap.deq_cnt()), qpid::management::ManagementAgent::SEV_NOTE);
}

//#define MAX_AIO_SLEEPS 1000000 // tot: ~10 sec
//#define AIO_SLEEP_TIME_US   10 // 0.01 ms
// Return true if content is recovered from store; false if content is external and must be recovered from an external store.
// Throw exception for all errors.
bool
JournalImpl::loadMsgContent(u_int64_t rid, std::string& data, size_t length, size_t offset)
{
    qpid::sys::Mutex::ScopedLock sl(_read_lock);
    if (_dtok.rid() != rid)
    {
        // Free any previous msg
        free_read_buffers();

        // Last read encountered out-of-order rids, check if this rid is in that list
        bool oooFlag = false;
        for (std::vector<u_int64_t>::const_iterator i=oooRidList.begin(); i!=oooRidList.end() && !oooFlag; i++) {
            if (*i == rid) {
                oooFlag = true;
            }
        }

        // TODO: This is a brutal approach - very inefficient and slow. Rather introduce a system of remembering
        // jumpover points and allow the read to jump back to the first known jumpover point - but this needs
        // a mechanism in rrfc to accomplish it. Also helpful is a struct containing a journal address - a
        // combination of lid/offset.
        // NOTE: The second part of the if stmt (rid < lastReadRid) is required to handle browsing.
        if (oooFlag || rid < lastReadRid) {
            _rmgr.invalidate();
            oooRidList.clear();
        }
        _dlen = 0;
        _dtok.reset();
        _dtok.set_wstate(DataTokenImpl::ENQ);
        _dtok.set_rid(0);
        _external = false;
        size_t xlen = 0;
        bool transient = false;
        bool done = false;
        bool rid_found = false;
        while (!done) {
            iores res = read_data_record(&_datap, _dlen, &_xidp, xlen, transient, _external, &_dtok);
            switch (res) {
                case mrg::journal::RHM_IORES_SUCCESS:
                    if (_dtok.rid() != rid) {
                        // Check if this is an out-of-order rid that may impact next read
                        if (_dtok.rid() > rid)
                            oooRidList.push_back(_dtok.rid());
                        free_read_buffers();
                        // Reset data token for next read
                        _dlen = 0;
                        _dtok.reset();
                        _dtok.set_wstate(DataTokenImpl::ENQ);
                        _dtok.set_rid(0);
                    } else {
                        rid_found = _dtok.rid() == rid;
                        lastReadRid = rid;
                        done = true;
                    }
                    break;
                case mrg::journal::RHM_IORES_PAGE_AIOWAIT:
                    if (get_wr_events(&_aio_cmpl_timeout) == journal::jerrno::AIO_TIMEOUT) {
                        std::stringstream ss;
                        ss << "read_data_record() returned " << mrg::journal::iores_str(res);
                        ss << "; timed out waiting for page to be processed.";
                        throw jexception(mrg::journal::jerrno::JERR__TIMEOUT, ss.str().c_str(), "JournalImpl",
                            "loadMsgContent");
                    }
                    break;
                default:
                    std::stringstream ss;
                    ss << "read_data_record() returned " << mrg::journal::iores_str(res);
                    throw jexception(mrg::journal::jerrno::JERR__UNEXPRESPONSE, ss.str().c_str(), "JournalImpl",
                        "loadMsgContent");
            }
        }
        if (!rid_found) {
            std::stringstream ss;
            ss << "read_data_record() was unable to find rid 0x" << std::hex << rid << std::dec;
            ss << " (" << rid << "); last rid found was 0x" << std::hex << _dtok.rid() << std::dec;
            ss << " (" << _dtok.rid() << ")";
            throw jexception(mrg::journal::jerrno::JERR__RECNFOUND, ss.str().c_str(), "JournalImpl", "loadMsgContent");
        }
    }

    if (_external) return false;

    u_int32_t hdr_offs = qpid::framing::Buffer(static_cast<char*>(_datap), sizeof(u_int32_t)).getLong() + sizeof(u_int32_t);
    if (hdr_offs + offset + length > _dlen) {
        data.append((const char*)_datap + hdr_offs + offset, _dlen - hdr_offs - offset);
    } else {
        data.append((const char*)_datap + hdr_offs + offset, length);
    }
    return true;
}

void
JournalImpl::enqueue_data_record(const void* const data_buff, const size_t tot_data_len,
        const size_t this_data_len, data_tok* dtokp, const bool transient)
{
    handleIoResult(jcntl::enqueue_data_record(data_buff, tot_data_len, this_data_len, dtokp, transient));

    if (_mgmtObject.get() != 0)
    {
        _mgmtObject->inc_enqueues();
        _mgmtObject->inc_recordDepth();
    }
}

void
JournalImpl::enqueue_extern_data_record(const size_t tot_data_len, data_tok* dtokp,
        const bool transient)
{
    handleIoResult(jcntl::enqueue_extern_data_record(tot_data_len, dtokp, transient));

    if (_mgmtObject.get() != 0)
    {
        _mgmtObject->inc_enqueues();
        _mgmtObject->inc_recordDepth();
    }
}

void
JournalImpl::enqueue_txn_data_record(const void* const data_buff, const size_t tot_data_len,
        const size_t this_data_len, data_tok* dtokp, const std::string& xid, const bool transient)
{
    bool txn_incr = _mgmtObject.get() != 0 ? _tmap.in_map(xid) : false;

    handleIoResult(jcntl::enqueue_txn_data_record(data_buff, tot_data_len, this_data_len, dtokp, xid, transient));

    if (_mgmtObject.get() != 0)
    {
        if (!txn_incr) // If this xid was not in _tmap, it will be now...
            _mgmtObject->inc_txn();
        _mgmtObject->inc_enqueues();
        _mgmtObject->inc_txnEnqueues();
        _mgmtObject->inc_recordDepth();
    }
}

void
JournalImpl::enqueue_extern_txn_data_record(const size_t tot_data_len, data_tok* dtokp,
        const std::string& xid, const bool transient)
{
    bool txn_incr = _mgmtObject.get() != 0 ? _tmap.in_map(xid) : false;

    handleIoResult(jcntl::enqueue_extern_txn_data_record(tot_data_len, dtokp, xid, transient));

    if (_mgmtObject.get() != 0)
    {
        if (!txn_incr) // If this xid was not in _tmap, it will be now...
            _mgmtObject->inc_txn();
        _mgmtObject->inc_enqueues();
        _mgmtObject->inc_txnEnqueues();
        _mgmtObject->inc_recordDepth();
    }
}

void
JournalImpl::dequeue_data_record(data_tok* const dtokp, const bool txn_coml_commit)
{
    handleIoResult(jcntl::dequeue_data_record(dtokp, txn_coml_commit));

    if (_mgmtObject.get() != 0)
    {
        _mgmtObject->inc_dequeues();
        _mgmtObject->inc_txnDequeues();
        _mgmtObject->dec_recordDepth();
    }
}

void
JournalImpl::dequeue_txn_data_record(data_tok* const dtokp, const std::string& xid, const bool txn_coml_commit)
{
    bool txn_incr = _mgmtObject.get() != 0 ? _tmap.in_map(xid) : false;

    handleIoResult(jcntl::dequeue_txn_data_record(dtokp, xid, txn_coml_commit));

    if (_mgmtObject.get() != 0)
    {
        if (!txn_incr) // If this xid was not in _tmap, it will be now...
            _mgmtObject->inc_txn();
        _mgmtObject->inc_dequeues();
        _mgmtObject->inc_txnDequeues();
        _mgmtObject->dec_recordDepth();
    }
}

void
JournalImpl::txn_abort(data_tok* const dtokp, const std::string& xid)
{
    handleIoResult(jcntl::txn_abort(dtokp, xid));

    if (_mgmtObject.get() != 0)
    {
        _mgmtObject->dec_txn();
        _mgmtObject->inc_txnAborts();
    }
}

void
JournalImpl::txn_commit(data_tok* const dtokp, const std::string& xid)
{
    handleIoResult(jcntl::txn_commit(dtokp, xid));

    if (_mgmtObject.get() != 0)
    {
        _mgmtObject->dec_txn();
        _mgmtObject->inc_txnCommits();
    }
}

void
JournalImpl::stop(bool block_till_aio_cmpl)
{
    InactivityFireEvent* ifep = dynamic_cast<InactivityFireEvent*>(inactivityFireEventPtr.get());
    assert(ifep); // dynamic_cast can return null if the cast fails
    ifep->cancel();
    jcntl::stop(block_till_aio_cmpl);

    if (_mgmtObject.get() != 0) {
        _mgmtObject->resourceDestroy();
        _mgmtObject.reset();
    }
}

iores
JournalImpl::flush(const bool block_till_aio_cmpl)
{
    const iores res = jcntl::flush(block_till_aio_cmpl);
    {
        qpid::sys::Mutex::ScopedLock sl(_getf_lock);
        if (_wmgr.get_aio_evt_rem() && !getEventsTimerSetFlag) { setGetEventTimer(); }
    }
    return res;
}

void
JournalImpl::log(mrg::journal::log_level ll, const std::string& log_stmt) const
{
    log(ll, log_stmt.c_str());
}

void
JournalImpl::log(mrg::journal::log_level ll, const char* const log_stmt) const
{
    switch (ll)
    {
        case LOG_TRACE:  QPID_LOG(trace, "Journal \"" << _jid << "\": " << log_stmt); break;
        case LOG_DEBUG:  QPID_LOG(debug, "Journal \"" << _jid << "\": " << log_stmt); break;
        case LOG_INFO:  QPID_LOG(info, "Journal \"" << _jid << "\": " << log_stmt); break;
        case LOG_NOTICE:  QPID_LOG(notice, "Journal \"" << _jid << "\": " << log_stmt); break;
        case LOG_WARN:  QPID_LOG(warning, "Journal \"" << _jid << "\": " << log_stmt); break;
        case LOG_ERROR: QPID_LOG(error, "Journal \"" << _jid << "\": " << log_stmt); break;
        case LOG_CRITICAL: QPID_LOG(critical, "Journal \"" << _jid << "\": " << log_stmt); break;
    }
}

void
JournalImpl::getEventsFire()
{
    qpid::sys::Mutex::ScopedLock sl(_getf_lock);
    getEventsTimerSetFlag = false;
    if (_wmgr.get_aio_evt_rem()) { jcntl::get_wr_events(0); }
    if (_wmgr.get_aio_evt_rem()) { setGetEventTimer(); }
}

void
JournalImpl::flushFire()
{
    if (writeActivityFlag) {
        writeActivityFlag = false;
        flushTriggeredFlag = false;
    } else {
        if (!flushTriggeredFlag) {
            flush();
            flushTriggeredFlag = true;
        }
    }
    inactivityFireEventPtr->setupNextFire();
    {
        timer.add(inactivityFireEventPtr);
    }
}

void
JournalImpl::wr_aio_cb(std::vector<data_tok*>& dtokl)
{
    for (std::vector<data_tok*>::const_iterator i=dtokl.begin(); i!=dtokl.end(); i++)
    {
        DataTokenImpl* dtokp = static_cast<DataTokenImpl*>(*i);
	    if (/*!is_stopped() &&*/ dtokp->getSourceMessage())
	    {
		    switch (dtokp->wstate())
		    {
 			    case data_tok::ENQ:
             	    dtokp->getSourceMessage()->enqueueComplete();
 				    break;
			    case data_tok::DEQ:
/* Don't need to signal until we have a way to ack completion of dequeue in AMQP
                    dtokp->getSourceMessage()->dequeueComplete();
                    if ( dtokp->getSourceMessage()->isDequeueComplete()  ) // clear id after last dequeue
                        dtokp->getSourceMessage()->setPersistenceId(0);
*/
				    break;
			    default: ;
		    }
	    }
	    dtokp->release();
    }
}

void
JournalImpl::rd_aio_cb(std::vector<u_int16_t>& /*pil*/)
{}

void
JournalImpl::free_read_buffers()
{
    if (_xidp) {
        ::free(_xidp);
        _xidp = 0;
        _datap = 0;
    } else if (_datap) {
        ::free(_datap);
        _datap = 0;
    }
}

void
JournalImpl::handleIoResult(const iores r)
{
    writeActivityFlag = true;
    switch (r)
    {
        case mrg::journal::RHM_IORES_SUCCESS:
            return;
        case mrg::journal::RHM_IORES_ENQCAPTHRESH:
            {
                std::ostringstream oss;
                oss << "Enqueue capacity threshold exceeded on queue \"" << _jid << "\".";
                log(LOG_WARN, oss.str());
                if (_agent != 0)
                    _agent->raiseEvent(qmf::org::apache::qpid::legacystore::EventEnqThresholdExceeded(_jid, "Journal enqueue capacity threshold exceeded"),
                                       qpid::management::ManagementAgent::SEV_WARN);
                THROW_STORE_FULL_EXCEPTION(oss.str());
            }
        case mrg::journal::RHM_IORES_FULL:
            {
                std::ostringstream oss;
                oss << "Journal full on queue \"" << _jid << "\".";
                log(LOG_CRITICAL, oss.str());
                if (_agent != 0)
                    _agent->raiseEvent(qmf::org::apache::qpid::legacystore::EventFull(_jid, "Journal full"), qpid::management::ManagementAgent::SEV_ERROR);
                THROW_STORE_FULL_EXCEPTION(oss.str());
            }
        default:
            {
                std::ostringstream oss;
                oss << "Unexpected I/O response (" << mrg::journal::iores_str(r) << ") on queue " << _jid << "\".";
                log(LOG_ERROR, oss.str());
                THROW_STORE_FULL_EXCEPTION(oss.str());
            }
    }
}

qpid::management::Manageable::status_t JournalImpl::ManagementMethod (uint32_t methodId,
                                                                      qpid::management::Args& /*args*/,
                                                                      std::string& /*text*/)
{
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;

    switch (methodId)
    {
    case _qmf::Journal::METHOD_EXPAND :
        //_qmf::ArgsJournalExpand& eArgs = (_qmf::ArgsJournalExpand&) args;

        // Implement "expand" using eArgs.i_by (expand-by argument)

        status = Manageable::STATUS_NOT_IMPLEMENTED;
        break;
    }

    return status;
}
