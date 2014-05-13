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

#ifndef QPID_LINEARSTORE_JOURNAL_PMGR_H
#define QPID_LINEARSTORE_JOURNAL_PMGR_H

#include <deque>
#include "qpid/linearstore/journal/aio.h"
#include "qpid/linearstore/journal/deq_rec.h"
#include "qpid/linearstore/journal/enq_map.h"
#include "qpid/linearstore/journal/enq_rec.h"
#include "qpid/linearstore/journal/txn_map.h"
#include "qpid/linearstore/journal/txn_rec.h"

namespace qpid {
namespace linearstore {
namespace journal {

class aio_callback;
class data_tok;
class jcntl;
class JournalFile;

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
        AIO_PENDING                 ///< An AIO request outstanding.
    };

    /**
    * \brief Page control block, carries control and state information for each page in the
    *     cache.
    */
    struct page_cb
    {
        uint16_t _index;            ///< Index of this page
        page_state _state;          ///< Status of page
        uint64_t _frid;             ///< First rid in page (used for fhdr init)
        uint32_t _wdblks;           ///< Total number of dblks in page so far
        std::deque<data_tok*>* _pdtokl; ///< Page message tokens list
        JournalFile* _jfp;          ///< Journal file for incrementing compl counts
        void* _pbuff;               ///< Page buffer

        page_cb(uint16_t index);   ///< Convenience constructor
        const char* state_str() const; ///< Return state as string for this pcb
    };

protected:
    static const uint32_t _sblkSizeBytes; ///< Disk softblock size
    uint32_t _cache_pgsize_sblks;   ///< Size of page cache cache_num_pages
    uint16_t _cache_num_pages;      ///< Number of page cache cache_num_pages
    jcntl* _jc;                     ///< Pointer to journal controller
    enq_map& _emap;                 ///< Ref to enqueue map
    txn_map& _tmap;                 ///< Ref to transaction map
    void* _page_base_ptr;           ///< Base pointer to page memory
    void** _page_ptr_arr;           ///< Array of pointers to cache_num_pages in page memory
    page_cb* _page_cb_arr;          ///< Array of page_cb structs
    aio_cb* _aio_cb_arr;            ///< Array of iocb structs
    aio_event* _aio_event_arr;      ///< Array of io_events
    io_context_t _ioctx;            ///< AIO context for read/write operations
    uint16_t _pg_index;             ///< Index of current page being used
    uint32_t _pg_cntr;              ///< Page counter; determines if file rotation req'd
    uint32_t _pg_offset_dblks;      ///< Page offset (used so far) in data blocks
    uint32_t _aio_evt_rem;          ///< Remaining AIO events
    aio_callback* _cbp;             ///< Pointer to callback object

    enq_rec _enq_rec;               ///< Enqueue record used for encoding/decoding
    deq_rec _deq_rec;               ///< Dequeue record used for encoding/decoding
    txn_rec _txn_rec;               ///< Transaction record used for encoding/decoding

public:
    pmgr(jcntl* jc, enq_map& emap, txn_map& tmap);
    virtual ~pmgr();

    virtual int32_t get_events(timespec* const timeout, bool flush) = 0;
    inline uint32_t get_aio_evt_rem() const { return _aio_evt_rem; }
    static const char* page_state_str(page_state ps);
    inline uint32_t cache_pgsize_sblks() const { return _cache_pgsize_sblks; }
    inline uint16_t cache_num_pages() const { return _cache_num_pages; }

protected:
    virtual void initialize(aio_callback* const cbp, const uint32_t cache_pgsize_sblks,
            const uint16_t cache_num_pages);
    virtual void rotate_page() = 0;
    virtual void clean();
};

}}}

#endif // ifndef QPID_LINEARSTORE_JOURNAL_PMGR_H
