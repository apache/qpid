#ifndef _broker_BrokerChannel_h
#define _broker_BrokerChannel_h

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

#include <list>
#include <map>

#include <boost/scoped_ptr.hpp>
#include <boost/shared_ptr.hpp>

#include <AccumulatedAck.h>
#include <Consumer.h>
#include <DeliveryRecord.h>
#include <MessageBuilder.h>
#include <NameGenerator.h>
#include <Prefetch.h>
#include <TxBuffer.h>
#include "framing/ChannelAdapter.h"
#include "CompletionHandler.h"

namespace qpid {
namespace broker {

class ConnectionToken;
class Connection;
class Queue;
class BrokerAdapter;

using framing::string;

/**
 * Maintains state for an AMQP channel. Handles incoming and
 * outgoing messages for that channel.
 */
class Channel : public framing::ChannelAdapter,
                public CompletionHandler
{
    class ConsumerImpl : public virtual Consumer
    {
        Channel* parent;
        const string tag;
        Queue::shared_ptr queue;
        ConnectionToken* const connection;
        const bool ackExpected;
        bool blocked;
      public:
        ConsumerImpl(Channel* parent, const string& tag,
                     Queue::shared_ptr queue,
                     ConnectionToken* const connection, bool ack);
        virtual bool deliver(Message::shared_ptr& msg);            
        void cancel();
        void requestDispatch();
    };

    typedef std::map<string,ConsumerImpl*>::iterator consumer_iterator;

    Connection& connection;
    u_int16_t id;
    u_int64_t currentDeliveryTag;
    Queue::shared_ptr defaultQueue;
    bool transactional;
    std::map<string, ConsumerImpl*> consumers;
    u_int32_t prefetchSize;    
    u_int16_t prefetchCount;    
    Prefetch outstanding;
    u_int32_t framesize;
    NameGenerator tagGenerator;
    std::list<DeliveryRecord> unacked;
    sys::Mutex deliveryLock;
    TxBuffer txBuffer;
    AccumulatedAck accumulatedAck;
    MessageStore* const store;
    MessageBuilder messageBuilder;//builder for in-progress message
    Exchange::shared_ptr exchange;//exchange to which any in-progress message was published to
    bool opened;

    boost::scoped_ptr<BrokerAdapter> adapter;

	// completion handler for MessageBuilder
    void complete(Message::shared_ptr msg);
    
    void deliver(Message::shared_ptr& msg, const string& tag, Queue::shared_ptr& queue, bool ackExpected);            
    void cancel(consumer_iterator consumer);
    bool checkPrefetch(Message::shared_ptr& msg);
        
  public:
    Channel(Connection& channel,
            framing::ChannelId id,
            u_int32_t framesize, 
            MessageStore* const _store = 0,
            u_int64_t stagingThreshold = 0);
    
    ~Channel();

	// For ChannelAdapter
    bool isOpen() const { return opened; }
    
    void open() { opened = true; }
    void setDefaultQueue(Queue::shared_ptr queue){ defaultQueue = queue; }
    Queue::shared_ptr getDefaultQueue() const { return defaultQueue; }
    u_int32_t setPrefetchSize(u_int32_t size){ return prefetchSize = size; }
    u_int16_t setPrefetchCount(u_int16_t n){ return prefetchCount = n; }

    bool exists(const string& consumerTag);
    void consume(string& tag, Queue::shared_ptr queue, bool acks,
                 bool exclusive, ConnectionToken* const connection = 0,
                 const framing::FieldTable* = 0);
    void cancel(const string& tag);
    bool get(Queue::shared_ptr queue, bool ackExpected);
    void begin();
    void close();
    void commit();
    void rollback();
    void ack(u_int64_t deliveryTag, bool multiple);
    void recover(bool requeue);
    void deliver(Message::shared_ptr& msg, const string& consumerTag, u_int64_t deliveryTag);            
    void handlePublish(Message* msg, Exchange::shared_ptr exchange);
    void handleHeader(boost::shared_ptr<framing::AMQHeaderBody>);
    void handleContent(boost::shared_ptr<framing::AMQContentBody>);
    void handleHeartbeat(boost::shared_ptr<framing::AMQHeartbeatBody>);
    
    void handleInlineTransfer(Message::shared_ptr msg, Exchange::shared_ptr& exchange);
    
    // For ChannelAdapter
    void handleMethodInContext(
        boost::shared_ptr<framing::AMQMethodBody> method,
        const framing::MethodContext& context);
    
};

struct InvalidAckException{};

}} // namespace broker


#endif  /*!_broker_BrokerChannel_h*/
