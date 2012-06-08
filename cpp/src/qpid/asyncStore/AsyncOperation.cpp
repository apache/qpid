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
 * \file AsyncOperation.cpp
 */

#include "AsyncOperation.h"

#include "qpid/Exception.h"

#include <sstream>

namespace qpid {
namespace asyncStore {

AsyncOperation::AsyncOperation() :
        m_op(NONE),
        m_targetHandle(),
        m_dataSrc(0),
        m_txnHandle(0)
{}

AsyncOperation::AsyncOperation(const opCode op,
                               const qpid::broker::IdHandle* th,
                               boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt) :
        m_op(op),
        m_targetHandle(th),
        m_dataSrc(0),
        m_txnHandle(0),
        m_brokerCtxt(brokerCtxt)
{}

AsyncOperation::AsyncOperation(const opCode op,
                               const qpid::broker::IdHandle* th,
                               const qpid::broker::DataSource* const dataSrc,
                               boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt) :
        m_op(op),
        m_targetHandle(th),
        m_dataSrc(dataSrc),
        m_txnHandle(0),
        m_brokerCtxt(brokerCtxt)
{}

AsyncOperation::AsyncOperation(const opCode op,
                               const qpid::broker::IdHandle* th,
                               const qpid::broker::TxnHandle* txnHandle,
                               boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt) :
        m_op(op),
        m_targetHandle(th),
        m_dataSrc(0),
        m_txnHandle(txnHandle),
        m_brokerCtxt(brokerCtxt)
{}

AsyncOperation::AsyncOperation(const opCode op,
                               const qpid::broker::IdHandle* th,
                               const qpid::broker::DataSource* const dataSrc,
                               const qpid::broker::TxnHandle* txnHandle,
                               boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt) :
        m_op(op),
        m_targetHandle(th),
        m_dataSrc(dataSrc),
        m_txnHandle(txnHandle),
        m_brokerCtxt(brokerCtxt)
{}

AsyncOperation::~AsyncOperation()
{}

const char*
AsyncOperation::getOpStr() const
{
    return getOpStr(m_op);
}

//static
const char*
AsyncOperation::getOpStr(const opCode op)
{
    switch (op) {
    case NONE: return "<none>";
    case TXN_PREPARE: return "TXN_PREPARE";
    case TXN_COMMIT: return "TXN_COMMIT";
    case TXN_ABORT: return "TXN_ABORT";
    case CONFIG_CREATE: return "CONFIG_CREATE";
    case CONFIG_DESTROY: return "CONFIG_DESTROY";
    case QUEUE_CREATE: return "QUEUE_CREATE";
    case QUEUE_FLUSH: return "QUEUE_FLUSH";
    case QUEUE_DESTROY: return "QUEUE_DESTROY";
    case EVENT_CREATE: return "EVENT_CREATE";
    case EVENT_DESTROY: return "EVENT_DESTROY";
    case MSG_ENQUEUE: return "MSG_ENQUEUE";
    case MSG_DEQUEUE: return "MSG_DEQUEUE";
    }
    std::ostringstream oss;
    oss << "AsyncStore: AsyncOperation::getOpStr(): Unknown op-code \"" << op << "\"";
    throw qpid::Exception(oss.str());
}

}} // namespace qpid::asyncStore
