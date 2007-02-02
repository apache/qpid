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
#include "AMQP_ServerOperations.h"
#include "MessageHandlerImpl.h"
#include "BrokerChannel.h"

namespace qpid {
namespace broker {

class Channel;
class Connection;
class Broker;

/**
 * Per-channel protocol adapter.
 *
 * Translates protocol bodies into calls on the core Channel,
 * Connection and Broker objects.
 */

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

class BrokerAdapter : public framing::AMQP_ServerOperations
{
  public:
    BrokerAdapter(Channel& ch, Connection& c, Broker& b) :
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

        void startOk(const framing::MethodContext& context,
                     const qpid::framing::FieldTable& clientProperties,
                     const std::string& mechanism, const std::string& response,
                     const std::string& locale); 
        void secureOk(const framing::MethodContext& context,
                      const std::string& response); 
        void tuneOk(const framing::MethodContext& context,
                    u_int16_t channelMax,
                    u_int32_t frameMax, u_int16_t heartbeat); 
        void open(const framing::MethodContext& context,
                  const std::string& virtualHost,
                  const std::string& capabilities, bool insist); 
        void close(const framing::MethodContext& context, u_int16_t replyCode,
                   const std::string& replyText,
                   u_int16_t classId, u_int16_t methodId); 
        void closeOk(const framing::MethodContext& context); 
    };

    class ChannelHandlerImpl : private CoreRefs, public ChannelHandler{
      public:
        ChannelHandlerImpl(Channel& ch, Connection& c, Broker& b) : CoreRefs(ch, c, b) {}
        void open(const framing::MethodContext& context, const std::string& outOfBand); 
        void flow(const framing::MethodContext& context, bool active); 
        void flowOk(const framing::MethodContext& context, bool active); 
        void ok( const framing::MethodContext& context );
        void ping( const framing::MethodContext& context );
        void pong( const framing::MethodContext& context );
        void resume( const framing::MethodContext& context, const std::string& channelId );
        void close(const framing::MethodContext& context, u_int16_t replyCode, const
                   std::string& replyText, u_int16_t classId, u_int16_t methodId); 
        void closeOk(const framing::MethodContext& context); 
    };
    
    class ExchangeHandlerImpl : private CoreRefs, public ExchangeHandler{
      public:
        ExchangeHandlerImpl(Channel& ch, Connection& c, Broker& b) : CoreRefs(ch, c, b) {}
        void declare(const framing::MethodContext& context, u_int16_t ticket,
                     const std::string& exchange, const std::string& type, 
                     bool passive, bool durable, bool autoDelete,
                     bool internal, bool nowait, 
                     const qpid::framing::FieldTable& arguments); 
        void delete_(const framing::MethodContext& context, u_int16_t ticket,
                     const std::string& exchange, bool ifUnused, bool nowait); 
    };

    class QueueHandlerImpl : private CoreRefs, public QueueHandler{
      public:
        QueueHandlerImpl(Channel& ch, Connection& c, Broker& b) : CoreRefs(ch, c, b) {}
        void declare(const framing::MethodContext& context, u_int16_t ticket, const std::string& queue, 
                     bool passive, bool durable, bool exclusive, 
                     bool autoDelete, bool nowait,
                     const qpid::framing::FieldTable& arguments); 
        void bind(const framing::MethodContext& context, u_int16_t ticket, const std::string& queue, 
                  const std::string& exchange, const std::string& routingKey,
                  bool nowait, const qpid::framing::FieldTable& arguments); 
        void unbind(const framing::MethodContext& context,
                    u_int16_t ticket,
                    const std::string& queue,
                    const std::string& exchange,
                    const std::string& routingKey,
                    const qpid::framing::FieldTable& arguments );
        void purge(const framing::MethodContext& context, u_int16_t ticket, const std::string& queue, 
                   bool nowait); 
        void delete_(const framing::MethodContext& context, u_int16_t ticket, const std::string& queue,
                     bool ifUnused, bool ifEmpty, 
                     bool nowait);
    };

    class BasicHandlerImpl : private CoreRefs, public BasicHandler{
      public:
        BasicHandlerImpl(Channel& ch, Connection& c, Broker& b) : CoreRefs(ch, c, b) {}
        void qos(const framing::MethodContext& context, u_int32_t prefetchSize,
                 u_int16_t prefetchCount, bool global); 
        void consume(
            const framing::MethodContext& context, u_int16_t ticket, const std::string& queue,
            const std::string& consumerTag, bool noLocal, bool noAck,
            bool exclusive, bool nowait,
            const qpid::framing::FieldTable& fields); 
        void cancel(const framing::MethodContext& context, const std::string& consumerTag,
                    bool nowait); 
        void publish(const framing::MethodContext& context, u_int16_t ticket,
                     const std::string& exchange, const std::string& routingKey, 
                     bool mandatory, bool immediate); 
        void get(const framing::MethodContext& context, u_int16_t ticket, const std::string& queue,
                 bool noAck); 
        void ack(const framing::MethodContext& context, u_int64_t deliveryTag, bool multiple); 
        void reject(const framing::MethodContext& context, u_int64_t deliveryTag, bool requeue); 
        void recover(const framing::MethodContext& context, bool requeue); 
    };

    class TxHandlerImpl : private CoreRefs, public TxHandler{
      public:
        TxHandlerImpl(Channel& ch, Connection& c, Broker& b) : CoreRefs(ch, c, b) {}
        void select(const framing::MethodContext& context);
        void commit(const framing::MethodContext& context);
        void rollback(const framing::MethodContext& context);
    };

    BasicHandlerImpl basicHandler;
    ChannelHandlerImpl channelHandler;
    ConnectionHandlerImpl connectionHandler;
    ExchangeHandlerImpl exchangeHandler;
    MessageHandlerImpl messageHandler;
    QueueHandlerImpl queueHandler;
    TxHandlerImpl txHandler;

};
}} // namespace qpid::broker



#endif  /*!_broker_BrokerAdapter_h*/
