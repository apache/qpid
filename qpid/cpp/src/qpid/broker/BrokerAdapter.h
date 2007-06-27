#ifndef _broker_BrokerAdapter_h
#define _broker_BrokerAdapter_h

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
#include "qpid/framing/AMQP_ServerOperations.h"
#include "HandlerImpl.h"
#include "MessageHandlerImpl.h"
#include "DtxHandlerImpl.h"
#include "qpid/Exception.h"

namespace qpid {
namespace broker {

class Channel;
class Connection;
class Broker;
class ChannelHandler;
class ConnectionHandler;
class BasicHandler;
class ExchangeHandler;
class QueueHandler;
class TxHandler;
class MessageHandler;
class AccessHandler;
class FileHandler;
class StreamHandler;
class DtxHandler;
class TunnelHandler;
class MessageHandlerImpl;

/**
 * Per-channel protocol adapter.
 *
 * A container for a collection of AMQP-class adapters that translate
 * AMQP method bodies into calls on the core Channel, Connection and
 * Broker objects. Each adapter class also provides a client proxy
 * to send methods to the peer.
 * 
 */
class BrokerAdapter : public CoreRefs, public framing::AMQP_ServerOperations
{
  public:
    BrokerAdapter(Channel& ch, Connection& c, Broker& b);

    framing::ProtocolVersion getVersion() const;
    ChannelHandler* getChannelHandler() { return &channelHandler; }
    ConnectionHandler* getConnectionHandler() { return &connectionHandler; }
    BasicHandler* getBasicHandler() { return &basicHandler; }
    ExchangeHandler* getExchangeHandler() { return &exchangeHandler; }
    BindingHandler* getBindingHandler() { return &bindingHandler; }
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

    DtxCoordinationHandler* getDtxCoordinationHandler() { return &dtxHandler; }
    DtxDemarcationHandler* getDtxDemarcationHandler() { return &dtxHandler; }

    framing::AMQP_ClientProxy& getProxy() { return proxy; }

  private:

    class ConnectionHandlerImpl :
        public ConnectionHandler,
        public HandlerImpl<framing::AMQP_ClientProxy::Connection>
    {
      public:
        ConnectionHandlerImpl(BrokerAdapter& parent) : HandlerImplType(parent) {}

        void startOk(const framing::MethodContext& context,
                     const qpid::framing::FieldTable& clientProperties,
                     const std::string& mechanism, const std::string& response,
                     const std::string& locale); 
        void secureOk(const framing::MethodContext& context,
                      const std::string& response); 
        void tuneOk(const framing::MethodContext& context,
                    uint16_t channelMax,
                    uint32_t frameMax, uint16_t heartbeat); 
        void open(const framing::MethodContext& context,
                  const std::string& virtualHost,
                  const std::string& capabilities, bool insist); 
        void close(const framing::MethodContext& context, uint16_t replyCode,
                   const std::string& replyText,
                   uint16_t classId, uint16_t methodId); 
        void closeOk(const framing::MethodContext& context); 
    };

    class ChannelHandlerImpl :
        public ChannelHandler,
        public HandlerImpl<framing::AMQP_ClientProxy::Channel>
    {
      public:
        ChannelHandlerImpl(BrokerAdapter& parent) : HandlerImplType(parent) {}
        
        void open(const framing::MethodContext& context, const std::string& outOfBand); 
        void flow(const framing::MethodContext& context, bool active); 
        void flowOk(const framing::MethodContext& context, bool active); 
        void ok( const framing::MethodContext& context );
        void ping( const framing::MethodContext& context );
        void pong( const framing::MethodContext& context );
        void resume( const framing::MethodContext& context, const std::string& channelId );
        void close(const framing::MethodContext& context, uint16_t replyCode, const
                   std::string& replyText, uint16_t classId, uint16_t methodId); 
        void closeOk(const framing::MethodContext& context); 
    };
    
    class ExchangeHandlerImpl :
        public ExchangeHandler,
        public HandlerImpl<framing::AMQP_ClientProxy::Exchange>
    {
      public:
        ExchangeHandlerImpl(BrokerAdapter& parent) : HandlerImplType(parent) {}
        
        void declare(const framing::MethodContext& context, uint16_t ticket,
                     const std::string& exchange, const std::string& type, 
                     bool passive, bool durable, bool autoDelete,
                     bool internal, bool nowait, 
                     const qpid::framing::FieldTable& arguments); 
        void delete_(const framing::MethodContext& context, uint16_t ticket,
                     const std::string& exchange, bool ifUnused, bool nowait); 
        void query(const framing::MethodContext& context,
                   u_int16_t ticket,
                   const string& name);
    };

