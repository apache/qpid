#ifndef QPID_LINEARSTORE_JOURNAL_UTILS_TXN_HDR_H
#define QPID_LINEARSTORE_JOURNAL_UTILS_TXN_HDR_H
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

#include "rec_hdr.h"

#ifdef __cplusplus
extern "C"{
#endif

#pragma pack(1)

/**
 * \brief Struct for transaction commit and abort records.
 *
 * Struct for local and DTX commit and abort records. Only the magic distinguishes between them.
 * Since this record must be used in the context of a valid XID, the xidsize field must not be
 * zero. Immediately following this record is the XID itself which is xidsize bytes long,
 * followed by a rec_tail.
 *
 * Note that this record had its own rid distinct from the rids of the record(s) making up the
 * transaction it is committing or aborting.
 *
 * Record header info in binary format (32 bytes):
 * <pre>
 *   0                           7
 * +---+---+---+---+---+---+---+---+  -+
 * |     magic     |  ver  | flags |   |
 * +---+---+---+---+---+---+---+---+   |
 * |             serial            |   | struct rec_hdr_t
 * +---+---+---+---+---+---+---+---+   |
 * |              rid              |   |
 * +---+---+---+---+---+---+---+---+  -+
 * |            xidsize            |
 * +---+---+---+---+---+---+---+---+
 * </pre>
 */
typedef struct txn_hdr_t {
    rec_hdr_t _rhdr;		/**< Common record header struct */
    uint64_t  _xidsize;		/**< XID size */
} txn_hdr_t;

void txn_hdr_init(txn_hdr_t* dest, const uint32_t magic, const uint16_t version, const uint16_t uflag,
                  const uint64_t serial, const uint64_t rid, const uint64_t xidsize);
void txn_hdr_copy(txn_hdr_t* dest, const txn_hdr_t* src);

#pragma pack()

#ifdef __cplusplus
}
#endif

#endif /* ifndef QPID_LINEARSTORE_JOURNAL_UTILS_TXN_HDR_H */
