#ifndef QPID_LINEARSTORE_JRNL_UTILS_ENQ_HDR_H
#define QPID_LINEARSTORE_JRNL_UTILS_ENQ_HDR_H
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
 * \file enq_hdr.h
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::enq_hdr (enueue header),
 * used to start an enqueue record in the journal.
 *
 * \author Kim van der Riet
 */

#include <stdbool.h>
#include "rec_hdr.h"

#pragma pack(1)

    /**
    * \brief Struct for enqueue record.
    *
    * Struct for enqueue record. In addition to the common data, this header includes both the
    * xid and data blob sizes.
    *
    * This header precedes all enqueue data in journal files.
    *
    * Record header info in binary format (32 bytes):
    * <pre>
    *   0                           7
    * +---+---+---+---+---+---+---+---+  -+
    * |     magic     | v | e | flags |   |
    * +---+---+---+---+---+---+---+---+   | struct hdr
    * |              rid              |   |
    * +---+---+---+---+---+---+---+---+  -+
    * |            xidsize            |
    * +---+---+---+---+---+---+---+---+
    * |             dsize             |
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
typedef struct enq_hdr_t {
    rec_hdr_t  _rhdr;
    uint64_t  _xidsize;        ///< XID size
    uint64_t  _dsize;          ///< Record data size
} enq_hdr_t;

static const uint16_t ENQ_HDR_TRANSIENT_MASK;
static const uint16_t ENQ_HDR_EXTERNAL_MASK;

bool is_transient(enq_hdr_t *eh);
void set_transient(enq_hdr_t *eh, const bool transient);
bool is_external(enq_hdr_t *eh);
void set_external(enq_hdr_t *eh, const bool external);

#pragma pack()

#endif // ifndef QPID_LINEARSTORE_JRNL_UTILS_ENQ_HDR_H
