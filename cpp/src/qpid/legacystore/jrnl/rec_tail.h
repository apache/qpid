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
 * \file rec_tail.h
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::rec_tail (record tail), used to
 * finalize a persistent record. The presence of a valid tail at the expected
 * position in the journal file indicates that the record write was completed.
 *
 * \author Kim van der Riet
 */

#ifndef QPID_LEGACYSTORE_JRNL_REC_TAIL_H
#define QPID_LEGACYSTORE_JRNL_REC_TAIL_H

#include <cstddef>
#include "qpid/legacystore/jrnl/jcfg.h"

namespace mrg
{
namespace journal
{

#pragma pack(1)

    /**
    * \brief Struct for data common to the tail of all records. The magic number
    * used here is the binary inverse (1's complement) of the magic used in the
    * record header; this minimizes possible confusion with other headers that may
    * be present during recovery. The tail is used with all records that have either
    * XIDs or data - ie any size-variable content. Currently the only records that
    * do NOT use the tail are non-transactional dequeues and filler records.
    *
    * Record header info in binary format (12 bytes):
    * <pre>
    *   0                           7
    * +---+---+---+---+---+---+---+---+
    * |   ~(magic)    |      rid      |
    * +---+---+---+---+---+---+---+---+
    * |  rid (con't)  |
    * +---+---+---+---+
    * </pre>
    */
    struct rec_tail
    {
        u_int32_t _xmagic;      ///< Binary inverse (1's complement) of hdr magic number
        u_int64_t _rid;         ///< ID (rotating 64-bit counter)


        /**
        * \brief Default constructor, which sets all values to 0.
        */
        inline rec_tail(): _xmagic(0xffffffff), _rid(0) {}

        /**
        * \brief Convenience constructor which initializes values during construction from
        *     existing enq_hdr instance.
        */
        inline rec_tail(const rec_hdr& h): _xmagic(~h._magic), _rid(h._rid) {}

        /**
        * \brief Convenience constructor which initializes values during construction.
        */
        inline rec_tail(const u_int32_t xmagic, const u_int64_t rid): _xmagic(xmagic), _rid(rid) {}

        /**
        * \brief Returns the size of the header in bytes.
        */
        inline static std::size_t size() { return sizeof(rec_tail); }
    };

#pragma pack()

} // namespace journal
} // namespace mrg

#endif // ifndef QPID_LEGACYSTORE_JRNL_REC_TAIL_H
