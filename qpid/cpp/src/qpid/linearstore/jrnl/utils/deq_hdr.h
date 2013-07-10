#ifndef QPID_LINEARSTORE_JRNL_UTILS_DEQ_HDR_H
#define QPID_LINEARSTORE_JRNL_UTILS_DEQ_HDR_H
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
 * \file deq_hdr.h
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::deq_hdr (dequeue record),
 * used to dequeue a previously enqueued record.
 *
 * \author Kim van der Riet
 */

#include <stdbool.h>
#include "rec_hdr.h"

#pragma pack(1)

/**
* \brief Struct for dequeue record.
*
* Struct for dequeue record. If this record has a non-zero xidsize field (i.e., there is a
* valid XID), then this header is followed by the XID of xidsize bytes and a rec_tail. If,
* on the other hand, this record has a zero xidsize (i.e., there is no XID), then the rec_tail
* is absent.
*
* Note that this record had its own rid distinct from the rid of the record it is dequeueing.
* The rid field below is the rid of the dequeue record itself; the deq-rid field is the rid of a
* previous enqueue record being dequeued by this record.
*
* Record header info in binary format (32 bytes):
* <pre>
*   0                           7
* +---+---+---+---+---+---+---+---+  -+
* |     magic     | v | e | flags |   |
* +---+---+---+---+---+---+---+---+   | struct hdr
* |              rid              |   |
* +---+---+---+---+---+---+---+---+  -+
* |            deq-rid            |
* +---+---+---+---+---+---+---+---+
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
typedef struct deq_hdr_t {
    rec_hdr_t _rhdr;
    uint64_t  _deq_rid; ///< Record ID of dequeued record
    uint64_t  _xidsize; ///< XID size
} deq_hdr_t;

static const uint16_t DEQ_HDR_TXNCMPLCOMMIT_MASK;

bool is_txn_coml_commit(deq_hdr_t *dh);
void set_txn_coml_commit(deq_hdr_t *dh, const bool commit);

#pragma pack()

#endif // ifndef QPID_LINEARSTORE_JRNL_UTILS_DEQ_HDR_H
