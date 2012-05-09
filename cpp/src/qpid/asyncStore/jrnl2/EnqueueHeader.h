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
 * \file EnqueueHeader.h
 *
 * List of journal record structs:
 *   struct DequeueHeader
 *   struct EnqueueHeader      <-- This file
 *   struct EventHeader
 *   struct FileHeader
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

#ifndef qpid_asyncStore_jrnl2_EnqueueHeader_h_
#define qpid_asyncStore_jrnl2_EnqueueHeader_h_

#include "EventHeader.h"

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

#pragma pack(1)

/**
 * \brief Struct for enqueue record header. This record stores message data to the
 * journal.
 *
 * Enqueue records record the content of messages for possible later recovery, and
 * are so-called because they correspond with the event of enqueuing the record on
 * a queue.
 *
 * In addition to the fields inherited from RecordHeader, this struct includes both the
 * transaction id (xid) and data blob sizes.
 *
 * This header precedes the enqueued message data in journal files, and unless there is
 * no xid and no data (both with 0 length), is followed by a RecordTail.
 *
 * Record layout in binary format (32 bytes):
 * <pre>
 *        0x0                                       0x7
 *      +-----+-----+-----+-----+-----+-----+-----+-----+  -+
 * 0x00 |        m_magic        |  v  |  e  |  m_flags  |   |
 *      +-----+-----+-----+-----+-----+-----+-----+-----+   | struct RecordHeader
 * 0x08 |                   m_recordId                  |   |
 *      +-----+-----+-----+-----+-----+-----+-----+-----+  -+
 * 0x10 |                   m_xidSize                   |
 *      +-----+-----+-----+-----+-----+-----+-----+-----+
 * 0x18 |                   m_dataSize                  |
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
 * <pre>
 * No content (ie m_xidSize==0 AND m_dataSize==0):
 * +---------+
 * | enqueue |
 * | header  |
 * +---------+
 * <-- 32 --->
 *
 * With content:
 * +---------+----------------+---------------------------+--------+
 * | enqueue | XID            | Data                      | record |
 * | header  |                |                           | tail   |
 * +---------+----------------+---------------------------+--------+
 * <-- 32 ---><-- m_xidSize --><------ m_dataSize -------><-- 16 -->
 * </pre>
 */
class EnqueueHeader : public EventHeader
{
public:
    /**
     * \brief Mask for the record header flags field m_flags which is used to indicate that a record is transient
     * (if the flag is set) or durable (if the flag is not set).
     */
    static const uint16_t ENQ_HDR_TRANSIENT_MASK = 0x10;

    /**
     * \brief Mask for the record header flags field m_flags which is used to indicate that a record is using
     * external storage for its data content (if the flag is set). If the flag is not set, then the data content
     * is stored in the journal itself.
     */
    static const uint16_t ENQ_HDR_EXTERNAL_MASK = 0x20;

    /**
     * \brief Default constructor, which sets all values to 0.
     */
    EnqueueHeader();

    /**
     * \brief Convenience constructor which initializes values during construction.
     *
     * \param magic The magic for this record
     * \param version Version of this record
     * \param recordId Record identifier for this record
     * \param xidSize Size of the transaction (or distributed transaction) ID for this record
     * \param dataSize Size of the opaque data block for this record
     * \param overwriteIndicator Flag indicating the present value of the overwrite indicator when writing this
     *        record
     * \param transient Flag indicating that this record is transient (ie to be discarded on recovery)
     * \param external Flag indicating that this record's data is stored externally to the journal, the data portion
     *        of the record identifies the storage location.
     */
    EnqueueHeader(const uint32_t magic,
                  const uint8_t version,
                  const uint64_t recordId,
                  const uint64_t xidSize,
                  const uint64_t dataSize,
                  const bool overwriteIndicator,
                  const bool transient = false,
                  const bool external = false);

    /**
     * \brief Copy constructor
     *
     * \param eh Instance to be copied
     */
    EnqueueHeader(const EnqueueHeader& eh);

    /**
     * \brief Virtual destructor
     */
    virtual ~EnqueueHeader();

    /**
     * \brief Return the value of the Transient flag for this record. If set, this record is ignored during
     * recovery.
     *
     * \returns true if the Transient flag for this record is set, false otherwise.
     */
    bool getTransientFlag() const;

    /**
     * \brief Set the value of the Transient flag for this record.
     *
     * \param transient The value to be set in the transient flag.
     */
    void setTransientFlag(const bool transient = true);

    /**
     * \brief Return the value of the External flag for this record. If set, this record data is not within the
     * journal but external to it. The data part of this record contains the location of the stored data.
     *
     * \returns true if the Transient flag for this record is set, false otherwise.
     */
    bool getExternalFlag() const;

    /**
     * \brief Set the value of the External flag for this record.
     *
     * \param external The value to be set in the External flag.
     */
    void setExternalFlag(const bool external = true);

    /**
     * \brief Return the header size of this record in bytes.
     *
     * \returns Size of record header in bytes.
     */
    static uint64_t getHeaderSize();

};

#pragma pack()

}}} // namespace qpid::asyncStore::jrnl2

#endif // qpid_asyncStore_jrnl2_EnqueueHeader_h_
