#ifndef QPID_LINEARSTORE_JRNL_UTILS_REC_HDR_H
#define QPID_LINEARSTORE_JRNL_UTILS_REC_HDR_H
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
 * \file rec_hdr.h
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::rec_hdr (record header),
 * which is a common initial header used for all journal record structures
 * except the record tail (rec_tail).
 *
 * \author Kim van der Riet
 */

#include <stdint.h>
/*#include "qpid/legacystore/jrnl/jcfg.h"*/
/*#include <sys/types.h>*/

#pragma pack(1)

/**
 * \brief Struct for data common to the head of all journal files and records.
 * This includes identification for the file type, the encoding version, endian
 * indicator and a record ID.
 *
 * File header info in binary format (16 bytes):
 * <pre>
 *   0                           7
 * +---+---+---+---+---+---+---+---+
 * |     magic     | v | 0 | flags |
 * +---+---+---+---+---+---+---+---+
 * |              rid              |
 * +---+---+---+---+---+---+---+---+
 * v = file version (If the format or encoding of this file changes, then this
 *     number should be incremented)
 * 0 = reserved
 * </pre>
 *
 * Note that journal files should be transferable between 32- and 64-bit
 * hardware of the same endianness, but not between hardware of opposite
 * entianness without some sort of binary conversion utility. Thus buffering
 * will be needed for types that change size between 32- and 64-bit compiles.
 */
typedef struct rec_hdr_t {
    uint32_t _magic;       ///< File type identifier (magic number)
    uint8_t  _version;     ///< File encoding version
    uint8_t  _zero;        ///< Flag for determining endianness
    uint16_t _uflag;       ///< User-defined flags
    uint64_t _rid;         ///< Record ID (rotating 64-bit counter)
} rec_hdr_t;

#pragma pack()

#endif // ifndef QPID_LINEARSTORE_JRNL_UTILS_REC_HDR_H
