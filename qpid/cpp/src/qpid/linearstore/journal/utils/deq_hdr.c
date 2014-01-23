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

#include "deq_hdr.h"

/*static const uint16_t DEQ_HDR_TXNCMPLCOMMIT_MASK = 0x10;*/

void deq_hdr_init(deq_hdr_t* dest, const uint32_t magic, const uint16_t version, const uint16_t uflag,
                  const uint64_t serial, const uint64_t rid, const uint64_t deq_rid, const uint64_t xidsize) {
    rec_hdr_init(&dest->_rhdr, magic, version, uflag, serial, rid);
    dest->_deq_rid = deq_rid;
    dest->_xidsize = xidsize;
}

void deq_hdr_copy(deq_hdr_t* dest, const deq_hdr_t* src) {
    rec_hdr_copy(&dest->_rhdr, &src->_rhdr);
    dest->_deq_rid = src->_deq_rid;
    dest->_xidsize = src->_xidsize;
}

bool is_txn_coml_commit(const deq_hdr_t *dh) {
    return dh->_rhdr._uflag & DEQ_HDR_TXNCMPLCOMMIT_MASK;
}

void set_txn_coml_commit(deq_hdr_t *dh, const bool commit) {
    dh->_rhdr._uflag = commit ? dh->_rhdr._uflag | DEQ_HDR_TXNCMPLCOMMIT_MASK : // set flag bit
                                dh->_rhdr._uflag & (~DEQ_HDR_TXNCMPLCOMMIT_MASK); // unset flag bit
}
