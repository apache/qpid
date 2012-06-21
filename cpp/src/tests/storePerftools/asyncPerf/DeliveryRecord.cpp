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
 * \file DeliveryRecord.cpp
 */

#include "DeliveryRecord.h"

#include "SimpleMessage.h"
#include "SimpleQueue.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

DeliveryRecord::DeliveryRecord(const QueuedMessage& qm,
                               bool accepted) :
        m_queuedMessage(qm),
        m_accepted(accepted),
        m_ended(accepted)
{}

DeliveryRecord::~DeliveryRecord()
{}

bool
DeliveryRecord::accept(qpid::broker::TxnHandle* txn)
{
    if (!m_ended) {
        assert(m_queuedMessage.getQueue());
        m_queuedMessage.getQueue()->dequeue(*txn, m_queuedMessage);
        m_accepted = true;
        setEnded();
    }
    return isRedundant();
}

bool
DeliveryRecord::isAccepted() const
{
    return m_accepted;
}

bool
DeliveryRecord::setEnded()
{
    m_ended = true;
    m_queuedMessage.payload() = boost::intrusive_ptr<SimpleMessage>(0);
    return isRedundant();
}

bool
DeliveryRecord::isEnded() const
{
    return m_ended;
}

bool
DeliveryRecord::isRedundant() const
{
    return m_ended;
}


}}} // namespace tests::storePerftools::asyncPerf
