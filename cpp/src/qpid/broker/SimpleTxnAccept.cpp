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
 * \file SimpleTxnAccept.cpp
 */

#include "qpid/broker/SimpleTxnAccept.h"

#include "qpid/broker/SimpleDeliveryRecord.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace broker {

SimpleTxnAccept::SimpleTxnAccept(std::deque<boost::shared_ptr<SimpleDeliveryRecord> >& ops) :
        m_ops(ops)
{}

SimpleTxnAccept::~SimpleTxnAccept() {}

// --- Interface TxnOp ---

bool
SimpleTxnAccept::prepare(SimpleTxnBuffer* tb) throw() {
    try {
        for (std::deque<boost::shared_ptr<SimpleDeliveryRecord> >::iterator i = m_ops.begin(); i != m_ops.end(); ++i) {
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
SimpleTxnAccept::commit() throw() {
    try {
        for (std::deque<boost::shared_ptr<SimpleDeliveryRecord> >::iterator i=m_ops.begin(); i!=m_ops.end(); ++i) {
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
SimpleTxnAccept::rollback() throw() {}

}} // namespace qpid::broker
