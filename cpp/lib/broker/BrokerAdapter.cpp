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
#include "MessageHandlerImpl.h"

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

        void startOk(const MethodContext& context,
                     const qpid::framing::FieldTable& clientProperties,
                     const std::string& mechanism, const std::string& response,
                     const std::string& locale); 
        void secureOk(const MethodContext& context, const std::string& response); 
        void tuneOk(const MethodContext& context, u_int16_t channelMax,
                    u_int32_t frameMax, u_int16_t heartbeat); 
        void open(const MethodContext& context, const std::string& virtualHost,
                  const std::string& capabilities, bool insist); 
        void close(const MethodContext& context, u_int16_t replyCode,
                   const std::string& replyText,
                   u_int16_t classId, u_int16_t methodId); 
        void closeOk(const MethodContext& context); 
    };

    class ChannelHandlerImpl : private CoreRefs, public ChannelHandler{
      public:
        ChannelHandlerImpl(Channel& ch, Connection& c, Broker& b) : CoreRefs(ch, c, b) {}
        void open(const MethodContext& context, const std::string& outOfBand); 
        void flow(const MethodContext& context, bool active); 
        void flowOk(const MethodContext& context, bool active); 
        void ok( const MethodContext& context );
        void ping( const MethodContext& context );
        void pong( const MethodContext& context );
        void resume( const MethodContext& context, const std::string& channelId );
        void close(const MethodContext& context, u_int16_t replyCode, const
                   std::string& replyText, u_int16_t classId, u_int16_t methodId); 
        void closeOk(const MethodContext& context); 
    };
    
    class ExchangeHandlerImpl : private CoreRefs, public ExchangeHandler{
      public:
        ExchangeHandlerImpl(Channel& ch, Connection& c, Broker& b) : CoreRefs(ch, c, b) {}
        void declare(const MethodContext& context, u_int16_t ticket,
                     const std::string& exchange, const std::string& type, 
                     bool passive, bool durable, bool autoDelete,
                     bool internal, bool nowait, 
                     const qpid::framing::FieldTable& arguments); 
        void delete_(const MethodContext& context, u_int16_t ticket,
                     const std::string& exchange, bool ifUnused, bool nowait); 
    };

    class QueueHandlerImpl : private CoreRefs, public QueueHandler{
      public:
        QueueHandlerImpl(Channel& ch, Connection& c, Broker& b) : CoreRefs(ch, c, b) {}
        void declare(const MethodContext& context, u_int16_t ticket, const std::string& queue, 
                     bool passive, bool durable, bool exclusive, 
                     bool autoDelete, bool nowait,
                     const qpid::framing::FieldTable& arguments); 
        void bind(const MethodContext& context, u_int16_t ticket, const std::string& queue, 
                  const std::string& exchange, const std::string& routingKey,
                  bool nowait, const qpid::framing::FieldTable& arguments); 
        void unbind(const MethodContext& context,
                    u_int16_t ticket,
                    const std::string& queue,
                    const std::string& exchange,
                    const std::string& routingKey,
                    const qpid::framing::FieldTable& arguments );
        void purge(const MethodContext& context, u_int16_t ticket, const std::string& queue, 
                   bool nowait); 
        void delete_(const MethodContext& context, u_int16_t ticket, const std::string& queue,
                     bool ifUnused, bool ifEmpty, 
                     bool nowait);
    };

    class BasicHandlerImpl : private CoreRefs, public BasicHandler{
      public:
        BasicHandlerImpl(Channel& ch, Connection& c, Broker& b) : CoreRefs(ch, c, b) {}
        void qos(const MethodContext& context, u_int32_t prefetchSize,
                 u_int16_t prefetchCount, bool global); 
        void consume(
            const MethodContext& context, u_int16_t ticket, const std::string& queue,
            const std::string& consumerTag, bool noLocal, bool noAck,
            bool exclusive, bool nowait,
            const qpid::framing::FieldTable& fields); 
        void cancel(const MethodContext& context, const std::string& consumerTag,
                    bool nowait); 
        void publish(const MethodContext& context, u_int16_t ticket,
                     const std::string& exchange, const std::string& routingKey, 
                     bool mandatory, bool immediate); 
        void get(const MethodContext& context, u_int16_t ticket, const std::string& queue,
                 bool noAck); 
        void ack(const MethodContext& context, u_int64_t deliveryTag, bool multiple); 
        void reject(const MethodContext& context, u_int64_t deliveryTag, bool requeue); 
        void recover(const MethodContext& context, bool requeue); 
    };

    class TxHandlerImpl : private CoreRefs, public TxHandler{
      public:
        TxHandlerImpl(Channel& ch, Connection& c, Broker& b) : CoreRefs(ch, c, b) {}
        void select(const MethodContext& context);
        void commit(const MethodContext& context);
        void rollback(const MethodContext& context);
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
    const MethodContext& context , const FieldTable& /*clientProperties*/, const string& /*mechanism*/, 
    const string& /*response*/, const string& /*locale*/){
    connection.client->getConnection().tune(
        context, 100, connection.getFrameMax(), connection.getHeartbeat());
}
        
