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
#include "BrokerAdapter.h"
#include "Connection.h"
#include "Exception.h"
#include "AMQMethodBody.h"
#include "Exception.h"

namespace qpid {
namespace broker {

using namespace qpid;
using namespace qpid::framing;

typedef std::vector<Queue::shared_ptr>::iterator queue_iterator;

class BrokerAdapter::ServerOps : public AMQP_ServerOperations
{
  public:
    ServerOps(Channel& ch, Connection& c, Broker& b) :
        basicHandler(ch, c, b),
        channelHandler(ch, c, b),
        connectionHandler(ch, c, b),
        exchangeHandler(ch, c, b),
        messageHandler(ch, c, b),
        queueHandler(ch, c, b),
        txHandler(ch, c, b)    
    {}
    
    ChannelHandler* getChannelHandler() { return &channelHandler; }
    ConnectionHandler* getConnectionHandler() { return &connectionHandler; }
    BasicHandler* getBasicHandler() { return &basicHandler; }
    ExchangeHandler* getExchangeHandler() { return &exchangeHandler; }
    QueueHandler* getQueueHandler() { return &queueHandler; }
    TxHandler* getTxHandler() { return &txHandler;  }
    MessageHandler* getMessageHandler() { return &messageHandler;  }
    AccessHandler* getAccessHandler() {
        throw ConnectionException(540, "Access class not implemented");  }
    FileHandler* getFileHandler() {
        throw ConnectionException(540, "File class not implemented");  }
    StreamHandler* getStreamHandler() {
        throw ConnectionException(540, "Stream class not implemented");  }
    DtxHandler* getDtxHandler() {
        throw ConnectionException(540, "Dtx class not implemented");  }
    TunnelHandler* getTunnelHandler() {
        throw ConnectionException(540, "Tunnel class not implemented"); }

  private:
    struct CoreRefs {
        CoreRefs(Channel& ch, Connection& c, Broker& b)
            : channel(ch), connection(c), broker(b) {}

        Channel& channel;
        Connection& connection;
        Broker& broker;
    };
    
    class ConnectionHandlerImpl : private CoreRefs, public ConnectionHandler {
      public:
        ConnectionHandlerImpl(Channel& ch, Connection& c, Broker& b) : CoreRefs(ch, c, b) {}

        void startOk(u_int16_t channel,
                     const qpid::framing::FieldTable& clientProperties,
                     const std::string& mechanism, const std::string& response,
                     const std::string& locale); 
        void secureOk(u_int16_t channel, const std::string& response); 
        void tuneOk(u_int16_t channel, u_int16_t channelMax,
                    u_int32_t frameMax, u_int16_t heartbeat); 
        void open(u_int16_t channel, const std::string& virtualHost,
                  const std::string& capabilities, bool insist); 
        void close(u_int16_t channel, u_int16_t replyCode,
                   const std::string& replyText,
                   u_int16_t classId, u_int16_t methodId); 
        void closeOk(u_int16_t channel); 
    };

    class ChannelHandlerImpl : private CoreRefs, public ChannelHandler{
      public:
        ChannelHandlerImpl(Channel& ch, Connection& c, Broker& b) : CoreRefs(ch, c, b) {}
        void open(u_int16_t channel, const std::string& outOfBand); 
        void flow(u_int16_t channel, bool active); 
        void flowOk(u_int16_t channel, bool active); 
        void ok( u_int16_t channel );
        void ping( u_int16_t channel );
        void pong( u_int16_t channel );
        void resume( u_int16_t channel, const std::string& channelId );
        void close(u_int16_t channel, u_int16_t replyCode, const
                   std::string& replyText, u_int16_t classId, u_int16_t methodId); 
        void closeOk(u_int16_t channel); 
    };
    
    class ExchangeHandlerImpl : private CoreRefs, public ExchangeHandler{
      public:
        ExchangeHandlerImpl(Channel& ch, Connection& c, Broker& b) : CoreRefs(ch, c, b) {}
        void declare(u_int16_t channel, u_int16_t ticket,
                     const std::string& exchange, const std::string& type, 
                     bool passive, bool durable, bool autoDelete,
                     bool internal, bool nowait, 
                     const qpid::framing::FieldTable& arguments); 
        void delete_(u_int16_t channel, u_int16_t ticket,
                     const std::string& exchange, bool ifUnused, bool nowait); 
        void unbind(u_int16_t channel,
                    u_int16_t ticket, const std::string& queue,
                    const std::string& exchange, const std::string& routingKey,
                    const qpid::framing::FieldTable& arguments );
    };

