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
 * \file TxnAccept.cpp
 */

#include "TxnAccept.h"

#include "DeliveryRecord.h"

#include "qpid/log/Statement.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

TxnAccept::TxnAccept(std::deque<boost::shared_ptr<DeliveryRecord> >& ops) :
        m_ops(ops)
{}

TxnAccept::~TxnAccept() {}

// --- Interface TxnOp ---

bool
TxnAccept::prepare(qpid::broker::TxnBuffer* tb) throw() {
    try {
        for (std::deque<boost::shared_ptr<DeliveryRecord> >::iterator i = m_ops.begin(); i != m_ops.end(); ++i) {
            (*i)->dequeue(tb);
        }
        return true;
    } catch (const std::exception& e) {
        QPID_LOG(error, "TxnAccept: Failed to prepare transaction: " << e.what());
    } catch (...) {
        QPID_LOG(error, "TxnAccept: Failed to prepare transaction: (unknown error)");
    }
    return false;
}

void
TxnAccept::commit() throw() {
    try {
        for (std::deque<boost::shared_ptr<DeliveryRecord> >::iterator i=m_ops.begin(); i!=m_ops.end(); ++i) {
            (*i)->committed();
            (*i)->setEnded();
        }
    } catch (const std::exception& e) {
        QPID_LOG(error, "TxnAccept: Failed to commit transaction: " << e.what());
    } catch(...) {
        QPID_LOG(error, "TxnAccept: Failed to commit transaction: (unknown error)");
    }
}

void
TxnAccept::rollback() throw() {}

}}} // namespace tests::storePerftools::asyncPerf
