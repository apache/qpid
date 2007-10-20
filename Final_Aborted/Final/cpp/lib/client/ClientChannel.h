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

#include <framing/amqp_framing.h>
#include <Connection.h>
#include <ClientExchange.h>
#include <IncomingMessage.h>
#include <ClientMessage.h>
#include <MessageListener.h>
#include <ClientQueue.h>
#include <ResponseHandler.h>
#include <ReturnedMessageHandler.h>

namespace qpid {
namespace client {
    /**
     * The available acknowledgements modes
     * 
     * \ingroup clientapi
     */
    enum ack_modes {
        /** No acknowledgement will be sent, broker can
            discard messages as soon as they are delivered
            to a consumer using this mode. **/
        NO_ACK     = 0,  
        /** Each message will be automatically
            acknowledged as soon as it is delivered to the
            application **/  
        AUTO_ACK   = 1,  
        /** Acknowledgements will be sent automatically,
            but not for each message. **/
        LAZY_ACK   = 2,
        /** The application is responsible for explicitly
            acknowledging messages. **/  
        CLIENT_ACK = 3 
    };

    /**
     * Represents an AMQP channel, i.e. loosely a session of work. It
     * is through a channel that most of the AMQP 'methods' are
     * exposed.
     * 
     * \ingroup clientapi
     */
    class Channel : private virtual qpid::framing::BodyHandler, public virtual qpid::sys::Runnable{
        struct Consumer{
            MessageListener* listener;
            int ackMode;
            int count;
            u_int64_t lastDeliveryTag;
        };
        typedef std::map<std::string,Consumer*>::iterator consumer_iterator; 

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
        qpid::framing::ProtocolVersion version;

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
        /**
         * Creates a channel object.
         * 
         * @param transactional if true, the publishing and acknowledgement
         * of messages will be transactional and can be committed or
         * aborted in atomic units (@see commit(), @see rollback())
         * 
         * @param prefetch specifies the number of unacknowledged
         * messages the channel is willing to have sent to it
         * asynchronously
         */
	Channel(bool transactional = false, u_int16_t prefetch = 500);
	~Channel();

        /**
         * Declares an exchange.
         * 
         * In AMQP Exchanges are the destinations to which messages
         * are published. They have Queues bound to them and route
         * messages they receive to those queues. The routing rules
         * depend on the type of the exchange.
         * 
         * @param exchange an Exchange object representing the
         * exchange to declare
         * 
         * @param synch if true this call will block until a response
         * is received from the broker
         */
	void declareExchange(Exchange& exchange, bool synch = true);
        /**
         * Deletes an exchange
         * 
         * @param exchange an Exchange object representing the exchange to delete
         * 
         * @param synch if true this call will block until a response
         * is received from the broker
         */
	void deleteExchange(Exchange& exchange, bool synch = true);
        /**
         * Declares a Queue
         * 
         * @param queue a Queue object representing the queue to declare
         * 
         * @param synch if true this call will block until a response
         * is received from the broker
         */
	void declareQueue(Queue& queue, bool synch = true);
        /**
         * Deletes a Queue
         * 
         * @param queue a Queue object representing the queue to delete
         * 
         * @param synch if true this call will block until a response
         * is received from the broker
         */
	void deleteQueue(Queue& queue, bool ifunused = false, bool ifempty = false, bool synch = true);
        /**
         * Binds a queue to an exchange. The exact semantics of this
         * (in particular how 'routing keys' and 'binding arguments'
         * are used) depends on the type of the exchange.
         * 
         * @param exchange an Exchange object representing the
         * exchange to bind to
         * 
         * @param queue a Queue object representing the queue to be
         * bound
         * 
         * @param key the 'routing key' for the binding
         * 
         * @param args the 'binding arguments' for the binding
         * 
         * @param synch if true this call will block until a response
         * is received from the broker
         */
	void bind(const Exchange& exchange, const Queue& queue, const std::string& key, 
                  const qpid::framing::FieldTable& args, bool synch = true);
        /**
         * Creates a 'consumer' for a queue. Messages in (or arriving
         * at) that queue will be delivered to consumers
         * asynchronously.
         * 
         * @param queue a Queue instance representing the queue to
         * consume from
         * 
         * @param tag an identifier to associate with the consumer
         * that can be used to cancel its subscription (if empty, this
         * will be assigned by the broker)
         * 
         * @param listener a pointer to an instance of an
         * implementation of the MessageListener interface. Messages
         * received from this queue for this consumer will result in
         * invocation of the received() method on the listener, with
         * the message itself passed in.
         * 
         * @param ackMode the mode of acknowledgement that the broker
         * should assume for this consumer. @see ack_modes
         * 
         * @param noLocal if true, this consumer will not be sent any
         * message published by this connection
         * 
         * @param synch if true this call will block until a response
         * is received from the broker
         */
        void consume(
            Queue& queue, std::string& tag, MessageListener* listener, 
            int ackMode = NO_ACK, bool noLocal = false, bool synch = true,
            const qpid::framing::FieldTable* fields = 0);
        
        /**
         * Cancels a subscription previously set up through a call to consume().
         *
         * @param tag the identifier used (or assigned) in the consume
         * request that set up the subscription to be cancelled.
         * 
         * @param synch if true this call will block until a response
         * is received from the broker
         */
	void cancel(std::string& tag, bool synch = true);
        /**
         * Synchronous pull of a message from a queue.
         * 
         * @param msg a message object that will contain the message
         * headers and content if the call completes.
         * 
         * @param queue the queue to consume from
         * 
         * @param ackMode the acknowledgement mode to use (@see
         * ack_modes)
         * 
         * @return true if a message was succcessfully dequeued from
         * the queue, false if the queue was empty.
         */
        bool get(Message& msg, const Queue& queue, int ackMode = NO_ACK);
        /**
         * Publishes (i.e. sends a message to the broker).
         * 
         * @param msg the message to publish
         * 
         * @param exchange the exchange to publish the message to
         * 
         * @param routingKey the routing key to publish with
         * 
         * @param mandatory if true and the exchange to which this
         * publish is directed has no matching bindings, the message
         * will be returned (see setReturnedMessageHandler()).
         * 
         * @param immediate if true and there is no consumer to
         * receive this message on publication, the message will be
         * returned (see setReturnedMessageHandler()).
         */
        void publish(Message& msg, const Exchange& exchange, const std::string& routingKey, 
                     bool mandatory = false, bool immediate = false);

        /**
         * For a transactional channel this will commit all
         * publications and acknowledgements since the last commit (or
         * the channel was opened if there has been no previous
         * commit). This will cause published messages to become
         * available to consumers and acknowledged messages to be
         * consumed and removed from the queues they were dispatched
         * from.
         * 
         * Transactionailty of a channel is specified when the channel
         * object is created (@see Channel()).
         */
        void commit();
        /**
         * For a transactional channel, this will rollback any
         * publications or acknowledgements. It will be as if the
         * ppblished messages were never sent and the acknowledged
         * messages were never consumed.
         */
        void rollback();

        /**
         * Change the prefetch in use.
         */
        void setPrefetch(u_int16_t prefetch);

	/**
	 * Start message dispatching on a new thread
	 */
	void start();
	/**
	 * Do message dispatching on this thread
	 */
	void run();

        /**
         * Closes a channel, stopping any message dispatching.
         */
        void close();

        /**
         * Set a handler for this channel that will process any
         * returned messages
         * 
         * @see publish()
         */
	void setReturnedMessageHandler(ReturnedMessageHandler* handler);

        friend class Connection;
    };

}
}


#endif
