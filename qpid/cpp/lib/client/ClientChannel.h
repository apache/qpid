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
#include "sys/types.h"
#include <framing/amqp_framing.h>
#include <ClientExchange.h>
#include <ClientMessage.h>
#include <ClientQueue.h>
#include <ResponseHandler.h>
#include "ChannelAdapter.h"
#include "Thread.h"
#include "Basic.h"

namespace qpid {

namespace framing {
class ChannelCloseBody;
class AMQMethodBody;
}

namespace client {

class Connection;


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
    // TODO aconway 2007-02-22: Remove friendship.
  friend class Basic;
    // FIXME aconway 2007-02-22: friend class Message;
    
    struct UnknownMethod {};
        
    sys::Mutex lock;
    Basic basic;
    Connection* connection;
    sys::Thread basicDispatcher;
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
    
    void sendAndReceive(
        framing::AMQMethodBody*, framing::ClassId, framing::MethodId);

    void sendAndReceiveSync(
        bool sync,
        framing::AMQMethodBody*, framing::ClassId, framing::MethodId);

    template <class BodyType>
    boost::shared_ptr<BodyType> sendAndReceive(framing::AMQMethodBody* body) {
        sendAndReceive(body, BodyType::CLASS_ID, BodyType::METHOD_ID);
        return boost::shared_polymorphic_downcast<BodyType>(
            responses.getResponse());
    }

    template <class BodyType> void sendAndReceiveSync(
        bool sync, framing::AMQMethodBody* body) {
        sendAndReceiveSync(
            sync, body, BodyType::CLASS_ID, BodyType::METHOD_ID);
    }

    void open(framing::ChannelId, Connection&);
    void closeInternal();
    void peerClose(boost::shared_ptr<framing::ChannelCloseBody>);
    
  friend class Connection;
        
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
    Channel(bool transactional = false, uint16_t prefetch = 500);
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
              const std::string& key, const framing::FieldTable& args,
              bool synch = true);

    /**
     * Get a Basic object which provides functions to send and
     * receive messages using the AMQP 0-8 Basic class methods.
     *@see Basic
     */
    Basic& getBasic() { return basic; }

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
};

}}

#endif  /*!_client_ClientChannel_h*/
