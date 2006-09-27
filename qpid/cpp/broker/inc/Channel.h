/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#ifndef _Channel_
#define _Channel_

#include <algorithm>
#include <map>
#include "AMQContentBody.h"
#include "AMQHeaderBody.h"
#include "BasicPublishBody.h"
#include "Binding.h"
#include "Consumer.h"
#include "Message.h"
#include "MonitorImpl.h"
#include "NameGenerator.h"
#include "OutputHandler.h"
#include "Queue.h"

namespace qpid {
    namespace broker {
        class Channel{
        private:
            class ConsumerImpl : public virtual Consumer{
                Channel* parent;
                string tag;
                Queue::shared_ptr queue;
                ConnectionToken* const connection;
                const bool ackExpected;
            public:
                ConsumerImpl(Channel* parent, string& tag, Queue::shared_ptr queue, ConnectionToken* const connection, bool ack);
                virtual bool deliver(Message::shared_ptr& msg);            
                void cancel();
            };

            typedef std::map<string,ConsumerImpl*>::iterator consumer_iterator; 

            struct AckRecord{
                Message::shared_ptr msg;
                Queue::shared_ptr queue;
                string consumerTag;
                u_int64_t deliveryTag;

                AckRecord(Message::shared_ptr _msg, Queue::shared_ptr _queue, 
                          string _consumerTag, u_int64_t _deliveryTag) : msg(_msg), 
                                                                        queue(_queue), 
                                                                        consumerTag(_consumerTag),
                                                                        deliveryTag(_deliveryTag){}
            };

            typedef std::vector<AckRecord>::iterator ack_iterator; 

            class MatchAck{
                const u_int64_t tag;
            public:
                MatchAck(u_int64_t tag);
                bool operator()(AckRecord& record) const;
            };

            class Requeue{
            public:
                void operator()(AckRecord& record) const;
            };

            class Redeliver{
                Channel* const channel;
            public:
                Redeliver(Channel* const channel);
                void operator()(AckRecord& record) const;
            };

            const int id;
            qpid::framing::OutputHandler* out;
            u_int64_t deliveryTag;
            Queue::shared_ptr defaultQueue;
            bool transactional;
            std::map<string, ConsumerImpl*> consumers;
            u_int32_t prefetchSize;    
            u_int16_t prefetchCount;    
            u_int32_t framesize;
            Message::shared_ptr message;
            NameGenerator tagGenerator;
            std::vector<AckRecord> unacknowledged;
            qpid::concurrent::MonitorImpl deliveryLock;

            void deliver(Message::shared_ptr& msg, string& tag, Queue::shared_ptr& queue, bool ackExpected);            
            void publish(ExchangeRegistry* exchanges);
        
        public:
            Channel(qpid::framing::OutputHandler* out, int id, u_int32_t framesize);
            ~Channel();
            inline void setDefaultQueue(Queue::shared_ptr queue){ defaultQueue = queue; }
            inline Queue::shared_ptr getDefaultQueue(){ return defaultQueue; }
            inline u_int32_t setPrefetchSize(u_int32_t size){ prefetchSize = size; }
            inline u_int16_t setPrefetchCount(u_int16_t count){ prefetchCount = count; }
            void handlePublish(Message* msg);
            void handleHeader(qpid::framing::AMQHeaderBody::shared_ptr header, ExchangeRegistry* exchanges);
            void handleContent(qpid::framing::AMQContentBody::shared_ptr content, ExchangeRegistry* exchanges);
            bool exists(string& consumerTag);
            void consume(string& tag, Queue::shared_ptr queue, bool acks, bool exclusive, ConnectionToken* const connection = 0);
            void cancel(string& tag);
            void begin();
            void close();
            void commit();
            void rollback();
            void ack(u_int64_t deliveryTag, bool multiple);
            void recover(bool requeue);
        };
    }
}


#endif
