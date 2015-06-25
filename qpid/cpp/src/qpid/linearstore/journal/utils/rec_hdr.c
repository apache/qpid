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

#include "rec_hdr.h"

void rec_hdr_init(rec_hdr_t* dest, const uint32_t magic, const uint16_t version, const uint16_t uflag, const uint64_t serial, const uint64_t rid) {
    dest->_magic = magic;
    dest->_version = version;
    dest->_uflag = uflag;
    dest->_serial = serial;
    dest->_rid = rid;
}

void rec_hdr_copy(rec_hdr_t* dest, const rec_hdr_t* src) {
    dest->_magic = src->_magic;
    dest->_version = src->_version;
    dest->_uflag = src->_uflag;
    dest->_serial = src->_serial;
    dest->_rid = src->_rid;
}

int rec_hdr_check_base(rec_hdr_t* header, const uint32_t magic, const uint16_t version) {
    int err = 0;
    if (header->_magic != magic) err |= 0x1;
    if (header->_version != version) err |= 0x10;
    return err;
}

int rec_hdr_check(rec_hdr_t* header, const uint32_t magic, const uint16_t version, const uint64_t serial) {
    int err = rec_hdr_check_base(header, magic, version);
    if (header->_serial != serial) err |= 0x100;
    return err;
}
