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
#ifndef _Channel_
#define _Channel_

#include <algorithm>
#include <functional>
#include <list>
#include <map>
#include <AccumulatedAck.h>
#include <Binding.h>
#include <Consumer.h>
#include <DeletingTxOp.h>
#include <DeliverableMessage.h>
#include <DeliveryRecord.h>
#include <BrokerMessage.h>
#include <MessageBuilder.h>
#include <NameGenerator.h>
#include <Prefetch.h>
#include <BrokerQueue.h>
#include <MessageStore.h>
#include <TxAck.h>
#include <TxBuffer.h>
#include <TxPublish.h>
#include <sys/Monitor.h>
#include <OutputHandler.h>
#include <AMQContentBody.h>
#include <AMQHeaderBody.h>
#include <BasicPublishBody.h>

namespace qpid {
    namespace broker {
        using qpid::framing::string;

        /**
         * Maintains state for an AMQP channel. Handles incoming and
         * outgoing messages for that channel.
         */
        class Channel : private MessageBuilder::CompletionHandler{
            class ConsumerImpl : public virtual Consumer{
                Channel* parent;
                const string tag;
                Queue::shared_ptr queue;
                ConnectionToken* const connection;
                const bool ackExpected;
                bool blocked;
            public:
                ConsumerImpl(Channel* parent, const string& tag, Queue::shared_ptr queue, ConnectionToken* const connection, bool ack);
                virtual bool deliver(Message::shared_ptr& msg);            
                void cancel();
                void requestDispatch();
            };

            typedef std::map<string,ConsumerImpl*>::iterator consumer_iterator; 
            const int id;
            qpid::framing::OutputHandler* out;
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
            qpid::sys::Mutex deliveryLock;
            TxBuffer txBuffer;
            AccumulatedAck accumulatedAck;
            MessageStore* const store;
            MessageBuilder messageBuilder;//builder for in-progress message
            Exchange::shared_ptr exchange;//exchange to which any in-progress message was published to
	    qpid::framing::ProtocolVersion version; // version used for this channel
            bool flowActive;

            virtual void complete(Message::shared_ptr& msg);
            void deliver(Message::shared_ptr& msg, const string& tag, Queue::shared_ptr& queue, bool ackExpected);            
            void cancel(consumer_iterator consumer);
            bool checkPrefetch(Message::shared_ptr& msg);
        
        public:
            Channel(qpid::framing::ProtocolVersion& _version, qpid::framing::OutputHandler* out, int id, u_int32_t framesize, 
                    MessageStore* const _store = 0, u_int64_t stagingThreshold = 0);
            ~Channel();
            inline void setDefaultQueue(Queue::shared_ptr queue){ defaultQueue = queue; }
            inline Queue::shared_ptr getDefaultQueue(){ return defaultQueue; }
            inline u_int32_t setPrefetchSize(u_int32_t size){ return prefetchSize = size; }
            inline u_int16_t setPrefetchCount(u_int16_t count){ return prefetchCount = count; }
            bool exists(const string& consumerTag);
            void consume(string& tag, Queue::shared_ptr queue, bool acks, bool exclusive,
                         ConnectionToken* const connection = 0, const qpid::framing::FieldTable* = 0);
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
            void handleHeader(qpid::framing::AMQHeaderBody::shared_ptr header);
            void handleContent(qpid::framing::AMQContentBody::shared_ptr content);
            void flow(bool active);
        };

        struct InvalidAckException{};
    }
}


#endif
