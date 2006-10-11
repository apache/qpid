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
        /**
         * Maintains state for an AMQP channel. Handles incoming and
         * outgoing messages for that channel.
         */
        class Channel{
        private:
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

            struct AckRecord{
                Message::shared_ptr msg;
                Queue::shared_ptr queue;
                string consumerTag;
                u_int64_t deliveryTag;
                bool pull;

                AckRecord(Message::shared_ptr _msg, 
                          Queue::shared_ptr _queue, 
                          const string _consumerTag, 
                          const u_int64_t _deliveryTag) : msg(_msg), 
                                                          queue(_queue), 
                                                          consumerTag(_consumerTag),
                                                          deliveryTag(_deliveryTag),
                                                          pull(false){}

                AckRecord(Message::shared_ptr _msg, 
                          Queue::shared_ptr _queue, 
                          const u_int64_t _deliveryTag) : msg(_msg), 
                                                          queue(_queue), 
                                                          consumerTag(""),
                                                          deliveryTag(_deliveryTag),
                                                          pull(true){}
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

            class CalculatePrefetch{
                u_int32_t size;
                u_int16_t count;
            public:
                CalculatePrefetch();
                void operator()(AckRecord& record);
                u_int32_t getSize();
                u_int16_t getCount();
            };

            const int id;
            qpid::framing::OutputHandler* out;
            u_int64_t deliveryTag;
            Queue::shared_ptr defaultQueue;
            bool transactional;
            std::map<string, ConsumerImpl*> consumers;
            u_int32_t prefetchSize;    
            u_int16_t prefetchCount;    
            u_int32_t outstandingSize;    
            u_int16_t outstandingCount;    
            u_int32_t framesize;
            Message::shared_ptr message;
            NameGenerator tagGenerator;
            std::vector<AckRecord> unacknowledged;
            qpid::concurrent::MonitorImpl deliveryLock;

            void deliver(Message::shared_ptr& msg, string& tag, Queue::shared_ptr& queue, bool ackExpected);            
            void checkMessage(const std::string& text);
            bool checkPrefetch(Message::shared_ptr& msg);
            void cancel(consumer_iterator consumer);

            template<class Operation> Operation processMessage(Operation route){
                if(message->isComplete()){
                    route(message);
                    message.reset();
                }
                return route;
            }

        
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

            /**
             * Handles the initial publish request though a
             * channel. The header and (if applicable) content will be
             * accumulated through calls to handleHeader() and
             * handleContent()
             */
            void handlePublish(Message* msg);

            /**
             * A template method that handles a received header and if
             * there is no content routes it using the functor passed
             * in.
             */
            template<class Operation> Operation handleHeader(qpid::framing::AMQHeaderBody::shared_ptr header, Operation route){
                checkMessage("Invalid message sequence: got header before publish.");
                message->setHeader(header);
                return processMessage(route);
            }

            /**
             * A template method that handles a received content and
             * if this completes the message, routes it using the
             * functor passed in.
             */
            template<class Operation> Operation handleContent(qpid::framing::AMQContentBody::shared_ptr content, Operation route){
                checkMessage("Invalid message sequence: got content before publish.");
                message->addContent(content);
                return processMessage(route);
            }

        };

        struct InvalidAckException{};
    }
}


#endif
