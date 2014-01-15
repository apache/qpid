#ifndef QPID_LINEARSTORE_JOURNAL_UTILS_REC_TAIL_H
#define QPID_LINEARSTORE_JOURNAL_UTILS_REC_TAIL_H
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

#include <stdint.h>
#include "rec_hdr.h"

#ifdef __cplusplus
extern "C"{
#endif

#pragma pack(1)

/**
 * \brief Struct for data common to the tail of all records. The magic number
 * used here is the binary inverse (1's complement) of the magic used in the
 * record header; this minimizes possible confusion with other headers that may
 * be present during recovery. The tail is used with all records that have either
 * XIDs or data - ie any size-variable content. Currently the only records that
 * do NOT use the tail are non-transactional dequeues and filler records.
 *
 * The checksum is used to verify the xid and/or data portion of the record
 * on recovery, and excludes the header and tail.
 *
 * Record header info in binary format (24 bytes):
 * <pre>
 *   0                           7
 * +---+---+---+---+---+---+---+---+
 * |   ~(magic)    |   checksum    |
 * +---+---+---+---+---+---+---+---+
 * |             serial            |
 * +---+---+---+---+---+---+---+---+
 * |              rid              |
 * +---+---+---+---+---+---+---+---+
 *
 * ~(magic) = 1's compliment of magic of matching record header
 * rid = Record ID of matching record header
 * </pre>
 */
typedef struct rec_tail_t {
    uint32_t _xmagic;		/**< Binary inverse (1's complement) of hdr magic number */
    uint32_t _checksum;		/**< Checksum of xid and data (excluding header itself) */
    uint64_t _serial;       /**< Serial number for this journal file */
    uint64_t _rid;			/**< Record ID (rotating 64-bit counter) */
} rec_tail_t;

static const uint16_t REC_TAIL_MAGIC_ERR_MASK = 0x01;
static const uint16_t REC_TAIL_SERIAL_ERR_MASK = 0x02;
static const uint16_t REC_TAIL_RID_ERR_MASK = 0x04;
static const uint16_t REC_TAIL_CHECKSUM_ERR_MASK = 0x08;

void rec_tail_init(rec_tail_t* dest, const uint32_t xmagic, const uint32_t checksum, const uint64_t serial,
                   const uint64_t rid);
void rec_tail_copy(rec_tail_t* dest, const rec_hdr_t* src, const uint32_t checksum);
uint16_t rec_tail_check(const rec_tail_t* tail, const rec_hdr_t* header, const uint32_t checksum);

#pragma pack()

#ifdef __cplusplus
}
#endif

#endif /* ifnedf QPID_LINEARSTORE_JOURNAL_UTILS_REC_TAIL_H */
