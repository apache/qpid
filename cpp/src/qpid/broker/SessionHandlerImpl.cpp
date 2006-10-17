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
#include <iostream>
#include "qpid/broker/SessionHandlerImpl.h"
#include "qpid/broker/FanOutExchange.h"
#include "qpid/broker/HeadersExchange.h"
#include "qpid/broker/Router.h"
#include "qpid/broker/TopicExchange.h"
#include "assert.h"

using namespace std::tr1;
using namespace qpid::broker;
using namespace qpid::io;
using namespace qpid::framing;
using namespace qpid::concurrent;

SessionHandlerImpl::SessionHandlerImpl(SessionContext* _context, 
                                       QueueRegistry* _queues, 
                                       ExchangeRegistry* _exchanges, 
                                       AutoDelete* _cleaner,
                                       const u_int32_t _timeout) :
    context(_context), 
    client(context),
    queues(_queues), 
    exchanges(_exchanges),
    cleaner(_cleaner),
    timeout(_timeout),
    connectionHandler(new ConnectionHandlerImpl(this)),
    channelHandler(new ChannelHandlerImpl(this)),
    basicHandler(new BasicHandlerImpl(this)),
    exchangeHandler(new ExchangeHandlerImpl(this)),
    queueHandler(new QueueHandlerImpl(this)),
    framemax(65536), 
    heartbeat(0) {}

SessionHandlerImpl::~SessionHandlerImpl(){
    // TODO aconway 2006-09-07: Should be auto_ptr or plain members.
    delete channelHandler;
    delete connectionHandler;
    delete basicHandler;
    delete exchangeHandler;
    delete queueHandler;
}

