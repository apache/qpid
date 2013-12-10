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
 * \file deq_rec.h
 *
 * Qpid asynchronous store plugin library
 *
 * This file contains the code for the mrg::journal::deq_rec (journal dequeue
 * record) class. See class documentation for details.
 *
 * \author Kim van der Riet
 */

#ifndef QPID_LEGACYSTORE_JRNL_DEQ_REQ_H
#define QPID_LEGACYSTORE_JRNL_DEQ_REQ_H

namespace mrg
{
namespace journal
{
class deq_rec;
}
}

#include <cstddef>
#include "qpid/legacystore/jrnl/deq_hdr.h"
#include "qpid/legacystore/jrnl/jrec.h"

namespace mrg
{
namespace journal
{

    /**
    * \class deq_rec
    * \brief Class to handle a single journal dequeue record.
    */
    class deq_rec : public jrec
    {
    private:
        deq_hdr _deq_hdr;           ///< Dequeue header
        const void* _xidp;          ///< xid pointer for encoding (writing to disk)
        void* _buff;                ///< Pointer to buffer to receive data read from disk
        rec_tail _deq_tail;         ///< Record tail, only encoded if XID is present

    public:
        // constructor used for read operations and xid will have memory allocated
        deq_rec();
        // constructor used for write operations, where xid already exists
        deq_rec(const u_int64_t rid, const u_int64_t drid, const void* const xidp,
                const std::size_t xidlen, const bool owi, const bool txn_coml_commit);
        virtual ~deq_rec();

        // Prepare instance for use in reading data from journal
        void reset();
        // Prepare instance for use in writing data to journal
        void reset(const  u_int64_t rid, const  u_int64_t drid, const void* const xidp,
                const std::size_t xidlen, const bool owi, const bool txn_coml_commit);
        u_int32_t encode(void* wptr, u_int32_t rec_offs_dblks, u_int32_t max_size_dblks);
        u_int32_t decode(rec_hdr& h, void* rptr, u_int32_t rec_offs_dblks,
                u_int32_t max_size_dblks);
        // Decode used for recover
        bool rcv_decode(rec_hdr h, std::ifstream* ifsp, std::size_t& rec_offs);

        inline bool is_txn_coml_commit() const { return _deq_hdr.is_txn_coml_commit(); }
        inline u_int64_t rid() const { return _deq_hdr._rid; }
        inline u_int64_t deq_rid() const { return _deq_hdr._deq_rid; }
        std::size_t get_xid(void** const xidpp);
        std::string& str(std::string& str) const;
        inline std::size_t data_size() const { return 0; } // This record never carries data
        std::size_t xid_size() const;
        std::size_t rec_size() const;

    private:
        virtual void chk_hdr() const;
        virtual void chk_hdr(u_int64_t rid) const;
        virtual void chk_tail() const;
        virtual void clean();
    }; // class deq_rec

} // namespace journal
} // namespace mrg

#endif // ifndef QPID_LEGACYSTORE_JRNL_DEQ_REQ_H
