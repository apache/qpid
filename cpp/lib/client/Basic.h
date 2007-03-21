#ifndef _client_Basic_h
#define _client_Basic_h

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

#include "IncomingMessage.h"
#include "sys/Runnable.h"

namespace qpid {

namespace framing {
class AMQMethodBody;
class FieldTable;
}

namespace client {

class Channel;
class Message;
class Queue;
class Exchange;
class MessageListener;
class ReturnedMessageHandler;

/**
 * The available acknowledgements modes.
 * 
 * \ingroup clientapi
 */
enum AckMode {
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
 * Represents the AMQP Basic class for sending and receiving messages.
 */
class Basic : public sys::Runnable
{
  public:
    Basic(Channel& parent);
    
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


  private:

    struct Consumer{
        MessageListener* listener;
        AckMode ackMode;
        int count;
        uint64_t lastDeliveryTag;
    };

    typedef std::map<std::string, Consumer> ConsumerMap;

    void handle(boost::shared_ptr<framing::AMQMethodBody>);
    void setQos();
    void cancelAll();
    void deliver(Consumer& consumer, Message& msg);
    
    sys::Mutex lock;
    Channel& channel;
    IncomingMessage incoming;
    ConsumerMap consumers;
    ReturnedMessageHandler* returnsHandler;

    // FIXME aconway 2007-02-22: Remove friendship.
  friend class Channel;
};

}} // namespace qpid::client



#endif  /*!_client_Basic_h*/
