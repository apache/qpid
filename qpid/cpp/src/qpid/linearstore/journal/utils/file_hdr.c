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

#include "file_hdr.h"
#include <string.h>

void file_hdr_create(file_hdr_t* dest, const uint32_t magic, const uint16_t version, const uint16_t fhdr_size_sblks,
                     const uint16_t efp_partition, const uint64_t file_size) {
    rec_hdr_init(&dest->_rhdr, magic, version, 0, 0, 0);
    dest->_fhdr_size_sblks = fhdr_size_sblks;
    dest->_efp_partition = efp_partition;
    dest->_reserved = 0;
    dest->_data_size_kib = file_size;
    dest->_fro = 0;
    dest->_ts_nsec = 0;
    dest->_ts_sec = 0;
    dest->_file_number = 0;
    dest->_queue_name_len = 0;
}

int file_hdr_init(void* dest, const uint64_t dest_len, const uint16_t uflag, const uint64_t serial, const uint64_t rid,
                  const uint64_t fro, const uint64_t file_number, const uint16_t queue_name_len, const char* queue_name) {
    file_hdr_t* fhp = (file_hdr_t*)dest;
    fhp->_rhdr._uflag = uflag;
    fhp->_rhdr._serial = serial;
    fhp->_rhdr._rid = rid;
    fhp->_fro = fro;
    fhp->_file_number = file_number;
    if (sizeof(file_hdr_t) + queue_name_len < MAX_FILE_HDR_LEN) {
        fhp->_queue_name_len = queue_name_len;
    } else {
        fhp->_queue_name_len = MAX_FILE_HDR_LEN - sizeof(file_hdr_t);
    }
    fhp->_queue_name_len = queue_name_len;
    memcpy((char*)dest + sizeof(file_hdr_t), queue_name, queue_name_len);
    memset((char*)dest + sizeof(file_hdr_t) + queue_name_len, 0, dest_len - sizeof(file_hdr_t) - queue_name_len);
    return set_time_now(dest);
}

int file_hdr_check(file_hdr_t* hdr, const uint32_t magic, const uint16_t version, const uint64_t data_size_kib, const uint16_t max_queue_name_len) {
    int err = rec_hdr_check_base(&hdr->_rhdr, magic, version);
    if (data_size_kib && hdr->_data_size_kib != data_size_kib) err |= 0x1000;
    if (hdr->_queue_name_len > max_queue_name_len) err |= 0x10000;
    return err;
}

void file_hdr_copy(file_hdr_t* dest, const file_hdr_t* src) {
    rec_hdr_copy(&dest->_rhdr, &src->_rhdr);
    dest->_fhdr_size_sblks = src->_fhdr_size_sblks; // Should this be copied?
    dest->_efp_partition = src->_efp_partition;     // Should this be copied?
    dest->_data_size_kib = src->_data_size_kib;
    dest->_fro = src->_fro;
    dest->_ts_sec = src->_ts_sec;
    dest->_ts_nsec = src->_ts_nsec;
    dest->_file_number = src->_file_number;
}

void file_hdr_reset(file_hdr_t* target) {
    target->_rhdr._uflag = 0;
    target->_rhdr._serial = 0;
    target->_rhdr._rid = 0;
    target->_fro = 0;
    target->_ts_sec = 0;
    target->_ts_nsec = 0;
    target->_file_number = 0;
    target->_queue_name_len = 0;
}

int is_file_hdr_reset(file_hdr_t* target) {
    return target->_rhdr._uflag == 0 &&
           target->_rhdr._serial == 0 &&
           target->_rhdr._rid == 0 &&
           target->_ts_sec == 0 &&
           target->_ts_nsec == 0 &&
           target->_file_number == 0 &&
           target->_queue_name_len == 0;
}

int set_time_now(file_hdr_t *fh)
{
    struct timespec ts;
    int    err = clock_gettime(CLOCK_REALTIME, &ts);
    if (err)
        return err;
    fh->_ts_sec = ts.tv_sec;
    fh->_ts_nsec = ts.tv_nsec;
    return 0;
}


void set_time(file_hdr_t *fh, struct timespec *ts)
{
    fh->_ts_sec  = ts->tv_sec;
    fh->_ts_nsec = ts->tv_nsec;
}


