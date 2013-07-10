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

static const uint16_t DEQ_HDR_TXNCMPLCOMMIT_MASK = 0x10;

bool is_txn_coml_commit(deq_hdr_t *dh) {
    return dh->_rhdr._uflag & DEQ_HDR_TXNCMPLCOMMIT_MASK;
}

void set_txn_coml_commit(deq_hdr_t *dh, const bool commit) {
    dh->_rhdr._uflag = commit ? dh->_rhdr._uflag | DEQ_HDR_TXNCMPLCOMMIT_MASK :
                                dh->_rhdr._uflag & (~DEQ_HDR_TXNCMPLCOMMIT_MASK);
}
