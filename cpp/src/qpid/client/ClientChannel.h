#ifndef _client_ClientChannel_h
#define _client_ClientChannel_h

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
#include <boost/scoped_ptr.hpp>
#include "qpid/framing/amqp_framing.h"
#include "ClientExchange.h"
#include "ClientMessage.h"
#include "ClientQueue.h"
#include "ResponseHandler.h"
#include "qpid/framing/ChannelAdapter.h"
#include "qpid/sys/Thread.h"
#include "AckMode.h"

namespace qpid {

namespace framing {
class ChannelCloseBody;
class AMQMethodBody;
}

namespace client {

class Connection;
class MessageChannel;
class MessageListener;
class ReturnedMessageHandler;

/**
 * Represents an AMQP channel, i.e. loosely a session of work. It
 * is through a channel that most of the AMQP 'methods' are
 * exposed.
 * 
 * \ingroup clientapi
 */
class Channel : public framing::ChannelAdapter
{
  private:
    struct UnknownMethod {};
    typedef shared_ptr<framing::AMQMethodBody> MethodPtr;
        
    sys::Mutex lock;
    boost::scoped_ptr<MessageChannel> messaging;
    Connection* connection;
    sys::Thread dispatcher;
    ResponseHandler responses;

    uint16_t prefetch;
    const bool transactional;
    framing::ProtocolVersion version;

    void handleHeader(framing::AMQHeaderBody::shared_ptr body);
    void handleContent(framing::AMQContentBody::shared_ptr body);
    void handleHeartbeat(framing::AMQHeartbeatBody::shared_ptr body);
    void handleMethodInContext(
        framing::AMQMethodBody::shared_ptr, const framing::MethodContext&);
    void handleChannel(framing::AMQMethodBody::shared_ptr method);
    void handleConnection(framing::AMQMethodBody::shared_ptr method);

    void setQos();

    void protocolInit(
        const std::string& uid, const std::string& pwd,
        const std::string& vhost);
    
    framing::AMQMethodBody::shared_ptr sendAndReceive(
        framing::AMQMethodBody::shared_ptr,
        framing::ClassId, framing::MethodId);

    framing::AMQMethodBody::shared_ptr sendAndReceiveSync(
        bool sync,
        framing::AMQMethodBody::shared_ptr,
        framing::ClassId, framing::MethodId);

    template <class BodyType>
    boost::shared_ptr<BodyType> sendAndReceive(framing::AMQMethodBody::shared_ptr body) {
        return boost::shared_polymorphic_downcast<BodyType>(
            sendAndReceive(body, BodyType::CLASS_ID, BodyType::METHOD_ID));
    }

    template <class BodyType>
    boost::shared_ptr<BodyType> sendAndReceiveSync(
        bool sync, framing::AMQMethodBody::shared_ptr body) {
        return boost::shared_polymorphic_downcast<BodyType>(
            sendAndReceiveSync(
                sync, body, BodyType::CLASS_ID, BodyType::METHOD_ID));
    }

    void open(framing::ChannelId, Connection&);
    void closeInternal();
    void peerClose(boost::shared_ptr<framing::ChannelCloseBody>);
    
    // FIXME aconway 2007-02-23: Get rid of friendships.
  friend class Connection;
  friend class BasicMessageChannel; // for sendAndReceive.
  friend class MessageMessageChannel; // for sendAndReceive.
        
  public:
    enum InteropMode { AMQP_08, AMQP_09 };

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
     *
     * @param messageImpl Alternate messaging implementation class to
     * allow alternate protocol implementations of messaging
     * operations. Takes ownership.
     */
    Channel(
        bool transactional = false, u_int16_t prefetch = 500,
        InteropMode=AMQP_08);
     
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
    void bind(const Exchange& exchange, const Queue& queue,
              const std::string& key,
              const framing::FieldTable& args=framing::FieldTable(),
              bool synch = true);

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
    void setPrefetch(uint16_t prefetch);

    uint16_t getPrefetch() { return prefetch; }

    /**
     * Start message dispatching on a new thread
     */
    void start();

    /**
     * Close the channel with optional error information.
     * Closing a channel that is not open has no effect.
     */
    void close(
        framing::ReplyCode = 200, const std::string& ="OK",
        framing::ClassId = 0, framing::MethodId  = 0);

    /** True if the channel is transactional */
    bool isTransactional() { return transactional; }
    
    /** True if the channel is open */
    bool isOpen() const;

    /** Get the connection associated with this channel */
    Connection& getConnection() { return *connection; }

    /** Return the protocol version */
    framing::ProtocolVersion getVersion() const { return version ; }
    
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
     * should assume for this consumer. @see AckMode
     * 
     * @param noLocal if true, this consumer will not be sent any
     * message published by this connection
     * 
     * @param synch if true this call will block until a response
     * is received from the broker
     */
    void consume(
        Queue& queue, std::string& tag, MessageListener* listener, 
        AckMode ackMode = NO_ACK, bool noLocal = false, bool synch = true,
        const framing::FieldTable* fields = 0);
        
    /**
     * Cancels a subscription previously set up through a call to consume().
     *
     * @param tag the identifier used (or assigned) in the consume
     * request that set up the subscription to be cancelled.
     * 
     * @param synch if true this call will block until a response
     * is received from the broker
     */
    void cancel(const std::string& tag, bool synch = true);
    /**
     * Synchronous pull of a message from a queue.
     * 
     * @param msg a message object that will contain the message
     * headers and content if the call completes.
     * 
     * @param queue the queue to consume from
     * 
     * @param ackMode the acknowledgement mode to use (@see
     * AckMode)
     * 
     * @return true if a message was succcessfully dequeued from
     * the queue, false if the queue was empty.
     */
    bool get(Message& msg, const Queue& queue, AckMode ackMode = NO_ACK);

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
    void publish(const Message& msg, const Exchange& exchange,
                 const std::string& routingKey, 
                 bool mandatory = false, bool immediate = false);

    /**
     * Set a handler for this channel that will process any
     * returned messages
     * 
     * @see publish()
     */
    void setReturnedMessageHandler(ReturnedMessageHandler* handler);

    /**
     * Deliver messages from the broker to the appropriate MessageListener. 
     */
    void run();


};

}}

#endif  /*!_client_ClientChannel_h*/
