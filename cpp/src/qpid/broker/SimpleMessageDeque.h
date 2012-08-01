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
 * \file SimpleMessageDeque.h
 */

/*
 * This is a copy of qpid::broker::MessageDeque.h, but using the local
 * SimpleQueuedMessage class instead of QueuedMessage.
 */

#ifndef qpid_broker_SimpleMessageDeque_h_
#define qpid_broker_SimpleMessageDeque_h_

#include "SimpleMessages.h"

#include "qpid/sys/Mutex.h"

#include <deque>

namespace qpid  {
namespace broker {

class SimpleMessageDeque : public SimpleMessages
{
public:
    SimpleMessageDeque();
    virtual ~SimpleMessageDeque();
    uint32_t size();
    bool push(boost::shared_ptr<SimpleQueuedMessage>& added);
    bool consume(boost::shared_ptr<SimpleQueuedMessage>& msg);
private:
    std::deque<boost::shared_ptr<SimpleQueuedMessage> > m_messages;
    qpid::sys::Mutex m_msgMutex;

};

}} // namespace qpid::broker

#endif // qpid_broker_SimpleMessageDeque_h_
