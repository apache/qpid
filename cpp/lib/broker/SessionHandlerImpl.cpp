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
#include <iostream>
#include <sstream>
#include <SessionHandlerImpl.h>
#include <FanOutExchange.h>
#include <HeadersExchange.h>
#include <TopicExchange.h>
#include "assert.h"

using namespace boost;
using namespace qpid::broker;
using namespace qpid::sys;
using namespace qpid::framing;
using namespace qpid::sys;

SessionHandlerImpl::SessionHandlerImpl(SessionContext* _context, 
                                       QueueRegistry* _queues, 
                                       ExchangeRegistry* _exchanges, 
                                       AutoDelete* _cleaner,
                                       const Settings& _settings) :
    context(_context), 
    queues(_queues), 
    exchanges(_exchanges),
    cleaner(_cleaner),
    settings(_settings),
    basicHandler(new BasicHandlerImpl(this)),
    channelHandler(new ChannelHandlerImpl(this)),
    connectionHandler(new ConnectionHandlerImpl(this)),
    exchangeHandler(new ExchangeHandlerImpl(this)),
    queueHandler(new QueueHandlerImpl(this)),
    txHandler(new TxHandlerImpl(this)),
    framemax(65536), 
    heartbeat(0) 
    {
    
    client =NULL;    
}

SessionHandlerImpl::~SessionHandlerImpl(){

   if (client != NULL)
    	delete client;

}

Channel* SessionHandlerImpl::getChannel(u_int16_t channel){
    channel_iterator i = channels.find(channel);
    if(i == channels.end()){
        std::stringstream out;
        out << "Unknown channel: " << channel;
        throw ConnectionException(504, out.str());
    }
    return i->second;
}

Queue::shared_ptr SessionHandlerImpl::getQueue(const string& name, u_int16_t channel){
    Queue::shared_ptr queue;
    if (name.empty()) {
        queue = getChannel(channel)->getDefaultQueue();
        if (!queue) throw ConnectionException( 530, "Queue must be specified or previously declared" );
    } else {
        queue = queues->find(name);
        if (queue == 0) {
            throw ChannelException( 404, "Queue not found: " + name);
        }
    }
    return queue;
}


Exchange::shared_ptr SessionHandlerImpl::findExchange(const string& name){
    return exchanges->get(name);
}

void SessionHandlerImpl::received(qpid::framing::AMQFrame* frame){
    u_int16_t channel = frame->getChannel();
    AMQBody::shared_ptr body = frame->getBody();
    AMQMethodBody::shared_ptr method;

    switch(body->type())
    {
    case METHOD_BODY:
        method = dynamic_pointer_cast<AMQMethodBody, AMQBody>(body);
        try{
            method->invoke(*this, channel);
        }catch(ChannelException& e){
            channels[channel]->close();
            channels.erase(channel);
            client->getChannel().close(channel, e.code, e.text, method->amqpClassId(), method->amqpMethodId());
        }catch(ConnectionException& e){
            client->getConnection().close(0, e.code, e.text, method->amqpClassId(), method->amqpMethodId());
            context->close();
        }catch(std::exception& e){
            string error(e.what());
            client->getConnection().close(0, 541/*internal error*/, error, method->amqpClassId(), method->amqpMethodId());
            context->close();
        }
	break;

    case HEADER_BODY:
	this->handleHeader(channel, dynamic_pointer_cast<AMQHeaderBody, AMQBody>(body));
	break;

    case CONTENT_BODY:
	this->handleContent(channel, dynamic_pointer_cast<AMQContentBody, AMQBody>(body));
	break;

    case HEARTBEAT_BODY:
        //channel must be 0
	this->handleHeartbeat(dynamic_pointer_cast<AMQHeartbeatBody, AMQBody>(body));
	break;
    }
}

void SessionHandlerImpl::initiated(qpid::framing::ProtocolInitiation* header){

  if (client == NULL)
    {
    	client = new qpid::framing::AMQP_ClientProxy(context, header->getMajor(), header->getMinor());


    	//send connection start
    	FieldTable properties;
    	string mechanisms("PLAIN");
    	string locales("en_US");    // channel, majour, minor
      	client->getConnection().start(0, header->getMajor(), header->getMinor(), properties, mechanisms, locales);
    }
}


void SessionHandlerImpl::idleOut(){

}

void SessionHandlerImpl::idleIn(){

}

