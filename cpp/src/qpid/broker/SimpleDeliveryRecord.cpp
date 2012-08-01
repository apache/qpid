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
 * \file SimpleDeliveryRecord.cpp
 */

#include "SimpleDeliveryRecord.h"

#include "SimpleConsumer.h"
#include "SimpleMessage.h"
#include "SimpleQueue.h"
#include "SimpleQueuedMessage.h"

namespace qpid  {
namespace broker {

SimpleDeliveryRecord::SimpleDeliveryRecord(boost::shared_ptr<SimpleQueuedMessage> qm,
                                           SimpleConsumer& sc,
                                           bool accepted) :
        m_queuedMessage(qm),
        m_msgConsumer(sc),
        m_accepted(accepted),
        m_ended(accepted)
{}

SimpleDeliveryRecord::~SimpleDeliveryRecord() {}

bool
SimpleDeliveryRecord::accept() {
    if (!m_ended) {
        m_queuedMessage->getQueue()->dequeue(m_queuedMessage);
        m_accepted = true;
        setEnded();
    }
    return isRedundant();
}

bool
SimpleDeliveryRecord::isAccepted() const {
    return m_accepted;
}

bool
SimpleDeliveryRecord::setEnded() {
    m_ended = true;
    m_queuedMessage->payload() = boost::intrusive_ptr<SimpleMessage>(0);
    return isRedundant();
}

bool
SimpleDeliveryRecord::isEnded() const {
    return m_ended;
}

bool
SimpleDeliveryRecord::isRedundant() const {
    return m_ended;
}

void
SimpleDeliveryRecord::dequeue(qpid::broker::SimpleTxnBuffer* tb) {
    m_queuedMessage->getQueue()->dequeue(tb, m_queuedMessage);
}

void
SimpleDeliveryRecord::committed() const {
    m_msgConsumer.commitComplete();
}

boost::shared_ptr<SimpleQueuedMessage>
SimpleDeliveryRecord::getQueuedMessage() const {
    return m_queuedMessage;
}

}} // namespace qpid::broker
