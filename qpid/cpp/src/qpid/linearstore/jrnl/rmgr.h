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
 * \file rmgr.h
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::rmgr (read manager). See
 * class documentation for details.
 *
 * \author Kim van der Riet
 */

#ifndef QPID_LEGACYSTORE_JRNL_RMGR_H
#define QPID_LEGACYSTORE_JRNL_RMGR_H

namespace mrg
{
namespace journal
{
class rmgr;
}
}

#include <cstring>
#include "qpid/legacystore/jrnl/enums.h"
#include "qpid/legacystore/jrnl/file_hdr.h"
#include "qpid/legacystore/jrnl/pmgr.h"
#include "qpid/legacystore/jrnl/rec_hdr.h"
#include "qpid/legacystore/jrnl/rrfc.h"

namespace mrg
{
namespace journal
{

    /**
    * \brief Class for managing a read page cache of arbitrary size and number of pages.
    *
    * The read page cache works on the principle of filling as many pages as possilbe in advance of
    * reading the data. This ensures that delays caused by AIO operations are minimized.
    */
    class rmgr : public pmgr
    {
    private:
        rrfc& _rrfc;                ///< Ref to read rotating file controller
        rec_hdr _hdr;               ///< Header used to determind record type

        void* _fhdr_buffer;         ///< Buffer used for fhdr reads
        aio_cb* _fhdr_aio_cb_ptr;   ///< iocb pointer for fhdr reads
        file_hdr _fhdr;             ///< file header instance for reading file headers
        bool _fhdr_rd_outstanding;  ///< true if a fhdr read is outstanding

    public:
        rmgr(jcntl* jc, enq_map& emap, txn_map& tmap, rrfc& rrfc);
        virtual ~rmgr();

        using pmgr::initialize;
        void initialize(aio_callback* const cbp);
        iores read(void** const datapp, std::size_t& dsize, void** const xidpp,
                std::size_t& xidsize, bool& transient, bool& external, data_tok* dtokp,
                bool ignore_pending_txns);
        int32_t get_events(page_state state, timespec* const timeout, bool flush = false);
        void recover_complete();
        inline iores synchronize() { if (_rrfc.is_valid()) return RHM_IORES_SUCCESS; return aio_cycle(); }
        void invalidate();
        bool wait_for_validity(timespec* const timeout, const bool throw_on_timeout = false);

        /* TODO (if required)
        const iores get(const u_int64_t& rid, const std::size_t& dsize, const std::size_t& dsize_avail,
                const void** const data, bool auto_discard);
        const iores discard(data_tok* dtok);
        */

    private:
        void clean();
        void flush(timespec* timeout);
        iores pre_read_check(data_tok* dtokp);
        iores read_enq(rec_hdr& h, void* rptr, data_tok* dtokp);
        void consume_xid_rec(rec_hdr& h, void* rptr, data_tok* dtokp);
        void consume_filler();
        iores skip(data_tok* dtokp);
        iores aio_cycle();
        iores init_aio_reads(const int16_t first_uninit, const u_int16_t num_uninit);
        void rotate_page();
        u_int32_t dblks_rem() const;
        void set_params_null(void** const datapp, std::size_t& dsize, void** const xidpp,
                std::size_t& xidsize);
        void init_file_header_read();
    };

} // namespace journal
} // namespace mrg

#endif // ifndef QPID_LEGACYSTORE_JRNL_RMGR_H
