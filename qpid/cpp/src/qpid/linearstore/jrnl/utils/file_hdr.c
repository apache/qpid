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
#include <time.h>

int set_time_now(file_hdr_t *fh)
{
    struct timespec ts;
    int    err;

    if (err = clock_gettime(CLOCK_REALTIME, &ts))
        return err;
    fh->_ts_sec = ts.tv_sec;
    fh->_ts_nsec = ts.tv_nsec;
}


void set_time(file_hdr_t *fh, struct timespec *ts)
{
    fh->_ts_sec  = ts->tv_sec;
    fh->_ts_nsec = ts->tv_nsec;
}


