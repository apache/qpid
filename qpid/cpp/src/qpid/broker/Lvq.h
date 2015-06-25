#ifndef QPID_BROKER_LVQ_H
#define QPID_BROKER_LVQ_H

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
#include "qpid/broker/Queue.h"

namespace qpid {
namespace broker {
class MessageMap;

/**
 * Subclass of queue that handles last-value-queue semantics in
 * conjunction with the MessageMap class. This requires an existing
 * message to be 'replaced' by a newer message with the same key.
 */
class Lvq : public virtual Queue
{
  public:
    Lvq(const std::string&, std::auto_ptr<MessageMap>, const QueueSettings&, MessageStore* const, management::Manageable*, Broker*);
    void push(Message& msg, bool isRecovery=false);
  private:
    MessageMap& messageMap;
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_LVQ_H*/
