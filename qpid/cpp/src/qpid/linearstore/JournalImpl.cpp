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

#include "qpid/linearstore/JournalImpl.h"

#include "qpid/linearstore/DataTokenImpl.h"
#include "qpid/linearstore/JournalLogImpl.h"
#include "qpid/linearstore/journal/jexception.h"
#include "qpid/linearstore/StoreException.h"
#include "qpid/management/ManagementAgent.h"

namespace qpid {
namespace linearstore {

InactivityFireEvent::InactivityFireEvent(JournalImpl* p,
                                         const ::qpid::sys::Duration timeout):
        ::qpid::sys::TimerTask(timeout, "JournalInactive:"+p->id()), _parent(p) {}

void InactivityFireEvent::fire() {
    ::qpid::sys::Mutex::ScopedLock sl(_ife_lock);
    if (_parent) {
        _parent->flushFire();
    }
}

GetEventsFireEvent::GetEventsFireEvent(JournalImpl* p,
                                       const ::qpid::sys::Duration timeout):
        ::qpid::sys::TimerTask(timeout, "JournalGetEvents:"+p->id()), _parent(p)
{}

void GetEventsFireEvent::fire() {
    ::qpid::sys::Mutex::ScopedLock sl(_gefe_lock);
    if (_parent) {
        _parent->getEventsFire();
    }
}

JournalImpl::JournalImpl(::qpid::sys::Timer& timer_,
                         const std::string& journalId,
                         const std::string& journalDirectory,
                         JournalLogImpl& journalLogRef,
                         const ::qpid::sys::Duration getEventsTimeout,
                         const ::qpid::sys::Duration flushTimeout,
                         ::qpid::management::ManagementAgent* a,
                         DeleteCallback onDelete):
                         jcntl(journalId, journalDirectory, journalLogRef),
                         timer(timer_),
                         _journalLogRef(journalLogRef),
                         getEventsTimerSetFlag(false),
                         writeActivityFlag(false),
                         flushTriggeredFlag(true),
                         deleteCallback(onDelete)
{
    getEventsFireEventsPtr = new GetEventsFireEvent(this, getEventsTimeout);
    inactivityFireEventPtr = new InactivityFireEvent(this, flushTimeout);
    {
        timer.start();
        timer.add(inactivityFireEventPtr);
    }

    initManagement(a);

    QLS_LOG2(info, _jid, "Created");
    std::ostringstream oss;
    oss << "Journal directory = \"" << journalDirectory << "\"";
    QLS_LOG2(debug, _jid, oss.str());
}

JournalImpl::~JournalImpl()
{
    if (deleteCallback) deleteCallback(*this);
    if (_init_flag && !_stop_flag){
    	try { stop(true); } // NOTE: This will *block* until all outstanding disk aio calls are complete!
        catch (const ::qpid::linearstore::journal::jexception& e) { QLS_LOG2(error, _jid, e.what()); }
	}
    getEventsFireEventsPtr->cancel();
    inactivityFireEventPtr->cancel();

    if (_mgmtObject.get() != 0) {
        _mgmtObject->resourceDestroy();
	_mgmtObject.reset();
    }

    QLS_LOG2(info, _jid, "Destroyed");
}

void
JournalImpl::initManagement(::qpid::management::ManagementAgent* a)
{
    _agent = a;
    if (_agent != 0)
    {
        _mgmtObject = ::qmf::org::apache::qpid::linearstore::Journal::shared_ptr (
            new ::qmf::org::apache::qpid::linearstore::Journal(_agent, this, _jid));

        _mgmtObject->set_directory(_jdir.dirname());
//        _mgmtObject->set_baseFileName(_base_filename);
//        _mgmtObject->set_readPageSize(JRNL_RMGR_PAGE_SIZE * JRNL_SBLK_SIZE);
//        _mgmtObject->set_readPages(JRNL_RMGR_PAGES);

        // The following will be set on initialize(), but being properties, these must be set to 0 in the meantime
        //_mgmtObject->set_initialFileCount(0);
        //_mgmtObject->set_dataFileSize(0);
        //_mgmtObject->set_currentFileCount(0);
        _mgmtObject->set_writePageSize(0);
        _mgmtObject->set_writePages(0);

        _agent->addObject(_mgmtObject, 0, true);
    }
}


void
JournalImpl::initialize(::qpid::linearstore::journal::EmptyFilePool* efpp_,
                        const uint16_t wcache_num_pages,
                        const uint32_t wcache_pgsize_sblks,
                        ::qpid::linearstore::journal::aio_callback* const cbp)
{
//    efpp->createJournal(_jdir);
//    QLS_LOG2(info, _jid, "Initialized");
//    std::ostringstream oss;
////    oss << "Initialize; num_jfiles=" << num_jfiles << " jfsize_sblks=" << jfsize_sblks;
//    oss << "Initialize; efpPartitionNumber=" << efpp_->getPartitionNumber();
//    oss << " efpFileSizeKb=" << efpp_->fileSizeKib();
//    oss << " wcache_pgsize_sblks=" << wcache_pgsize_sblks;
//    oss << " wcache_num_pages=" << wcache_num_pages;
//    QLS_LOG2(debug, _jid, oss.str());
    jcntl::initialize(efpp_, wcache_num_pages, wcache_pgsize_sblks, cbp);
//    QLS_LOG2(debug, _jid, "Initialization complete");
    // TODO: replace for linearstore: _lpmgr
/*
    if (_mgmtObject.get() != 0)
    {
        _mgmtObject->set_initialFileCount(_lpmgr.num_jfiles());
        _mgmtObject->set_autoExpand(_lpmgr.is_ae());
        _mgmtObject->set_currentFileCount(_lpmgr.num_jfiles());
        _mgmtObject->set_maxFileCount(_lpmgr.ae_max_jfiles());
        _mgmtObject->set_dataFileSize(_jfsize_sblks * JRNL_SBLK_SIZE);
        _mgmtObject->set_writePageSize(wcache_pgsize_sblks * JRNL_SBLK_SIZE);
        _mgmtObject->set_writePages(wcache_num_pages);
    }
    if (_agent != 0)
        _agent->raiseEvent::(qmf::org::apache::qpid::linearstore::EventCreated(_jid, _jfsize_sblks * JRNL_SBLK_SIZE, _lpmgr.num_jfiles()),
                           qpid::management::ManagementAgent::SEV_NOTE);
*/
}

void
JournalImpl::recover(boost::shared_ptr< ::qpid::linearstore::journal::EmptyFilePoolManager> efpm,
                     const uint16_t wcache_num_pages,
                     const uint32_t wcache_pgsize_sblks,
                     ::qpid::linearstore::journal::aio_callback* const cbp,
                     boost::ptr_list<PreparedTransaction>* prep_tx_list_ptr,
                     uint64_t& highest_rid,
                     uint64_t queue_id)
{
    std::ostringstream oss1;
    oss1 << "Recover;";
    oss1 << " queue_id = 0x" << std::hex << queue_id << std::dec;
    oss1 << " wcache_pgsize_sblks=" << wcache_pgsize_sblks;
    oss1 << " wcache_num_pages=" << wcache_num_pages;
    QLS_LOG2(debug, _jid, oss1.str());
    // TODO: replace for linearstore: _lpmgr
/*
    if (_mgmtObject.get() != 0)
    {
        _mgmtObject->set_initialFileCount(_lpmgr.num_jfiles());
        _mgmtObject->set_autoExpand(_lpmgr.is_ae());
        _mgmtObject->set_currentFileCount(_lpmgr.num_jfiles());
        _mgmtObject->set_maxFileCount(_lpmgr.ae_max_jfiles());
        _mgmtObject->set_dataFileSize(_jfsize_sblks * JRNL_SBLK_SIZE);
        _mgmtObject->set_writePageSize(wcache_pgsize_sblks * JRNL_SBLK_SIZE);
        _mgmtObject->set_writePages(wcache_num_pages);
    }
*/

    // TODO: This is ugly, find a way for RecoveryManager to use boost::ptr_list<PreparedTransaction>* directly
    if (prep_tx_list_ptr) {
        // Create list of prepared xids
        std::vector<std::string> prep_xid_list;
        for (PreparedTransaction::list::iterator i = prep_tx_list_ptr->begin(); i != prep_tx_list_ptr->end(); i++) {
            prep_xid_list.push_back(i->xid);
        }

        jcntl::recover(efpm.get(), wcache_num_pages, wcache_pgsize_sblks, cbp, &prep_xid_list, highest_rid);
    } else {
        jcntl::recover(efpm.get(), wcache_num_pages, wcache_pgsize_sblks, cbp, 0, highest_rid);
    }

    // Populate PreparedTransaction lists from _tmap
    if (prep_tx_list_ptr)
    {
        for (PreparedTransaction::list::iterator i = prep_tx_list_ptr->begin(); i != prep_tx_list_ptr->end(); i++) {
            ::qpid::linearstore::journal::txn_data_list_t tdl = _tmap.get_tdata_list(i->xid); // tdl will be empty if xid not found
            for (::qpid::linearstore::journal::tdl_itr_t tdl_itr = tdl.begin(); tdl_itr < tdl.end(); tdl_itr++) {
                if (tdl_itr->enq_flag_) { // enqueue op
                    i->enqueues->add(queue_id, tdl_itr->rid_);
                } else { // dequeue op
                    i->dequeues->add(queue_id, tdl_itr->drid_);
                }
            }
        }
    }
    std::ostringstream oss2;
    oss2 << "Recover phase 1 complete; highest rid found = 0x" << std::hex << highest_rid;
    oss2 << std::dec << "; emap.size=" << _emap.size() << "; tmap.size=" << _tmap.size();
    oss2 << "; journal now read-only.";
    QLS_LOG2(debug, _jid, oss2.str());

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
    QLS_LOG2(debug, _jid, "Recover phase 2 complete; journal now writable.");
    // TODO: replace for linearstore: _lpmgr
/*
    if (_agent != 0)
        _agent->raiseEvent(qmf::org::apache::qpid::linearstore::EventRecovered(_jid, _jfsize_sblks * JRNL_SBLK_SIZE, _lpmgr.num_jfiles(),
                        _emap.size(), _tmap.size(), _tmap.enq_cnt(), _tmap.deq_cnt()), qpid::management::ManagementAgent::SEV_NOTE);
*/
}


void
JournalImpl::enqueue_data_record(const void* const data_buff,
                                 const size_t tot_data_len,
                                 const size_t this_data_len,
                                 ::qpid::linearstore::journal::data_tok* dtokp,
                                 const bool transient)
{
    handleIoResult(jcntl::enqueue_data_record(data_buff, tot_data_len, this_data_len, dtokp, transient));

    if (_mgmtObject.get() != 0)
    {
        _mgmtObject->inc_enqueues();
        _mgmtObject->inc_recordDepth();
    }
}

void
JournalImpl::enqueue_extern_data_record(const size_t tot_data_len,
                                        ::qpid::linearstore::journal::data_tok* dtokp,
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
JournalImpl::enqueue_txn_data_record(const void* const data_buff,
                                     const size_t tot_data_len,
                                     const size_t this_data_len,
                                     ::qpid::linearstore::journal::data_tok* dtokp,
                                     const std::string& xid,
                                     const bool tpc_flag,
                                     const bool transient)
{
    bool txn_incr = _mgmtObject.get() != 0 ? _tmap.in_map(xid) : false;

    handleIoResult(jcntl::enqueue_txn_data_record(data_buff, tot_data_len, this_data_len, dtokp, xid, tpc_flag, transient));

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
JournalImpl::enqueue_extern_txn_data_record(const size_t tot_data_len,
                                            ::qpid::linearstore::journal::data_tok* dtokp,
                                            const std::string& xid,
                                            const bool tpc_flag,
                                            const bool transient)
{
    bool txn_incr = _mgmtObject.get() != 0 ? _tmap.in_map(xid) : false;

    handleIoResult(jcntl::enqueue_extern_txn_data_record(tot_data_len, dtokp, xid, tpc_flag, transient));

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
JournalImpl::dequeue_data_record(::qpid::linearstore::journal::data_tok* const dtokp,
                                 const bool txn_coml_commit)
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
JournalImpl::dequeue_txn_data_record(::qpid::linearstore::journal::data_tok* const dtokp,
                                     const std::string& xid,
                                     const bool tpc_flag,
                                     const bool txn_coml_commit)
{
    bool txn_incr = _mgmtObject.get() != 0 ? _tmap.in_map(xid) : false;

    handleIoResult(jcntl::dequeue_txn_data_record(dtokp, xid, tpc_flag, txn_coml_commit));

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
JournalImpl::txn_abort(::qpid::linearstore::journal::data_tok* const dtokp,
                       const std::string& xid)
{
    handleIoResult(jcntl::txn_abort(dtokp, xid));

    if (_mgmtObject.get() != 0)
    {
        _mgmtObject->dec_txn();
        _mgmtObject->inc_txnAborts();
    }
}

void
JournalImpl::txn_commit(::qpid::linearstore::journal::data_tok* const dtokp,
                        const std::string& xid)
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

::qpid::linearstore::journal::iores
JournalImpl::flush(const bool block_till_aio_cmpl)
{
    const ::qpid::linearstore::journal::iores res = jcntl::flush(block_till_aio_cmpl);
    {
        ::qpid::sys::Mutex::ScopedLock sl(_getf_lock);
        if (_wmgr.get_aio_evt_rem() && !getEventsTimerSetFlag) { setGetEventTimer(); }
    }
    return res;
}

void
JournalImpl::getEventsFire()
{
    ::qpid::sys::Mutex::ScopedLock sl(_getf_lock);
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
            flush(false);
            flushTriggeredFlag = true;
        }
    }
    inactivityFireEventPtr->setupNextFire();
    {
        timer.add(inactivityFireEventPtr);
    }
}

void
JournalImpl::wr_aio_cb(std::vector< ::qpid::linearstore::journal::data_tok*>& dtokl)
{
    for (std::vector< ::qpid::linearstore::journal::data_tok*>::const_iterator i=dtokl.begin(); i!=dtokl.end(); i++)
    {
        DataTokenImpl* dtokp = static_cast<DataTokenImpl*>(*i);
	    if (/*!is_stopped() &&*/ dtokp->getSourceMessage())
	    {
		    switch (dtokp->wstate())
		    {
 			    case ::qpid::linearstore::journal::data_tok::ENQ:
//std::cout << "<<<>>> JournalImpl::wr_aio_cb() ENQ dtokp rid=0x" << std::hex << dtokp->rid() << std::dec << std::endl << std::flush; // DEBUG
             	    dtokp->getSourceMessage()->enqueueComplete();
 				    break;
			    case ::qpid::linearstore::journal::data_tok::DEQ:
//std::cout << "<<<>>> JournalImpl::wr_aio_cb() DEQ dtokp rid=0x" << std::hex << dtokp->rid() << std::dec << std::endl << std::flush; // DEBUG
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
JournalImpl::rd_aio_cb(std::vector<uint16_t>& /*pil*/)
{}

void
JournalImpl::createStore() {

}

void
JournalImpl::handleIoResult(const ::qpid::linearstore::journal::iores r)
{
    writeActivityFlag = true;
    switch (r)
    {
        case ::qpid::linearstore::journal::RHM_IORES_SUCCESS:
            return;
        default:
            {
                std::ostringstream oss;
                oss << "Unexpected I/O response (" << ::qpid::linearstore::journal::iores_str(r) << ").";
                QLS_LOG2(error, _jid, oss.str());
                THROW_STORE_FULL_EXCEPTION(oss.str());
            }
    }
}

::qpid::management::Manageable::status_t JournalImpl::ManagementMethod (uint32_t /*methodId*/,
                                                                      ::qpid::management::Args& /*args*/,
                                                                      std::string& /*text*/)
{
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;

/*
    switch (methodId)
    {
    case _qmf::Journal::METHOD_EXPAND :
        //_qmf::ArgsJournalExpand& eArgs = (_qmf::ArgsJournalExpand&) args;

        // Implement "expand" using eArgs.i_by (expand-by argument)

        status = Manageable::STATUS_NOT_IMPLEMENTED;
        break;
    }
*/

    return status;
}

}}
