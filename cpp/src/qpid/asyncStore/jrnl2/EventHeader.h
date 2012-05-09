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
 * \file EventHeader.h
 *
 * List of journal record structs:
 *   struct DequeueHeader
 *   struct EnqueueHeader
 *   struct EventHeader        <-- This file
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

#ifndef qpid_asyncStore_jrnl2_EventHeader_h_
#define qpid_asyncStore_jrnl2_EventHeader_h_

#include "RecordHeader.h"

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

#pragma pack(1)

/**
 * \brief Struct for an event record header. Event records can be used to record
 * system events in the store.
 *
 * The EventHeader record type may be used to store events into the journal which do
 * not constitute data content but changes of state in the broker. These can be
 * recovered and used to set appropriate state in the broker.
 *
 * This record is almost identical to EnqueueRecord, but without the flags. It
 * precedes the xid and stored event data in journal files, and unless there is
 * no xid and no data (both with 0 length), is followed by a RecordTail.
 *
 * \todo TODO: I am uncertain at this time whether it is necessary to set an XID on an event
 * record, but in case, I have left this feature in. In any event, there is only a
 * 1 byte size penalty in the header size for doing so.
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
 * | event   |
 * | header  |
 * +---------+
 * <-- 32 --->
 *
 * With content:
 * +---------+----------------+---------------------------+--------+
 * | event   | XID            | Event data                | record |
 * | header  |                |                           | tail   |
 * +---------+----------------+---------------------------+--------+
 * <-- 32 ---><-- m_xidSize --><------ m_dataSize -------><-- 16 -->
 * </pre>
 */
class EventHeader : public RecordHeader
{
public:
    uint64_t m_xidSize;          ///< XID size
    uint64_t m_dataSize;         ///< Record data size

    /**
     * \brief Default constructor, which sets all values to 0.
     */
    EventHeader();

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
     */
    EventHeader(const uint32_t magic,
                       const uint8_t version,
                       const uint64_t recordId,
                       const uint64_t xidSize,
                       const uint64_t dataSize,
                       const bool overwriteIndicator);

    /**
     * \brief Copy constructor
     *
     * \param eh Instance to be copied
     */
    EventHeader(const EventHeader& eh);

    /**
     * \brief Virtual destructor
     */
    virtual ~EventHeader();

    /**
     * \brief Convenience copy method.
     */
    virtual void copy(const EventHeader& eh);

    /**
     * \brief Reset this record to default values (mostly 0)
     */
    virtual void reset();

    /**
     * \brief Return the header size of this record in bytes.
     *
     * \returns Size of record header in bytes.
     */
    static uint64_t getHeaderSize();

    /**
     * \brief Return the body (data) size of this record in bytes.
     *
     * \returns Size of record body in bytes.
     */
    virtual uint64_t getBodySize() const;

    /**
     * \brief Return total size of this record in bytes, being in the case of the enqueue record the size of the
     * header, the size of the body (xid and data) and the size of the tail.
     *
     * \returns Total size of record in bytes.
     */
    virtual uint64_t getRecordSize() const;

};

#pragma pack()

}}} // namespace qpid::asyncStore::jrnl2

#endif // qpid_asyncStore_jrnl2_EventHeader_h_
