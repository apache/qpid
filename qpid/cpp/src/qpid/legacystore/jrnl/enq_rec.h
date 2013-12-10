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
 * \file enq_rec.h
 *
 * Qpid asynchronous store plugin library
 *
 * This file contains the code for the mrg::journal::enq_rec (journal enqueue
 * record) class. See class documentation for details.
 */

#ifndef QPID_LEGACYSTORE_JRNL_ENQ_REC_H
#define QPID_LEGACYSTORE_JRNL_ENQ_REC_H

namespace mrg
{
namespace journal
{
class enq_rec;
}
}

#include <cstddef>
#include "qpid/legacystore/jrnl/enq_hdr.h"
#include "qpid/legacystore/jrnl/jrec.h"

namespace mrg
{
namespace journal
{

    /**
    * \class enq_rec
    * \brief Class to handle a single journal enqueue record.
    */
    class enq_rec : public jrec
    {
    private:
        enq_hdr _enq_hdr;
        const void* _xidp;          ///< xid pointer for encoding (for writing to disk)
        const void* _data;          ///< Pointer to data to be written to disk
        void* _buff;                ///< Pointer to buffer to receive data read from disk
        rec_tail _enq_tail;

    public:
        /**
        * \brief Constructor used for read operations.
        */
        enq_rec();

        /**
        * \brief Constructor used for write operations, where mbuf contains data to be written.
        */
        enq_rec(const u_int64_t rid, const void* const dbuf, const std::size_t dlen,
                const void* const xidp, const std::size_t xidlen, const bool owi, const bool transient);

        /**
        * \brief Destructor
        */
        virtual ~enq_rec();

        // Prepare instance for use in reading data from journal, xid and data will be allocated
        void reset();
        // Prepare instance for use in writing data to journal
        void reset(const u_int64_t rid, const void* const dbuf, const std::size_t dlen,
                const void* const xidp, const std::size_t xidlen, const bool owi, const bool transient,
                const bool external);

        u_int32_t encode(void* wptr, u_int32_t rec_offs_dblks, u_int32_t max_size_dblks);
        u_int32_t decode(rec_hdr& h, void* rptr, u_int32_t rec_offs_dblks,
                u_int32_t max_size_dblks);
        // Decode used for recover
        bool rcv_decode(rec_hdr h, std::ifstream* ifsp, std::size_t& rec_offs);

        std::size_t get_xid(void** const xidpp);
        std::size_t get_data(void** const datapp);
        inline bool is_transient() const { return _enq_hdr.is_transient(); }
        inline bool is_external() const { return _enq_hdr.is_external(); }
        std::string& str(std::string& str) const;
        inline std::size_t data_size() const { return _enq_hdr._dsize; }
        inline std::size_t xid_size() const { return _enq_hdr._xidsize; }
        std::size_t rec_size() const;
        static std::size_t rec_size(const std::size_t xidsize, const std::size_t dsize, const bool external);
        inline u_int64_t rid() const { return _enq_hdr._rid; }
        void set_rid(const u_int64_t rid);

    private:
        void chk_hdr() const;
        void chk_hdr(u_int64_t rid) const;
        void chk_tail() const;
        virtual void clean();
    }; // class enq_rec

} // namespace journal
} // namespace mrg

#endif // ifndef QPID_LEGACYSTORE_JRNL_ENQ_REC_H