void BrokerAdapter::ServerOps::ConnectionHandlerImpl::secureOk(const MethodContext&, const string& /*response*/){}
        
void BrokerAdapter::ServerOps::ConnectionHandlerImpl::tuneOk(
    const MethodContext&, u_int16_t /*channelmax*/,
    u_int32_t framemax, u_int16_t heartbeat)
{
    connection.setFrameMax(framemax);
    connection.setHeartbeat(heartbeat);
}
        
void BrokerAdapter::ServerOps::ConnectionHandlerImpl::open(const MethodContext& context, const string& /*virtualHost*/, const string& /*capabilities*/, bool /*insist*/){
    string knownhosts;
    connection.client->getConnection().openOk(context, knownhosts);
}
        
void BrokerAdapter::ServerOps::ConnectionHandlerImpl::close(
    const MethodContext& context, u_int16_t /*replyCode*/, const string& /*replyText*/, 
    u_int16_t /*classId*/, u_int16_t /*methodId*/)
{
    connection.client->getConnection().closeOk(context);
    connection.getOutput().close();
} 
        
void BrokerAdapter::ServerOps::ConnectionHandlerImpl::closeOk(const MethodContext&){
    connection.getOutput().close();
} 
              
void BrokerAdapter::ServerOps::ChannelHandlerImpl::open(
    const MethodContext& context, const string& /*outOfBand*/){
    channel.open();
    // FIXME aconway 2007-01-04: provide valid ID as per ampq 0-9
    connection.client->getChannel().openOk(context, std::string()/* ID */);
} 
        
void BrokerAdapter::ServerOps::ChannelHandlerImpl::flow(const MethodContext&, bool /*active*/){}         
void BrokerAdapter::ServerOps::ChannelHandlerImpl::flowOk(const MethodContext&, bool /*active*/){} 
        
void BrokerAdapter::ServerOps::ChannelHandlerImpl::close(
    const MethodContext& context, u_int16_t /*replyCode*/,
    const string& /*replyText*/,
    u_int16_t /*classId*/, u_int16_t /*methodId*/)
{
    connection.client->getChannel().closeOk(context);
    // FIXME aconway 2007-01-18: Following line will "delete this". Ugly.
    connection.closeChannel(channel.getId()); 
} 
        
void BrokerAdapter::ServerOps::ChannelHandlerImpl::closeOk(const MethodContext&){} 
              


void BrokerAdapter::ServerOps::ExchangeHandlerImpl::declare(const MethodContext& context, u_int16_t /*ticket*/, const string& exchange, const string& type, 
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
        connection.client->getExchange().declareOk(context);
    }
}
                
void BrokerAdapter::ServerOps::ExchangeHandlerImpl::delete_(const MethodContext& context, u_int16_t /*ticket*/, 
                                                            const string& exchange, bool /*ifUnused*/, bool nowait){

    //TODO: implement unused
    broker.getExchanges().destroy(exchange);
    if(!nowait) connection.client->getExchange().deleteOk(context);
} 

