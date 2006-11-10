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
#ifndef _Message_
#define _Message_

#include <boost/shared_ptr.hpp>
#include <qpid/broker/ConnectionToken.h>
#include <qpid/broker/TxBuffer.h>
#include <qpid/framing/AMQContentBody.h>
#include <qpid/framing/AMQHeaderBody.h>
#include <qpid/framing/BasicHeaderProperties.h>
#include <qpid/framing/BasicPublishBody.h>
#include <qpid/framing/OutputHandler.h>

namespace qpid {
    namespace broker {
 
        /**
         * Represents an AMQP message, i.e. a header body, a list of
         * content bodies and some details about the publication
         * request.
         */
        class Message{
            typedef std::vector<qpid::framing::AMQContentBody::shared_ptr> content_list;
            typedef content_list::iterator content_iterator;

            const ConnectionToken* const publisher;
            string exchange;
            string routingKey;
            const bool mandatory;
            const bool immediate;
            bool redelivered;
            qpid::framing::AMQHeaderBody::shared_ptr header;
            content_list content;
            u_int64_t size;
            u_int64_t persistenceId;

            void sendContent(qpid::framing::OutputHandler* out, 
                             int channel, u_int32_t framesize);

        public:
            typedef boost::shared_ptr<Message> shared_ptr;

            Message(const ConnectionToken* const publisher, 
                    const string& exchange, const string& routingKey, 
                    bool mandatory, bool immediate);
            Message(qpid::framing::Buffer& buffer);
            ~Message();
            void setHeader(qpid::framing::AMQHeaderBody::shared_ptr header);
            void addContent(qpid::framing::AMQContentBody::shared_ptr data);
            bool isComplete();
            const ConnectionToken* const getPublisher();

            void deliver(qpid::framing::OutputHandler* out, 
                         int channel, 
                         const string& consumerTag, 
                         u_int64_t deliveryTag, 
                         u_int32_t framesize);
            void sendGetOk(qpid::framing::OutputHandler* out, 
                           int channel, 
                           u_int32_t messageCount,
                           u_int64_t deliveryTag, 
                           u_int32_t framesize);
            void redeliver();

            qpid::framing::BasicHeaderProperties* getHeaderProperties();
            bool isPersistent();
            const string& getRoutingKey() const { return routingKey; }
            const string& getExchange() const { return exchange; }
            u_int64_t contentSize() const { return size; }
            u_int64_t getPersistenceId() const { return persistenceId; }
            void setPersistenceId(u_int64_t _persistenceId) { persistenceId = _persistenceId; }
            void encode(qpid::framing::Buffer& buffer);
            /**
             * @returns the size of the buffer needed to encode this message
             */
            u_int32_t encodedSize();
            
        };

    }
}


#endif
