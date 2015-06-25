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
 * \file txn_hdr.h
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::txn_hdr (transaction
 * record header), used to start a transaction (commit or abort) record.
 *
 * \author Kim van der Riet
 */

#ifndef QPID_LEGACYSTORE_JRNL_TXN_HDR_H
#define QPID_LEGACYSTORE_JRNL_TXN_HDR_H

#include <cstddef>
#include "qpid/legacystore/jrnl/rec_hdr.h"

namespace mrg
{
namespace journal
{

#pragma pack(1)

    /**
    * \brief Struct for transaction commit and abort records.
    *
    * Struct for DTX commit and abort records. Only the magic distinguishes between them. Since
    * this record must be used in the context of a valid XID, the xidsize field must not be zero.
    * Immediately following this record is the XID itself which is xidsize bytes long, followed by
    * a rec_tail.
    *
    * Note that this record had its own rid distinct from the rids of the record(s) making up the
    * transaction it is committing or aborting.
    *
    * Record header info in binary format (24 bytes):
    * <pre>
    *   0                           7
    * +---+---+---+---+---+---+---+---+  -+
    * |     magic     | v | e | flags |   |
    * +---+---+---+---+---+---+---+---+   | struct hdr
    * |              rid              |   |
    * +---+---+---+---+---+---+---+---+  -+
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
    struct txn_hdr : rec_hdr
    {
#if defined(JRNL_BIG_ENDIAN) && defined(JRNL_32_BIT)
        u_int32_t _filler0;     ///< Big-endian filler for 32-bit size_t
#endif
        std::size_t _xidsize;        ///< XID size
#if defined(JRNL_LITTLE_ENDIAN) && defined(JRNL_32_BIT)
        u_int32_t _filler0;     ///< Little-endian filler for 32-bit size_t
#endif

        /**
        * \brief Default constructor, which sets all values to 0.
        */
        txn_hdr(): rec_hdr(),
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
        txn_hdr(const u_int32_t magic, const u_int8_t version, const u_int64_t rid,
                const std::size_t xidsize, const bool owi): rec_hdr(magic, version, rid, owi),
#if defined(JRNL_BIG_ENDIAN) && defined(JRNL_32_BIT)
            _filler0(0),
#endif
            _xidsize(xidsize)
#if defined(JRNL_LITTLE_ENDIAN) && defined(JRNL_32_BIT)
            , _filler0(0)
#endif
        {}

        /**
        * \brief Returns the size of the header in bytes.
        */
        inline static std::size_t size() { return sizeof(txn_hdr); }
    };

#pragma pack()

} // namespace journal
} // namespace mrg

#endif // ifndef QPID_LEGACYSTORE_JRNL_TXN_HDR_H
