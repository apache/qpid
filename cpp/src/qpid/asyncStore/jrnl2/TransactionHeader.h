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
 * \file TransactionHeader.h
 *
 * List of journal record structs:
 *   struct DequeueHeader
 *   struct EnqueueHeader
 *   struct EventHeader
 *   struct FileHeader
 *   struct RecordHeader
 *   struct RecordTail
 *   struct TransactionHeader  <-- This file
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

#ifndef qpid_asyncStore_jrnl2_TransactionHeader_h_
#define qpid_asyncStore_jrnl2_TransactionHeader_h_

#include "RecordHeader.h"

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

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
 * Record layout in binary format (24 bytes):
 * <pre>
 *        0x0                                       0x7
 *      +-----+-----+-----+-----+-----+-----+-----+-----+  -+
 * 0x00 |        _magic         |  v  |  e  |  _flags   |   |
 *      +-----+-----+-----+-----+-----+-----+-----+-----+   | struct RecordHeader
 * 0x08 |                   _recordId                   |   |
 *      +-----+-----+-----+-----+-----+-----+-----+-----+  -+
 * 0x10 |                   _xidSize                    |
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
class TransactionHeader : public RecordHeader
{
public:
    uint64_t m_xidSize;           ///< XID size

    /**
     * \brief Default constructor, which sets all values to 0.
     */
    TransactionHeader();

    /**
     * \brief Convenience constructor which initializes values during construction.
     *
     * \param magic The magic for this record
     * \param version Version of this record
     * \param recordId Record identifier for this record
     * \param xidSize Size of the transaction (or distributed transaction) ID for this record
     * \param overwriteIndicator Flag indicating the present value of the overwrite indicator when writing this
     *        record
     */
    TransactionHeader(const uint32_t magic,
                      const uint8_t version,
                      const uint64_t recordId,
                      const uint64_t xidSize,
                      const bool overwriteIndicator);

    /**
     * \brief Copy constructor
     *
     * \param th Instance to be copied
     */
    TransactionHeader(const TransactionHeader& th);

    /**
     * \brief Virtual destructor
     */
    virtual ~TransactionHeader();

    /**
     * \brief Convenience copy method.
     */
    inline void copy(const TransactionHeader& th);

    /**
     * \brief Reset this record to default values (mostly 0)
     */
    inline void reset();

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
    uint64_t getBodySize() const;

    /**
     * \brief Return total size of this record in bytes, being in the case of the dequeue record the size of the
     * header, the size of the body (xid only) and the size of the tail.
     */
    inline uint64_t getRecordSize() const;

};

#pragma pack()

}}} // namespace qpid::asyncStore::jrnl2

#endif // qpid_asyncStore_jrnl2_TransactionHeader_h_
