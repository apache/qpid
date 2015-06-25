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

#include "enq_hdr.h"

//static const uint16_t ENQ_HDR_TRANSIENT_MASK = 0x10;
//static const uint16_t ENQ_HDR_EXTERNAL_MASK = 0x20;

void enq_hdr_init(enq_hdr_t* dest, const uint32_t magic, const uint16_t version, const uint16_t uflag,
                  const uint64_t serial, const uint64_t rid, const uint64_t xidsize, const uint64_t dsize) {
    rec_hdr_init(&dest->_rhdr, magic, version, uflag, serial, rid);
    dest->_xidsize = xidsize;
    dest->_dsize = dsize;
}

void enq_hdr_copy(enq_hdr_t* dest, const enq_hdr_t* src) {
    rec_hdr_copy(&dest->_rhdr, &src->_rhdr);
    dest->_xidsize = src->_xidsize;
    dest->_dsize = src->_dsize;
}

bool is_enq_transient(const enq_hdr_t *eh) {
    return eh->_rhdr._uflag & ENQ_HDR_TRANSIENT_MASK;
}

void set_enq_transient(enq_hdr_t *eh, const bool transient) {
    eh->_rhdr._uflag = transient ? eh->_rhdr._uflag | ENQ_HDR_TRANSIENT_MASK :
                                   eh->_rhdr._uflag & (~ENQ_HDR_TRANSIENT_MASK);
}

bool is_enq_external(const enq_hdr_t *eh) {
    return eh->_rhdr._uflag & ENQ_HDR_EXTERNAL_MASK;
}

void set_enq_external(enq_hdr_t *eh, const bool external) {
    eh->_rhdr._uflag = external ? eh->_rhdr._uflag | ENQ_HDR_EXTERNAL_MASK :
                                  eh->_rhdr._uflag & (~ENQ_HDR_EXTERNAL_MASK);
}

bool validate_enq_hdr(enq_hdr_t *eh, const uint32_t magic, const uint16_t version, const uint64_t rid) {
    return eh->_rhdr._magic == magic &&
           eh->_rhdr._version == version &&
           rid > 0 ? eh->_rhdr._rid == rid /* If rid == 0, don't compare rids */
                   : true;
}
