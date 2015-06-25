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
#include "qpid/broker/IndexedDeque.h"

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
    bool deleted(const QueueCursor&);
    void publish(const Message& added);
    Message* next(QueueCursor&);
    Message* release(const QueueCursor& cursor);
    Message* find(const QueueCursor&);
    Message* find(const framing::SequenceNumber&, QueueCursor*);

    void foreach(Functor);

    void resetCursors();

  private:
    typedef IndexedDeque<Message> Deque;
    Deque messages;
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_MESSAGEDEQUE_H*/
