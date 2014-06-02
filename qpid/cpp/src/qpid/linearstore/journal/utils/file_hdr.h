#ifndef QPID_LINEARSTORE_JOURNAL_UTILS_FILE_HDR_H
#define QPID_LINEARSTORE_JOURNAL_UTILS_FILE_HDR_H
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

#ifdef __cplusplus
extern "C"{
#endif

#define MAX_FILE_HDR_LEN 4096 // Set to 1 sblk

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
 * File header info in binary format (74 bytes + size of file name in octets):
 * <pre>
 *   0                           7
 * +---+---+---+---+---+---+---+---+  -+
 * |     magic     |  ver  | flags |   |
 * +---+---+---+---+---+---+---+---+   |
 * |             serial            |   | struct rec_hdr_t
 * +---+---+---+---+---+---+---+---+   |
 * |              rid              |   |
 * +---+---+---+---+---+---+---+---+  -+
 * |  fhs  | partn |   reserved    |
 * +---+---+---+---+---+---+---+---+
 * |           data-size           |
 * +---+---+---+---+---+---+---+---+
 * |              fro              |
 * +---+---+---+---+---+---+---+---+
 * |           timestamp (sec)     |
 * +---+---+---+---+---+---+---+---+
 * |           timestamp (ns)      |
 * +---+---+---+---+---+---+---+---+
 * |          file-number          |
 * +---+---+---+---+---+---+---+---+
 * |  qnl  | Queue Name...         |
 * +-------+                       |
 * |                               |
 * +---+---+---+---+---+---+---+---+
 *
 * ver = Journal version
 * rid = Record ID
 * fhs = File header size in sblks (defined by JRNL_SBLK_SIZE)
 * partn = EFP partition from which this file came
 * fro = First Record Offset
 * qnl = Length of the queue name in octets.
 * </pre>
 */
typedef struct file_hdr_t {
    rec_hdr_t _rhdr;		    /**< Common record header struct, but rid field is used for rid of first compete record in file */
    uint16_t  _fhdr_size_sblks; /**< File header size in sblks (defined by JRNL_SBLK_SIZE) */
    uint16_t  _efp_partition;   /**< EFP Partition number from which this file was obtained */
    uint32_t  _reserved;
    uint64_t  _data_size_kib;   /**< Size of the data part of this file in KiB. (ie file size excluding file header sblk) */
    uint64_t  _fro;			    /**< First Record Offset (FRO) */
    uint64_t  _ts_sec;		    /**< Time stamp (seconds part) */
    uint64_t  _ts_nsec;		    /**< Time stamp (nanoseconds part) */
    uint64_t  _file_number;     /**< The logical number of this file in a monotonically increasing sequence */
    uint16_t  _queue_name_len;  /**< Length of the queue name in octets, which follows this struct in the header */
} file_hdr_t;

void file_hdr_create(file_hdr_t* dest, const uint32_t magic, const uint16_t version,
                     const uint16_t fhdr_size_sblks, const uint16_t efp_partition, const uint64_t file_size);
int file_hdr_init(void* dest, const uint64_t dest_len, const uint16_t uflag, const uint64_t serial, const uint64_t rid,
                  const uint64_t fro, const uint64_t file_number, const uint16_t queue_name_len,
                  const char* queue_name);
int file_hdr_check(file_hdr_t* hdr, const uint32_t magic, const uint16_t version, const uint64_t data_size_kib,
                   const uint16_t max_queue_name_len);
void file_hdr_reset(file_hdr_t* target);
int is_file_hdr_reset(file_hdr_t* target);
void file_hdr_copy(file_hdr_t* dest, const file_hdr_t* src);
int  set_time_now(file_hdr_t *fh);
void set_time(file_hdr_t *fh, struct timespec *ts);

#pragma pack()

#ifdef __cplusplus
}
#endif

#endif /* ifndef QPID_LINEARSTORE_JOURNAL_UTILS_FILE_HDR_H */
