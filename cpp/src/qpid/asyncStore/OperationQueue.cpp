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
 * \file OperationQueue.cpp
 */

#include "qpid/asyncStore/OperationQueue.h"

#include "qpid/broker/AsyncResultHandle.h"
#include "qpid/broker/AsyncResultHandleImpl.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace asyncStore {

OperationQueue::OperationQueue(const boost::shared_ptr<qpid::sys::Poller>& poller) :
        m_opQueue(boost::bind(&OperationQueue::handle, this, _1), poller)
{
    m_opQueue.start();
}

OperationQueue::~OperationQueue() {
    m_opQueue.stop();
}

void
OperationQueue::submit(boost::shared_ptr<const AsyncOperation> op) {
    m_opQueue.push(op);
}

// private
OperationQueue::OpQueue::Batch::const_iterator
OperationQueue::handle(const OperationQueue::OpQueue::Batch& e) {
    try {
        for (OpQueue::Batch::const_iterator i = e.begin(); i != e.end(); ++i) {
// DEBUG: kpvdr
std::cout << "#### OperationQueue::handle(): op=" << (*i)->getOpStr() << std::endl << std::flush;
            (*i)->executeOp(); // Do store work here
        }
    } catch (const std::exception& e) {
        QPID_LOG(error, "qpid::asyncStore::OperationQueue: Exception thrown processing async op: " << e.what());
    } catch (...) {
        QPID_LOG(error, "qpid::asyncStore::OperationQueue: Unknown exception thrown processing async op");
    }
    return e.end();
}

}} // namespace qpid::asyncStore
