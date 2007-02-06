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

#include <memory>

#include "AMQP_ServerOperations.h"
#include "Reference.h"
#include "BrokerChannel.h"

namespace qpid {
namespace broker {

class Connection;
class Broker;
class MessageMessage;

class MessageHandlerImpl :
        public framing::AMQP_ServerOperations::MessageHandler
{
  public:
    MessageHandlerImpl(Channel& ch, Connection& c, Broker& b)
        : channel(ch), connection(c), broker(b), references(ch) {}

    void append(const framing::MethodContext&,
                 const std::string& reference,
                 const std::string& bytes );

    void cancel(const framing::MethodContext&,
                 const std::string& destination );

    void checkpoint(const framing::MethodContext&,
                     const std::string& reference,
                     const std::string& identifier );

    void close(const framing::MethodContext&,
                const std::string& reference );

    void consume(const framing::MethodContext&,
                  u_int16_t ticket,
                  const std::string& queue,
                  const std::string& destination,
                  bool noLocal,
                  bool noAck,
                  bool exclusive,
                  const framing::FieldTable& filter );

    void empty( const framing::MethodContext& );

    void get(const framing::MethodContext&,
              u_int16_t ticket,
              const std::string& queue,
              const std::string& destination,
              bool noAck );

    void offset(const framing::MethodContext&,
                 u_int64_t value );

    void ok( const framing::MethodContext& );

    void open(const framing::MethodContext&,
               const std::string& reference );

    void qos(const framing::MethodContext&,
              u_int32_t prefetchSize,
              u_int16_t prefetchCount,
              bool global );

    void recover(const framing::MethodContext&,
                  bool requeue );

    void reject(const framing::MethodContext&,
                 u_int16_t code,
                 const std::string& text );

    void resume(const framing::MethodContext&,
                 const std::string& reference,
                 const std::string& identifier );

    void transfer(const framing::MethodContext&,
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
                   const framing::FieldTable& applicationHeaders,
                   framing::Content body,
                   bool mandatory );
  private:
    void sendOk(const framing::MethodContext&);
    
    Channel& channel;
    Connection& connection;
    Broker& broker;
    ReferenceRegistry references;
};

}} // namespace qpid::broker



#endif  /*!_broker_MessageHandlerImpl_h*/
