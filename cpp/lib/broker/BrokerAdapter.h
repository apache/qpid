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

namespace qpid {
namespace broker {

class Connection;

/**
 * Protocol adapter class for the broker.
 */
class BrokerAdapter : public qpid::framing::AMQP_ServerOperations 
{
  public:
    BrokerAdapter(Connection& connection);
    AccessHandler* getAccessHandler();
    BasicHandler* getBasicHandler();
    ChannelHandler* getChannelHandler();
    ConnectionHandler* getConnectionHandler();
    DtxHandler* getDtxHandler();
    ExchangeHandler* getExchangeHandler();
    FileHandler* getFileHandler();
    MessageHandler* getMessageHandler();
    QueueHandler* getQueueHandler();
    StreamHandler* getStreamHandler();
    TunnelHandler* getTunnelHandler();
    TxHandler* getTxHandler();

  private:

    class ConnectionHandlerImpl : public ConnectionHandler{
        Connection& connection;
      public:
        ConnectionHandlerImpl(Connection& c) : connection(c) {}

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

    class ChannelHandlerImpl : public ChannelHandler{
        Connection& connection;
      public:
        ChannelHandlerImpl(Connection& c) : connection(c) {}
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
    
    class ExchangeHandlerImpl : public ExchangeHandler{
        Connection& connection;
      public:
        ExchangeHandlerImpl(Connection& c) : connection(c) {}
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

    class QueueHandlerImpl : public QueueHandler{
        Connection& connection;
      public:
        QueueHandlerImpl(Connection& c) : connection(c) {}
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

    class BasicHandlerImpl : public BasicHandler{
        Connection& connection;
      public:
        BasicHandlerImpl(Connection& c) : connection(c) {}
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

    class TxHandlerImpl : public TxHandler{
        Connection& connection;
      public:
        TxHandlerImpl(Connection& c) : connection(c) {}
        void select(u_int16_t channel);
        void commit(u_int16_t channel);
        void rollback(u_int16_t channel);
    };

    class MessageHandlerImpl : public MessageHandler {
        Connection& connection;
      public:
        MessageHandlerImpl(Connection& c) : connection(c) {}

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

    Connection& connection;

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
