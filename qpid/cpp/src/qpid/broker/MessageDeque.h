#ifndef QPID_BROKER_MESSAGEDEQUE_H
#define QPID_BROKER_MESSAGEDEQUE_H

/*
 *
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
 *
 */
#include "qpid/broker/Messages.h"
#include "qpid/broker/QueuedMessage.h"
#include <deque>

namespace qpid {
namespace broker {

/**
 * Provides the standard FIFO queue behaviour.
 */
class MessageDeque : public Messages
{
  public:
    MessageDeque();
    size_t size();
    bool deleted(const QueuedMessage&);
    void release(const QueuedMessage&);
    bool acquire(const framing::SequenceNumber&, QueuedMessage&);
    bool find(const framing::SequenceNumber&, QueuedMessage&);
    bool browse(const framing::SequenceNumber&, QueuedMessage&, bool);
    bool consume(QueuedMessage&);
    bool push(const QueuedMessage& added, QueuedMessage& removed);

    void foreach(Functor);
    void removeIf(Predicate);

  private:
    typedef std::deque<QueuedMessage> Deque;
    Deque messages;
    size_t available;
    size_t head;

    size_t index(const framing::SequenceNumber&);
    void clean();
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_MESSAGEDEQUE_H*/
