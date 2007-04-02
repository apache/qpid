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
#include "RecoveryManagerImpl.h"

#include "BrokerMessage.h"
#include "BrokerMessageMessage.h"
#include "BrokerQueue.h"

using namespace qpid;
using namespace qpid::broker;
using boost::dynamic_pointer_cast;


static const uint8_t BASIC = 1;
static const uint8_t MESSAGE = 2;

RecoveryManagerImpl::RecoveryManagerImpl(QueueRegistry& _queues, ExchangeRegistry& _exchanges, uint64_t _stagingThreshold) 
    : queues(_queues), exchanges(_exchanges), stagingThreshold(_stagingThreshold) {}

RecoveryManagerImpl::~RecoveryManagerImpl() {}

class RecoverableMessageImpl : public RecoverableMessage
{
    Message::shared_ptr msg;
    const uint64_t stagingThreshold;
public:
    RecoverableMessageImpl(Message::shared_ptr& _msg, uint64_t _stagingThreshold) 
        : msg(_msg), stagingThreshold(_stagingThreshold) {}
    ~RecoverableMessageImpl() {};
    bool loadContent(uint64_t available);
    void decodeContent(framing::Buffer& buffer);
    void recover(Queue::shared_ptr queue);
};

class RecoverableQueueImpl : public RecoverableQueue
{
    Queue::shared_ptr queue;
public:
    RecoverableQueueImpl(Queue::shared_ptr& _queue) : queue(_queue) {}
    ~RecoverableQueueImpl() {};
    void recover(RecoverableMessage::shared_ptr msg);
};

void RecoveryManagerImpl::recoverExchange(framing::Buffer&)
{
    //TODO
}

RecoverableQueue::shared_ptr RecoveryManagerImpl::recoverQueue(framing::Buffer& buffer)
{
    Queue::shared_ptr queue = Queue::decode(queues, buffer);
    try {
        Exchange::shared_ptr exchange = exchanges.getDefault();
        if (exchange) {
            exchange->bind(queue, queue->getName(), 0);
        }
    } catch (ChannelException& e) {
        //assume no default exchange has been declared
    }
    return RecoverableQueue::shared_ptr(new RecoverableQueueImpl(queue));
}

RecoverableMessage::shared_ptr RecoveryManagerImpl::recoverMessage(framing::Buffer& buffer)
{
    buffer.record();
    //peek at type:
    Message::shared_ptr message(decodeMessageType(buffer) == MESSAGE ?  
                                ((Message*) new MessageMessage()) : 
                                ((Message*) new BasicMessage()));
    buffer.restore();
    message->decodeHeader(buffer);
    return RecoverableMessage::shared_ptr(new RecoverableMessageImpl(message, stagingThreshold));    
}

void RecoveryManagerImpl::recoveryComplete()
{
    //TODO (finalise binding setup etc)
}

uint8_t RecoveryManagerImpl::decodeMessageType(framing::Buffer& buffer)
{
    return buffer.getOctet();
}

void RecoveryManagerImpl::encodeMessageType(const Message& msg, framing::Buffer& buffer)
{
    buffer.putOctet(dynamic_cast<const MessageMessage*>(&msg) ? MESSAGE : BASIC);
}

uint32_t RecoveryManagerImpl::encodedMessageTypeSize()
{
    return 1;
}

bool RecoverableMessageImpl::loadContent(uint64_t available)
{
    return !stagingThreshold || available < stagingThreshold;
}

void RecoverableMessageImpl::decodeContent(framing::Buffer& buffer)
{
    msg->decodeContent(buffer);
}

void RecoverableMessageImpl::recover(Queue::shared_ptr queue)
{
    queue->recover(msg);
}

void RecoverableQueueImpl::recover(RecoverableMessage::shared_ptr msg)
{
    dynamic_pointer_cast<RecoverableMessageImpl>(msg)->recover(queue);
}
