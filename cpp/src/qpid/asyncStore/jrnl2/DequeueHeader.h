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
 * \file DequeueHeader.h
 *
 * List of journal record structs:
 *   struct DequeueHeader      <-- This file
 *   struct EnqueueHeader
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

#ifndef qpid_asyncStore_jrnl2_DequeueHeader_h_
#define qpid_asyncStore_jrnl2_DequeueHeader_h_

#include "RecordHeader.h"

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

#pragma pack(1)

/**
 * \brief Struct for dequeue record header. This record marks a previous enqueue as
 * logically deleted.
 *
 * The dequeue record conceptually marks a previous enqueue record as dequeued,
 * or logically deleted. This is achieved by recording the record ID (rid) of
 * the enqueue record being deleted in the m_dequeueRecordId field. Note that this
 * rid is distinct from the rid assigned to the dequeue record itself in m_recordId.
 *
 * Dequeue records do not carry data. However, if it is transactional (and thus has
 * a transaction ID (xid), then this record will be terminated by a RecordTail struct.
 * If, on the other hand, this record is non-transactional, then the rec_tail
 * is absent.
 *
 * Note that this record had its own rid distinct from the rid of the record it is dequeuing.
 * The rid field below is the rid of the dequeue record itself; the deq-rid field is the rid of a
 * previous enqueue record being dequeued by this record.
 *
 * Record layout in binary format (32 bytes):
 * <pre>
 *        0x0                                       0x7
 *      +-----+-----+-----+-----+-----+-----+-----+-----+  -+
 * 0x00 |        m_magic        |  v  |  e  |  m_flags  |   |
 *      +-----+-----+-----+-----+-----+-----+-----+-----+   | struct RecordHeader
 * 0x08 |                   m_recordId                  |   |
 *      +-----+-----+-----+-----+-----+-----+-----+-----+  -+
 * 0x10 |               m_dequeuedRecordId              |
 *      +-----+-----+-----+-----+-----+-----+-----+-----+
 * 0x18 |                   m_xidSize                   |
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
 * Non-transactional:
 * +---------+
 * | dequeue |
 * | header  |
 * +---------+
 * <-- 32 --->
 *
 * Transactional:
 * +---------+------------------------+--------+
 * | dequeue | XID                    | record |
 * | header  |                        | tail   |
 * +---------+------------------------+--------+
 * <-- 32 ---><----- m_xidSize ------><-- 16 -->
 * </pre>
 */
class DequeueHeader : public RecordHeader
{
public:
    uint64_t m_dequeuedRecordId;    ///< Record ID of dequeued record
    uint64_t m_xidSize;             ///< XID size

    /**
     * \brief Mask for the record header flags field m_flags which is used in dequeue records in the Transaction
     * Prepared List (TPL)  to indicate that a closed transaction resulted in a commit (if the flag is set)
     * or an abort (if the flag is not set).
     */
    static const uint16_t DEQ_HDR_TPL_COMMIT_ON_TXN_COMPL_MASK = 0x10;

    /**
     * \brief Default constructor, which sets all values to 0.
     */
    DequeueHeader();

    /**
     * \brief Convenience constructor which initializes values during construction.
     *
     * \param magic The magic for this record
     * \param version Version of this record
     * \param recordId Record identifier for this record
     * \param dequeuedRecordId  Record identifier of the record being dequeued by this record
     * \param xidSize Size of the transaction (or distributed transaction) ID for this record
     * \param overwriteIndicator Flag indicating the present value of the overwrite indicator when writing this
     *        record
     * \param tplCommitOnTxnComplFlag
     */
    DequeueHeader(const uint32_t magic,
                  const uint8_t version,
                  const uint64_t recordId,
                  const uint64_t dequeuedRecordId,
                  const uint64_t xidSize,
                  const bool overwriteIndicator,
                  const bool tplCommitOnTxnComplFlag = false);

    /**
     * \brief Copy constructor
     *
     * \param dh Instance to be copied
     */
    DequeueHeader(const DequeueHeader& dh);

    /**
     * \brief Virtual destructor
     */
    virtual ~DequeueHeader();

    /**
     * \brief Convenience copy method.
     */
    void copy(const DequeueHeader& dh);

    /**
     * \brief Reset this record to default values (mostly 0)
     */
    void reset();

    /**
     * \brief Return the value of the tplCommitOnTxnComplFlag for this record. This flag is used only within the
     * TPL, and if set, indicates that the transaction was closed using a commit. If not set, the transaction was
     * closed using an abort. This is used during recovery of the transactions in the store.
     *
     * \returns \b true if the tplCommitOnTxnComplFlag flag for this record is set, \b false otherwise.
     */
    bool getTplCommitOnTxnComplFlag() const;

    /**
     * \brief Set the value of the tplCommitOnTxnComplFlag for this record. This is only used in the TPL, and is
     * ignored elsewhere.
     *
     * \param commitOnTxnCompl The value to be set in the tplCommitOnTxnComplFlag. If \b true, the transaction was
     * closed with a commit; if \b false, with an abort.
     */
    void setTplCommitOnTxnComplFlag(const bool commitOnTxnCompl);

    /**
     * \brief Return the header size of this record in bytes.
     *
     * \returns Size of record header in bytes.
     */
    static uint64_t getHeaderSize();

    /**
     * \brief Return the body (xid and data) size of this record in bytes.
     *
     * \returns Size of record body in bytes.
     */
    uint64_t getBodySize() const;

    /**
     * \brief Return total size of this record in bytes, being in the case of the dequeue record the size of the
     * header, the size of the body (xid only) and the size of the tail.
     *
     * \returns Total size of record in bytes.
     */
    uint64_t getRecordSize() const;

};

#pragma pack()

}}} // namespace qpid::asyncStore::jrnl2

#endif // qpid_asyncStore_jrnl2_DequeueHeader_h_
