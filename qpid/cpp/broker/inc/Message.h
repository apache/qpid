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
#ifndef _Message_
#define _Message_

#include "memory.h"
#include "AMQContentBody.h"
#include "AMQHeaderBody.h"
#include "BasicHeaderProperties.h"
#include "BasicPublishBody.h"
#include "ConnectionToken.h"
#include "OutputHandler.h"

namespace qpid {
    namespace broker {
        class ExchangeRegistry;
 
        /**
         * Represents an AMQP message, i.e. a header body, a list of
         * content bodies and some details about the publication
         * request.
         */
        class Message{
            typedef std::vector<qpid::framing::AMQContentBody::shared_ptr> content_list;
            typedef content_list::iterator content_iterator;

            const ConnectionToken* const publisher;
            const string exchange;
            const string routingKey;
            const bool mandatory;
            const bool immediate;
            bool redelivered;
            qpid::framing::AMQHeaderBody::shared_ptr header;
            content_list content;

            u_int64_t contentSize();

        public:
            typedef std::tr1::shared_ptr<Message> shared_ptr;

            Message(const ConnectionToken* const publisher, 
                    const string& exchange, const string& routingKey, 
                    bool mandatory, bool immediate);
            ~Message();
            void setHeader(qpid::framing::AMQHeaderBody::shared_ptr header);
            void addContent(qpid::framing::AMQContentBody::shared_ptr data);
            bool isComplete();
            const ConnectionToken* const getPublisher();

            void deliver(qpid::framing::OutputHandler* out, int channel, 
                         string& consumerTag, u_int64_t deliveryTag, 
                         u_int32_t framesize);
            void redeliver();

            qpid::framing::BasicHeaderProperties* getHeaderProperties();
            const string& getRoutingKey() const { return routingKey; }
            const string& getExchange() const { return exchange; }
        };
    }
}


#endif
