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
 * \file file_hdr.h
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::file_hdr (file
 * record header), used to start a journal file. It contains some
 * file metadata and information to aid journal recovery.
 *
 * \author Kim van der Riet
 */

#ifndef QPID_LEGACYSTORE_JRNL_FILE_HDR_H
#define QPID_LEGACYSTORE_JRNL_FILE_HDR_H

#include <cerrno>
#include <ctime>
#include "qpid/legacystore/jrnl/rec_hdr.h"
#include "qpid/legacystore/jrnl/jerrno.h"
#include "qpid/legacystore/jrnl/jexception.h"
#include <sstream>

namespace mrg
{
namespace journal
{

#pragma pack(1)

    /**
    * \brief Struct for data common to the head of all journal files. In addition to
    * the common data, this includes the record ID and offset of the first record in
    * the file.
    *
    * This header precedes all data in journal files and occupies the first complete
    * block in the file. The record ID and offset are updated on each overwrite of the
    * file.
    *
    * File header info in binary format (48 bytes):
    * <pre>
    *   0                           7
    * +---+---+---+---+---+---+---+---+  -+
    * |     magic     | v | e | flags |   |
    * +---+---+---+---+---+---+---+---+   | struct hdr
    * |       first rid in file       |   |
    * +---+---+---+---+---+---+---+---+  -+
    * | pfid  | lfid  |  reserved (0) |
    * +---+---+---+---+---+---+---+---+
    * |              fro              |
    * +---+---+---+---+---+---+---+---+
    * |           timestamp (sec)     |
    * +---+---+---+---+---+---+---+---+
    * |           timestamp (ns)      |
    * +---+---+---+---+---+---+---+---+
    * v = file version (If the format or encoding of this file changes, then this
    *     number should be incremented)
    * e = endian flag, false (0x00) for little endian, true (0x01) for big endian
    * pfid = File ID (number used in naming file)
    * lfid = Logical ID (order used in circular buffer)
    * fro = First record offset, offset from start of file to first record header
    * </pre>
    *
    * Note that journal files should be transferable between 32- and 64-bit
    * hardware of the same endianness, but not between hardware of opposite
    * entianness without some sort of binary conversion utility. Thus buffering
    * will be needed for types that change size between 32- and 64-bit compiles.
    */
    struct file_hdr : rec_hdr
    {
        u_int16_t _pfid;        ///< Physical file ID (pfid)
        u_int16_t _lfid;        ///< Logical file ID (lfid)
        u_int32_t _res;         ///< Reserved (for alignment/flags)
#if defined(JRNL_BIG_ENDIAN) && defined(JRNL_32_BIT)
        u_int32_t _filler0;     ///< Big-endian filler for 32-bit size_t
#endif
        std::size_t _fro;       ///< First record offset
#if defined(JRNL_LITTLE_ENDIAN) && defined(JRNL_32_BIT)
        u_int32_t _filler0;     ///< Little-endian filler for 32-bit size_t
#endif
#if defined(JRNL_BIG_ENDIAN) && defined(JRNL_32_BIT)
        u_int32_t _filler1;     ///< Big-endian filler for 32-bit time_t
#endif
        std::time_t _ts_sec;    ///< Timestamp of journal initilailization
#if defined(JRNL_LITTLE_ENDIAN) && defined(JRNL_32_BIT)
        u_int32_t _filler1;     ///< Little-endian filler for 32-bit time_t
#endif
#if defined(JRNL_BIG_ENDIAN)
        u_int32_t _filler2;     ///< Big endian filler for u_int32_t
#endif
        u_int32_t _ts_nsec;     ///< Timestamp of journal initilailization
#if defined(JRNL_LITTLE_ENDIAN)
        u_int32_t _filler2;     ///< Little-endian filler for u_int32_t
#endif

        /**
        * \brief Default constructor, which sets all values to 0.
        */
        inline file_hdr(): rec_hdr(), _pfid(0), _lfid(0), _res(0),
#if defined(JRNL_BIG_ENDIAN) && defined(JRNL_32_BIT)
            _filler0(0),
#endif
            _fro(0),
#if defined(JRNL_LITTLE_ENDIAN) && defined(JRNL_32_BIT)
            _filler0(0),
#endif
#if defined(JRNL_BIG_ENDIAN) && defined(JRNL_32_BIT)
            _filler1(0),
#endif
            _ts_sec(0),
#if defined(JRNL_LITTLE_ENDIAN) && defined(JRNL_32_BIT)
            _filler1(0),
#endif
#if defined(JRNL_BIG_ENDIAN)
            _filler2(0),
#endif
            _ts_nsec(0)
#if defined(JRNL_LITTLE_ENDIAN)
            , _filler2(0)
#endif
        {}

        /**
        * \brief Convenience constructor which initializes values during construction.
        */
        inline file_hdr(const u_int32_t magic, const u_int8_t version, const u_int64_t rid,
		        const u_int16_t pfid, const u_int16_t lfid, const std::size_t fro,
                const bool owi, const bool settime = false):
                rec_hdr(magic, version, rid, owi), _pfid(pfid), _lfid(lfid), _res(0),
#if defined(JRNL_BIG_ENDIAN) && defined(JRNL_32_BIT)
            _filler0(0),
#endif
            _fro(fro),
#if defined(JRNL_LITTLE_ENDIAN) && defined(JRNL_32_BIT)
            _filler0(0),
#endif
#if defined(JRNL_BIG_ENDIAN) && defined(JRNL_32_BIT)
            _filler1(0),
#endif
            _ts_sec(0),
#if defined(JRNL_LITTLE_ENDIAN) && defined(JRNL_32_BIT)
            _filler1(0),
#endif
#if defined(JRNL_BIG_ENDIAN)
            _filler2(0),
#endif
            _ts_nsec(0)
#if defined(JRNL_LITTLE_ENDIAN)
            , _filler2(0)
#endif
        { if (settime) set_time(); }

        /**
        * \brief Gets the current time from the system clock and sets the timestamp in the struct.
        */
        inline void set_time()
        {
            // TODO: Standardize on method for getting time that does not requrie a context switch.
            timespec ts;
            if (::clock_gettime(CLOCK_REALTIME, &ts))
            {
                std::ostringstream oss;
                oss << FORMAT_SYSERR(errno);
                throw jexception(jerrno::JERR__RTCLOCK, oss.str(), "file_hdr", "set_time");
            }
            _ts_sec = ts.tv_sec;
            _ts_nsec = ts.tv_nsec;
        }

        /**
        * \brief Sets the timestamp in the struct to the provided value (in seconds and
        *        nanoseconds).
        */
        inline void set_time(timespec& ts)
        {
            _ts_sec = ts.tv_sec;
            _ts_nsec = ts.tv_nsec;
        }

        /**
        * \brief Returns the size of the header in bytes.
        */
        inline static std::size_t size() { return sizeof(file_hdr); }
    }; // struct file_hdr

#pragma pack()

} // namespace journal
} // namespace mrg

#endif // ifndef QPID_LEGACYSTORE_JRNL_FILE_HDR_H
