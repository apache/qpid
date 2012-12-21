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
 * \file pmgr.cpp
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::pmgr (page manager). See
 * comments in file pmgr.h for details.
 *
 * \author Kim van der Riet
 */

#include "qpid/legacystore/jrnl/pmgr.h"

#include <cerrno>
#include <cstdlib>
#include <cstring>
#include "qpid/legacystore/jrnl/jcfg.h"
#include "qpid/legacystore/jrnl/jcntl.h"
#include "qpid/legacystore/jrnl/jerrno.h"
#include <sstream>


namespace mrg
{
namespace journal
{

pmgr::page_cb::page_cb(u_int16_t index):
        _index(index),
        _state(UNUSED),
        _wdblks(0),
        _rdblks(0),
        _pdtokl(0),
        _wfh(0),
        _rfh(0),
        _pbuff(0)
{}

const char*
pmgr::page_cb::state_str() const
{
    switch(_state)
    {
        case UNUSED:
            return "UNUSED";
        case IN_USE:
            return "IN_USE";
        case AIO_PENDING:
            return "AIO_PENDING";
        case AIO_COMPLETE:
            return "AIO_COMPLETE";
    }
    return "<unknown>";
}

const u_int32_t pmgr::_sblksize = JRNL_SBLK_SIZE * JRNL_DBLK_SIZE;

pmgr::pmgr(jcntl* jc, enq_map& emap, txn_map& tmap):
        _cache_pgsize_sblks(0),
        _cache_num_pages(0),
        _jc(jc),
        _emap(emap),
        _tmap(tmap),
        _page_base_ptr(0),
        _page_ptr_arr(0),
        _page_cb_arr(0),
        _aio_cb_arr(0),
        _aio_event_arr(0),
        _ioctx(0),
        _pg_index(0),
        _pg_cntr(0),
        _pg_offset_dblks(0),
        _aio_evt_rem(0),
        _cbp(0),
        _enq_rec(),
        _deq_rec(),
        _txn_rec()
{}

pmgr::~pmgr()
{
    pmgr::clean();
}

void
pmgr::initialize(aio_callback* const cbp, const u_int32_t cache_pgsize_sblks, const u_int16_t cache_num_pages)
{
    // As static use of this class keeps old values around, clean up first...
    pmgr::clean();
    _pg_index = 0;
    _pg_cntr = 0;
    _pg_offset_dblks = 0;
    _aio_evt_rem = 0;
    _cache_pgsize_sblks = cache_pgsize_sblks;
    _cache_num_pages = cache_num_pages;
    _cbp = cbp;

    // 1. Allocate page memory (as a single block)
    std::size_t cache_pgsize = _cache_num_pages * _cache_pgsize_sblks * _sblksize;
    if (::posix_memalign(&_page_base_ptr, _sblksize, cache_pgsize))
    {
        clean();
        std::ostringstream oss;
        oss << "posix_memalign(): blksize=" << _sblksize << " size=" << cache_pgsize;
        oss << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR__MALLOC, oss.str(), "pmgr", "initialize");
    }
    // 2. Allocate array of page pointers
    _page_ptr_arr = (void**)std::malloc(_cache_num_pages * sizeof(void*));
    MALLOC_CHK(_page_ptr_arr, "_page_ptr_arr", "pmgr", "initialize");

    // 3. Allocate and initilaize page control block (page_cb) array
    _page_cb_arr = (page_cb*)std::malloc(_cache_num_pages * sizeof(page_cb));
    MALLOC_CHK(_page_cb_arr, "_page_cb_arr", "pmgr", "initialize");
    std::memset(_page_cb_arr, 0, _cache_num_pages * sizeof(page_cb));

    // 5. Allocate IO control block (iocb) array
    _aio_cb_arr = (aio_cb*)std::malloc(_cache_num_pages * sizeof(aio_cb));
    MALLOC_CHK(_aio_cb_arr, "_aio_cb_arr", "pmgr", "initialize");

    // 6. Set page pointers in _page_ptr_arr, _page_cb_arr and iocbs to pages within page block
    for (u_int16_t i=0; i<_cache_num_pages; i++)
    {
        _page_ptr_arr[i] = (void*)((char*)_page_base_ptr + _cache_pgsize_sblks * _sblksize * i);
        _page_cb_arr[i]._index = i;
        _page_cb_arr[i]._state = UNUSED;
        _page_cb_arr[i]._pbuff = _page_ptr_arr[i];
        _page_cb_arr[i]._pdtokl = new std::deque<data_tok*>;
        _page_cb_arr[i]._pdtokl->clear();
        _aio_cb_arr[i].data = (void*)&_page_cb_arr[i];
    }

    // 7. Allocate io_event array, max one event per cache page plus one for each file
    const u_int16_t max_aio_evts = _cache_num_pages + _jc->num_jfiles();
    _aio_event_arr = (aio_event*)std::malloc(max_aio_evts * sizeof(aio_event));
    MALLOC_CHK(_aio_event_arr, "_aio_event_arr", "pmgr", "initialize");

    // 8. Initialize AIO context
    if (int ret = aio::queue_init(max_aio_evts, &_ioctx))
    {
        std::ostringstream oss;
        oss << "io_queue_init() failed: " << FORMAT_SYSERR(-ret);
        throw jexception(jerrno::JERR__AIO, oss.str(), "pmgr", "initialize");
    }
}

void
pmgr::clean()
{
    // clean up allocated memory here

    if (_ioctx)
        aio::queue_release(_ioctx);

    std::free(_page_base_ptr);
    _page_base_ptr = 0;

    if (_page_cb_arr)
    {
        for (int i=0; i<_cache_num_pages; i++)
            delete _page_cb_arr[i]._pdtokl;
        std::free(_page_ptr_arr);
        _page_ptr_arr = 0;
    }

    std::free(_page_cb_arr);
    _page_cb_arr = 0;

    std::free(_aio_cb_arr);
    _aio_cb_arr = 0;

    std::free(_aio_event_arr);
    _aio_event_arr = 0;
}

const char*
pmgr::page_state_str(page_state ps)
{
    switch (ps)
    {
        case UNUSED:
            return "UNUSED";
        case IN_USE:
            return "IN_USE";
        case AIO_PENDING:
            return "AIO_PENDING";
        case AIO_COMPLETE:
            return "AIO_COMPLETE";
    }
    return "<page_state unknown>";
}

} // namespace journal
} // namespace mrg
