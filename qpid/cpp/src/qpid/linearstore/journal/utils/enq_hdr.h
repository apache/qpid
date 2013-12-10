#ifndef QPID_LINEARSTORE_JOURNAL_UTILS_ENQ_HDR_H
#define QPID_LINEARSTORE_JOURNAL_UTILS_ENQ_HDR_H
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
 * \brief Struct for enqueue record.
 *
 * Struct for enqueue record. In addition to the common data, this header includes both the
 * xid and data blob sizes.
 *
 * This header precedes all enqueue data in journal files.
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
 * |            xidsize            |
 * +---+---+---+---+---+---+---+---+
 * |             dsize             |
 * +---+---+---+---+---+---+---+---+
 * v = file version (If the format or encoding of this file changes, then this
 *     number should be incremented)
 * </pre>
 */
typedef struct enq_hdr_t {
    rec_hdr_t  _rhdr;		/**< Common record header struct */
    uint64_t  _xidsize;		/**< XID size in octets */
    uint64_t  _dsize;		/**< Record data size in octets */
} enq_hdr_t;

static const uint16_t ENQ_HDR_TRANSIENT_MASK = 0x10;
static const uint16_t ENQ_HDR_EXTERNAL_MASK = 0x20;

void enq_hdr_init(enq_hdr_t* dest, const uint32_t magic, const uint16_t version, const uint16_t uflag,
                  const uint64_t serial, const uint64_t rid, const uint64_t xidsize, const uint64_t dsize);
void enq_hdr_copy(enq_hdr_t* dest, const enq_hdr_t* src);
bool is_enq_transient(const enq_hdr_t *eh);
void set_enq_transient(enq_hdr_t *eh, const bool transient);
bool is_enq_external(const enq_hdr_t *eh);
void set_enq_external(enq_hdr_t *eh, const bool external);
bool validate_enq_hdr(enq_hdr_t *eh, const uint32_t magic, const uint16_t version, const uint64_t rid);

#pragma pack()

#ifdef __cplusplus
}
#endif

#endif /* ifndef QPID_LINEARSTORE_JOURNAL_UTILS_ENQ_HDR_H */
