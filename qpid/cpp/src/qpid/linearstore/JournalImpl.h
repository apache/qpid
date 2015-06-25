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

#ifndef QPID_LINEARSTORE_JOURNALIMPL_H
#define QPID_LINEARSTORE_JOURNALIMPL_H

#include <boost/ptr_container/ptr_list.hpp>
#include "qpid/broker/PersistableQueue.h"
#include "qpid/linearstore/journal/aio_callback.h"
#include "qpid/linearstore/journal/jcntl.h"
#include "qpid/linearstore/PreparedTransaction.h"
#include "qpid/sys/Timer.h"

#include "qmf/org/apache/qpid/linearstore/Journal.h"

namespace qpid{

namespace sys {
//class Timer;
}

namespace linearstore{
namespace journal {
//    class EmptyFilePool;
}
class JournalImpl;
class JournalLogImpl;

class InactivityFireEvent : public ::qpid::sys::TimerTask
{
    JournalImpl* _parent;
    ::qpid::sys::Mutex _ife_lock;

  public:
    InactivityFireEvent(JournalImpl* p,
                        const ::qpid::sys::Duration timeout);
    virtual ~InactivityFireEvent() {}
    void fire();
    inline void cancel() { ::qpid::sys::Mutex::ScopedLock sl(_ife_lock); _parent = 0; }
};

class GetEventsFireEvent : public ::qpid::sys::TimerTask
{
    JournalImpl* _parent;
    ::qpid::sys::Mutex _gefe_lock;

