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
#include "DtxHandlerImpl.h"
#include "MessageHandlerImpl.h"

#include "qpid/Exception.h"
#include "qpid/framing/AMQP_ServerOperations.h"
#include "qpid/framing/reply_exceptions.h"

namespace qpid {
namespace broker {

class Channel;
class Connection;
class Broker;
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
class Exchange;

/**
 * Per-channel protocol adapter.
 *
 * A container for a collection of AMQP-class adapters that translate
 * AMQP method bodies into calls on the core Broker objects. Each
 * adapter class also provides a client proxy to send methods to the
 * peer.
 * 
 */
class BrokerAdapter : public HandlerImpl, public framing::AMQP_ServerOperations
{
  public:
    BrokerAdapter(SemanticState& session);

    BasicHandler* getBasicHandler() { return &basicHandler; }
    ExchangeHandler* getExchangeHandler() { return &exchangeHandler; }
    BindingHandler* getBindingHandler() { return &bindingHandler; }
    QueueHandler* getQueueHandler() { return &queueHandler; }
    TxHandler* getTxHandler() { return &txHandler;  }
    MessageHandler* getMessageHandler() { return &messageHandler;  }
    DtxCoordinationHandler* getDtxCoordinationHandler() { return &dtxHandler; }
    DtxDemarcationHandler* getDtxDemarcationHandler() { return &dtxHandler; }

    framing::ProtocolVersion getVersion() const { return session.getConnection().getVersion();}


    AccessHandler* getAccessHandler() {
        throw framing::NotImplementedException("Access class not implemented");  }
    FileHandler* getFileHandler() {
        throw framing::NotImplementedException("File class not implemented");  }
    StreamHandler* getStreamHandler() {
        throw framing::NotImplementedException("Stream class not implemented");  }
    TunnelHandler* getTunnelHandler() {
        throw framing::NotImplementedException("Tunnel class not implemented"); }

    Exchange010Handler* getExchange010Handler() { throw framing::NotImplementedException("Class not implemented");  }
    Queue010Handler* getQueue010Handler() { throw framing::NotImplementedException("Class not implemented");  }
    Message010Handler* getMessage010Handler() { throw framing::NotImplementedException("Class not implemented");  }
    Tx010Handler* getTx010Handler() { throw framing::NotImplementedException("Class not implemented");  }
    Dtx010Handler* getDtx010Handler() { throw framing::NotImplementedException("Class not implemented");  }
    Execution010Handler* getExecution010Handler() { throw framing::NotImplementedException("Class not implemented");  }

    // Handlers no longer implemented in BrokerAdapter:
#define BADHANDLER() assert(0); throw framing::NotImplementedException("")
    ExecutionHandler* getExecutionHandler() { BADHANDLER(); }
    ConnectionHandler* getConnectionHandler() { BADHANDLER(); }
    SessionHandler* getSessionHandler() { BADHANDLER(); }
    Connection010Handler* getConnection010Handler() { BADHANDLER(); }
    Session010Handler* getSession010Handler() { BADHANDLER(); }
#undef BADHANDLER

  private:
    class ExchangeHandlerImpl :
        public ExchangeHandler,
        public HandlerImpl
    {
      public:
        ExchangeHandlerImpl(SemanticState& session) : HandlerImpl(session) {}
        
        void declare(uint16_t ticket,
                     const std::string& exchange, const std::string& type,
                     const std::string& alternateExchange, 
                     bool passive, bool durable, bool autoDelete, 
                     const qpid::framing::FieldTable& arguments); 
        void delete_(uint16_t ticket,
                     const std::string& exchange, bool ifUnused); 
        framing::ExchangeQueryResult query(u_int16_t ticket,
                                           const std::string& name);
      private:
        void checkType(shared_ptr<Exchange> exchange, const std::string& type);

        void checkAlternate(shared_ptr<Exchange> exchange,
                            shared_ptr<Exchange> alternate);
    };

    class BindingHandlerImpl : 
        public BindingHandler,
            public HandlerImpl
    {
    public:
        BindingHandlerImpl(SemanticState& session) : HandlerImpl(session) {}

        framing::BindingQueryResult query(u_int16_t ticket,
                                          const std::string& exchange,
                                          const std::string& queue,
                                          const std::string& routingKey,
                                          const framing::FieldTable& arguments);
    };

    class QueueHandlerImpl :
        public QueueHandler,
        public HandlerImpl
    {
      public:
        QueueHandlerImpl(SemanticState& session) : HandlerImpl(session) {}
        
        void declare(uint16_t ticket, const std::string& queue,
                     const std::string& alternateExchange, 
                     bool passive, bool durable, bool exclusive, 
                     bool autoDelete,
                     const qpid::framing::FieldTable& arguments); 
        void bind(uint16_t ticket, const std::string& queue, 
                  const std::string& exchange, const std::string& routingKey,
                  const qpid::framing::FieldTable& arguments); 
        void unbind(uint16_t ticket,
                    const std::string& queue,
                    const std::string& exchange,
                    const std::string& routingKey,
                    const qpid::framing::FieldTable& arguments );
        framing::QueueQueryResult query(const std::string& queue);
        void purge(uint16_t ticket, const std::string& queue); 
        void delete_(uint16_t ticket, const std::string& queue,
                     bool ifUnused, bool ifEmpty);
    };

    class BasicHandlerImpl :
        public BasicHandler,
        public HandlerImpl
    {
        NameGenerator tagGenerator;
      public:
        BasicHandlerImpl(SemanticState& session) : HandlerImpl(session), tagGenerator("sgen") {}

        void qos(uint32_t prefetchSize,
                 uint16_t prefetchCount, bool global); 
        void consume(uint16_t ticket, const std::string& queue,
                     const std::string& consumerTag, 
                     bool noLocal, bool noAck, bool exclusive, bool nowait,
                     const qpid::framing::FieldTable& fields); 
        void cancel(const std::string& consumerTag); 
        void get(uint16_t ticket, const std::string& queue, bool noAck); 
        void ack(uint64_t deliveryTag, bool multiple); 
        void reject(uint64_t deliveryTag, bool requeue); 
        void recover(bool requeue); 
    };

    class TxHandlerImpl :
        public TxHandler,
        public HandlerImpl
    {
      public:
        TxHandlerImpl(SemanticState& session) : HandlerImpl(session) {}
        
        void select();
        void commit();
        void rollback();
    };

    BasicHandlerImpl basicHandler;
    ExchangeHandlerImpl exchangeHandler;
    BindingHandlerImpl bindingHandler;
    MessageHandlerImpl messageHandler;
    QueueHandlerImpl queueHandler;
    TxHandlerImpl txHandler;
    DtxHandlerImpl dtxHandler;        
};
}} // namespace qpid::broker



#endif  /*!_broker_BrokerAdapter_h*/
