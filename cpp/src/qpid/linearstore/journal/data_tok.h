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

#ifndef QPID_LINEARSTORE_JOURNAL_DATA_TOK_H
#define QPID_LINEARSTORE_JOURNAL_DATA_TOK_H

namespace qpid {
namespace linearstore {
namespace journal {
class data_tok;
}}}

#include <cassert>
#include "qpid/linearstore/journal/smutex.h"

namespace qpid {
namespace linearstore {
namespace journal {

    /**
    * \class data_tok
    * \brief Data block token (data_tok) used to track wstate of a data block through asynchronous
    *     I/O process
    */
    class data_tok
    {
    public:
        // TODO: Fix this, separate write state from operation
        // ie: wstate = NONE, CACHED, PART, SUBM, COMPL
        //     op = ENQUEUE, DEQUEUE, ABORT, COMMIT
        enum write_state
        {
            NONE,       ///< Data block not sent to journal
            ENQ_CACHED, ///< Data block enqueue written to page cache
            ENQ_PART,   ///< Data block part-submitted to AIO, waiting for page buffer to free up
            ENQ_SUBM,   ///< Data block enqueue submitted to AIO
            ENQ,        ///< Data block enqueue AIO write complete (enqueue complete)
            DEQ_CACHED, ///< Data block dequeue written to page cache
            DEQ_PART,   ///< Data block part-submitted to AIO, waiting for page buffer to free up
            DEQ_SUBM,   ///< Data block dequeue submitted to AIO
            DEQ,        ///< Data block dequeue AIO write complete (dequeue complete)
            ABORT_CACHED,
            ABORT_PART,
            ABORT_SUBM,
            ABORTED,
            COMMIT_CACHED,
            COMMIT_PART,
            COMMIT_SUBM,
            COMMITTED
        };

    protected:
        static smutex _mutex;
        static uint64_t _cnt;
        uint64_t    _icnt;
        write_state _wstate;        ///< Enqueued / dequeued state of data
        std::size_t _dsize;         ///< Data size in bytes
        uint32_t    _dblks_written; ///< Data blocks read/written
        uint32_t    _pg_cnt;        ///< Page counter - incr for each page containing part of data
        uint64_t    _fid;           ///< FID containing header of enqueue record
        uint64_t    _rid;           ///< RID of data set by enqueue operation
        std::string _xid;           ///< XID set by enqueue operation
        uint64_t    _dequeue_rid;   ///< RID of data set by dequeue operation
        bool        _external_rid;  ///< Flag to indicate external setting of rid

    public:
        data_tok();
        virtual ~data_tok();

        inline uint64_t id() const { return _icnt; }
        inline write_state wstate() const { return _wstate; }
        const char* wstate_str() const;
        static const char* wstate_str(write_state wstate);
        inline bool is_writable() const { return _wstate == NONE || _wstate == ENQ_PART; }
        inline bool is_enqueued() const { return _wstate == ENQ; }
        inline bool is_readable() const { return _wstate == ENQ; }
        inline bool is_dequeueable() const { return _wstate == ENQ || _wstate == DEQ_PART; }
        inline void set_wstate(const write_state wstate) { _wstate = wstate; }
        inline std::size_t dsize() const { return _dsize; }
        inline void set_dsize(std::size_t dsize) { _dsize = dsize; }

        inline uint32_t dblocks_written() const { return _dblks_written; }
        inline void incr_dblocks_written(uint32_t dblks_written)
                { _dblks_written += dblks_written; }
        inline void set_dblocks_written(uint32_t dblks_written) { _dblks_written = dblks_written; }

        inline uint32_t pg_cnt() const { return _pg_cnt; }
        inline uint32_t incr_pg_cnt() { return ++_pg_cnt; }
        inline uint32_t decr_pg_cnt() { assert(_pg_cnt != 0); return --_pg_cnt; }

        inline uint64_t fid() const { return _fid; }
        inline void set_fid(const uint64_t fid) { _fid = fid; }
        inline uint64_t rid() const { return _rid; }
        inline void set_rid(const uint64_t rid) { _rid = rid; }
        inline uint64_t dequeue_rid() const {return _dequeue_rid; }
        inline void set_dequeue_rid(const uint64_t rid) { _dequeue_rid = rid; }
        inline bool external_rid() const { return _external_rid; }
        inline void set_external_rid(const bool external_rid) { _external_rid = external_rid; }

        inline bool has_xid() const { return !_xid.empty(); }
        inline const std::string& xid() const { return _xid; }
        inline void clear_xid() { _xid.clear(); }
        inline void set_xid(const std::string& xid) { _xid.assign(xid); }
        inline void set_xid(const void* xidp, const std::size_t xid_len)
                { _xid.assign((const char*)xidp, xid_len); }

        void reset();

        // debug aid
        std::string status_str() const;
    };

}}}

#endif // ifndef QPID_LINEARSTORE_JOURNAL_DATA_TOK_H
