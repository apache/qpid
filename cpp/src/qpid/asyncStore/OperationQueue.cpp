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

#include "OperationQueue.h"

#include "qpid/broker/AsyncResultHandle.h"

namespace qpid {
namespace asyncStore {

OperationQueue::OperationQueue(const boost::shared_ptr<qpid::sys::Poller>& poller) :
        m_opQueue(boost::bind(&OperationQueue::handle, this, _1), poller)
{
    m_opQueue.start();
}

OperationQueue::~OperationQueue()
{
    m_opQueue.stop();
}

void
OperationQueue::submit(boost::shared_ptr<const AsyncOperation> op)
{
//std::cout << "--> OperationQueue::submit() op=" << op->getOpStr() << std::endl << std::flush;
    m_opQueue.push(op);
}

// private
OperationQueue::OpQueue::Batch::const_iterator
OperationQueue::handle(const OperationQueue::OpQueue::Batch& e)
{
    try {
        for (OpQueue::Batch::const_iterator i = e.begin(); i != e.end(); ++i) {
//std::cout << "<-- OperationQueue::handle() Op=" << (*i)->getOpStr() << std::endl << std::flush;
            boost::shared_ptr<qpid::broker::BrokerAsyncContext> bc = (*i)->getBrokerContext();
            if (bc) {
                qpid::broker::AsyncResultQueue* const arq = bc->getAsyncResultQueue();
                if (arq) {
                    qpid::broker::AsyncResultHandleImpl* arhi = new qpid::broker::AsyncResultHandleImpl(bc);
                    boost::shared_ptr<qpid::broker::AsyncResultHandle> arh(new qpid::broker::AsyncResultHandle(arhi));
                    arq->submit(arh);
                }
            }
        }
    } catch (const std::exception& e) {
        std::cerr << "qpid::asyncStore::OperationQueue: Exception thrown processing async op: " << e.what() << std::endl;
    } catch (...) {
        std::cerr << "qpid::asyncStore::OperationQueue: Unknown exception thrown processing async op" << std::endl;
    }
    return e.end();
}

}} // namespace qpid::asyncStore
