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
 * \file TxnPublish.cpp
 */

#include "SimplePersistableMessage.h"
#include "SimplePersistableQueue.h" // debug msg
#include "TxnPublish.h"

#include "QueuedMessage.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

TxnPublish::TxnPublish(boost::intrusive_ptr<SimplePersistableMessage> msg) :
        m_msg(msg)
{
//std::cout << "TTT new TxnPublish" << std::endl << std::flush;
}

TxnPublish::~TxnPublish()
{}

bool
TxnPublish::prepare(qpid::broker::TxnHandle& th) throw()
{
//std::cout << "TTT TxnPublish::prepare: " << m_queues.size() << " queues" << std::endl << std::flush;
    try{
        while (!m_queues.empty()) {
            m_queues.front()->prepareEnqueue(th);
            m_prepared.push_back(m_queues.front());
            m_queues.pop_front();
        }
        return true;
    } catch (const std::exception& e) {
        std::cerr << "Failed to prepare transaction: " << e.what() << std::endl;
    } catch (...) {
        std::cerr << "Failed to prepare transaction: (unknown error)" << std::endl;
    }
    return false;
}

void
TxnPublish::commit() throw()
{
//std::cout << "TTT TxnPublish::commit" << std::endl << std::flush;
    try {
        for (std::list<boost::shared_ptr<QueuedMessage> >::iterator i = m_prepared.begin(); i != m_prepared.end(); ++i) {
            (*i)->commitEnqueue();
        }
    } catch (const std::exception& e) {
        std::cerr << "Failed to commit transaction: " << e.what() << std::endl;
    } catch (...) {
        std::cerr << "Failed to commit transaction: (unknown error)" << std::endl;
    }
}

void
TxnPublish::rollback() throw()
{
//std::cout << "TTT TxnPublish::rollback" << std::endl << std::flush;
    try {
        for (std::list<boost::shared_ptr<QueuedMessage> >::iterator i = m_prepared.begin(); i != m_prepared.end(); ++i) {
            (*i)->abortEnqueue();
        }
    } catch (const std::exception& e) {
        std::cerr << "Failed to rollback transaction: " << e.what() << std::endl;
    } catch (...) {
        std::cerr << "Failed to rollback transaction: (unknown error)" << std::endl;
    }
}

uint64_t
TxnPublish::contentSize()
{
    return m_msg->contentSize();
}

void
TxnPublish::deliverTo(const boost::shared_ptr<SimplePersistableQueue>& queue)
{
//std::cout << "TTT TxnPublish::deliverTo queue=\"" << queue->getName() << "\"" << std::endl << std::flush;
    boost::shared_ptr<QueuedMessage> qm(new QueuedMessage(queue.get(), m_msg));
    m_queues.push_back(qm);
    m_delivered = true;
}

SimplePersistableMessage&
TxnPublish::getMessage()
{
    return *m_msg;
}

}}} // namespace tests::storePerftools::asyncPerf
