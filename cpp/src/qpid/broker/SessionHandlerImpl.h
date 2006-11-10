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
#ifndef _SessionHandlerImpl_
#define _SessionHandlerImpl_

#include <map>
#include <sstream>
#include <vector>
#include <exception>
#include <qpid/framing/AMQFrame.h>
#include <qpid/framing/AMQP_ClientProxy.h>
#include <qpid/framing/AMQP_ServerOperations.h>
#include <qpid/broker/AutoDelete.h>
#include <qpid/broker/ExchangeRegistry.h>
#include <qpid/broker/Channel.h>
#include <qpid/broker/ConnectionToken.h>
#include <qpid/broker/DirectExchange.h>
#include <qpid/framing/OutputHandler.h>
#include <qpid/framing/ProtocolInitiation.h>
#include <qpid/broker/QueueRegistry.h>
#include <qpid/sys/SessionContext.h>
#include <qpid/sys/SessionHandler.h>
#include <qpid/sys/TimeoutHandler.h>
#include <qpid/broker/TopicExchange.h>

namespace qpid {
namespace broker {

struct ChannelException : public std::exception {
    u_int16_t code;
    string text;
    ChannelException(u_int16_t _code, string _text) : code(_code), text(_text) {}
    ~ChannelException() throw() {}
    const char* what() const throw() { return text.c_str(); }
};

struct ConnectionException : public std::exception {
    u_int16_t code;
    string text;
    ConnectionException(u_int16_t _code, string _text) : code(_code), text(_text) {}
    ~ConnectionException() throw() {}
    const char* what() const throw() { return text.c_str(); }
};

class SessionHandlerImpl : public virtual qpid::sys::SessionHandler, 
                           public virtual qpid::framing::AMQP_ServerOperations, 
                           public virtual ConnectionToken
{
    typedef std::map<u_int16_t, Channel*>::iterator channel_iterator;
    typedef std::vector<Queue::shared_ptr>::iterator queue_iterator;

    qpid::sys::SessionContext* context;
    qpid::framing::AMQP_ClientProxy client;
    QueueRegistry* queues;
    ExchangeRegistry* const exchanges;
    AutoDelete* const cleaner;
    const u_int32_t timeout;//timeout for auto-deleted queues (in ms)

    std::auto_ptr<BasicHandler> basicHandler;
    std::auto_ptr<ChannelHandler> channelHandler;
    std::auto_ptr<ConnectionHandler> connectionHandler;
    std::auto_ptr<ExchangeHandler> exchangeHandler;
    std::auto_ptr<QueueHandler> queueHandler;
    std::auto_ptr<TxHandler> txHandler;

    std::map<u_int16_t, Channel*> channels;
    std::vector<Queue::shared_ptr> exclusiveQueues;

    u_int32_t framemax;
    u_int16_t heartbeat;

    void handleHeader(u_int16_t channel, qpid::framing::AMQHeaderBody::shared_ptr body);
    void handleContent(u_int16_t channel, qpid::framing::AMQContentBody::shared_ptr body);
    void handleHeartbeat(qpid::framing::AMQHeartbeatBody::shared_ptr body);

    Channel* getChannel(u_int16_t channel);
    /**
     * Get named queue, never returns 0.
     * @return: named queue or default queue for channel if name=""
     * @exception: ChannelException if no queue of that name is found.
     * @exception: ConnectionException if no queue specified and channel has not declared one.
     */
    Queue::shared_ptr getQueue(const string& name, u_int16_t channel);

    Exchange::shared_ptr findExchange(const string& name);
    
  public:
    SessionHandlerImpl(qpid::sys::SessionContext* context, QueueRegistry* queues, 
                       ExchangeRegistry* exchanges, AutoDelete* cleaner, const u_int32_t timeout);
    virtual void received(qpid::framing::AMQFrame* frame);
    virtual void initiated(qpid::framing::ProtocolInitiation* header);
    virtual void idleOut();
    virtual void idleIn();
    virtual void closed();
    virtual ~SessionHandlerImpl();

    class ConnectionHandlerImpl : public virtual ConnectionHandler{
        SessionHandlerImpl* parent;
      public:
        inline ConnectionHandlerImpl(SessionHandlerImpl* _parent) : parent(_parent) {}

        virtual void startOk(u_int16_t channel, qpid::framing::FieldTable& clientProperties, string& mechanism, 
                             string& response, string& locale); 
                
        virtual void secureOk(u_int16_t channel, string& response); 
                
        virtual void tuneOk(u_int16_t channel, u_int16_t channelMax, u_int32_t frameMax, u_int16_t heartbeat); 
                
        virtual void open(u_int16_t channel, string& virtualHost, string& capabilities, bool insist); 
                
        virtual void close(u_int16_t channel, u_int16_t replyCode, string& replyText, u_int16_t classId, 
                           u_int16_t methodId); 
                
        virtual void closeOk(u_int16_t channel); 
                
        virtual ~ConnectionHandlerImpl(){}
    };
    
    class ChannelHandlerImpl : public virtual ChannelHandler{
        SessionHandlerImpl* parent;
      public:
        inline ChannelHandlerImpl(SessionHandlerImpl* _parent) : parent(_parent) {}
        
        virtual void open(u_int16_t channel, string& outOfBand); 
        
        virtual void flow(u_int16_t channel, bool active); 
                
        virtual void flowOk(u_int16_t channel, bool active); 
                
        virtual void close(u_int16_t channel, u_int16_t replyCode, string& replyText, 
                           u_int16_t classId, u_int16_t methodId); 
                
        virtual void closeOk(u_int16_t channel); 
                
        virtual ~ChannelHandlerImpl(){}
    };
    
    class ExchangeHandlerImpl : public virtual ExchangeHandler{
        SessionHandlerImpl* parent;
      public:
        inline ExchangeHandlerImpl(SessionHandlerImpl* _parent) : parent(_parent) {}
        
        virtual void declare(u_int16_t channel, u_int16_t ticket, string& exchange, string& type, 
                             bool passive, bool durable, bool autoDelete, bool internal, bool nowait, 
                             qpid::framing::FieldTable& arguments); 
                
        virtual void delete_(u_int16_t channel, u_int16_t ticket, string& exchange, bool ifUnused, bool nowait); 
                
        virtual ~ExchangeHandlerImpl(){}
    };

    
    class QueueHandlerImpl : public virtual QueueHandler{
        SessionHandlerImpl* parent;
      public:
        inline QueueHandlerImpl(SessionHandlerImpl* _parent) : parent(_parent) {}
        
        virtual void declare(u_int16_t channel, u_int16_t ticket, string& queue, 
                             bool passive, bool durable, bool exclusive, 
                             bool autoDelete, bool nowait, qpid::framing::FieldTable& arguments); 
                
        virtual void bind(u_int16_t channel, u_int16_t ticket, string& queue, 
                          string& exchange, string& routingKey, bool nowait, 
                          qpid::framing::FieldTable& arguments); 
                
        virtual void purge(u_int16_t channel, u_int16_t ticket, string& queue, 
                           bool nowait); 
                
        virtual void delete_(u_int16_t channel, u_int16_t ticket, string& queue, bool ifUnused, bool ifEmpty, 
                             bool nowait); 
                
        virtual ~QueueHandlerImpl(){}
    };

    class BasicHandlerImpl : public virtual BasicHandler{
        SessionHandlerImpl* parent;
      public:
        inline BasicHandlerImpl(SessionHandlerImpl* _parent) : parent(_parent) {}
        
        virtual void qos(u_int16_t channel, u_int32_t prefetchSize, u_int16_t prefetchCount, bool global); 
                    
        virtual void consume(u_int16_t channel, u_int16_t ticket, string& queue, string& consumerTag, 
                             bool noLocal, bool noAck, bool exclusive, bool nowait); 
                
        virtual void cancel(u_int16_t channel, string& consumerTag, bool nowait); 
                
        virtual void publish(u_int16_t channel, u_int16_t ticket, string& exchange, string& routingKey, 
                             bool mandatory, bool immediate); 
                
        virtual void get(u_int16_t channel, u_int16_t ticket, string& queue, bool noAck); 
                
        virtual void ack(u_int16_t channel, u_int64_t deliveryTag, bool multiple); 
                
        virtual void reject(u_int16_t channel, u_int64_t deliveryTag, bool requeue); 
                
        virtual void recover(u_int16_t channel, bool requeue); 
                
        virtual ~BasicHandlerImpl(){}
    };

    class TxHandlerImpl : public virtual TxHandler{
        SessionHandlerImpl* parent;
    public:
        TxHandlerImpl(SessionHandlerImpl* _parent) : parent(_parent) {}
        virtual ~TxHandlerImpl() {}
        virtual void select(u_int16_t channel);
        virtual void commit(u_int16_t channel);
        virtual void rollback(u_int16_t channel);
    };


    inline virtual ChannelHandler* getChannelHandler(){ return channelHandler.get(); }
    inline virtual ConnectionHandler* getConnectionHandler(){ return connectionHandler.get(); }
    inline virtual BasicHandler* getBasicHandler(){ return basicHandler.get(); }
    inline virtual ExchangeHandler* getExchangeHandler(){ return exchangeHandler.get(); }
    inline virtual QueueHandler* getQueueHandler(){ return queueHandler.get(); }
    inline virtual TxHandler* getTxHandler(){ return txHandler.get(); }       
 
    inline virtual AccessHandler* getAccessHandler(){ throw ConnectionException(540, "Access class not implemented"); }       
    inline virtual FileHandler* getFileHandler(){ throw ConnectionException(540, "File class not implemented"); }       
    inline virtual StreamHandler* getStreamHandler(){ throw ConnectionException(540, "Stream class not implemented"); }       
    inline virtual DtxHandler* getDtxHandler(){ throw ConnectionException(540, "Dtx class not implemented"); }       
    inline virtual TunnelHandler* getTunnelHandler(){ throw ConnectionException(540, "Tunnel class not implemented"); }       
};

}
}


#endif
