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

#include "qpid/Exception.h"
#include "qpid/broker/TxnBuffer.h"
#include "qpid/messaging/PrivateImplRef.h"

#include <uuid/uuid.h>

namespace qpid {
namespace asyncStore {

TxnHandleImpl::TxnHandleImpl() :
        m_tpcFlag(false),
        m_asyncOpCnt(0UL),
        m_txnBuffer(0)
{
    createLocalXid();
}

TxnHandleImpl::TxnHandleImpl(qpid::broker::TxnBuffer* tb) :
        m_tpcFlag(false),
        m_asyncOpCnt(0UL),
        m_txnBuffer(tb)
{
    createLocalXid();
}

TxnHandleImpl::TxnHandleImpl(const std::string& xid) :
        m_xid(xid),
        m_tpcFlag(!xid.empty()),
        m_asyncOpCnt(0UL),
        m_txnBuffer(0)
{
    if (m_xid.empty()) {
        createLocalXid();
    }
}

TxnHandleImpl::TxnHandleImpl(const std::string& xid,
                             qpid::broker::TxnBuffer* tb) :
        m_xid(xid),
        m_tpcFlag(!xid.empty()),
        m_asyncOpCnt(0UL),
        m_txnBuffer(tb)
{
    if (m_xid.empty()) {
        createLocalXid();
    }
}

TxnHandleImpl::~TxnHandleImpl()
{}

const std::string&
TxnHandleImpl::getXid() const
{
    return m_xid;
}

bool
TxnHandleImpl::is2pc() const
{
    return m_tpcFlag;
}

void
TxnHandleImpl::incrOpCnt()
{
    ++m_asyncOpCnt;
}

void
TxnHandleImpl::decrOpCnt()
{
    if (m_asyncOpCnt == 0UL) {
        throw qpid::Exception("Transaction async operation count underflow");
    }
    if (--m_asyncOpCnt == 0UL && m_txnBuffer) {
        m_txnBuffer->asyncLocalCommit();
    }
}

// private
void
TxnHandleImpl::createLocalXid()
{
    uuid_t uuid;
    ::uuid_generate_random(uuid);
    char uuidStr[37]; // 36-char uuid + trailing '\0'
    ::uuid_unparse(uuid, uuidStr);
    m_xid.assign(uuidStr);
//std::cout << "TTT TxnHandleImpl::createLocalXid(): Local XID created: \"" << m_xid << "\"" << std::endl << std::flush;
}

}} // namespace qpid::asyncStore
