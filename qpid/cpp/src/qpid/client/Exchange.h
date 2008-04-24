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
#include <string>

#ifndef _Exchange_
#define _Exchange_

namespace qpid {
namespace client {
    
    /**
     * DEPRECATED
     * 
     * A 'handle' used to represent an AMQP exchange in the Channel
     * methods. Exchanges are the destinations to which messages are
     * published. 
     * 
     * There are different types of exchange (the standard types are
     * available as static constants, see DIRECT_EXCHANGE,
     * TOPIC_EXCHANGE and HEADERS_EXCHANGE).  A Queue can be bound to
     * an exchange using Channel::bind() and messages published to
     * that exchange are then routed to the queue based on the details
     * of the binding and the type of exchange.
     *
     * There are some standard exchange instances that are predeclared
     * on all AMQP brokers. These are defined as static members
     * STANDARD_DIRECT_EXCHANGE, STANDARD_TOPIC_EXCHANGE and
     * STANDARD_HEADERS_EXCHANGE. There is also the 'default' exchange
     * (member DEFAULT_EXCHANGE) which is nameless and of type
     * 'direct' and has every declared queue bound to it by queue
     * name.
     */
    class Exchange{
	const std::string name;
	const std::string type;

    public:
        /**
         * A direct exchange routes messages published with routing
         * key X to any queue bound with key X (i.e. an exact match is
         * used).
         */
	static const std::string DIRECT_EXCHANGE;
        /**
         * A topic exchange treats the key with which a queue is bound
         * as a pattern and routes all messages whose routing keys
         * match that pattern to the bound queue. The routing key for
         * a message must consist of zero or more alpha-numeric words
         * delimited by dots. The pattern is of a similar form, but *
         * can be used to match exactly one word and # can be used to
         * match zero or more words.
         */
	static const std::string TOPIC_EXCHANGE;
        /**
         * The headers exchange routes messages based on whether their
         * headers match the binding arguments specified when
         * binding. (see the AMQP spec for more details).
         */
	static const std::string HEADERS_EXCHANGE;

        /**
         * The 'default' exchange, nameless and of type 'direct', has
         * every declared queue bound to it by name.
         */
	static const Exchange DEFAULT_EXCHANGE;
        /**
         * The standard direct exchange, named amq.direct.
         */
	static const Exchange STANDARD_DIRECT_EXCHANGE;
        /**
         * The standard topic exchange, named amq.topic.
         */
	static const Exchange STANDARD_TOPIC_EXCHANGE;
        /**
         * The standard headers exchange, named amq.header.
         */
	static const Exchange STANDARD_HEADERS_EXCHANGE;

	Exchange(std::string name, std::string type = DIRECT_EXCHANGE);
	const std::string& getName() const;
	const std::string& getType() const;
    };

}
}


#endif
