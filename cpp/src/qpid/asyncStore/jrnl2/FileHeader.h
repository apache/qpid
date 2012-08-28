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
 * \file FileHeader.h
 *
 * List of journal record structs:
 *   struct DequeueHeader
 *   struct EnqueueHeader
 *   struct EventHeader
 *   struct FileHeader         <-- This file
 *   struct RecordHeader
 *   struct RecordTail
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

#ifndef qpid_asyncStore_jrnl2_FileHeader_h_
#define qpid_asyncStore_jrnl2_FileHeader_h_

#include "qpid/asyncStore/jrnl2/RecordHeader.h"

struct timespec;

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

#pragma pack(1)

/**
 * \brief Struct for the data written to the head of all journal files.
 *
 * In addition to the fields inherited from RecordHeader, this struct includes the
 * unique record ID for this record and an offset of the first record in the file.
 * There is also a field for a nanosecond-resolution time stamp which records the
 * time that this file was first written in each cycle.
 *
 * This header precedes all data in journal files and occupies the first complete
 * block in the file. The fields in this record are updated as necessary each time
 * the file is written or overwritten.
 *
 * File layout in binary format (48 bytes):
 * <pre>
 *        0x0                                       0x7
 *      +-----+-----+-----+-----+-----+-----+-----+-----+  -+
 * 0x00 |        m_magic        |  v  |  e  | m_flags   |   |
 *      +-----+-----+-----+-----+-----+-----+-----+-----+   | struct RecordHeader
 * 0x08 |  m_recordId (used to show first rid in file)  |   |
 *      +-----+-----+-----+-----+-----+-----+-----+-----+  -+
 * 0x10 |   m_physicalFileId    |   m_logicalFileId     |
 *      +-----+-----+-----+-----+-----+-----+-----+-----+
 * 0x18 |             m_firstRecordOffset               |
 *      +-----+-----+-----+-----+-----+-----+-----+-----+
 * 0x20 |             m_timestampSeconds                |
 *      +-----+-----+-----+-----+-----+-----+-----+-----+
 * 0x28 |m_timestampNanoSeconds |      m_reserved       |
 *      +-----+-----+-----+-----+-----+-----+-----+-----+
 * </pre>
 * <table>
 * <tr>
 *     <td>v</td>
 *     <td>file version [ <code>_version</code> ] (If the format or encoding of
 *     this file changes, then this number should be incremented)</td>
 * </tr>
 * <tr>
 *     <td>e</td>
 *     <td>endian flag [ <code>_bigEndianFlag</code> ], <b>false</b> (0x00) for
 *     little endian, <b>true</b> (0x01) for big endian</td>
 * </tr>
 * </table>
 * General journal file structure:
 * <pre>
 * <----------------------------- Journal File ----------------------------------->
 * +--------+ +-----------------+ +---------+ +---------+     +-------------------+
 * | file   | | continuation of | | first   | | second  | ... | last record       |
 * | header | | record from     | | record  | | record  |     | (or part thereof) |
 * |        | | prev. file (opt)| | in file | | in file |     | in file           |
 * +--------+ +-----------------+ +---------+ +---------+     +-------------------+
 *                                ^
 *                                |
 * m_firstRecordOffset -----------+
 * </pre>
 */
class FileHeader : public RecordHeader
{
public:
    uint32_t m_physicalFileId;      ///< Physical file ID (pfid)
    uint32_t m_logicalFileId;       ///< Logical file ID (lfid)
    uint64_t m_firstRecordOffset;///< First record offset (fro)
    uint64_t m_timestampSeconds; ///< Timestamp of journal initialization, seconds component
    uint32_t m_timestampNanoSeconds;///< Timestamp of journal initialization, nanoseconds component
    uint32_t m_reserved;            ///< Little-endian filler for uint32_t

    /**
     * \brief Default constructor, which sets all values to 0.
     */
    FileHeader();

    /**
     * \brief Convenience constructor which initializes values during construction.
     * \param magic Magic for this record
     * \param version Version of this record
     * \param recordId RecordId for this record
     * \param overwriteIndicator Overwrite indicator for this record
     * \param physicalFileId Physical file ID (file number on disk)
     * \param logicalFileId  Logical file ID (file number as seen by circular file buffer)
     * \param firstRecordOffset First record offset in bytes from beginning of file
     * \param setTimestampFlag If true, causes the timestamp to be initialized with the current system time
     */
    FileHeader(const uint32_t magic,
               const uint8_t version,
               const uint64_t recordId,
               const bool overwriteIndicator,
               const uint16_t physicalFileId,
               const uint16_t logicalFileId,
               const uint64_t firstRecordOffset,
               const bool setTimestampFlag = false);

    /**
     * \brief Copy constructor.
     * \param fh FileHeader instance to be copied
     */
    FileHeader(const FileHeader& fh);

    /**
     * \brief Destructor.
     */
    virtual ~FileHeader();

    /**
     * \brief Convenience copy method.
     * \param fh FileHeader instance to be copied
     */
    void copy(const FileHeader& fh);

    /**
     * \brief Resets all fields to default values (mostly 0).
     */
    void reset();

    /**
     * \brief Return the header size of this record in bytes.
     * \returns Size of record header in bytes.
     */
    static uint64_t getHeaderSize();

    /**
     * \brief Return the body (data) size of this record in bytes, which in the case of a FileHeader is always 0.
     * \returns Size of record body in bytes. By definition, a FileHeader has no body.
     */
    uint64_t getBodySize() const;

    /**
     * \brief Return total size of this record in bytes, being in the case of the
     * FileHeader the size of the header itself only.
     * \returns Total size of record in bytes.
     */
    uint64_t getRecordSize() const;

    /**
     * \brief Gets the current time from the system clock and sets the timestamp in the struct.
     */
    void setTimestamp();

    /**
     * \brief Sets the timestamp in the struct to the provided value (in seconds and nanoseconds).
     * \param ts Timestamp from which the file header time stamp is to be copied
     */
    void setTimestamp(const timespec& ts);

};

#pragma pack()

}}} // namespace qpid::asyncStore::jrnl2

#endif // qpid_asyncStore_jrnl2_FileHeader_h_