    class QueueHandlerImpl : private CoreRefs, public QueueHandler{
      public:
        QueueHandlerImpl(Channel& ch, Connection& c, Broker& b) : CoreRefs(ch, c, b) {}
        void declare(u_int16_t channel, u_int16_t ticket, const std::string& queue, 
                     bool passive, bool durable, bool exclusive, 
                     bool autoDelete, bool nowait,
                     const qpid::framing::FieldTable& arguments); 
        void bind(u_int16_t channel, u_int16_t ticket, const std::string& queue, 
                  const std::string& exchange, const std::string& routingKey,
                  bool nowait, const qpid::framing::FieldTable& arguments); 
        void unbind(u_int16_t channel,
                    u_int16_t ticket,
                    const std::string& queue,
                    const std::string& exchange,
                    const std::string& routingKey,
                    const qpid::framing::FieldTable& arguments );
        void purge(u_int16_t channel, u_int16_t ticket, const std::string& queue, 
                   bool nowait); 
        void delete_(u_int16_t channel, u_int16_t ticket, const std::string& queue,
                     bool ifUnused, bool ifEmpty, 
                     bool nowait);
    };

    class BasicHandlerImpl : private CoreRefs, public BasicHandler{
      public:
        BasicHandlerImpl(Channel& ch, Connection& c, Broker& b) : CoreRefs(ch, c, b) {}
        void qos(u_int16_t channel, u_int32_t prefetchSize,
                 u_int16_t prefetchCount, bool global); 
        void consume(
            u_int16_t channel, u_int16_t ticket, const std::string& queue,
            const std::string& consumerTag, bool noLocal, bool noAck,
            bool exclusive, bool nowait,
            const qpid::framing::FieldTable& fields); 
        void cancel(u_int16_t channel, const std::string& consumerTag,
                    bool nowait); 
        void publish(u_int16_t channel, u_int16_t ticket,
                     const std::string& exchange, const std::string& routingKey, 
                     bool mandatory, bool immediate); 
        void get(u_int16_t channel, u_int16_t ticket, const std::string& queue,
                 bool noAck); 
        void ack(u_int16_t channel, u_int64_t deliveryTag, bool multiple); 
        void reject(u_int16_t channel, u_int64_t deliveryTag, bool requeue); 
        void recover(u_int16_t channel, bool requeue); 
    };

    class TxHandlerImpl : private CoreRefs, public TxHandler{
      public:
        TxHandlerImpl(Channel& ch, Connection& c, Broker& b) : CoreRefs(ch, c, b) {}
        void select(u_int16_t channel);
        void commit(u_int16_t channel);
        void rollback(u_int16_t channel);
    };

    class MessageHandlerImpl : private CoreRefs, public MessageHandler {
      public:
        MessageHandlerImpl(Channel& ch, Connection& c, Broker& b) : CoreRefs(ch, c, b) {}

        void append( u_int16_t channel,
                     const std::string& reference,
                     const std::string& bytes );

        void cancel( u_int16_t channel,
                     const std::string& destination );

        void checkpoint( u_int16_t channel,
                         const std::string& reference,
                         const std::string& identifier );

        void close( u_int16_t channel,
                    const std::string& reference );

        void consume( u_int16_t channel,
                      u_int16_t ticket,
                      const std::string& queue,
                      const std::string& destination,
                      bool noLocal,
                      bool noAck,
                      bool exclusive,
                      const qpid::framing::FieldTable& filter );

        void empty( u_int16_t channel );

        void get( u_int16_t channel,
                  u_int16_t ticket,
                  const std::string& queue,
                  const std::string& destination,
                  bool noAck );

        void offset( u_int16_t channel,
                     u_int64_t value );

        void ok( u_int16_t channel );

        void open( u_int16_t channel,
                   const std::string& reference );

