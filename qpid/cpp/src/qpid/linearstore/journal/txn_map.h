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

#ifndef QPID_LINEARSTORE_JOURNAL_TXN_MAP_H
#define QPID_LINEARSTORE_JOURNAL_TXN_MAP_H

#include "qpid/linearstore/journal/smutex.h"
#include <map>
#include <vector>

namespace qpid {
namespace linearstore {
namespace journal {

    /**
    * \struct txn_data_struct
    * \brief Struct encapsulating transaction data necessary for processing a transaction
    *     in the journal once it is closed with either a commit or abort.
    */
    typedef struct txn_data_t
    {
        uint64_t rid_;      ///< Record id for this operation
        uint64_t drid_;     ///< Dequeue record id for this operation
        uint64_t fid_;      ///< File seq number, to be used when transferring to emap on commit
        uint64_t foffs_;    ///< Offset in file for this record
        bool enq_flag_;     ///< If true, enq op, otherwise deq op
        bool tpc_flag_;     ///< 2PC transaction if true
        bool commit_flag_;  ///< TPL only: (2PC transactions) Records 2PC complete c/a mode
        bool aio_compl_;    ///< Initially false, set to true when record AIO returns
        txn_data_t(const uint64_t rid,
                   const uint64_t drid,
                   const uint64_t fid,
                   const uint64_t foffs,
                   const bool enq_flag,
                   const bool tpc_flag,
                   const bool commit_flag);
    } txn_data_t;
    typedef std::vector<txn_data_t> txn_data_list_t;
    typedef txn_data_list_t::iterator tdl_itr_t;
    typedef txn_data_list_t::const_iterator tdl_const_itr_t;

    typedef struct txn_op_stats_t
    {
        uint16_t enqCnt;
        uint16_t deqCnt;
        uint16_t tpcCnt;
        uint16_t abortCnt;
        uint16_t commitCnt;
        uint64_t rid;
        txn_op_stats_t(const txn_data_list_t& tdl);
    } txn_op_stats_t;

    /**
    * \class txn_map
    * \brief Class for storing transaction data for each open (ie not committed or aborted)
    *     xid in the store. If aborted, records are discarded; if committed, they are
    *     transferred to the enqueue map.
    *
    * The data is encapsulated by struct txn_data_struct. A vector containing the information
    * for each operation included as part of the same transaction is mapped against the
    * xid.
    *
    * The aio_compl flag is set true as each AIO write operation for the enqueue or dequeue
    * returns. Checking that all of these flags are true for a given xid is the mechanism
    * used to determine if the transaction is syncronized (through method is_txn_synced()).
    *
    * On transaction commit, then each operation is handled as follows:
    *
    * If an enqueue (_enq_flag is true), then the rid and pfid are transferred to the enq_map.
    * If a dequeue (_enq_flag is false), then the rid stored in the drid field is used to
    * remove the corresponding record from the enq_map.
    *
    * On transaction abort, then each operation is handled as follows:
    *
    * If an enqueue (_enq_flag is true), then the data is simply discarded.
    * If a dequeue (_enq_flag is false), then the lock for the corresponding enqueue in enq_map
    * (if not a part of the same transaction) is removed, and the data discarded.
    *
    * <pre>
    *   key      data
    *
    *   xid1 --- vector< [ rid, drid, pfid, enq_flag, commit_flag, aio_compl ] >
    *   xid2 --- vector< [ rid, drid, pfid, enq_flag, commit_flag, aio_compl ] >
    *   xid3 --- vector< [ rid, drid, pfid, enq_flag, commit_flag, aio_compl ] >
    *   ...
    * </pre>
    */
    class txn_map
    {
    public:
        // return/error codes
        static int16_t TMAP_RID_NOT_FOUND;
        static int16_t TMAP_XID_NOT_FOUND;
        static int16_t TMAP_OK;
        static int16_t TMAP_NOT_SYNCED;
        static int16_t TMAP_SYNCED;

    private:
        typedef std::pair<std::string, txn_data_list_t> xmap_param;
        typedef std::map<std::string, txn_data_list_t> xmap;
        typedef xmap::iterator xmap_itr;

        xmap _map;
        smutex _mutex;
        const txn_data_list_t _empty_data_list;

    public:
        txn_map();
        virtual ~txn_map();

        bool insert_txn_data(const std::string& xid, const txn_data_t& td);
        const txn_data_list_t get_tdata_list(const std::string& xid);
        const txn_data_list_t get_remove_tdata_list(const std::string& xid);
        bool in_map(const std::string& xid);
        uint32_t enq_cnt();
        uint32_t deq_cnt();
        int16_t is_txn_synced(const std::string& xid); // -1=xid not found; 0=not synced; 1=synced
        int16_t set_aio_compl(const std::string& xid, const uint64_t rid); // -2=rid not found; -1=xid not found; 0=done
        bool data_exists(const std::string& xid, const uint64_t rid);
        bool is_enq(const uint64_t rid);
        inline void clear() { _map.clear(); }
        inline bool empty() const { return _map.empty(); }
        inline size_t size() const { return _map.size(); }
        void xid_list(std::vector<std::string>& xv);
    private:
        uint32_t cnt(const bool enq_flag);
        const txn_data_list_t get_tdata_list_nolock(const std::string& xid);
    };

}}}

#endif // ifndef QPID_LINEARSTORE_JOURNAL_TXN_MAP_H
