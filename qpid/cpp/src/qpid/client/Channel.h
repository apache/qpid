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
#include <map>
#include <string>
#include <queue>
#include "sys/types.h"

#ifndef _Channel_
#define _Channel_

#include <qpid/framing/amqp_framing.h>
#include <qpid/client/Connection.h>
#include <qpid/client/Exchange.h>
#include <qpid/client/IncomingMessage.h>
#include <qpid/client/Message.h>
#include <qpid/client/MessageListener.h>
#include <qpid/client/Queue.h>
#include <qpid/client/ResponseHandler.h>
#include <qpid/client/ReturnedMessageHandler.h>

namespace qpid {
namespace client {
    enum ack_modes {NO_ACK=0, AUTO_ACK=1, LAZY_ACK=2, CLIENT_ACK=3};

    class Channel : private virtual qpid::framing::BodyHandler, public virtual qpid::sys::Runnable{
        struct Consumer{
            MessageListener* listener;
            int ackMode;
            int count;
            u_int64_t lastDeliveryTag;
        };
        typedef std::map<string,Consumer*>::iterator consumer_iterator; 

	u_int16_t id;
	Connection* con;
	qpid::sys::Thread dispatcher;
	qpid::framing::OutputHandler* out;
	IncomingMessage* incoming;
	ResponseHandler responses;
	std::queue<IncomingMessage*> messages;//holds returned messages or those delivered for a consume
	IncomingMessage* retrieved;//holds response to basic.get
	qpid::sys::Monitor dispatchMonitor;
	qpid::sys::Monitor retrievalMonitor;
	std::map<std::string, Consumer*> consumers;
	ReturnedMessageHandler* returnsHandler;
	bool closed;

        u_int16_t prefetch;
        const bool transactional;

	void enqueue();
	void retrieve(Message& msg);
	IncomingMessage* dequeue();
	void dispatch();
	void stop();
	void sendAndReceive(qpid::framing::AMQFrame* frame, const qpid::framing::AMQMethodBody& body);            
        void deliver(Consumer* consumer, Message& msg);
        void setQos();
	void cancelAll();

	virtual void handleMethod(qpid::framing::AMQMethodBody::shared_ptr body);
	virtual void handleHeader(qpid::framing::AMQHeaderBody::shared_ptr body);
	virtual void handleContent(qpid::framing::AMQContentBody::shared_ptr body);
	virtual void handleHeartbeat(qpid::framing::AMQHeartbeatBody::shared_ptr body);

    public:
	Channel(bool transactional = false, u_int16_t prefetch = 500);
	~Channel();

	void declareExchange(Exchange& exchange, bool synch = true);
	void deleteExchange(Exchange& exchange, bool synch = true);
	void declareQueue(Queue& queue, bool synch = true);
	void deleteQueue(Queue& queue, bool ifunused = false, bool ifempty = false, bool synch = true);
	void bind(const Exchange& exchange, const Queue& queue, const std::string& key, 
                  const qpid::framing::FieldTable& args, bool synch = true);
        void consume(Queue& queue, std::string& tag, MessageListener* listener, 
                     int ackMode = NO_ACK, bool noLocal = false, bool synch = true);
	void cancel(std::string& tag, bool synch = true);
        bool get(Message& msg, const Queue& queue, int ackMode = NO_ACK);
        void publish(Message& msg, const Exchange& exchange, const std::string& routingKey, 
                     bool mandatory = false, bool immediate = false);

        void commit();
        void rollback();

        void setPrefetch(u_int16_t prefetch);

	/**
	 * Start message dispatching on a new thread
	 */
	void start();
	/**
	 * Do message dispatching on this thread
	 */
	void run();

        void close();

	void setReturnedMessageHandler(ReturnedMessageHandler* handler);

        friend class Connection;
    };

}
}


#endif