void BrokerAdapter::ServerOps::QueueHandlerImpl::declare(const MethodContext& context, u_int16_t /*ticket*/, const string& name, 
                                                         bool passive, bool durable, bool exclusive, 
                                                         bool autoDelete, bool nowait, const qpid::framing::FieldTable& arguments){
    Queue::shared_ptr queue;
    if (passive && !name.empty()) {
	queue = connection.getQueue(name, channel.getId());
    } else {
	std::pair<Queue::shared_ptr, bool> queue_created =  
            broker.getQueues().declare(name, durable, autoDelete ? connection.settings.timeout : 0, exclusive ? &connection : 0);
	queue = queue_created.first;
	assert(queue);
	if (queue_created.second) { // This is a new queue
	    channel.setDefaultQueue(queue);

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
        connection.client->getQueue().declareOk(context, queueName, queue->getMessageCount(), queue->getConsumerCount());
    }
} 
        
void BrokerAdapter::ServerOps::QueueHandlerImpl::bind(const MethodContext& context, u_int16_t /*ticket*/, const string& queueName, 
                                                      const string& exchangeName, const string& routingKey, bool nowait, 
                                                      const FieldTable& arguments){

    Queue::shared_ptr queue = connection.getQueue(queueName, channel.getId());
    Exchange::shared_ptr exchange = broker.getExchanges().get(exchangeName);
    if(exchange){
        string exchangeRoutingKey = routingKey.empty() && queueName.empty() ? queue->getName() : routingKey;
        exchange->bind(queue, exchangeRoutingKey, &arguments);
        if(!nowait) connection.client->getQueue().bindOk(context);    
    }else{
        throw ChannelException(
            404, "Bind failed. No such exchange: " + exchangeName);
    }
}
 
void 
BrokerAdapter::ServerOps::QueueHandlerImpl::unbind(
    const MethodContext& context,
    u_int16_t /*ticket*/,
    const string& queueName,
    const string& exchangeName,
    const string& routingKey,
    const qpid::framing::FieldTable& arguments )
{
    Queue::shared_ptr queue = connection.getQueue(queueName, channel.getId());
    if (!queue.get()) throw ChannelException(404, "Unbind failed. No such exchange: " + exchangeName);

    Exchange::shared_ptr exchange = broker.getExchanges().get(exchangeName);
    if (!exchange.get()) throw ChannelException(404, "Unbind failed. No such exchange: " + exchangeName);

    exchange->unbind(queue, routingKey, &arguments);

    connection.client->getQueue().unbindOk(context);    
}
        
void BrokerAdapter::ServerOps::QueueHandlerImpl::purge(const MethodContext& context, u_int16_t /*ticket*/, const string& queueName, bool nowait){

    Queue::shared_ptr queue = connection.getQueue(queueName, channel.getId());
    int count = queue->purge();
    if(!nowait) connection.client->getQueue().purgeOk(context, count);
} 
        
void BrokerAdapter::ServerOps::QueueHandlerImpl::delete_(const MethodContext& context, u_int16_t /*ticket*/, const string& queue, 
                                                         bool ifUnused, bool ifEmpty, bool nowait){
    ChannelException error(0, "");
    int count(0);
    Queue::shared_ptr q = connection.getQueue(queue, channel.getId());
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

    if(!nowait) connection.client->getQueue().deleteOk(context, count);
} 
              
        


void BrokerAdapter::ServerOps::BasicHandlerImpl::qos(const MethodContext& context, u_int32_t prefetchSize, u_int16_t prefetchCount, bool /*global*/){
    //TODO: handle global
    channel.setPrefetchSize(prefetchSize);
    channel.setPrefetchCount(prefetchCount);
    connection.client->getBasic().qosOk(context);
} 
        
void BrokerAdapter::ServerOps::BasicHandlerImpl::consume(
    const MethodContext& context, u_int16_t /*ticket*/, 
    const string& queueName, const string& consumerTag, 
    bool noLocal, bool noAck, bool exclusive, 
    bool nowait, const FieldTable& fields)
{
    
    Queue::shared_ptr queue = connection.getQueue(queueName, channel.getId());    
    if(!consumerTag.empty() && channel.exists(consumerTag)){
        throw ConnectionException(530, "Consumer tags must be unique");
    }

    try{
        string newTag = consumerTag;
        channel.consume(
            newTag, queue, !noAck, exclusive, noLocal ? &connection : 0, &fields);

        if(!nowait) connection.client->getBasic().consumeOk(context, newTag);

        //allow messages to be dispatched if required as there is now a consumer:
        queue->dispatch();
    }catch(ExclusiveAccessException& e){
        if(exclusive) throw ChannelException(403, "Exclusive access cannot be granted");
        else throw ChannelException(403, "Access would violate previously granted exclusivity");
    }

} 
        
void BrokerAdapter::ServerOps::BasicHandlerImpl::cancel(const MethodContext& context, const string& consumerTag, bool nowait){
    channel.cancel(consumerTag);

    if(!nowait) connection.client->getBasic().cancelOk(context, consumerTag);
} 
        
void BrokerAdapter::ServerOps::BasicHandlerImpl::publish(const MethodContext&, u_int16_t /*ticket*/, 
                                                         const string& exchangeName, const string& routingKey, 
                                                         bool mandatory, bool immediate){

    Exchange::shared_ptr exchange = exchangeName.empty() ? broker.getExchanges().getDefault() : broker.getExchanges().get(exchangeName);
    if(exchange){
        BasicMessage* msg = new BasicMessage(&connection, exchangeName, routingKey, mandatory, immediate);
        channel.handlePublish(msg, exchange);
    }else{
        throw ChannelException(
            404, "Exchange not found '" + exchangeName + "'");
    }
} 
        
void BrokerAdapter::ServerOps::BasicHandlerImpl::get(const MethodContext& context, u_int16_t /*ticket*/, const string& queueName, bool noAck){
    Queue::shared_ptr queue = connection.getQueue(queueName, channel.getId());    
    if(!connection.getChannel(channel.getId()).get(queue, !noAck)){
        string clusterId;//not used, part of an imatix hack

        connection.client->getBasic().getEmpty(context, clusterId);
    }
} 
        
void BrokerAdapter::ServerOps::BasicHandlerImpl::ack(const MethodContext&, u_int64_t deliveryTag, bool multiple){
    try{
        channel.ack(deliveryTag, multiple);
    }catch(InvalidAckException& e){
        throw ConnectionException(530, "Received ack for unrecognised delivery tag");
    }
} 
        
void BrokerAdapter::ServerOps::BasicHandlerImpl::reject(const MethodContext&, u_int64_t /*deliveryTag*/, bool /*requeue*/){} 
        
void BrokerAdapter::ServerOps::BasicHandlerImpl::recover(const MethodContext&, bool requeue){
    channel.recover(requeue);
} 

void BrokerAdapter::ServerOps::TxHandlerImpl::select(const MethodContext& context){
    channel.begin();
    connection.client->getTx().selectOk(context);
}

void BrokerAdapter::ServerOps::TxHandlerImpl::commit(const MethodContext& context){
    channel.commit();
    connection.client->getTx().commitOk(context);
}

void BrokerAdapter::ServerOps::TxHandlerImpl::rollback(const MethodContext& context){
    
    channel.rollback();
    connection.client->getTx().rollbackOk(context);
    channel.recover(false);    
}
              
void
BrokerAdapter::ServerOps::ChannelHandlerImpl::ok( const MethodContext& )
{
    //no specific action required, generic response handling should be sufficient
}

void
BrokerAdapter::ServerOps::ChannelHandlerImpl::ping( const MethodContext& context)
{
    connection.client->getChannel().ok(context);
    connection.client->getChannel().pong(context);
}

void
BrokerAdapter::ServerOps::ChannelHandlerImpl::pong( const MethodContext& context)
{
    connection.client->getChannel().ok(context);
}

void
BrokerAdapter::ServerOps::ChannelHandlerImpl::resume(
    const MethodContext&,
    const string& /*channel*/ )
{
    assert(0);                // FIXME aconway 2007-01-04: 0-9 feature
}

BrokerAdapter::BrokerAdapter(
    std::auto_ptr<Channel> ch, Connection& c, Broker& b
) :
    channel(ch),
    connection(c),
    broker(b),
    serverOps(new ServerOps(*channel,c,b))
{
    init(channel->getId(), c.getOutput(), channel->getVersion());
}

void BrokerAdapter::handleMethodInContext(
    boost::shared_ptr<qpid::framing::AMQMethodBody> method,
    const MethodContext& context
)
{
    try{
        method->invoke(*serverOps, context);
    }catch(ChannelException& e){
        connection.client->getChannel().close(
            context, e.code, e.toString(),
            method->amqpClassId(), method->amqpMethodId());
        connection.closeChannel(getId());
    }catch(ConnectionException& e){
        connection.client->getConnection().close(
            context, e.code, e.toString(),
            method->amqpClassId(), method->amqpMethodId());
    }catch(std::exception& e){
        connection.client->getConnection().close(
            context, 541/*internal error*/, e.what(),
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


bool BrokerAdapter::isOpen() const {
    return channel->isOpen();
}

}} // namespace qpid::broker

