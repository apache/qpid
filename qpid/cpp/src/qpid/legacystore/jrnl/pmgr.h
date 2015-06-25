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
 * \file pmgr.h
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::pmgr (page manager). See
 * class documentation for details.
 *
 * \author Kim van der Riet
 */

#ifndef QPID_LEGACYSTORE_JRNL_PMGR_H
#define QPID_LEGACYSTORE_JRNL_PMGR_H

namespace mrg
{
namespace journal
{
    class pmgr;
    class jcntl;
}
}

#include <deque>
#include "qpid/legacystore/jrnl/aio.h"
#include "qpid/legacystore/jrnl/aio_callback.h"
#include "qpid/legacystore/jrnl/data_tok.h"
#include "qpid/legacystore/jrnl/deq_rec.h"
#include "qpid/legacystore/jrnl/enq_map.h"
#include "qpid/legacystore/jrnl/enq_rec.h"
#include "qpid/legacystore/jrnl/fcntl.h"
#include "qpid/legacystore/jrnl/txn_map.h"
#include "qpid/legacystore/jrnl/txn_rec.h"

namespace mrg
{
namespace journal
{

    /**
    * \brief Abstract class for managing either read or write page cache of arbitrary size and
    *    number of cache_num_pages.
    */
    class pmgr
    {
    public:
        /**
        * \brief Enumeration of possible stats of a page within a page cache.
        */
        enum page_state
        {
            UNUSED,                     ///< A page is uninitialized, contains no data.
            IN_USE,                     ///< Page is in use.
            AIO_PENDING,                ///< An AIO request outstanding.
            AIO_COMPLETE                ///< An AIO request is complete.
        };

    protected:
        /**
        * \brief Page control block, carries control and state information for each page in the
        *     cache.
        */
        struct page_cb
        {
            u_int16_t _index;           ///< Index of this page
            page_state _state;          ///< Status of page
            u_int64_t _frid;            ///< First rid in page (used for fhdr init)
            u_int32_t _wdblks;          ///< Total number of dblks in page so far
            u_int32_t _rdblks;          ///< Total number of dblks in page
            std::deque<data_tok*>* _pdtokl; ///< Page message tokens list
            fcntl* _wfh;                ///< File handle for incrementing write compl counts
            fcntl* _rfh;                ///< File handle for incrementing read compl counts
            void* _pbuff;               ///< Page buffer

            page_cb(u_int16_t index);   ///< Convenience constructor
            const char* state_str() const; ///< Return state as string for this pcb
        };

        static const u_int32_t _sblksize; ///< Disk softblock size
        u_int32_t _cache_pgsize_sblks;  ///< Size of page cache cache_num_pages
        u_int16_t _cache_num_pages;     ///< Number of page cache cache_num_pages
        jcntl* _jc;                     ///< Pointer to journal controller
        enq_map& _emap;                 ///< Ref to enqueue map
        txn_map& _tmap;                 ///< Ref to transaction map
        void* _page_base_ptr;           ///< Base pointer to page memory
        void** _page_ptr_arr;           ///< Array of pointers to cache_num_pages in page memory
        page_cb* _page_cb_arr;          ///< Array of page_cb structs
        aio_cb* _aio_cb_arr;            ///< Array of iocb structs
        aio_event* _aio_event_arr;      ///< Array of io_events
        io_context_t _ioctx;            ///< AIO context for read/write operations
        u_int16_t _pg_index;            ///< Index of current page being used
        u_int32_t _pg_cntr;             ///< Page counter; determines if file rotation req'd
        u_int32_t _pg_offset_dblks;     ///< Page offset (used so far) in data blocks
        u_int32_t _aio_evt_rem;         ///< Remaining AIO events
        aio_callback* _cbp;             ///< Pointer to callback object

        enq_rec _enq_rec;               ///< Enqueue record used for encoding/decoding
        deq_rec _deq_rec;               ///< Dequeue record used for encoding/decoding
        txn_rec _txn_rec;               ///< Transaction record used for encoding/decoding

    public:
        pmgr(jcntl* jc, enq_map& emap, txn_map& tmap);
        virtual ~pmgr();

        virtual int32_t get_events(page_state state, timespec* const timeout, bool flush = false) = 0;
        inline u_int32_t get_aio_evt_rem() const { return _aio_evt_rem; }
        static const char* page_state_str(page_state ps);
        inline u_int32_t cache_pgsize_sblks() const { return _cache_pgsize_sblks; }
        inline u_int16_t cache_num_pages() const { return _cache_num_pages; }

    protected:
        virtual void initialize(aio_callback* const cbp, const u_int32_t cache_pgsize_sblks,
                const u_int16_t cache_num_pages);
        virtual void rotate_page() = 0;
        virtual void clean();
    };

} // namespace journal
} // namespace mrg

#endif // ifndef QPID_LEGACYSTORE_JRNL_PMGR_H
