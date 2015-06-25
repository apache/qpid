#ifndef QPID_LINEARSTORE_JOURNAL_UTILS_DEQ_HDR_H
#define QPID_LINEARSTORE_JOURNAL_UTILS_DEQ_HDR_H
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

#include <stdbool.h>
#include "rec_hdr.h"

#ifdef __cplusplus
extern "C"{
#endif

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
 * Record header info in binary format (40 bytes):
 * <pre>
 *   0                           7
 * +---+---+---+---+---+---+---+---+  -+
 * |     magic     |  ver  | flags |   |
 * +---+---+---+---+---+---+---+---+   |
 * |             serial            |   | struct rec_hdr_t
 * +---+---+---+---+---+---+---+---+   |
 * |              rid              |   |
 * +---+---+---+---+---+---+---+---+  -+
 * |            deq-rid            |
 * +---+---+---+---+---+---+---+---+
 * |            xidsize            |
 * +---+---+---+---+---+---+---+---+
 *
 * deq-rid = dequeue record ID
 * </pre>
 */
typedef struct deq_hdr_t {
    rec_hdr_t _rhdr;		/**< Common record header struct */
    uint64_t  _deq_rid;		/**< Record ID of record being dequeued */
    uint64_t  _xidsize;		/**< XID size */
} deq_hdr_t;

static const uint16_t DEQ_HDR_TXNCMPLCOMMIT_MASK = 0x10;

void deq_hdr_init(deq_hdr_t* dest, const uint32_t magic, const uint16_t version, const uint16_t uflag,
                  const uint64_t serial, const uint64_t rid, const uint64_t deq_rid, const uint64_t xidsize);
void deq_hdr_copy(deq_hdr_t* dest, const deq_hdr_t* src);
bool is_txn_coml_commit(const deq_hdr_t *dh);
void set_txn_coml_commit(deq_hdr_t *dh, const bool commit);

#pragma pack()

#ifdef __cplusplus
}
#endif

#endif /* ifndef QPID_LINEARSTORE_JOURNAL_UTILS_DEQ_HDR_H */