void SessionHandlerImpl::closed(){
    try {
        for(channel_iterator i = channels.begin(); i != channels.end(); i = channels.begin()){
            Channel* c = i->second;
            channels.erase(i);
            c->close();
            delete c;
        }
        for(queue_iterator i = exclusiveQueues.begin(); i < exclusiveQueues.end(); i = exclusiveQueues.begin()){
            string name = (*i)->getName();
            queues->destroy(name);
            exclusiveQueues.erase(i);
        }
    } catch(std::exception& e) {
        std::cout << "Caught unhandled exception while closing session: " << e.what() << std::endl;
    }
}

void SessionHandlerImpl::handleHeader(u_int16_t channel, AMQHeaderBody::shared_ptr body){
    getChannel(channel)->handleHeader(body);
}

void SessionHandlerImpl::handleContent(u_int16_t channel, AMQContentBody::shared_ptr body){
    getChannel(channel)->handleContent(body);
}

void SessionHandlerImpl::handleHeartbeat(AMQHeartbeatBody::shared_ptr /*body*/){
    std::cout << "SessionHandlerImpl::handleHeartbeat()" << std::endl;
}
        
void SessionHandlerImpl::ConnectionHandlerImpl::startOk(
    u_int16_t /*channel*/, const FieldTable& /*clientProperties*/, const string& /*mechanism*/, 
    const string& /*response*/, const string& /*locale*/){
    parent->client->getConnection().tune(0, 100, parent->framemax, parent->heartbeat);
}
        
void SessionHandlerImpl::ConnectionHandlerImpl::secureOk(u_int16_t /*channel*/, const string& /*response*/){}
        
void SessionHandlerImpl::ConnectionHandlerImpl::tuneOk(u_int16_t /*channel*/, u_int16_t /*channelmax*/, u_int32_t framemax, u_int16_t heartbeat){
    parent->framemax = framemax;
    parent->heartbeat = heartbeat;
}
        
void SessionHandlerImpl::ConnectionHandlerImpl::open(u_int16_t /*channel*/, const string& /*virtualHost*/, const string& /*capabilities*/, bool /*insist*/){
    string knownhosts;
    parent->client->getConnection().openOk(0, knownhosts);
}
        
void SessionHandlerImpl::ConnectionHandlerImpl::close(
    u_int16_t /*channel*/, u_int16_t /*replyCode*/, const string& /*replyText*/, 
    u_int16_t /*classId*/, u_int16_t /*methodId*/)
{
    parent->client->getConnection().closeOk(0);
    parent->context->close();
} 
        
void SessionHandlerImpl::ConnectionHandlerImpl::closeOk(u_int16_t /*channel*/){
    parent->context->close();
} 
              


void SessionHandlerImpl::ChannelHandlerImpl::open(u_int16_t channel, const string& /*outOfBand*/){

    
    if (parent->channels[channel] == 0) {
        parent->channels[channel] = new Channel(parent->client->getProtocolVersion() , parent->context, channel, parent->framemax, 
                                                parent->queues->getStore(), parent->settings.stagingThreshold);
        parent->client->getChannel().openOk(channel);
    } else {
        std::stringstream out;
        out << "Channel already open: " << channel;
        throw ConnectionException(504, out.str());
    }
} 
        
void SessionHandlerImpl::ChannelHandlerImpl::flow(u_int16_t channel, bool active){
    parent->getChannel(channel)->flow(active);
    parent->client->getChannel().flowOk(channel, active);    
}         
void SessionHandlerImpl::ChannelHandlerImpl::flowOk(u_int16_t /*channel*/, bool /*active*/){} 
        
void SessionHandlerImpl::ChannelHandlerImpl::close(u_int16_t channel, u_int16_t /*replyCode*/, const string& /*replyText*/, 
                                                   u_int16_t /*classId*/, u_int16_t /*methodId*/){
    Channel* c = parent->getChannel(channel);
    if(c){
        parent->channels.erase(channel);
        c->close();
        delete c;
        parent->client->getChannel().closeOk(channel);
    }
} 
        
void SessionHandlerImpl::ChannelHandlerImpl::closeOk(u_int16_t /*channel*/){} 
              


void SessionHandlerImpl::ExchangeHandlerImpl::declare(u_int16_t channel, u_int16_t /*ticket*/, const string& exchange, const string& type, 
                                                      bool passive, bool /*durable*/, bool /*autoDelete*/, bool /*internal*/, bool nowait, 
                                                      const FieldTable& /*arguments*/){

    if(passive){
        if(!parent->exchanges->get(exchange)){
            throw ChannelException(404, "Exchange not found: " + exchange);            
        }
    }else{        
        try{
            std::pair<Exchange::shared_ptr, bool> response = parent->exchanges->declare(exchange, type);
            if(!response.second && response.first->getType() != type){
                throw ConnectionException(530, "Exchange already declared to be of type " 
                                          + response.first->getType() + ", requested " + type);
            }
        }catch(UnknownExchangeTypeException& e){
            throw ConnectionException(503, "Exchange type not implemented: " + type);
        }
    }
    if(!nowait){
        parent->client->getExchange().declareOk(channel);
    }
} 
                
