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

#include "qpid/framing/AMQP_ServerOperations.h"
#include "qpid/framing/AMQP_ClientProxy.h"
#include "HandlerImpl.h"

#include <boost/function.hpp>

namespace qpid {
namespace broker {

class Connection;
class Broker;
class MessageMessage;

class MessageHandlerImpl :
        public framing::AMQP_ServerOperations::MessageHandler,
        public HandlerImpl
{
    typedef boost::function<void(DeliveryId, DeliveryId)> RangedOperation;    
    RangedOperation releaseOp;
    RangedOperation rejectOp;

  public:
    MessageHandlerImpl(SemanticState&);

    void append(const std::string& reference, const std::string& bytes);

    void cancel(const std::string& destination );

    void checkpoint(const std::string& reference,
                     const std::string& identifier );

    void close(const std::string& reference );

    void empty();

    void get(uint16_t ticket,
              const std::string& queue,
              const std::string& destination,
              bool noAck );

    void offset(uint64_t value);

    void ok();

    void open(const std::string& reference );

    void qos(uint32_t prefetchSize,
              uint16_t prefetchCount,
              bool global );

    void recover(bool requeue );

    void reject(const framing::SequenceNumberSet& transfers,
                uint16_t code,
                const std::string& text );

    void resume(const std::string& reference,
                 const std::string& identifier );

    void flow(const std::string& destination, u_int8_t unit, u_int32_t value);
    
    void flowMode(const std::string& destination, u_int8_t mode);
    
    void flush(const std::string& destination);

    void stop(const std::string& destination);

    void acquire(const framing::SequenceNumberSet& transfers, u_int8_t mode);

    void release(const framing::SequenceNumberSet& transfers);

    void subscribe(u_int16_t ticket,
                   const std::string& queue,
                   const std::string& destination,
                   bool noLocal,
                   u_int8_t confirmMode,
                   u_int8_t acquireMode,
                   bool exclusive,
                   const framing::FieldTable& filter);

};

}} // namespace qpid::broker



#endif  /*!_broker_MessageHandlerImpl_h*/