        void qos( u_int16_t channel,
                  u_int32_t prefetchSize,
                  u_int16_t prefetchCount,
                  bool global );

        void recover( u_int16_t channel,
                      bool requeue );

        void reject( u_int16_t channel,
                     u_int16_t code,
                     const std::string& text );

        void resume( u_int16_t channel,
                     const std::string& reference,
                     const std::string& identifier );

        void transfer( u_int16_t channel,
                       u_int16_t ticket,
                       const std::string& destination,
                       bool redelivered,
                       bool immediate,
                       u_int64_t ttl,
                       u_int8_t priority,
                       u_int64_t timestamp,
                       u_int8_t deliveryMode,
                       u_int64_t expiration,
                       const std::string& exchange,
                       const std::string& routingKey,
                       const std::string& messageId,
                       const std::string& correlationId,
                       const std::string& replyTo,
                       const std::string& contentType,
                       const std::string& contentEncoding,
                       const std::string& userId,
                       const std::string& appId,
                       const std::string& transactionId,
                       const std::string& securityToken,
                       const qpid::framing::FieldTable& applicationHeaders,
                       qpid::framing::Content body );
    };

    BasicHandlerImpl basicHandler;
    ChannelHandlerImpl channelHandler;
    ConnectionHandlerImpl connectionHandler;
    ExchangeHandlerImpl exchangeHandler;
    MessageHandlerImpl messageHandler;
    QueueHandlerImpl queueHandler;
    TxHandlerImpl txHandler;

};

void BrokerAdapter::ServerOps::ConnectionHandlerImpl::startOk(
    u_int16_t /*channel*/, const FieldTable& /*clientProperties*/, const string& /*mechanism*/, 
    const string& /*response*/, const string& /*locale*/){
    connection.client->getConnection().tune(0, 100, connection.framemax, connection.heartbeat);
}
        
void BrokerAdapter::ServerOps::ConnectionHandlerImpl::secureOk(u_int16_t /*channel*/, const string& /*response*/){}
        
void BrokerAdapter::ServerOps::ConnectionHandlerImpl::tuneOk(u_int16_t /*channel*/, u_int16_t /*channelmax*/, u_int32_t framemax, u_int16_t heartbeat){
    connection.framemax = framemax;
    connection.heartbeat = heartbeat;
}
        
void BrokerAdapter::ServerOps::ConnectionHandlerImpl::open(u_int16_t /*channel*/, const string& /*virtualHost*/, const string& /*capabilities*/, bool /*insist*/){
    string knownhosts;
    connection.client->getConnection().openOk(0, knownhosts);
}
        
void BrokerAdapter::ServerOps::ConnectionHandlerImpl::close(
    u_int16_t /*channel*/, u_int16_t /*replyCode*/, const string& /*replyText*/, 
    u_int16_t /*classId*/, u_int16_t /*methodId*/)
{
    connection.client->getConnection().closeOk(0);
    connection.context->close();
} 
        
void BrokerAdapter::ServerOps::ConnectionHandlerImpl::closeOk(u_int16_t /*channel*/){
    connection.context->close();
} 
              
void BrokerAdapter::ServerOps::ChannelHandlerImpl::open(
    u_int16_t channelId, const string& /*outOfBand*/){
    // FIXME aconway 2007-01-17: Assertions on all channel methods,
    // Drop channelId param.
    assertChannelNonZero(channel.getId());
    if (channel.isOpen())
        throw ConnectionException(504, "Channel already open");
    channel.open();
    // FIXME aconway 2007-01-04: provide valid channel Id as per ampq 0-9
    connection.client->getChannel().openOk(channelId, std::string()/* ID */);
} 
        
void BrokerAdapter::ServerOps::ChannelHandlerImpl::flow(u_int16_t /*channel*/, bool /*active*/){}         
void BrokerAdapter::ServerOps::ChannelHandlerImpl::flowOk(u_int16_t /*channel*/, bool /*active*/){} 
        
void BrokerAdapter::ServerOps::ChannelHandlerImpl::close(u_int16_t channel, u_int16_t /*replyCode*/, const string& /*replyText*/, 
                                                         u_int16_t /*classId*/, u_int16_t /*methodId*/){
    connection.closeChannel(channel);
    connection.client->getChannel().closeOk(channel);
} 
        
void BrokerAdapter::ServerOps::ChannelHandlerImpl::closeOk(u_int16_t /*channel*/){} 
              


void BrokerAdapter::ServerOps::ExchangeHandlerImpl::declare(u_int16_t channel, u_int16_t /*ticket*/, const string& exchange, const string& type, 
                                                            bool passive, bool /*durable*/, bool /*autoDelete*/, bool /*internal*/, bool nowait, 
                                                            const FieldTable& /*arguments*/){

    if(passive){
        if(!broker.getExchanges().get(exchange)) {
            throw ChannelException(404, "Exchange not found: " + exchange);
        }
    }else{        
        try{
            std::pair<Exchange::shared_ptr, bool> response = broker.getExchanges().declare(exchange, type);
            if(!response.second && response.first->getType() != type){
                throw ConnectionException(
                    507,
                    "Exchange already declared to be of type "
                    + response.first->getType() + ", requested " + type);
            }
        }catch(UnknownExchangeTypeException& e){
            throw ConnectionException(
                503, "Exchange type not implemented: " + type);
        }
    }
    if(!nowait){
        connection.client->getExchange().declareOk(channel);
    }
}

                
void BrokerAdapter::ServerOps::ExchangeHandlerImpl::unbind(
    u_int16_t /*channel*/,
    u_int16_t /*ticket*/,
    const string& /*queue*/,
    const string& /*exchange*/,
    const string& /*routingKey*/,
    const qpid::framing::FieldTable& /*arguments*/ )
{
    assert(0);            // FIXME aconway 2007-01-04: 0-9 feature
}


                
void BrokerAdapter::ServerOps::ExchangeHandlerImpl::delete_(u_int16_t channel, u_int16_t /*ticket*/, 
                                                            const string& exchange, bool /*ifUnused*/, bool nowait){

    //TODO: implement unused
    broker.getExchanges().destroy(exchange);
    if(!nowait) connection.client->getExchange().deleteOk(channel);
} 

void BrokerAdapter::ServerOps::QueueHandlerImpl::declare(u_int16_t channel, u_int16_t /*ticket*/, const string& name, 
                                                         bool passive, bool durable, bool exclusive, 
                                                         bool autoDelete, bool nowait, const qpid::framing::FieldTable& arguments){
    Queue::shared_ptr queue;
    if (passive && !name.empty()) {
	queue = connection.getQueue(name, channel);
    } else {
	std::pair<Queue::shared_ptr, bool> queue_created =  
            broker.getQueues().declare(name, durable, autoDelete ? connection.settings.timeout : 0, exclusive ? &connection : 0);
	queue = queue_created.first;
	assert(queue);
	if (queue_created.second) { // This is a new queue
	    connection.getChannel(channel).setDefaultQueue(queue);

            //apply settings & create persistent record if required
            queue_created.first->create(arguments);

	    //add default binding:
	    broker.getExchanges().getDefault()->bind(queue, name, 0);
	    if (exclusive) {
		connection.exclusiveQueues.push_back(queue);
	    } else if(autoDelete){
		broker.getCleaner().add(queue);
	    }
	}
    }
    if (exclusive && !queue->isExclusiveOwner(&connection)) {
	throw ChannelException(405, "Cannot grant exclusive access to queue");
    }
    if (!nowait) {
        string queueName = queue->getName();
        connection.client->getQueue().declareOk(channel, queueName, queue->getMessageCount(), queue->getConsumerCount());
    }
} 
        
void BrokerAdapter::ServerOps::QueueHandlerImpl::bind(u_int16_t channel, u_int16_t /*ticket*/, const string& queueName, 
                                                      const string& exchangeName, const string& routingKey, bool nowait, 
                                                      const FieldTable& arguments){

    Queue::shared_ptr queue = connection.getQueue(queueName, channel);
    Exchange::shared_ptr exchange = broker.getExchanges().get(exchangeName);
    if(exchange){
        // kpvdr - cannot use this any longer as routingKey is now const
        //        if(routingKey.empty() && queueName.empty()) routingKey = queue->getName();
        //        exchange->bind(queue, routingKey, &arguments);
        string exchangeRoutingKey = routingKey.empty() && queueName.empty() ? queue->getName() : routingKey;
        exchange->bind(queue, exchangeRoutingKey, &arguments);
        if(!nowait) connection.client->getQueue().bindOk(channel);    
    }else{
        throw ChannelException(
            404, "Bind failed. No such exchange: " + exchangeName);
    }
} 
        
void BrokerAdapter::ServerOps::QueueHandlerImpl::purge(u_int16_t channel, u_int16_t /*ticket*/, const string& queueName, bool nowait){

    Queue::shared_ptr queue = connection.getQueue(queueName, channel);
    int count = queue->purge();
    if(!nowait) connection.client->getQueue().purgeOk(channel, count);
} 
        
void BrokerAdapter::ServerOps::QueueHandlerImpl::delete_(u_int16_t channel, u_int16_t /*ticket*/, const string& queue, 
                                                         bool ifUnused, bool ifEmpty, bool nowait){
    ChannelException error(0, "");
    int count(0);
    Queue::shared_ptr q = connection.getQueue(queue, channel);
    if(ifEmpty && q->getMessageCount() > 0){
        throw ChannelException(406, "Queue not empty.");
    }else if(ifUnused && q->getConsumerCount() > 0){
        throw ChannelException(406, "Queue in use.");
    }else{
        //remove the queue from the list of exclusive queues if necessary
        if(q->isExclusiveOwner(&connection)){
            queue_iterator i = find(connection.exclusiveQueues.begin(), connection.exclusiveQueues.end(), q);
            if(i < connection.exclusiveQueues.end()) connection.exclusiveQueues.erase(i);
        }
        count = q->getMessageCount();
        q->destroy();
        broker.getQueues().destroy(queue);
    }

    if(!nowait) connection.client->getQueue().deleteOk(channel, count);
} 
              
        


void BrokerAdapter::ServerOps::BasicHandlerImpl::qos(u_int16_t channel, u_int32_t prefetchSize, u_int16_t prefetchCount, bool /*global*/){
    //TODO: handle global
    connection.getChannel(channel).setPrefetchSize(prefetchSize);
    connection.getChannel(channel).setPrefetchCount(prefetchCount);
    connection.client->getBasic().qosOk(channel);
} 
        
void BrokerAdapter::ServerOps::BasicHandlerImpl::consume(
    u_int16_t channelId, u_int16_t /*ticket*/, 
    const string& queueName, const string& consumerTag, 
    bool noLocal, bool noAck, bool exclusive, 
    bool nowait, const FieldTable& fields)
{
    
    Queue::shared_ptr queue = connection.getQueue(queueName, channelId);    
    Channel& channel = connection.getChannel(channelId);
    if(!consumerTag.empty() && channel.exists(consumerTag)){
        throw ConnectionException(530, "Consumer tags must be unique");
    }

    try{
        string newTag = consumerTag;
        channel.consume(
            newTag, queue, !noAck, exclusive, noLocal ? &connection : 0, &fields);

        if(!nowait) connection.client->getBasic().consumeOk(channelId, newTag);

        //allow messages to be dispatched if required as there is now a consumer:
        queue->dispatch();
    }catch(ExclusiveAccessException& e){
        if(exclusive) throw ChannelException(403, "Exclusive access cannot be granted");
        else throw ChannelException(403, "Access would violate previously granted exclusivity");
    }

} 
        
void BrokerAdapter::ServerOps::BasicHandlerImpl::cancel(u_int16_t channel, const string& consumerTag, bool nowait){
    connection.getChannel(channel).cancel(consumerTag);

    if(!nowait) connection.client->getBasic().cancelOk(channel, consumerTag);
} 
        
void BrokerAdapter::ServerOps::BasicHandlerImpl::publish(u_int16_t channel, u_int16_t /*ticket*/, 
                                                         const string& exchangeName, const string& routingKey, 
                                                         bool mandatory, bool immediate){

    Exchange::shared_ptr exchange = exchangeName.empty() ? broker.getExchanges().getDefault() : broker.getExchanges().get(exchangeName);
    if(exchange){
        Message* msg = new Message(&connection, exchangeName, routingKey, mandatory, immediate);
        connection.getChannel(channel).handlePublish(msg, exchange);
    }else{
        throw ChannelException(
            404, "Exchange not found '" + exchangeName + "'");
    }
} 
        
void BrokerAdapter::ServerOps::BasicHandlerImpl::get(u_int16_t channelId, u_int16_t /*ticket*/, const string& queueName, bool noAck){
    Queue::shared_ptr queue = connection.getQueue(queueName, channelId);    
    if(!connection.getChannel(channelId).get(queue, !noAck)){
        string clusterId;//not used, part of an imatix hack

        connection.client->getBasic().getEmpty(channelId, clusterId);
    }
} 
        
void BrokerAdapter::ServerOps::BasicHandlerImpl::ack(u_int16_t channel, u_int64_t deliveryTag, bool multiple){
    try{
        connection.getChannel(channel).ack(deliveryTag, multiple);
    }catch(InvalidAckException& e){
        throw ConnectionException(530, "Received ack for unrecognised delivery tag");
    }
} 
        
void BrokerAdapter::ServerOps::BasicHandlerImpl::reject(u_int16_t /*channel*/, u_int64_t /*deliveryTag*/, bool /*requeue*/){} 
        
void BrokerAdapter::ServerOps::BasicHandlerImpl::recover(u_int16_t channel, bool requeue){
    connection.getChannel(channel).recover(requeue);
} 

void BrokerAdapter::ServerOps::TxHandlerImpl::select(u_int16_t channel){
    connection.getChannel(channel).begin();
    connection.client->getTx().selectOk(channel);
}

void BrokerAdapter::ServerOps::TxHandlerImpl::commit(u_int16_t channel){
    connection.getChannel(channel).commit();
    connection.client->getTx().commitOk(channel);
}

void BrokerAdapter::ServerOps::TxHandlerImpl::rollback(u_int16_t channel){
    
    connection.getChannel(channel).rollback();
    connection.client->getTx().rollbackOk(channel);
    connection.getChannel(channel).recover(false);    
}
              
void
BrokerAdapter::ServerOps::QueueHandlerImpl::unbind(
    u_int16_t /*channel*/,
    u_int16_t /*ticket*/,
    const string& /*queue*/,
    const string& /*exchange*/,
    const string& /*routingKey*/,
    const qpid::framing::FieldTable& /*arguments*/ )
{
    assert(0);                // FIXME aconway 2007-01-04: 0-9 feature
}

void
BrokerAdapter::ServerOps::ChannelHandlerImpl::ok( u_int16_t /*channel*/ )
{
    assert(0);                // FIXME aconway 2007-01-04: 0-9 feature
}

void
BrokerAdapter::ServerOps::ChannelHandlerImpl::ping( u_int16_t /*channel*/ )
{
    assert(0);                // FIXME aconway 2007-01-04: 0-9 feature
}

void
BrokerAdapter::ServerOps::ChannelHandlerImpl::pong( u_int16_t /*channel*/ )
{
    assert(0);                // FIXME aconway 2007-01-04: 0-9 feature
}

void
BrokerAdapter::ServerOps::ChannelHandlerImpl::resume(
    u_int16_t /*channel*/,
    const string& /*channelId*/ )
{
    assert(0);                // FIXME aconway 2007-01-04: 0-9 feature
}

// Message class method handlers
void
BrokerAdapter::ServerOps::MessageHandlerImpl::append( u_int16_t /*channel*/,
                                                      const string& /*reference*/,
                                                      const string& /*bytes*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}


void
BrokerAdapter::ServerOps::MessageHandlerImpl::cancel( u_int16_t /*channel*/,
                                                      const string& /*destination*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::ServerOps::MessageHandlerImpl::checkpoint( u_int16_t /*channel*/,
                                                          const string& /*reference*/,
                                                          const string& /*identifier*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::ServerOps::MessageHandlerImpl::close( u_int16_t /*channel*/,
                                                     const string& /*reference*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::ServerOps::MessageHandlerImpl::consume( u_int16_t /*channel*/,
                                                       u_int16_t /*ticket*/,
                                                       const string& /*queue*/,
                                                       const string& /*destination*/,
                                                       bool /*noLocal*/,
                                                       bool /*noAck*/,
                                                       bool /*exclusive*/,
                                                       const qpid::framing::FieldTable& /*filter*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::ServerOps::MessageHandlerImpl::empty( u_int16_t /*channel*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::ServerOps::MessageHandlerImpl::get( u_int16_t /*channel*/,
                                                   u_int16_t /*ticket*/,
                                                   const string& /*queue*/,
                                                   const string& /*destination*/,
                                                   bool /*noAck*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::ServerOps::MessageHandlerImpl::offset( u_int16_t /*channel*/,
                                                      u_int64_t /*value*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::ServerOps::MessageHandlerImpl::ok( u_int16_t /*channel*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::ServerOps::MessageHandlerImpl::open( u_int16_t /*channel*/,
                                                    const string& /*reference*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::ServerOps::MessageHandlerImpl::qos( u_int16_t /*channel*/,
                                                   u_int32_t /*prefetchSize*/,
                                                   u_int16_t /*prefetchCount*/,
                                                   bool /*global*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::ServerOps::MessageHandlerImpl::recover( u_int16_t /*channel*/,
                                                       bool /*requeue*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::ServerOps::MessageHandlerImpl::reject( u_int16_t /*channel*/,
                                                      u_int16_t /*code*/,
                                                      const string& /*text*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::ServerOps::MessageHandlerImpl::resume( u_int16_t /*channel*/,
                                                      const string& /*reference*/,
                                                      const string& /*identifier*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::ServerOps::MessageHandlerImpl::transfer( u_int16_t /*channel*/,
                                                        u_int16_t /*ticket*/,
                                                        const string& /*destination*/,
                                                        bool /*redelivered*/,
                                                        bool /*immediate*/,
                                                        u_int64_t /*ttl*/,
                                                        u_int8_t /*priority*/,
                                                        u_int64_t /*timestamp*/,
                                                        u_int8_t /*deliveryMode*/,
                                                        u_int64_t /*expiration*/,
                                                        const string& /*exchange*/,
                                                        const string& /*routingKey*/,
                                                        const string& /*messageId*/,
                                                        const string& /*correlationId*/,
                                                        const string& /*replyTo*/,
                                                        const string& /*contentType*/,
                                                        const string& /*contentEncoding*/,
                                                        const string& /*userId*/,
                                                        const string& /*appId*/,
                                                        const string& /*transactionId*/,
                                                        const string& /*securityToken*/,
                                                        const qpid::framing::FieldTable& /*applicationHeaders*/,
                                                        qpid::framing::Content /*body*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

BrokerAdapter::BrokerAdapter(
    Channel* ch, Connection& c, Broker& b
) :
    channel(ch),
    connection(c),
    broker(b),
    serverOps(new ServerOps(*ch,c,b))
{
    assert(ch);
}

void BrokerAdapter::handleMethod(
    boost::shared_ptr<qpid::framing::AMQMethodBody> method)
{
    try{
        // FIXME aconway 2007-01-17: invoke to take Channel&?
        method->invoke(*serverOps, channel->getId());
    }catch(ChannelException& e){
        connection.closeChannel(channel->getId());
        connection.client->getChannel().close(
            channel->getId(), e.code, e.toString(),
            method->amqpClassId(), method->amqpMethodId());
    }catch(ConnectionException& e){
        connection.client->getConnection().close(
            0, e.code, e.toString(),
            method->amqpClassId(), method->amqpMethodId());
    }catch(std::exception& e){
        connection.client->getConnection().close(
            0, 541/*internal error*/, e.what(),
            method->amqpClassId(), method->amqpMethodId());
    }
}

void BrokerAdapter::handleHeader(AMQHeaderBody::shared_ptr body) {
    channel->handleHeader(body);
}

void BrokerAdapter::handleContent(AMQContentBody::shared_ptr body) {
    channel->handleContent(body);
}

void BrokerAdapter::handleHeartbeat(AMQHeartbeatBody::shared_ptr) {
    // TODO aconway 2007-01-17: Implement heartbeats.
}



}} // namespace qpid::broker

