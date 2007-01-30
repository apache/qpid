#ifndef _broker_BrokerMessageMessage_h
#define _broker_BrokerMessageMessage_h

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

#include "BrokerMessageBase.h"

namespace qpid {
    namespace broker {
        class MessageMessage: public Message{

        public:
            ~MessageMessage();
            
            void deliver(qpid::framing::OutputHandler* out, 
                         int channel, 
                         const std::string& consumerTag, 
                         u_int64_t deliveryTag, 
                         u_int32_t framesize,
			 			 qpid::framing::ProtocolVersion* version);
            void sendGetOk(qpid::framing::OutputHandler* out, 
                           int channel, 
                           u_int32_t messageCount,
                           u_int64_t deliveryTag, 
                           u_int32_t framesize,
			   			   qpid::framing::ProtocolVersion* version);
            void redeliver();
            void setHeader(qpid::framing::AMQHeaderBody::shared_ptr header);
            void addContent(qpid::framing::AMQContentBody::shared_ptr data);
            bool isComplete();
            void setContent(std::auto_ptr<Content>& content);
            
            u_int64_t contentSize() const;
            qpid::framing::BasicHeaderProperties* getHeaderProperties();
            bool isPersistent();
            const std::string& getRoutingKey() const;
            const ConnectionToken* const getPublisher();
            
            u_int32_t encodedContentSize();
            u_int64_t expectedContentSize();
            void releaseContent(MessageStore* store);
        };

    }
}


#endif  /*!_broker_BrokerMessage_h*/
#ifndef _broker_BrokerMessageMessage_h
#define _broker_BrokerMessageMessage_h

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

#include "BrokerMessageBase.h"

namespace qpid {
    namespace broker {
        class MessageMessage: public Message{

        public:
            ~MessageMessage();
            
            void deliver(qpid::framing::OutputHandler* out, 
                         int channel, 
                         const std::string& consumerTag, 
                         u_int64_t deliveryTag, 
                         u_int32_t framesize,
			 			 qpid::framing::ProtocolVersion* version);
            void sendGetOk(qpid::framing::OutputHandler* out, 
                           int channel, 
                           u_int32_t messageCount,
                           u_int64_t deliveryTag, 
                           u_int32_t framesize,
			   			   qpid::framing::ProtocolVersion* version);
            void redeliver();
            void setHeader(qpid::framing::AMQHeaderBody::shared_ptr header);
            void addContent(qpid::framing::AMQContentBody::shared_ptr data);
            bool isComplete();
            void setContent(std::auto_ptr<Content>& content);
            
            u_int64_t contentSize() const;
            qpid::framing::BasicHeaderProperties* getHeaderProperties();
            bool isPersistent();
            const std::string& getRoutingKey() const;
            const ConnectionToken* const getPublisher();
            
            u_int32_t encodedContentSize();
            u_int64_t expectedContentSize();
            void releaseContent(MessageStore* store);
        };

    }
}


#endif  /*!_broker_BrokerMessage_h*/