void SessionHandlerImpl::ExchangeHandlerImpl::delete_(u_int16_t channel, u_int16_t /*ticket*/, 
                                                      const string& exchange, bool /*ifUnused*/, bool nowait){

    //TODO: implement unused
    parent->exchanges->destroy(exchange);
    if(!nowait) parent->client->getExchange().deleteOk(channel);
} 

void SessionHandlerImpl::QueueHandlerImpl::declare(u_int16_t channel, u_int16_t /*ticket*/, const string& name, 
                                                   bool passive, bool durable, bool exclusive, 
                                                   bool autoDelete, bool nowait, const qpid::framing::FieldTable& arguments){
    Queue::shared_ptr queue;
    if (passive && !name.empty()) {
	queue = parent->getQueue(name, channel);
    } else {
	std::pair<Queue::shared_ptr, bool> queue_created =  
            parent->queues->declare(name, durable, autoDelete ? parent->settings.timeout : 0, exclusive ? parent : 0);
	queue = queue_created.first;
	assert(queue);
	if (queue_created.second) { // This is a new queue

            //apply settings & create persistent record if required
            queue_created.first->create(arguments);

	    //add default binding:
	    parent->exchanges->getDefault()->bind(queue, name, 0);
	    if (exclusive) {
		parent->exclusiveQueues.push_back(queue);
	    } else if(autoDelete){
		parent->cleaner->add(queue);
	    }
	}
    }
    if (exclusive && !queue->isExclusiveOwner(parent)) {
	throw ChannelException(405, "Cannot grant exclusive access to queue");
    }
    parent->getChannel(channel)->setDefaultQueue(queue);
    if (!nowait) {
        string queueName = queue->getName();
        parent->client->getQueue().declareOk(channel, queueName, queue->getMessageCount(), queue->getConsumerCount());
    }
} 
        
void SessionHandlerImpl::QueueHandlerImpl::bind(u_int16_t channel, u_int16_t /*ticket*/, const string& queueName, 
                                                const string& exchangeName, const string& routingKey, bool nowait, 
                                                const FieldTable& arguments){

    Queue::shared_ptr queue = parent->getQueue(queueName, channel);
    Exchange::shared_ptr exchange = parent->exchanges->get(exchangeName);
    if(exchange){
// kpvdr - cannot use this any longer as routingKey is now const
//        if(routingKey.empty() && queueName.empty()) routingKey = queue->getName();
//        exchange->bind(queue, routingKey, &arguments);
        string exchangeRoutingKey = routingKey.empty() && queueName.empty() ? queue->getName() : routingKey;
        exchange->bind(queue, exchangeRoutingKey, &arguments);
        if(!nowait) parent->client->getQueue().bindOk(channel);    
    }else{
        throw ChannelException(404, "Bind failed. No such exchange: " + exchangeName);
    }
} 
        
void SessionHandlerImpl::QueueHandlerImpl::purge(u_int16_t channel, u_int16_t /*ticket*/, const string& queueName, bool nowait){

    Queue::shared_ptr queue = parent->getQueue(queueName, channel);
    int count = queue->purge();
    if(!nowait) parent->client->getQueue().purgeOk(channel, count);
} 
        
void SessionHandlerImpl::QueueHandlerImpl::delete_(u_int16_t channel, u_int16_t /*ticket*/, const string& queue, 
                                                   bool ifUnused, bool ifEmpty, bool nowait){
    ChannelException error(0, "");
    int count(0);
    Queue::shared_ptr q = parent->getQueue(queue, channel);
    if(ifEmpty && q->getMessageCount() > 0){
        throw ChannelException(406, "Queue not empty.");
    }else if(ifUnused && q->getConsumerCount() > 0){
        throw ChannelException(406, "Queue in use.");
    }else{
        //remove the queue from the list of exclusive queues if necessary
        if(q->isExclusiveOwner(parent)){
            queue_iterator i = find(parent->exclusiveQueues.begin(), parent->exclusiveQueues.end(), q);
            if(i < parent->exclusiveQueues.end()) parent->exclusiveQueues.erase(i);
        }
        count = q->getMessageCount();
        q->destroy();
        parent->queues->destroy(queue);
    }

    if(!nowait) parent->client->getQueue().deleteOk(channel, count);
} 
              
        


