/*
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
 */

/**
 * \file TxnHandleImpl.cpp
 */

#include "TxnHandleImpl.h"

namespace qpid {
namespace asyncStore {

TxnHandleImpl::TxnHandleImpl() :
        m_tpcFlag(false),
        m_txnBuffer(0)
{}

TxnHandleImpl::TxnHandleImpl(qpid::broker::SimpleTxnBuffer* tb) :
        m_tpcFlag(false),
        m_txnBuffer(tb)
{}

TxnHandleImpl::TxnHandleImpl(const std::string& xid, const bool tpcFlag) :
        m_xid(xid),
        m_tpcFlag(tpcFlag),
        m_txnBuffer(0)
{}

TxnHandleImpl::TxnHandleImpl(const std::string& xid,
                             const bool tpcFlag,
                             qpid::broker::SimpleTxnBuffer* tb) :
        m_xid(xid),
        m_tpcFlag(tpcFlag),
        m_txnBuffer(tb)
{}

TxnHandleImpl::~TxnHandleImpl() {}

const std::string&
TxnHandleImpl::getXid() const {
    return m_xid;
}

bool
TxnHandleImpl::is2pc() const {
    return m_tpcFlag;
}

}} // namespace qpid::asyncStore
