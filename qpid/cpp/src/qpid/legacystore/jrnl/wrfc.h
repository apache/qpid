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
 * \file wrfc.h
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::wrfc (write rotating
 * file controller). See class documentation for details.
 *
 * \author Kim van der Riet
 */

#ifndef QPID_LEGACYSTORE_JRNL_WRFC_H
#define QPID_LEGACYSTORE_JRNL_WRFC_H

namespace mrg
{
namespace journal
{
class wrfc;
}
}

#include <cstddef>
#include "qpid/legacystore/jrnl/enums.h"
#include "qpid/legacystore/jrnl/rrfc.h"

namespace mrg
{
namespace journal
{

    /**
    * \class wrfc
    * \brief Class to handle write management of a journal rotating file controller.
    */
    class wrfc : public rfc
    {
    private:
        u_int32_t _fsize_sblks;         ///< Size of journal files in sblks
        u_int32_t _fsize_dblks;         ///< Size of journal files in dblks
        u_int32_t _enq_cap_offs_dblks;  ///< Enqueue capacity offset
        u_int64_t _rid;                 ///< Master counter for record ID (rid)
        bool _reset_ok;                 ///< Flag set when reset succeeds
        bool _owi;                      ///< Overwrite indicator
        bool _frot;                     ///< Flag is true for first rotation, false otherwise

    public:
        wrfc(const lpmgr* lpmp);
        virtual ~wrfc();

        /**
        * \brief Initialize the controller.
        * \param fsize_sblks Size of each journal file in sblks.
        * \param rdp Struct carrying restore information. Optional for non-restore use, defaults to 0 (NULL).
        */
        using rfc::initialize;
        void initialize(const u_int32_t fsize_sblks, rcvdat* rdp = 0);

        /**
        * \brief Rotate active file controller to next file in rotating file group.
        * \exception jerrno::JERR__NINIT if called before calling initialize().
        */
        iores rotate();

        /**
        * \brief Returns the index of the earliest complete file within the rotating
        *     file group. Unwritten files are excluded. The currently active file is
        *     excluded unless it is the only written file.
        */
        u_int16_t earliest_index() const;

        /**
        * \brief Determines if a proposed write would cause the enqueue threshold to be exceeded.
        *
        * The following routine finds whether the next write will take the write pointer to beyond the
        * enqueue limit threshold. The following illustrates how this is achieved.
        * <pre>
        * Current file index: 4                         +---+----------+
        * X's mark still-enqueued records               |msg| 1-thresh |
        * msg = current msg size + unwritten cache      +---+----------+
        * thresh = JRNL_ENQ_THRESHOLD as a fraction     ^              V
        *            +-------+-------+-------+-------+--+----+-------+-+-----+-------+
        * file num ->|   0   |   1   |   2   |   3   |   4   |   5   |   6   |   7   |
        * enq recs ->| X  XX |XX XXX |XX XXXX|XXXXXXX|XX     |       |       |     X |
        *            +-------+-------+-------+-------+--+----+-------+-+-----+-------+
        *                                               ^        ^       ^
        *                                  subm_dblks --+        |       |
        *                                                      These files must be free of enqueues
        *                                                      If not, return true.
        * </pre>
        * \param enq_dsize_dblks Proposed size of write in dblocks
        */
        bool enq_threshold(const u_int32_t enq_dsize_dblks) const;

        inline u_int64_t rid() const { return _rid; }
        inline u_int64_t get_incr_rid() { return _rid++; }
        bool wr_reset();
        inline bool is_wr_reset() const { return _reset_ok; }
        inline bool owi() const { return _owi; }
        inline bool frot() const { return _frot; }

        // Convenience access methods to current file controller

        inline int fh() const { return _curr_fc->wr_fh(); }

        inline u_int32_t subm_cnt_dblks() const { return _curr_fc->wr_subm_cnt_dblks(); }
        inline std::size_t subm_offs() const { return _curr_fc->wr_subm_offs(); }
        inline u_int32_t add_subm_cnt_dblks(u_int32_t a) { return _curr_fc->add_wr_subm_cnt_dblks(a); }

        inline u_int32_t cmpl_cnt_dblks() const { return _curr_fc->wr_cmpl_cnt_dblks(); }
        inline std::size_t cmpl_offs() const { return _curr_fc->wr_cmpl_offs(); }
        inline u_int32_t add_cmpl_cnt_dblks(u_int32_t a) { return _curr_fc->add_wr_cmpl_cnt_dblks(a); }

        inline u_int16_t aio_cnt() const { return _curr_fc->aio_cnt(); }
        inline u_int16_t incr_aio_cnt() { return _curr_fc->incr_aio_cnt(); }
        inline u_int16_t decr_aio_cnt() { return _curr_fc->decr_aio_cnt(); }

        inline bool is_void() const { return _curr_fc->wr_void(); }
        inline bool is_empty() const { return _curr_fc->wr_empty(); }
        inline u_int32_t remaining_dblks() const { return _curr_fc->wr_remaining_dblks(); }
        inline bool is_full() const { return _curr_fc->is_wr_full(); };
        inline bool is_compl() const { return _curr_fc->is_wr_compl(); };
        inline u_int32_t aio_outstanding_dblks() const { return _curr_fc->wr_aio_outstanding_dblks(); }
        inline bool file_rotate() const { return _curr_fc->wr_file_rotate(); }

        // Debug aid
        std::string status_str() const;
    };

} // namespace journal
} // namespace mrg

#endif // ifndef QPID_LEGACYSTORE_JRNL_WRFC_H
