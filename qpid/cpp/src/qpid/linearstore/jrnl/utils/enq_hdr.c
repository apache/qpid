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

static const uint16_t ENQ_HDR_TRANSIENT_MASK = 0x10;
static const uint16_t ENQ_HDR_EXTERNAL_MASK = 0x20;

bool is_transient(enq_hdr_t *eh) {
    return eh->_rhdr._uflag & ENQ_HDR_TRANSIENT_MASK;
}

void set_transient(enq_hdr_t *eh, const bool transient) {
    eh->_rhdr._uflag = transient ? eh->_rhdr._uflag | ENQ_HDR_TRANSIENT_MASK :
                                   eh->_rhdr._uflag & (~ENQ_HDR_TRANSIENT_MASK);
}

bool is_external(enq_hdr_t *eh) {
    return eh->_rhdr._uflag & ENQ_HDR_EXTERNAL_MASK;
}

void set_external(enq_hdr_t *eh, const bool external) {
    eh->_rhdr._uflag = external ? eh->_rhdr._uflag | ENQ_HDR_EXTERNAL_MASK :
                                  eh->_rhdr._uflag & (~ENQ_HDR_EXTERNAL_MASK);
}
