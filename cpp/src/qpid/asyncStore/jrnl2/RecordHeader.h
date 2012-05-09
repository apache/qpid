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
 * \file RecordHeader.h
 *
 * List of journal record structs:
 *   struct DequeueHeader
 *   struct EnqueueHeader
 *   struct EventHeader
 *   struct FileHeader
 *   struct RecordHeader       <-- This file
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

#ifndef qpid_asyncStore_jrnl2_RecordHeader_h_
#define qpid_asyncStore_jrnl2_RecordHeader_h_

#include <stdint.h> // uint8_t, uint16_t, uint32_t, uint64_t

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

#pragma pack(1)

/**
 * \brief Struct for data common to the head of all journal files and records.
 *
 * This block of data includes identification for the file type, the encoding
 * version, an endian indicator and a record ID.
 *
 * File layout in binary format (16 bytes):
 * <pre>
 *        0x0                                       0x7
 *      +-----+-----+-----+-----+-----+-----+-----+-----+
 * 0x00 |        m_magic        |  v  |  e  |  m_flags  |
 *      +-----+-----+-----+-----+-----+-----+-----+-----+
 * 0x08 |                  m_recordId                   |
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
 */
class RecordHeader
{
public:
    uint32_t m_magic;               ///< File type identifier (magic number)
    uint8_t m_version;              ///< File encoding version
    uint8_t m_bigEndianFlag;        ///< Flag for determining endianness
    uint16_t m_flags;               ///< User and system flags
    uint64_t m_recordId;            ///< Record ID (rotating 64-bit counter)

    /**
     * \brief Mask for the record header flags field m_flags which is used to indicate that this record is part
     * of a cycle opposite to the previous cycle.
     *
     * In order to identify which files and records were written last, the journal uses an overwrite indicator
     * (OWI) flag in the headers of all records. This flag changes between \b true and \b false on consecutive runs
     * through a circular buffer composed of several files. Looking for the point in the journal where the value of
     * the OWI changes from that in the file header of the first journal file marks the last write point in the
     * journal.
     *
     * This mask is used to isolate the OWI in the m_flags field of the record and file headers.
     */
    static const uint16_t HDR_OVERWRITE_INDICATOR_MASK = 0x1;

    /**
     * \brief Default constructor, which sets all values to 0.
     */
    RecordHeader();

    /**
     * \brief Convenience constructor which initializes values during construction.
     *
     * \param magic Magic for this record
     * \param version Version of this record
     * \param recordId Record identifier for this record
     * \param overwriteIndicator Overwrite indicator for this record
     */
    RecordHeader(const uint32_t magic,
                 const uint8_t version,
                 const uint64_t recordId,
                 const bool overwriteIndicator);

    /**
     * \brief Copy constructor.
     */
    RecordHeader(const RecordHeader& rh);

    /**
     * \brief Virtual destructor
     */
    virtual ~RecordHeader();

    /**
     * \brief Convenience copy method.
     */
    void copy(const RecordHeader& rh);

    /**
     * \brief Resets all fields to default values (mostly 0).
     */
    virtual void reset();

    /**
     * \brief Return the value of the Overwrite Indicator for this record.
     *
     * \returns true if the Overwrite Indicator flag is set, false otherwise.
     */
    bool getOverwriteIndicator() const;

    /**
     * \brief Set the value of the Overwrite Indicator for this record
     */
    void setOverwriteIndicator(const bool owi);

    /**
     * \brief Return the header size of this record in bytes. Must be implemented by
     * subclasses.
     *
     * \returns Size of record header in bytes.
     */
    static uint64_t getHeaderSize();

    /**
     * \brief Return the body (data) size of this record in bytes. Must be implemented
     * by subclasses.
     *
     * \returns Size of record body in bytes.
     */
    virtual uint64_t getBodySize() const = 0;

    /**
     * \brief Return total size of this record in bytes, being the sum of the header,
     * xid (if present), data (if present) and tail (if present). Must be implemented
     * by subclasses.
     *
     * \returns The size of the entire record, including header, body (xid and data,
     * if present) and record tail (if persent) in bytes.
     */
    virtual uint64_t getRecordSize() const = 0;

    /// \todo TODO - Is this the right place for encode/decode fns?
    ///**
    // * \brief Encode (write) this record instance into the buffer pointed to by the buffer
    // * pointer. Must be implemented by subclasses.
    // */
    //virtual uint64_t encode(char* bufferPtr,
    //                           const uint64_t bufferSize,
    //                           const uint64_t encodeOffset = 0) = 0;

    /**
     * \brief Return a uint32_t checksum for the header and body content of this record.
     *
     * \param initialValue The initial (or seed) value of the checksum.
     *
     * \returns Checksum for header and body of record. Tail (if any) is excluded.
     */
    uint32_t getCheckSum(uint32_t initialValue = 0) const;

};

#pragma pack()

}}} // namespace qpid::asyncStore::jrnl2

#endif // qpid_asyncStore_jrnl2_RecordHeader_h_
