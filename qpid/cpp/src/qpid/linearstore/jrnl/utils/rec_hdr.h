#ifndef QPID_LINEARSTORE_JOURNAL_UTILS_REC_HDR_H
#define QPID_LINEARSTORE_JOURNAL_UTILS_REC_HDR_H
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

#ifdef __cplusplus
extern "C"{
#endif

#pragma pack(1)

/**
 * \brief Struct for data common to the head of all journal files and records.
 * This includes identification for the file type, the encoding version, endian
 * indicator and a record ID.
 *
 * File header info in binary format (24 bytes):
 * <pre>
 *   0                           7
 * +---+---+---+---+---+---+---+---+
 * |     magic     |  ver  | uflag |
 * +---+---+---+---+---+---+---+---+
 * |             serial            |
 * +---+---+---+---+---+---+---+---+
 * |              rid              |
 * +---+---+---+---+---+---+---+---+
 *
 * ver = file version (If the format or encoding of this file changes, then this
 *       number should be incremented)
 * rid = Record ID
 * </pre>
 */
typedef struct rec_hdr_t {
    uint32_t _magic;		/**< File type identifier (magic number) */
    uint16_t _version;		/**< File encoding version */
    uint16_t _uflag;		/**< User-defined flags */
    uint64_t _serial;       /**< Serial number for this journal file */
    uint64_t _rid;			/**< Record ID (rotating 64-bit counter) */
} rec_hdr_t;

void rec_hdr_init(rec_hdr_t* dest, const uint32_t magic, const uint16_t version, const uint16_t uflag, const uint64_t serial, const uint64_t rid);
void rec_hdr_copy(rec_hdr_t* dest, const rec_hdr_t* src);
int rec_hdr_check_base(rec_hdr_t* header, const uint32_t magic, const uint16_t version);
int rec_hdr_check(rec_hdr_t* header, const uint32_t magic, const uint16_t version, const uint64_t serial);

#pragma pack()

#ifdef __cplusplus
}
#endif

#endif /* ifndef QPID_LINEARSTORE_JOURNAL_UTILS_REC_HDR_H */
