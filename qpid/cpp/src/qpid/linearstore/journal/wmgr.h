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

#ifndef QPID_LINEARSTORE_JOURNAL_WMGR_H
#define QPID_LINEARSTORE_JOURNAL_WMGR_H

#include <deque>
#include <map>
#include "qpid/linearstore/journal/enums.h"
#include "qpid/linearstore/journal/pmgr.h"
#include <vector>

namespace qpid {
namespace linearstore {
namespace journal {

class LinearFileController;

/**
* \brief Class for managing a write page cache of arbitrary size and number of pages.
*
* The write page cache works on the principle of caching the write data within a page until
* that page is either full or flushed; this initiates a single AIO write operation to store
* the data on disk.
*
* The maximum disk throughput is achieved by keeping the write operations of uniform size.
* Waiting for a page cache to fill achieves this; and in high data volume/throughput situations
* achieves the optimal disk throughput. Calling flush() forces a write of the current page cache
* no matter how full it is, and disrupts the uniformity of the write operations. This should
* normally only be done if throughput drops and there is a danger of a page of unwritten data
* waiting around for excessive time.
*
* The usual tradeoff between data storage latency and throughput performance applies.
*/
class wmgr : public pmgr
{
private:
    typedef std::vector<uint64_t> fidl_t;
    typedef fidl_t::iterator fidl_itr_t;
    typedef std::map<std::string, fidl_t> pending_txn_map_t;
    typedef pending_txn_map_t::iterator pending_txn_map_itr_t;

    LinearFileController& _lfc;     ///< Linear File Controller ref
    uint32_t _max_dtokpp;           ///< Max data writes per page
    uint32_t _max_io_wait_us;       ///< Max wait in microseconds till submit
    uint32_t _cached_offset_dblks;  ///< Amount of unwritten data in page (dblocks)

    // TODO: Convert _enq_busy etc into a proper threadsafe lock
    // TODO: Convert to enum? Are these encodes mutually exclusive?
    bool _enq_busy;                 ///< Flag true if enqueue is in progress
    bool _deq_busy;                 ///< Flag true if dequeue is in progress
    bool _abort_busy;               ///< Flag true if abort is in progress
    bool _commit_busy;              ///< Flag true if commit is in progress

    enum _op_type { WMGR_ENQUEUE = 0, WMGR_DEQUEUE, WMGR_ABORT, WMGR_COMMIT };
    static const char* _op_str[];

    enq_rec _enq_rec;               ///< Enqueue record used for encoding/decoding
    deq_rec _deq_rec;               ///< Dequeue record used for encoding/decoding
    txn_rec _txn_rec;               ///< Transaction record used for encoding/decoding
    pending_txn_map_t _txn_pending_map; ///< Set containing xids of pending commits/aborts

public:
    wmgr(jcntl* jc,
         enq_map& emap,
         txn_map& tmap,
         LinearFileController& lfc);
    wmgr(jcntl* jc,
         enq_map& emap,
         txn_map& tmap,
         LinearFileController& lfc,
         const uint32_t max_dtokpp,
         const uint32_t max_iowait_us);
    virtual ~wmgr();

    void initialize(aio_callback* const cbp,
                    const uint32_t wcache_pgsize_sblks,
                    const uint16_t wcache_num_pages,
                    const uint32_t max_dtokpp,
                    const uint32_t max_iowait_us,
                    std::size_t end_offset);
    iores enqueue(const void* const data_buff,
                  const std::size_t tot_data_len,
                  const std::size_t this_data_len,
                  data_tok* dtokp,
                  const void* const xid_ptr,
                  const std::size_t xid_len,
                  const bool tpc_flag,
                  const bool transient,
                  const bool external);
    iores dequeue(data_tok* dtokp,
                  const void* const xid_ptr,
                  const std::size_t xid_len,
                  const bool tpc_flag,
                  const bool txn_coml_commit);
    iores abort(data_tok* dtokp,
                const void* const xid_ptr,
                const std::size_t xid_len);
    iores commit(data_tok* dtokp,
                 const void* const xid_ptr,
                 const std::size_t xid_len);
    iores flush();
    int32_t get_events(timespec* const timeout,
                       bool flush);
    bool is_txn_synced(const std::string& xid);
    inline bool curr_pg_blocked() const { return _page_cb_arr[_pg_index]._state != UNUSED; }
    inline uint32_t unflushed_dblks() { return _cached_offset_dblks; }

    // Debug aid
    const std::string status_str() const;

private:
    void initialize(aio_callback* const cbp,
                    const uint32_t wcache_pgsize_sblks,
                    const uint16_t wcache_num_pages);
    iores pre_write_check(const _op_type op,
                          const data_tok* const dtokp,
                          const std::size_t xidsize = 0,
                          const std::size_t dsize = 0,
                          const bool external = false) const;
    void dequeue_check(const std::string& xid,
                       const uint64_t drid);
    void file_header_check(const uint64_t rid,
                           const bool cont,
                           const uint32_t rec_dblks_rem);
    void flush_check(iores& res,
                     bool& cont,
                     bool& done, const uint64_t rid);
    iores write_flush();
    void get_next_file();
    void dblk_roundup();
    void rotate_page();
    void clean();
};

}}}

#endif // ifndef QPID_LINEARSTORE_JOURNAL_WMGR_H
