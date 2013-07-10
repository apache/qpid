#ifndef QPID_LINEARSTORE_JRNL_UTILS_FILE_HDR_H
#define QPID_LINEARSTORE_JRNL_UTILS_FILE_HDR_H
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
 * \file file_hdr.h
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::file_hdr (file
 * record header), used to start a journal file. It contains some
 * file metadata and information to aid journal recovery.
 *
 * \author Kim van der Riet
 */

#include <time.h>
#include "rec_hdr.h"

#pragma pack(1)

/**
 * \brief Struct for data common to the head of all journal files. In addition to
 * the common data, this includes the record ID and offset of the first record in
 * the file.
 *
 * This header precedes all data in journal files and occupies the first complete
 * block in the file. The record ID and offset are updated on each overwrite of the
 * file.
 *
 * File header info in binary format (48 bytes):
 * <pre>
 *   0                           7
 * +---+---+---+---+---+---+---+---+  -+
 * |     magic     | v | 0 | flags |   |
 * +---+---+---+---+---+---+---+---+   | struct hdr
 * |       first rid in file       |   |
 * +---+---+---+---+---+---+---+---+  -+
 * |              fro              |
 * +---+---+---+---+---+---+---+---+
 * |           timestamp (sec)     |
 * +---+---+---+---+---+---+---+---+
 * |           timestamp (ns)      |
 * +---+---+---+---+---+---+---+---+
 * |  file-count   |    reserved   |
 * +---+---+---+---+---+---+---+---+
 * |           file-size           |
 * +---+---+---+---+---+---+---+---+
 * |          file-number          |
 * +---+---+---+---+---+---+---+---+
 * | n-len |  File Name...         |
 * +-------+                       |
 * |                               |
 * +---+---+---+---+---+---+---+---+
 *
 * v = file version (If the format or encoding of this file changes, then this
 *     number should be incremented)
 * fro = First record offset, offset from start of file to first record header
 * file-count  = Number of files in use for the journal at the time this header is written
 * file-size   = Size of the file in octets
 * file-number = Incrementing serial number indicating file order within a journal
 * n-len = Length of the queue name in octets.
 * </pre>
 *
 * Note that journal files should be transferable between 32- and 64-bit
 * hardware of the same endianness, but not between hardware of opposite
 * entianness without some sort of binary conversion utility. Thus buffering
 * will be needed for types that change size between 32- and 64-bit compiles.
 */
typedef struct file_hdr_t {
    rec_hdr_t _rhdr;
    uint64_t  _fro;
    uint64_t  _ts_sec;
    uint64_t  _ts_nsec;
    uint32_t  _file_count;
    uint32_t  _reserved;
    uint64_t  _file_size;
    uint64_t  _file_number;
    uint16_t  _name_length;
} file_hdr_t;

int  set_time_now(file_hdr_t *fh);
void set_time(file_hdr_t *fh, struct timespec *ts);

#pragma pack()

#endif // ifndef QPID_LINEARSTORE_JRNL_UTILS_FILE_HDR_H