Channel* SessionHandlerImpl::getChannel(u_int16_t channel){
    channel_iterator i = channels.find(channel);
    if(i == channels.end()){
        throw ConnectionException(504, "Unknown channel: " + channel);
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


Exchange* SessionHandlerImpl::findExchange(const string& name){
    exchanges->getLock()->acquire();
    Exchange* exchange(exchanges->get(name));
    exchanges->getLock()->release();
    return exchange;
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
            client.getChannel().close(channel, e.code, e.text, method->amqpClassId(), method->amqpMethodId());
        }catch(ConnectionException& e){
            client.getConnection().close(0, e.code, e.text, method->amqpClassId(), method->amqpMethodId());
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

void SessionHandlerImpl::initiated(qpid::framing::ProtocolInitiation* /*header*/){
    //send connection start
    FieldTable properties;
    string mechanisms("PLAIN");
    string locales("en_US");
    client.getConnection().start(0, 8, 0, properties, mechanisms, locales);
}

void SessionHandlerImpl::idleOut(){

}

void SessionHandlerImpl::idleIn(){

}

void SessionHandlerImpl::closed(){
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
}

void SessionHandlerImpl::handleHeader(u_int16_t channel, AMQHeaderBody::shared_ptr body){
    getChannel(channel)->handleHeader(body, Router(*exchanges));
}

void SessionHandlerImpl::handleContent(u_int16_t channel, AMQContentBody::shared_ptr body){
    getChannel(channel)->handleContent(body, Router(*exchanges));
}

void SessionHandlerImpl::handleHeartbeat(AMQHeartbeatBody::shared_ptr /*body*/){
    std::cout << "SessionHandlerImpl::handleHeartbeat()" << std::endl;
}
        
void SessionHandlerImpl::ConnectionHandlerImpl::startOk(
    u_int16_t /*channel*/, FieldTable& /*clientProperties*/, string& /*mechanism*/, 
    string& /*response*/, string& /*locale*/){

    parent->client.getConnection().tune(0, 100, parent->framemax, parent->heartbeat);
}
        
void SessionHandlerImpl::ConnectionHandlerImpl::secureOk(u_int16_t /*channel*/, string& /*response*/){}
        
void SessionHandlerImpl::ConnectionHandlerImpl::tuneOk(u_int16_t /*channel*/, u_int16_t /*channelmax*/, u_int32_t framemax, u_int16_t heartbeat){
    parent->framemax = framemax;
    parent->heartbeat = heartbeat;
}
        
void SessionHandlerImpl::ConnectionHandlerImpl::open(u_int16_t /*channel*/, string& /*virtualHost*/, string& /*capabilities*/, bool /*insist*/){
    string knownhosts;
    parent->client.getConnection().openOk(0, knownhosts);
}
        
void SessionHandlerImpl::ConnectionHandlerImpl::close(
    u_int16_t /*channel*/, u_int16_t /*replyCode*/, string& /*replyText*/, 
    u_int16_t /*classId*/, u_int16_t /*methodId*/)
{
    parent->client.getConnection().closeOk(0);
    parent->context->close();
} 
        
void SessionHandlerImpl::ConnectionHandlerImpl::closeOk(u_int16_t /*channel*/){
    parent->context->close();
} 
              


void SessionHandlerImpl::ChannelHandlerImpl::open(u_int16_t channel, string& /*outOfBand*/){
    parent->channels[channel] = new Channel(parent->context, channel, parent->framemax);
    parent->client.getChannel().openOk(channel);
} 
        
void SessionHandlerImpl::ChannelHandlerImpl::flow(u_int16_t /*channel*/, bool /*active*/){}         
void SessionHandlerImpl::ChannelHandlerImpl::flowOk(u_int16_t /*channel*/, bool /*active*/){} 
        
void SessionHandlerImpl::ChannelHandlerImpl::close(u_int16_t channel, u_int16_t /*replyCode*/, string& /*replyText*/, 
                                                   u_int16_t /*classId*/, u_int16_t /*methodId*/){
    Channel* c = parent->getChannel(channel);
    if(c){
        parent->channels.erase(channel);
        c->close();
        delete c;
        parent->client.getChannel().closeOk(channel);
    }
} 
        
void SessionHandlerImpl::ChannelHandlerImpl::closeOk(u_int16_t /*channel*/){} 
              


void SessionHandlerImpl::ExchangeHandlerImpl::declare(u_int16_t channel, u_int16_t /*ticket*/, string& exchange, string& type, 
                                                      bool passive, bool /*durable*/, bool /*autoDelete*/, bool /*internal*/, bool nowait, 
                                                      FieldTable& /*arguments*/){

    if(!passive && (
           type != TopicExchange::typeName &&
           type != DirectExchange::typeName &&
           type != FanOutExchange::typeName &&
           type != HeadersExchange::typeName
       )
        )
    {
        throw ChannelException(540, "Exchange type not implemented: " + type);
    }
    
    parent->exchanges->getLock()->acquire();
    if(!parent->exchanges->get(exchange)){
        if(type == TopicExchange::typeName){
            parent->exchanges->declare(new TopicExchange(exchange));
        }else if(type == DirectExchange::typeName){
            parent->exchanges->declare(new DirectExchange(exchange));
        }else if(type == FanOutExchange::typeName){
            parent->exchanges->declare(new DirectExchange(exchange));
        }else if (type == HeadersExchange::typeName) {
            parent->exchanges->declare(new HeadersExchange(exchange));
        }
    }
    parent->exchanges->getLock()->release();
    if(!nowait){
        parent->client.getExchange().declareOk(channel);
    }
} 
        
void SessionHandlerImpl::ExchangeHandlerImpl::delete_(u_int16_t channel, u_int16_t /*ticket*/, string& exchange, bool /*ifUnused*/, bool nowait){
    //TODO: implement unused
    parent->exchanges->getLock()->acquire();
    parent->exchanges->destroy(exchange);
    parent->exchanges->getLock()->release();
    if(!nowait) parent->client.getExchange().deleteOk(channel);
} 

void SessionHandlerImpl::QueueHandlerImpl::declare(u_int16_t channel, u_int16_t /*ticket*/, string& name, 
                                                   bool passive, bool durable, bool exclusive, 
                                                   bool autoDelete, bool nowait, FieldTable& /*arguments*/){
    Queue::shared_ptr queue;
    if (passive && !name.empty()) {
	queue = parent->getQueue(name, channel);
    } else {
	std::pair<Queue::shared_ptr, bool> queue_created =  parent->queues->declare(name, durable, autoDelete ? parent->timeout : 0, exclusive ? parent : 0);
	queue = queue_created.first;
	assert(queue);
	if (queue_created.second) { // This is a new queue
	    parent->getChannel(channel)->setDefaultQueue(queue);
	    //add default binding:
	    parent->exchanges->getDefault()->bind(queue, name, 0);
	    if(exclusive){
		parent->exclusiveQueues.push_back(queue);
	    } else if(autoDelete){
		parent->cleaner->add(queue);
	    }
	}
    }
    if(exclusive && !queue->isExclusiveOwner(parent)){
	throw ChannelException(405, "Cannot grant exclusive access to queue");
    }
    if(!nowait){
        name = queue->getName();
        parent->client.getQueue().declareOk(channel, name, queue->getMessageCount(), queue->getConsumerCount());
    }
} 
        
void SessionHandlerImpl::QueueHandlerImpl::bind(u_int16_t channel, u_int16_t /*ticket*/, string& queueName, 
                                                string& exchangeName, string& routingKey, bool nowait, 
                                                FieldTable& arguments){

    Queue::shared_ptr queue = parent->getQueue(queueName, channel);
    Exchange* exchange = parent->exchanges->get(exchangeName);
    if(exchange){
        if(routingKey.empty() && queueName.empty()) routingKey = queue->getName();
        exchange->bind(queue, routingKey, &arguments);
        if(!nowait) parent->client.getQueue().bindOk(channel);    
    }else{
        throw ChannelException(404, "Bind failed. No such exchange: " + exchangeName);
    }
} 
        
void SessionHandlerImpl::QueueHandlerImpl::purge(u_int16_t channel, u_int16_t /*ticket*/, string& queueName, bool nowait){

    Queue::shared_ptr queue = parent->getQueue(queueName, channel);
    int count = queue->purge();
    if(!nowait) parent->client.getQueue().purgeOk(channel, count);
} 
        
void SessionHandlerImpl::QueueHandlerImpl::delete_(u_int16_t channel, u_int16_t /*ticket*/, string& queue, 
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
        parent->queues->destroy(queue);
    }
    if(!nowait) parent->client.getQueue().deleteOk(channel, count);
} 
              
        


void SessionHandlerImpl::BasicHandlerImpl::qos(u_int16_t channel, u_int32_t prefetchSize, u_int16_t prefetchCount, bool /*global*/){
    //TODO: handle global
    parent->getChannel(channel)->setPrefetchSize(prefetchSize);
    parent->getChannel(channel)->setPrefetchCount(prefetchCount);
    parent->client.getBasic().qosOk(channel);
} 
        
void SessionHandlerImpl::BasicHandlerImpl::consume(u_int16_t channelId, u_int16_t /*ticket*/, 
                                                   string& queueName, string& consumerTag, 
                                                   bool noLocal, bool noAck, bool exclusive, 
                                                   bool nowait){
    
    Queue::shared_ptr queue = parent->getQueue(queueName, channelId);    
    Channel* channel = parent->channels[channelId];
    if(!consumerTag.empty() && channel->exists(consumerTag)){
        throw ConnectionException(530, "Consumer tags must be unique");
    }

    try{
        channel->consume(consumerTag, queue, !noAck, exclusive, noLocal ? parent : 0);
        if(!nowait) parent->client.getBasic().consumeOk(channelId, consumerTag);

        //allow messages to be dispatched if required as there is now a consumer:
        queue->dispatch();
    }catch(ExclusiveAccessException& e){
        if(exclusive) throw ChannelException(403, "Exclusive access cannot be granted");
        else throw ChannelException(403, "Access would violate previously granted exclusivity");
    }

} 
        
void SessionHandlerImpl::BasicHandlerImpl::cancel(u_int16_t channel, string& consumerTag, bool nowait){
    parent->getChannel(channel)->cancel(consumerTag);
    if(!nowait) parent->client.getBasic().cancelOk(channel, consumerTag);
} 
        
void SessionHandlerImpl::BasicHandlerImpl::publish(u_int16_t channel, u_int16_t /*ticket*/, 
                                                   string& exchange, string& routingKey, 
                                                   bool mandatory, bool immediate){

    Message* msg = new Message(parent, exchange, routingKey, mandatory, immediate);
    parent->getChannel(channel)->handlePublish(msg);
} 
        
void SessionHandlerImpl::BasicHandlerImpl::get(u_int16_t channelId, u_int16_t /*ticket*/, string& queueName, bool noAck){
    Queue::shared_ptr queue = parent->getQueue(queueName, channelId);    
    if(!parent->getChannel(channelId)->get(queue, !noAck)){
        string clusterId;//not used, part of an imatix hack
        parent->client.getBasic().getEmpty(channelId, clusterId);
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
} 
              