void SessionHandlerImpl::BasicHandlerImpl::qos(u_int16_t channel, u_int32_t prefetchSize, u_int16_t prefetchCount, bool /*global*/){
    //TODO: handle global
    parent->getChannel(channel)->setPrefetchSize(prefetchSize);
    parent->getChannel(channel)->setPrefetchCount(prefetchCount);
    parent->client->getBasic().qosOk(channel);
} 
        
void SessionHandlerImpl::BasicHandlerImpl::consume(
    u_int16_t channelId, u_int16_t /*ticket*/, 
    const string& queueName, const string& consumerTag, 
    bool noLocal, bool noAck, bool exclusive, 
    bool nowait, const FieldTable& fields)
{
    
    Queue::shared_ptr queue = parent->getQueue(queueName, channelId);    
    Channel* channel = parent->channels[channelId];
    if(!consumerTag.empty() && channel->exists(consumerTag)){
        throw ConnectionException(530, "Consumer tags must be unique");
    }

    try{
        string newTag = consumerTag;
        channel->consume(
            newTag, queue, !noAck, exclusive, noLocal ? parent : 0, &fields);

        if(!nowait) parent->client->getBasic().consumeOk(channelId, newTag);

        //allow messages to be dispatched if required as there is now a consumer:
        queue->dispatch();
    }catch(ExclusiveAccessException& e){
        if(exclusive) throw ChannelException(403, "Exclusive access cannot be granted");
        else throw ChannelException(403, "Access would violate previously granted exclusivity");
    }

} 
        
void SessionHandlerImpl::BasicHandlerImpl::cancel(u_int16_t channel, const string& consumerTag, bool nowait){
    parent->getChannel(channel)->cancel(consumerTag);

    if(!nowait) parent->client->getBasic().cancelOk(channel, consumerTag);
} 
        
void SessionHandlerImpl::BasicHandlerImpl::publish(u_int16_t channel, u_int16_t /*ticket*/, 
                                                   const string& exchangeName, const string& routingKey, 
                                                   bool mandatory, bool immediate){

    Exchange::shared_ptr exchange = exchangeName.empty() ? parent->exchanges->getDefault() : parent->exchanges->get(exchangeName);
    if(exchange){
        Message* msg = new Message(parent, exchangeName, routingKey, mandatory, immediate);
        parent->getChannel(channel)->handlePublish(msg, exchange);
    }else{
        throw ChannelException(404, "Exchange not found '" + exchangeName + "'");
    }
} 
        
void SessionHandlerImpl::BasicHandlerImpl::get(u_int16_t channelId, u_int16_t /*ticket*/, const string& queueName, bool noAck){
    Queue::shared_ptr queue = parent->getQueue(queueName, channelId);    
    if(!parent->getChannel(channelId)->get(queue, !noAck)){
        string clusterId;//not used, part of an imatix hack

        parent->client->getBasic().getEmpty(channelId, clusterId);
    }
} 
        
void SessionHandlerImpl::BasicHandlerImpl::ack(u_int16_t channel, u_int64_t deliveryTag, bool multiple){
    try{
        parent->getChannel(channel)->ack(deliveryTag, multiple);
    }catch(InvalidAckException& e){
        throw ConnectionException(530, "Received ack for unrecognised delivery tag");
    }
} 
        
void SessionHandlerImpl::BasicHandlerImpl::reject(u_int16_t /*channel*/, u_int64_t /*deliveryTag*/, bool /*requeue*/){} 
        
void SessionHandlerImpl::BasicHandlerImpl::recover(u_int16_t channel, bool requeue){
    parent->getChannel(channel)->recover(requeue);
    parent->client->getBasic().recoverOk(channel);
} 

void SessionHandlerImpl::TxHandlerImpl::select(u_int16_t channel){
    parent->getChannel(channel)->begin();
    parent->client->getTx().selectOk(channel);
}

void SessionHandlerImpl::TxHandlerImpl::commit(u_int16_t channel){
    parent->getChannel(channel)->commit();
    parent->client->getTx().commitOk(channel);
}

void SessionHandlerImpl::TxHandlerImpl::rollback(u_int16_t channel){
    
    parent->getChannel(channel)->rollback();
    parent->client->getTx().rollbackOk(channel);
    parent->getChannel(channel)->recover(false);    
}
              
