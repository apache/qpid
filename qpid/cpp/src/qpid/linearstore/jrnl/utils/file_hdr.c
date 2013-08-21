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

void file_hdr_init(file_hdr_t* dest, const uint32_t magic, const uint16_t version, const uint16_t uflag,
                   const uint64_t rid, const uint64_t fro, const uint64_t ts_sec, const uint64_t ts_nsec,
                   const uint32_t file_count, const uint64_t file_size, const uint64_t file_number) {
    rec_hdr_init(&dest->_rhdr, magic, version, uflag, rid);
    dest->_fro = fro;
    dest->_ts_sec = ts_sec;
    dest->_ts_nsec = ts_nsec;
    dest->_file_count = file_count;
    dest->_file_size = file_size;
    dest->_file_number = file_number;
}

void file_hdr_copy(file_hdr_t* dest, const file_hdr_t* src) {
    rec_hdr_copy(&dest->_rhdr, &src->_rhdr);
    dest->_fro = src->_fro;
    dest->_ts_sec = src->_ts_sec;
    dest->_ts_nsec = src->_ts_nsec;
    dest->_file_count = src->_file_count;
    dest->_file_size = src->_file_size;
    dest->_file_number = src->_file_number;
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


