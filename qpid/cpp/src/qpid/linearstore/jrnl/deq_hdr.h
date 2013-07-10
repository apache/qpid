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
 * \file deq_hdr.h
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::deq_hdr (dequeue record),
 * used to dequeue a previously enqueued record.
 *
 * \author Kim van der Riet
 */

#ifndef QPID_LEGACYSTORE_JRNL_DEQ_HDR_H
#define QPID_LEGACYSTORE_JRNL_DEQ_HDR_H

#include <cstddef>
#include "qpid/legacystore/jrnl/rec_hdr.h"

namespace mrg
{
namespace journal
{

#pragma pack(1)

    /**
    * \brief Struct for dequeue record.
    *
    * Struct for dequeue record. If this record has a non-zero xidsize field (i.e., there is a
    * valid XID), then this header is followed by the XID of xidsize bytes and a rec_tail. If,
    * on the other hand, this record has a zero xidsize (i.e., there is no XID), then the rec_tail
    * is absent.
    *
    * Note that this record had its own rid distinct from the rid of the record it is dequeueing.
    * The rid field below is the rid of the dequeue record itself; the deq-rid field is the rid of a
    * previous enqueue record being dequeued by this record.
    *
    * Record header info in binary format (32 bytes):
    * <pre>
    *   0                           7
    * +---+---+---+---+---+---+---+---+  -+
    * |     magic     | v | e | flags |   |
    * +---+---+---+---+---+---+---+---+   | struct hdr
    * |              rid              |   |
    * +---+---+---+---+---+---+---+---+  -+
    * |            deq-rid            |
    * +---+---+---+---+---+---+---+---+
    * |            xidsize            |
    * +---+---+---+---+---+---+---+---+
    * v = file version (If the format or encoding of this file changes, then this
    *     number should be incremented)
    * e = endian flag, false (0x00) for little endian, true (0x01) for big endian
    * </pre>
    *
    * Note that journal files should be transferable between 32- and 64-bit
    * hardware of the same endianness, but not between hardware of opposite
    * entianness without some sort of binary conversion utility. Thus buffering
    * will be needed for types that change size between 32- and 64-bit compiles.
    */
    struct deq_hdr : rec_hdr
    {
        u_int64_t _deq_rid;     ///< Record ID of dequeued record
#if defined(JRNL_BIG_ENDIAN) && defined(JRNL_32_BIT)
        u_int32_t _filler0;     ///< Big-endian filler for 32-bit size_t
#endif
        std::size_t _xidsize;   ///< XID size
#if defined(JRNL_LITTLE_ENDIAN) && defined(JRNL_32_BIT)
        u_int32_t _filler0;     ///< Little-endian filler for 32-bit size_t
#endif
        static const u_int16_t DEQ_HDR_TXNCMPLCOMMIT_MASK = 0x10;

        /**
        * \brief Default constructor, which sets all values to 0.
        */
        inline deq_hdr(): rec_hdr(), _deq_rid(0),
#if defined(JRNL_BIG_ENDIAN) && defined(JRNL_32_BIT)
            _filler0(0),
#endif
            _xidsize(0)
#if defined(JRNL_LITTLE_ENDIAN) && defined(JRNL_32_BIT)
            , _filler0(0)
#endif
        {}

        /**
        * \brief Convenience constructor which initializes values during construction.
        */
        inline deq_hdr(const u_int32_t magic, const u_int8_t version, const u_int64_t rid,
                const u_int64_t deq_rid, const std::size_t xidsize, const bool owi,
                const bool txn_coml_commit = false):
                rec_hdr(magic, version, rid, owi), _deq_rid(deq_rid),
#if defined(JRNL_BIG_ENDIAN) && defined(JRNL_32_BIT)
            _filler0(0),
#endif
            _xidsize(xidsize)
#if defined(JRNL_LITTLE_ENDIAN) && defined(JRNL_32_BIT)
            , _filler0(0)
#endif
        { set_txn_coml_commit(txn_coml_commit); }


        inline bool is_txn_coml_commit() const { return _uflag & DEQ_HDR_TXNCMPLCOMMIT_MASK; }

        inline void set_txn_coml_commit(const bool commit)
        {
            _uflag = commit ? _uflag | DEQ_HDR_TXNCMPLCOMMIT_MASK :
                    _uflag & (~DEQ_HDR_TXNCMPLCOMMIT_MASK);
        }

        /**
        * \brief Returns the size of the header in bytes.
        */
        inline static std::size_t size() { return sizeof(deq_hdr); }
    };

#pragma pack()

} // namespace journal
} // namespace mrg

#endif // ifndef QPID_LEGACYSTORE_JRNL_DEQ_HDR_H
