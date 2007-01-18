#ifndef _broker_MessageHandlerImpl_h
#define _broker_MessageHandlerImpl_h

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

class Channel;
class Connection;
class Broker;

class MessageHandlerImpl : public qpid::framing::AMQP_ServerOperations::MessageHandler {
    Channel& channel;
    Connection& connection;
    Broker& broker;

  public:
    MessageHandlerImpl(Channel& ch, Connection& c, Broker& b)
        : channel(ch), connection(c), broker(b) {}

    void append(const qpid::framing::MethodContext&,
                 const std::string& reference,
                 const std::string& bytes );

    void cancel(const qpid::framing::MethodContext&,
                 const std::string& destination );

    void checkpoint(const qpid::framing::MethodContext&,
                     const std::string& reference,
                     const std::string& identifier );

    void close(const qpid::framing::MethodContext&,
                const std::string& reference );

    void consume(const qpid::framing::MethodContext&,
                  u_int16_t ticket,
                  const std::string& queue,
                  const std::string& destination,
                  bool noLocal,
                  bool noAck,
                  bool exclusive,
                  const qpid::framing::FieldTable& filter );

    void empty( const qpid::framing::MethodContext& );

    void get(const qpid::framing::MethodContext&,
              u_int16_t ticket,
              const std::string& queue,
              const std::string& destination,
              bool noAck );

    void offset(const qpid::framing::MethodContext&,
                 u_int64_t value );

    void ok( const qpid::framing::MethodContext& );

    void open(const qpid::framing::MethodContext&,
               const std::string& reference );

    void qos(const qpid::framing::MethodContext&,
              u_int32_t prefetchSize,
              u_int16_t prefetchCount,
              bool global );

    void recover(const qpid::framing::MethodContext&,
                  bool requeue );

    void reject(const qpid::framing::MethodContext&,
                 u_int16_t code,
                 const std::string& text );

    void resume(const qpid::framing::MethodContext&,
                 const std::string& reference,
                 const std::string& identifier );

    void transfer(const qpid::framing::MethodContext&,
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

}} // namespace qpid::broker



#endif  /*!_broker_MessageHandlerImpl_h*/
