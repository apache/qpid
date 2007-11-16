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

#ifndef _Queue_
#define _Queue_

namespace qpid {
namespace client {

    /**
     * A 'handle' used to represent an AMQP queue in the Channel
     * methods. Creating an instance of this class does not cause the
     * queue to be created on the broker. Rather, an instance of this
     * class should be passed to Channel::declareQueue() to ensure
     * that the queue exists or is created.
     * 
     * Queues hold messages and allow clients to consume
     * (see Channel::consume()) or get (see Channel::get()) those messags. A
     * queue receives messages by being bound to one or more Exchange;
     * messages published to that exchange may then be routed to the
     * queue based on the details of the binding and the type of the
     * exchange (see Channel::bind()).
     * 
     * Queues are identified by a name. They can be exclusive (in which
     * case they can only be used in the context of the connection
     * over which they were declared, and are deleted when then
     * connection closes), or they can be shared. Shared queues can be
     * auto deleted when they have no consumers.
     * 
     * We use the term 'temporary queue' to refer to an exclusive
     * queue.
     * 
     * \ingroup clientapi
     */
    class Queue{
	std::string name;
        const bool autodelete;
        const bool exclusive;
        bool durable;

    public:

        /**
         * Creates an unnamed, non-durable, temporary queue. A name
         * will be assigned to this queue instance by a call to
         * Channel::declareQueue().
         */
	Queue();
        /**
         * Creates a shared, non-durable, queue with a given name,
         * that will not be autodeleted.
         * 
         * @param name the name of the queue
         */
	Queue(std::string name);
        /**
         * Creates a non-durable queue with a given name.
         * 
         * @param name the name of the queue
         * 
         * @param temp if true the queue will be a temporary queue, if
         * false it will be shared and not autodeleted.
         */
	Queue(std::string name, bool temp);
        /**
         * This constructor allows the autodelete, exclusive and
         * durable propeties to be explictly set. Note however that if
         * exclusive is true, autodelete has no meaning as exclusive
         * queues are always destroyed when the connection that
         * created them is closed.
         */
	Queue(std::string name, bool autodelete, bool exclusive, bool durable);
	const std::string& getName() const;
	void setName(const std::string&);
        bool isAutoDelete() const;
        bool isExclusive() const;
        bool isDurable() const;
        void setDurable(bool durable);
    };

}
}


#endif
