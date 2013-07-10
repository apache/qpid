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
 * \file enq_hdr.h
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::enq_hdr (enueue header),
 * used to start an enqueue record in the journal.
 *
 * \author Kim van der Riet
 */

#ifndef QPID_LEGACYSTORE_JRNL_ENQ_HDR_H
#define QPID_LEGACYSTORE_JRNL_ENQ_HDR_H

#include <cstddef>
#include "qpid/legacystore/jrnl/rec_hdr.h"

namespace mrg
{
namespace journal
{

#pragma pack(1)

    /**
    * \brief Struct for enqueue record.
    *
    * Struct for enqueue record. In addition to the common data, this header includes both the
    * xid and data blob sizes.
    *
    * This header precedes all enqueue data in journal files.
    *
    * Record header info in binary format (32 bytes):
    * <pre>
    *   0                           7
    * +---+---+---+---+---+---+---+---+  -+
    * |     magic     | v | e | flags |   |
    * +---+---+---+---+---+---+---+---+   | struct hdr
    * |              rid              |   |
    * +---+---+---+---+---+---+---+---+  -+
    * |            xidsize            |
    * +---+---+---+---+---+---+---+---+
    * |             dsize             |
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
    struct enq_hdr : rec_hdr
    {
#if defined(JRNL_BIG_ENDIAN) && defined(JRNL_32_BIT)
        u_int32_t _filler0;     ///< Big-endian filler for 32-bit size_t
#endif
        std::size_t _xidsize;        ///< XID size
#if defined(JRNL_LITTLE_ENDIAN) && defined(JRNL_32_BIT)
        u_int32_t _filler0;     ///< Little-endian filler for 32-bit size_t
#endif
#if defined(JRNL_BIG_ENDIAN) && defined(JRNL_32_BIT)
        u_int32_t _filler1;     ///< Big-endian filler for 32-bit size_t
#endif
        std::size_t _dsize;          ///< Record data size
#if defined(JRNL_LITTLE_ENDIAN) && defined(JRNL_32_BIT)
        u_int32_t _filler1;     ///< Little-endian filler for 32-bit size_t
#endif
        static const u_int16_t ENQ_HDR_TRANSIENT_MASK = 0x10;
        static const u_int16_t ENQ_HDR_EXTERNAL_MASK = 0x20;

        /**
        * \brief Default constructor, which sets all values to 0.
        */
        inline enq_hdr(): rec_hdr(),
#if defined(JRNL_BIG_ENDIAN) && defined(JRNL_32_BIT)
            _filler0(0),
#endif
            _xidsize(0),
#if defined(JRNL_LITTLE_ENDIAN) && defined(JRNL_32_BIT)
            _filler0(0),
#endif
#if defined(JRNL_BIG_ENDIAN) && defined(JRNL_32_BIT)
            _filler1(0),
#endif
            _dsize(0)
#if defined(JRNL_LITTLE_ENDIAN) && defined(JRNL_32_BIT)
            , _filler1(0)
#endif
        {}

        /**
        * \brief Convenience constructor which initializes values during construction.
        */
        inline enq_hdr(const u_int32_t magic, const u_int8_t version, const u_int64_t rid,
                const std::size_t xidsize, const std::size_t dsize, const bool owi,
                const bool transient = false): rec_hdr(magic, version, rid, owi),
#if defined(JRNL_BIG_ENDIAN) && defined(JRNL_32_BIT)
            _filler0(0),
#endif
            _xidsize(xidsize),
#if defined(JRNL_LITTLE_ENDIAN) && defined(JRNL_32_BIT)
            _filler0(0),
#endif
#if defined(JRNL_BIG_ENDIAN) && defined(JRNL_32_BIT)
            _filler1(0),
#endif
            _dsize(dsize)
#if defined(JRNL_LITTLE_ENDIAN) && defined(JRNL_32_BIT)
            , _filler1(0)
#endif
        { set_transient(transient); }


        inline bool is_transient() const { return _uflag & ENQ_HDR_TRANSIENT_MASK; }

        inline void set_transient(const bool transient)
        {
            _uflag = transient ? _uflag | ENQ_HDR_TRANSIENT_MASK :
                    _uflag & (~ENQ_HDR_TRANSIENT_MASK);
        }

        inline bool is_external() const { return _uflag & ENQ_HDR_EXTERNAL_MASK; }

        inline void set_external(const bool external)
        {
            _uflag = external ? _uflag | ENQ_HDR_EXTERNAL_MASK :
                    _uflag & (~ENQ_HDR_EXTERNAL_MASK);
        }

        /**
        * \brief Returns the size of the header in bytes.
        */
        inline static std::size_t size() { return sizeof(enq_hdr); }
    };

#pragma pack()

} // namespace journal
} // namespace mrg

#endif // ifndef QPID_LEGACYSTORE_JRNL_ENQ_HDR_H