    class BindingHandlerImpl : 
        public BindingHandler,
            public HandlerImpl<framing::AMQP_ClientProxy::Binding>
    {
    public:
        BindingHandlerImpl(BrokerAdapter& parent) : HandlerImplType(parent) {}

        void query(const framing::MethodContext& context,
                   u_int16_t ticket,
                   const std::string& exchange,
                   const std::string& queue,
                   const std::string& routingKey,
                   const framing::FieldTable& arguments);
    };

    class QueueHandlerImpl :
        public QueueHandler,
        public HandlerImpl<framing::AMQP_ClientProxy::Queue>
    {
      public:
        QueueHandlerImpl(BrokerAdapter& parent) : HandlerImplType(parent) {}
        
        void declare(const framing::MethodContext& context, uint16_t ticket, const std::string& queue, 
                     bool passive, bool durable, bool exclusive, 
                     bool autoDelete, bool nowait,
                     const qpid::framing::FieldTable& arguments); 
        void bind(const framing::MethodContext& context, uint16_t ticket, const std::string& queue, 
                  const std::string& exchange, const std::string& routingKey,
                  bool nowait, const qpid::framing::FieldTable& arguments); 
        void unbind(const framing::MethodContext& context,
                    uint16_t ticket,
                    const std::string& queue,
                    const std::string& exchange,
                    const std::string& routingKey,
                    const qpid::framing::FieldTable& arguments );
        void purge(const framing::MethodContext& context, uint16_t ticket, const std::string& queue, 
                   bool nowait); 
        void delete_(const framing::MethodContext& context, uint16_t ticket, const std::string& queue,
                     bool ifUnused, bool ifEmpty, 
                     bool nowait);
    };

    class BasicHandlerImpl :
        public BasicHandler,
        public HandlerImpl<framing::AMQP_ClientProxy::Basic>
    {
      public:
        BasicHandlerImpl(BrokerAdapter& parent) : HandlerImplType(parent) {}

        void qos(const framing::MethodContext& context, uint32_t prefetchSize,
                 uint16_t prefetchCount, bool global); 
        void consume(
            const framing::MethodContext& context, uint16_t ticket, const std::string& queue,
            const std::string& consumerTag, bool noLocal, bool noAck,
            bool exclusive, bool nowait,
            const qpid::framing::FieldTable& fields); 
        void cancel(const framing::MethodContext& context, const std::string& consumerTag,
                    bool nowait); 
        void publish(const framing::MethodContext& context, uint16_t ticket,
                     const std::string& exchange, const std::string& routingKey, 
                     bool mandatory, bool immediate); 
        void get(const framing::MethodContext& context, uint16_t ticket, const std::string& queue,
                 bool noAck); 
        void ack(const framing::MethodContext& context, uint64_t deliveryTag, bool multiple); 
        void reject(const framing::MethodContext& context, uint64_t deliveryTag, bool requeue); 
        void recover(const framing::MethodContext& context, bool requeue); 
    };

    class TxHandlerImpl :
        public TxHandler,
        public HandlerImpl<framing::AMQP_ClientProxy::Tx>
    {
      public:
        TxHandlerImpl(BrokerAdapter& parent) : HandlerImplType(parent) {}
        
        void select(const framing::MethodContext& context);
        void commit(const framing::MethodContext& context);
        void rollback(const framing::MethodContext& context);
    };

    Connection& connection;
    BasicHandlerImpl basicHandler;
    ChannelHandlerImpl channelHandler;
    ConnectionHandlerImpl connectionHandler;
    ExchangeHandlerImpl exchangeHandler;
    BindingHandlerImpl bindingHandler;
    MessageHandlerImpl messageHandler;
    QueueHandlerImpl queueHandler;
    TxHandlerImpl txHandler;
    DtxHandlerImpl dtxHandler;        
};
}} // namespace qpid::broker



#endif  /*!_broker_BrokerAdapter_h*/
