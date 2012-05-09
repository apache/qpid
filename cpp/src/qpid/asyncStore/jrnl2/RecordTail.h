/*
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
 */

/**
 * \file RecordTail.h
 *
 * List of journal record structs:
 *   struct DequeueHeader
 *   struct EnqueueHeader
 *   struct EventHeader
 *   struct FileHeader
 *   struct RecordHeader
 *   struct RecordTail         <-- This file
 *   struct TransactionHeader
 *
 * Overview of journal record structs:
 *
 * <pre>
 *  +------------+           +--------------+
 *  | RecordTail |           | RecordHeader |
 *  +------------+           |  (abstract)  |
 *                           +--------------+
 *                                   ^
 *                                   |
 *        +----------------+---------+-------+-------------------+
 *        |                |                 |                   |
 *  +------------+  +-------------+  +---------------+  +-------------------+
 *  | FileHeader |  | EventHeader |  | DequeueHeader |  | TransactionHeader |
 *  +------------+  +-------------+  +---------------+  +-------------------+
 *                         ^
 *                         |
 *                 +---------------+
 *                 | EnqueueHeader |
 *                 +---------------+
 *  </pre>
 */

#ifndef qpid_asyncStore_jrnl2_RecordTail_h_
#define qpid_asyncStore_jrnl2_RecordTail_h_

#include <stdint.h> // uint32_t, uint64_t

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

class RecordHeader;

#pragma pack(1)

/**
 * \brief Struct for the tail of all records which contain data and which must follow
 * the data portion of that record.
 *
 * The magic number used here is the binary inverse (1's complement) of the magic
 * used in the record header; this minimizes possible confusion with other headers
 * that may be present during recovery. The tail is used with all records that have
 * either XIDs or data - i.e. any size-variable content. Currently the only records
 * that do NOT use the tail are non-transactional dequeues and filler records.
 *
 * Record layout in binary format (16 bytes):
 * <pre>
 *        0x0                                       0x7
 *      +-----+-----+-----+-----+-----+-----+-----+-----+
 * 0x00 |       m_xMagic        |       m_checkSum      |
 *      +-----+-----+-----+-----+-----+-----+-----+-----+
 * 0x08 |                  m_recordId                   |
 *      +-----+-----+-----+-----+-----+-----+-----+-----+
 * </pre>
 */
class RecordTail
{
public:
    uint32_t m_xMagic;              ///< Binary inverse (1's complement) of hdr magic number
    uint32_t m_checkSum;            ///< Checksum for header and body of record
    uint64_t m_recordId;            ///< Record identifier matching that of the header for this record

    /**
     * \brief Default constructor, which sets most values to 0.
     */
    RecordTail();

    /**
     * \brief Convenience constructor which initializes values during construction.
     *
     * \param xMagic The inverse of the record header magic (ie ~rh._magic).
     * \param checkSum The checksum for this record header and body.
     * \param recordId The record identifier matching the record header.
     */
    RecordTail(const uint32_t xMagic,
               const uint32_t checkSum,
               const uint64_t recordId);

    /**
     * \brief Convenience constructor which initializes values during construction from existing RecordHeader
     * instance.
     *
     * \param rh Header instance for which the RecordTail is to be created.
     */
    RecordTail(const RecordHeader& rh);

    /**
     * \brief Copy constructor.
     *
     * \param rt Instance to be copied.
     */
    RecordTail(const RecordTail& rt);

    /**
     * \brief Convenience copy method.
     *
     * \param rt Instance to be copied.
     */
    void copy(const RecordTail& rt);

    /**
     * \brief Resets all fields to default values (mostly 0).
     */
    void reset();

    /**
    * \brief Returns the size of the header in bytes.
    */
    static uint64_t getSize();

};

#pragma pack()

}}} // namespace qpid::asyncStore::jrnl2

#endif // qpid_asyncStore_jrnl2_RecordTail_h_
