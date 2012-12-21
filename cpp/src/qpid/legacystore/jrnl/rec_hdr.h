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
 * \file rec_hdr.h
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::rec_hdr (record header),
 * which is a common initial header used for all journal record structures
 * except the record tail (rec_tail).
 *
 * \author Kim van der Riet
 */

#ifndef QPID_LEGACYSTORE_JRNL_REC_HDR_H
#define QPID_LEGACYSTORE_JRNL_REC_HDR_H

#include <cstddef>
#include "qpid/legacystore/jrnl/jcfg.h"
#include <sys/types.h>

namespace mrg
{
namespace journal
{

#pragma pack(1)

    /**
    * \brief Struct for data common to the head of all journal files and records.
    * This includes identification for the file type, the encoding version, endian
    * indicator and a record ID.
    *
    * File header info in binary format (16 bytes):
    * <pre>
    *   0                           7
    * +---+---+---+---+---+---+---+---+
    * |     magic     | v | e | flags |
    * +---+---+---+---+---+---+---+---+
    * |              rid              |
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
    struct rec_hdr
    {
        u_int32_t _magic;       ///< File type identifier (magic number)
        u_int8_t _version;      ///< File encoding version
        u_int8_t _eflag;        ///< Flag for determining endianness
        u_int16_t _uflag;       ///< User-defined flags
        u_int64_t _rid;         ///< Record ID (rotating 64-bit counter)

        // Global flags
        static const u_int16_t HDR_OVERWRITE_INDICATOR_MASK = 0x1;

        // Convenience constructors and methods
        /**
        * \brief Default constructor, which sets all values to 0.
        */
        inline rec_hdr(): _magic(0), _version(0), _eflag(0), _uflag(0), _rid(0) {}

        /**
        * \brief Convenience constructor which initializes values during construction.
        */
        inline rec_hdr(const u_int32_t magic, const u_int8_t version, const u_int64_t rid,
                const bool owi): _magic(magic), _version(version),
#if defined(JRNL_BIG_ENDIAN)
            _eflag(RHM_BENDIAN_FLAG),
#else
            _eflag(RHM_LENDIAN_FLAG),
#endif
            _uflag(owi ? HDR_OVERWRITE_INDICATOR_MASK : 0), _rid(rid) {}

        /**
        * \brief Convenience copy method.
        */
        inline void hdr_copy(const rec_hdr& h)
        {
            _magic = h._magic;
            _version = h._version;
            _eflag = h._eflag;
            _uflag = h._uflag;
            _rid =h._rid;
        }

        /**
        * \brief Resets all fields to 0
        */
        inline void reset()
        {
            _magic = 0;
            _version = 0;
            _eflag = 0;
            _uflag = 0;
            _rid = 0;
        }

        inline bool get_owi() const { return _uflag & HDR_OVERWRITE_INDICATOR_MASK; }

        inline void set_owi(const bool owi)
        {
            _uflag = owi ? _uflag | HDR_OVERWRITE_INDICATOR_MASK :
                    _uflag & (~HDR_OVERWRITE_INDICATOR_MASK);
        }

        /**
        * \brief Returns the size of the header in bytes.
        */
        inline static std::size_t size() { return sizeof(rec_hdr); }
    }; // struct rec_hdr

#pragma pack()

} // namespace journal
} // namespace mrg

#endif // ifndef QPID_LEGACYSTORE_JRNL_REC_HDR_H
