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

OperationQueue::OperationQueue(const boost::shared_ptr<qpid::sys::Poller>& poller,
                               qpid::broker::AsyncResultQueue* resultQueue) :
        m_opQueue(boost::bind(&OperationQueue::handle, this, _1), poller),
        m_resultQueue(resultQueue)
{
    m_opQueue.start();
}

OperationQueue::~OperationQueue()
{
    m_opQueue.stop();
}

void
OperationQueue::submit(const AsyncOperation* op)
{
std::cout << "--> OperationQueue::submit() op=" << op->getOpStr() << std::endl << std::flush;
    m_opQueue.push(op);
}

// protected
OperationQueue::OpQueue::Batch::const_iterator
OperationQueue::handle(const OperationQueue::OpQueue::Batch& e)
{
    for (OpQueue::Batch::const_iterator i = e.begin(); i != e.end(); ++i) {
std::cout << "<-- OperationQueue::handle() Op=" << (*i)->getOpStr() << std::endl << std::flush;
        qpid::broker::BrokerAsyncContext* bc = (*i)->m_brokerCtxt;
        qpid::broker::ResultCallback rcb = (*i)->m_resCb;
        if (rcb) {
//            ((*i)->m_resCb)(new qpid::broker::AsyncResult, (*i)->m_brokerCtxt);
//            rcb(new qpid::broker::AsyncResultHandle(new qpid::broker::AsyncResultHandleImpl(bc)));
            if (m_resultQueue) {
                (m_resultQueue->*rcb)(new qpid::broker::AsyncResultHandle(new qpid::broker::AsyncResultHandleImpl(bc)));
            }
        } else {
            delete bc;
        }
        delete (*i);
    }
    return e.end();
}

}} // namespace qpid::asyncStore