  public:
    GetEventsFireEvent(JournalImpl* p,
                       const ::qpid::sys::Duration timeout);
    virtual ~GetEventsFireEvent() {}
    void fire();
    inline void cancel() { ::qpid::sys::Mutex::ScopedLock sl(_gefe_lock); _parent = 0; }
};

class JournalImpl : public ::qpid::broker::ExternalQueueStore,
                    public ::qpid::linearstore::journal::jcntl,
                    public ::qpid::linearstore::journal::aio_callback
{
  public:
    typedef boost::function<void (JournalImpl&)> DeleteCallback;

  protected:
    ::qpid::sys::Timer& timer;
    JournalLogImpl& _journalLogRef;
    bool getEventsTimerSetFlag;
    boost::intrusive_ptr< ::qpid::sys::TimerTask> getEventsFireEventsPtr;
    ::qpid::sys::Mutex _getf_lock;
    ::qpid::sys::Mutex _read_lock;

    bool writeActivityFlag;
    bool flushTriggeredFlag;
    boost::intrusive_ptr< ::qpid::sys::TimerTask> inactivityFireEventPtr;

    ::qpid::management::ManagementAgent* _agent;
    ::qmf::org::apache::qpid::linearstore::Journal::shared_ptr _mgmtObject;
    DeleteCallback deleteCallback;

  public:

    JournalImpl(::qpid::sys::Timer& timer,
                const std::string& journalId,
                const std::string& journalDirectory,
                JournalLogImpl& journalLogRef,
                const ::qpid::sys::Duration getEventsTimeout,
                const ::qpid::sys::Duration flushTimeout,
                ::qpid::management::ManagementAgent* agent,
                DeleteCallback deleteCallback=DeleteCallback() );

    virtual ~JournalImpl();

    void initManagement(::qpid::management::ManagementAgent* agent);

    void initialize(::qpid::linearstore::journal::EmptyFilePool* efp,
                    const uint16_t wcache_num_pages,
                    const uint32_t wcache_pgsize_sblks,
                    ::qpid::linearstore::journal::aio_callback* const cbp);

    inline void initialize(::qpid::linearstore::journal::EmptyFilePool* efpp,
                           const uint16_t wcache_num_pages,
                           const uint32_t wcache_pgsize_sblks) {
        initialize(efpp, wcache_num_pages, wcache_pgsize_sblks, this);
    }

    void recover(boost::shared_ptr< ::qpid::linearstore::journal::EmptyFilePoolManager> efpm,
                 const uint16_t wcache_num_pages,
                 const uint32_t wcache_pgsize_sblks,
                 ::qpid::linearstore::journal::aio_callback* const cbp,
                 boost::ptr_list<PreparedTransaction>* prep_tx_list_ptr,
                 uint64_t& highest_rid,
                 uint64_t queue_id);

    inline void recover(boost::shared_ptr< ::qpid::linearstore::journal::EmptyFilePoolManager> efpm,
                        const uint16_t wcache_num_pages,
                        const uint32_t wcache_pgsize_sblks,
                        boost::ptr_list<PreparedTransaction>* prep_tx_list_ptr,
                        uint64_t& highest_rid,
                        uint64_t queue_id) {
        recover(efpm, wcache_num_pages, wcache_pgsize_sblks, this, prep_tx_list_ptr, highest_rid, queue_id);
    }

    void recover_complete();

    // Overrides for write inactivity timer
    void enqueue_data_record(const void* const data_buff,
                             const size_t tot_data_len,
                             const size_t this_data_len,
                             ::qpid::linearstore::journal::data_tok* dtokp,
                             const bool transient);

    void enqueue_extern_data_record(const size_t tot_data_len,
                                    ::qpid::linearstore::journal::data_tok* dtokp,
                                    const bool transient);

    void enqueue_txn_data_record(const void* const data_buff,
                                 const size_t tot_data_len,
                                 const size_t this_data_len,
                                 ::qpid::linearstore::journal::data_tok* dtokp,
                                 const std::string& xid,
                                 const bool tpc_flag,
                                 const bool transient);

    void enqueue_extern_txn_data_record(const size_t tot_data_len,
                                        ::qpid::linearstore::journal::data_tok* dtokp,
                                        const std::string& xid,
                                        const bool tpc_flag,
                                        const bool transient);

    void dequeue_data_record(::qpid::linearstore::journal::data_tok*
                             const dtokp,
                             const bool txn_coml_commit);

    void dequeue_txn_data_record(::qpid::linearstore::journal::data_tok* const dtokp,
                                 const std::string& xid,
                                 const bool tpc_flag,
                                 const bool txn_coml_commit);

    void txn_abort(::qpid::linearstore::journal::data_tok* const dtokp, const std::string& xid);

    void txn_commit(::qpid::linearstore::journal::data_tok* const dtokp, const std::string& xid);

    void stop(bool block_till_aio_cmpl = false);

    // Overrides for get_events timer
    ::qpid::linearstore::journal::iores flush(const bool block_till_aio_cmpl);

    // TimerTask callback
    void getEventsFire();
    void flushFire();

    // AIO callbacks
    virtual void wr_aio_cb(std::vector< ::qpid::linearstore::journal::data_tok*>& dtokl);
    virtual void rd_aio_cb(std::vector<uint16_t>& pil);

    ::qpid::management::ManagementObject::shared_ptr GetManagementObject (void) const
    { return _mgmtObject; }

    ::qpid::management::Manageable::status_t ManagementMethod(uint32_t,
                                                              ::qpid::management::Args&,
                                                              std::string&);

    void resetDeleteCallback() { deleteCallback = DeleteCallback(); }

  protected:
    void createStore();

    inline void setGetEventTimer()
    {
        getEventsFireEventsPtr->setupNextFire();
        timer.add(getEventsFireEventsPtr);
        getEventsTimerSetFlag = true;
    }
    void handleIoResult(const ::qpid::linearstore::journal::iores r);

    // Management instrumentation callbacks overridden from jcntl
    inline void instr_incr_outstanding_aio_cnt() {
      if (_mgmtObject.get() != 0) _mgmtObject->inc_outstandingAIOs();
    }
    inline void instr_decr_outstanding_aio_cnt() {
      if (_mgmtObject.get() != 0) _mgmtObject->dec_outstandingAIOs();
    }

}; // class JournalImpl

class TplJournalImpl : public JournalImpl
{
  public:
    TplJournalImpl(::qpid::sys::Timer& timer,
                   const std::string& journalId,
                   const std::string& journalDirectory,
                   JournalLogImpl& journalLogRef,
                   const ::qpid::sys::Duration getEventsTimeout,
                   const ::qpid::sys::Duration flushTimeout,
                   ::qpid::management::ManagementAgent* agent) :
        JournalImpl(timer, journalId, journalDirectory, journalLogRef, getEventsTimeout, flushTimeout, agent)
    {}

    virtual ~TplJournalImpl() {}

    // Special version of read_data_record that ignores transactions - needed when reading the TPL
    inline ::qpid::linearstore::journal::iores read_data_record(void** const datapp,
                                                                std::size_t& dsize,
                                                                void** const xidpp,
                                                                std::size_t& xidsize,
                                                                bool& transient,
                                                                bool& external,
                                                                ::qpid::linearstore::journal::data_tok* const dtokp) {
        return JournalImpl::read_data_record(datapp, dsize, xidpp, xidsize, transient, external, dtokp, false);
    }
}; // class TplJournalImpl

} // namespace msgstore
} // namespace mrg

#endif // ifndef QPID_LINEARSTORE_JOURNALIMPL_H
