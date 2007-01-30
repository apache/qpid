#ifndef _broker_BrokerMessageBase_h
#define _broker_BrokerMessageBase_h

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

#include "AMQContentBody.h"
#include "AMQHeaderBody.h"
#include "Content.h"

#include <string>
#include <boost/shared_ptr.hpp>

namespace qpid {
	
	namespace framing {
		class OutputHandler;
		class ProtocolVersion;
		class BasicHeaderProperties;
	}
	
    namespace broker {

		class MessageStore;
		class ConnectionToken;

        /**
         * Base class for all types of internal broker messages
         * abstracting away the operations
         * TODO; AMS: for the moment this is mostly a placeholder
         */
        class Message{

        public:
            typedef boost::shared_ptr<Message> shared_ptr;

            virtual ~Message() {};
            
            virtual void deliver(qpid::framing::OutputHandler* out, 
                         int channel, 
                         const std::string& consumerTag, 
                         u_int64_t deliveryTag, 
                         u_int32_t framesize,
			 			 qpid::framing::ProtocolVersion* version) = 0;
            virtual void sendGetOk(qpid::framing::OutputHandler* out, 
                           int channel, 
                           u_int32_t messageCount,
                           u_int64_t deliveryTag, 
                           u_int32_t framesize,
			   			   qpid::framing::ProtocolVersion* version) = 0;
            virtual void redeliver() = 0;
            
            virtual bool isComplete() = 0;
            
            virtual u_int64_t contentSize() const = 0;
            virtual qpid::framing::BasicHeaderProperties* getHeaderProperties() = 0;
            virtual bool isPersistent() = 0;
            virtual const std::string& getRoutingKey() const = 0;
            virtual const ConnectionToken* const getPublisher() = 0;
            virtual u_int64_t getPersistenceId() const = 0; // XXXX: Only used in tests?
            virtual const std::string& getExchange() const = 0; // XXXX: Only used in tests?

            virtual void setPersistenceId(u_int64_t /*persistenceId*/) {}; // XXXX: Only used in tests?
            
            virtual void encode(qpid::framing::Buffer& /*buffer*/) {}; // XXXX: Only used in tests?
            virtual void encodeHeader(qpid::framing::Buffer& /*buffer*/) {}; // XXXX: Only used in tests?

            /**
             * @returns the size of the buffer needed to encode this
             * message in its entirety
             * 
             * XXXX: Only used in tests?
             */
            virtual u_int32_t encodedSize() = 0;
            /**
             * @returns the size of the buffer needed to encode the
             * 'header' of this message (not just the header frame,
             * but other meta data e.g.routing key and exchange)
             * 
             * XXXX: Only used in tests?
             */
            virtual u_int32_t encodedHeaderSize() = 0;
            /**
             * @returns the size of the buffer needed to encode the
             * (possibly partial) content held by this message
             */
            virtual u_int32_t encodedContentSize() = 0;
            /**
             * If headers have been received, returns the expected
             * content size else returns 0.
             */
            virtual u_int64_t expectedContentSize() = 0;
            /**
             * Releases the in-memory content data held by this
             * message. Must pass in a store from which the data can
             * be reloaded.
             */
            virtual void releaseContent(MessageStore* /*store*/) {};
            
            // TODO: AMS 29/1/2007 Don't think these are really part of base class
            
            /**
             * Sets the 'content' implementation of this message (the
             * message controls the lifecycle of the content instance
             * it uses).
             */
            virtual void setContent(std::auto_ptr<Content>& /*content*/) {};
            virtual void setHeader(qpid::framing::AMQHeaderBody::shared_ptr /*header*/) {};
            virtual void addContent(qpid::framing::AMQContentBody::shared_ptr /*data*/) {};
        };

    }
}


#endif  /*!_broker_BrokerMessage_h*/
#ifndef _broker_BrokerMessageBase_h
#define _broker_BrokerMessageBase_h

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

#include "AMQContentBody.h"
#include "AMQHeaderBody.h"
#include "Content.h"

#include <string>
#include <boost/shared_ptr.hpp>

namespace qpid {
	
	namespace framing {
		class OutputHandler;
		class ProtocolVersion;
		class BasicHeaderProperties;
	}
	
    namespace broker {

		class MessageStore;
		class ConnectionToken;

        /**
         * Base class for all types of internal broker messages
         * abstracting away the operations
         * TODO; AMS: for the moment this is mostly a placeholder
         */
        class Message{

        public:
            typedef boost::shared_ptr<Message> shared_ptr;

            virtual ~Message() {};
            
            virtual void deliver(qpid::framing::OutputHandler* out, 
                         int channel, 
                         const std::string& consumerTag, 
                         u_int64_t deliveryTag, 
                         u_int32_t framesize,
			 			 qpid::framing::ProtocolVersion* version) = 0;
            virtual void sendGetOk(qpid::framing::OutputHandler* out, 
                           int channel, 
                           u_int32_t messageCount,
                           u_int64_t deliveryTag, 
                           u_int32_t framesize,
			   			   qpid::framing::ProtocolVersion* version) = 0;
            virtual void redeliver() = 0;
            
            virtual bool isComplete() = 0;
            
            virtual u_int64_t contentSize() const = 0;
            virtual qpid::framing::BasicHeaderProperties* getHeaderProperties() = 0;
            virtual bool isPersistent() = 0;
            virtual const std::string& getRoutingKey() const = 0;
            virtual const ConnectionToken* const getPublisher() = 0;
            virtual u_int64_t getPersistenceId() const = 0; // XXXX: Only used in tests?
            virtual const std::string& getExchange() const = 0; // XXXX: Only used in tests?

            virtual void setPersistenceId(u_int64_t /*persistenceId*/) {}; // XXXX: Only used in tests?
            
            virtual void encode(qpid::framing::Buffer& /*buffer*/) {}; // XXXX: Only used in tests?
            virtual void encodeHeader(qpid::framing::Buffer& /*buffer*/) {}; // XXXX: Only used in tests?

            /**
             * @returns the size of the buffer needed to encode this
             * message in its entirety
             * 
             * XXXX: Only used in tests?
             */
            virtual u_int32_t encodedSize() = 0;
            /**
             * @returns the size of the buffer needed to encode the
             * 'header' of this message (not just the header frame,
             * but other meta data e.g.routing key and exchange)
             * 
             * XXXX: Only used in tests?
             */
            virtual u_int32_t encodedHeaderSize() = 0;
            /**
             * @returns the size of the buffer needed to encode the
             * (possibly partial) content held by this message
             */
            virtual u_int32_t encodedContentSize() = 0;
            /**
             * If headers have been received, returns the expected
             * content size else returns 0.
             */
            virtual u_int64_t expectedContentSize() = 0;
            /**
             * Releases the in-memory content data held by this
             * message. Must pass in a store from which the data can
             * be reloaded.
             */
            virtual void releaseContent(MessageStore* /*store*/) {};
            
            // TODO: AMS 29/1/2007 Don't think these are really part of base class
            
            /**
             * Sets the 'content' implementation of this message (the
             * message controls the lifecycle of the content instance
             * it uses).
             */
            virtual void setContent(std::auto_ptr<Content>& /*content*/) {};
            virtual void setHeader(qpid::framing::AMQHeaderBody::shared_ptr /*header*/) {};
            virtual void addContent(qpid::framing::AMQContentBody::shared_ptr /*data*/) {};
        };

    }
}


#endif  /*!_broker_BrokerMessage_h*/
