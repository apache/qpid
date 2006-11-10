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
#include <qpid/broker/AccumulatedAck.h>
#include <qpid/broker/Binding.h>
#include <qpid/broker/Consumer.h>
#include <qpid/broker/DeletingTxOp.h>
#include <qpid/broker/DeliverableMessage.h>
#include <qpid/broker/DeliveryRecord.h>
#include <qpid/broker/Message.h>
#include <qpid/broker/MessageBuilder.h>
#include <qpid/broker/NameGenerator.h>
#include <qpid/broker/Prefetch.h>
#include <qpid/broker/Queue.h>
#include <qpid/broker/TransactionalStore.h>
#include <qpid/broker/TxAck.h>
#include <qpid/broker/TxBuffer.h>
#include <qpid/broker/TxPublish.h>
#include <qpid/sys/Monitor.h>
#include <qpid/framing/OutputHandler.h>
#include <qpid/framing/AMQContentBody.h>
#include <qpid/framing/AMQHeaderBody.h>
#include <qpid/framing/BasicPublishBody.h>

namespace qpid {
    namespace broker {
        /**
         * Maintains state for an AMQP channel. Handles incoming and
         * outgoing messages for that channel.
         */
        class Channel : private MessageBuilder::CompletionHandler{
            class ConsumerImpl : public virtual Consumer{
                Channel* parent;
                string tag;
                Queue::shared_ptr queue;
                ConnectionToken* const connection;
                const bool ackExpected;
                bool blocked;
            public:
                ConsumerImpl(Channel* parent, string& tag, Queue::shared_ptr queue, ConnectionToken* const connection, bool ack);
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
            TransactionalStore* store;
            MessageBuilder messageBuilder;//builder for in-progress message
            Exchange::shared_ptr exchange;//exchange to which any in-progress message was published to

            virtual void complete(Message::shared_ptr& msg);
            void deliver(Message::shared_ptr& msg, string& tag, Queue::shared_ptr& queue, bool ackExpected);            
            void cancel(consumer_iterator consumer);
            bool checkPrefetch(Message::shared_ptr& msg);
        
        public:
            Channel(qpid::framing::OutputHandler* out, int id, u_int32_t framesize);
            ~Channel();
            inline void setDefaultQueue(Queue::shared_ptr queue){ defaultQueue = queue; }
            inline Queue::shared_ptr getDefaultQueue(){ return defaultQueue; }
            inline u_int32_t setPrefetchSize(u_int32_t size){ return prefetchSize = size; }
            inline u_int16_t setPrefetchCount(u_int16_t count){ return prefetchCount = count; }
            bool exists(const string& consumerTag);
            void consume(string& tag, Queue::shared_ptr queue, bool acks, bool exclusive, ConnectionToken* const connection = 0);
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
        };

        struct InvalidAckException{};
    }
}


#endif
