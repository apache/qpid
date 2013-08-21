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
 * File header info in binary format (66 bytes + size of file name in octets):
 * <pre>
 *   0                           7
 * +---+---+---+---+---+---+---+---+  -+
 * |     magic     |  ver  | flags |   |
 * +---+---+---+---+---+---+---+---+   | struct rec_hdr_t
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
 * | n-len |  Queue Name...        |
 * +-------+                       |
 * |                               |
 * +---+---+---+---+---+---+---+---+
 *
 * ver = file version (If the format or encoding of this file changes, then this
 *       number should be incremented)
 * fro = First Record Offset
 * n-len = Length of the queue name in octets.
 * </pre>
 */
typedef struct file_hdr_t {
    rec_hdr_t _rhdr;		/**< Common record header struct, but rid field is used for rid of first compete record in file */
    uint64_t  _fro;			/**< First Record Offset (FRO) */
    uint64_t  _ts_sec;		/**< Time stamp (seconds part) */
    uint64_t  _ts_nsec;		/**< Time stamp (nanoseconds part) */
    uint32_t  _file_count;	/**< Total number of files in linear sequence at time of writing this file */
    uint32_t  _reserved;
    uint64_t  _file_size;	/**< Size of this file in octets, including header sblk */
    uint64_t  _file_number; /**< The logical number of this file in a monotonically increasing sequence */
    uint16_t  _name_length;	/**< Length of the queue name in octets, which follows this struct in the header */
} file_hdr_t;

void file_hdr_init(file_hdr_t* dest, const uint32_t magic, const uint16_t version, const uint16_t uflag,
                   const uint64_t rid, const uint64_t fro, const uint64_t ts_sec, const uint64_t ts_nsec,
                   const uint32_t file_count, const uint64_t file_size, const uint64_t file_number);
void file_hdr_copy(file_hdr_t* dest, const file_hdr_t* src);
int  set_time_now(file_hdr_t *fh);
void set_time(file_hdr_t *fh, struct timespec *ts);

#pragma pack()

#endif /* ifndef QPID_LINEARSTORE_JRNL_UTILS_FILE_HDR_H */
