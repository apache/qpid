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

#include "rec_tail.h"

void rec_tail_init(rec_tail_t* dest, const uint32_t xmagic, const uint32_t checksum, const uint64_t serial,
                   const uint64_t rid) {
    dest->_xmagic = xmagic;
    dest->_checksum = checksum;
    dest->_serial = serial;
    dest->_rid = rid;
}

void rec_tail_copy(rec_tail_t* dest, const rec_hdr_t* src, const uint32_t checksum) {
    dest->_xmagic = ~(src->_magic);
    dest->_checksum = checksum;
    dest->_serial = src->_serial;
    dest->_rid = src->_rid;
}

uint16_t rec_tail_check(const rec_tail_t* tail, const rec_hdr_t* header, const uint32_t checksum) {
    uint16_t err = 0;
    if (tail->_xmagic != ~header->_magic) err |= REC_TAIL_MAGIC_ERR_MASK;
    if (tail->_serial != header->_serial) err |= REC_TAIL_SERIAL_ERR_MASK;
    if (tail->_rid != header->_rid) err |= REC_TAIL_RID_ERR_MASK;
    if (tail->_checksum != checksum) err |= REC_TAIL_CHECKSUM_ERR_MASK;
    return err;
}
