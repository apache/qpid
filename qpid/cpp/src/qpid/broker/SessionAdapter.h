#ifndef _broker_SessionAdapter_h
#define _broker_SessionAdapter_h

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

#include "HandlerImpl.h"

#include "ConnectionToken.h"
#include "OwnershipToken.h"
#include "qpid/Exception.h"
#include "qpid/framing/AMQP_ServerOperations.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/SequenceSet.h"

#include <vector>
#include <boost/function.hpp>
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace broker {

class Channel;
class Connection;
class Broker;
class Queue;

/**
 * Per-channel protocol adapter.
 *
 * A container for a collection of AMQP-class adapters that translate
 * AMQP method bodies into calls on the core Broker objects. Each
 * adapter class also provides a client proxy to send methods to the
 * peer.
 * 
 */
 class SessionAdapter : public HandlerImpl, public framing::AMQP_ServerOperations
{
  public:
    SessionAdapter(SemanticState& session);


    framing::ProtocolVersion getVersion() const { return session.getConnection().getVersion();}

    Message010Handler* getMessage010Handler(){ return &messageImpl;  }
    Exchange010Handler* getExchange010Handler(){ return &exchangeImpl; }
    Queue010Handler* getQueue010Handler(){ return &queueImpl;  }
    Execution010Handler* getExecution010Handler(){ return &executionImpl; }
    Tx010Handler* getTx010Handler(){ return &txImpl; }
    Dtx010Handler* getDtx010Handler(){ return &dtxImpl; }

    BasicHandler* getBasicHandler() { throw framing::NotImplementedException("Class not implemented");  }
    ExchangeHandler* getExchangeHandler(){ throw framing::NotImplementedException("Class not implemented");  }
    BindingHandler* getBindingHandler(){ throw framing::NotImplementedException("Class not implemented");  }
    QueueHandler* getQueueHandler(){ throw framing::NotImplementedException("Class not implemented");  }
    TxHandler* getTxHandler(){ throw framing::NotImplementedException("Class not implemented");  }
    MessageHandler* getMessageHandler(){ throw framing::NotImplementedException("Class not implemented");  }
    DtxCoordinationHandler* getDtxCoordinationHandler(){ throw framing::NotImplementedException("Class not implemented");  }
    DtxDemarcationHandler* getDtxDemarcationHandler(){ throw framing::NotImplementedException("Class not implemented");  }
    AccessHandler* getAccessHandler() { throw framing::NotImplementedException("Class not implemented");  }
    FileHandler* getFileHandler() { throw framing::NotImplementedException("Class not implemented");  }
    StreamHandler* getStreamHandler() { throw framing::NotImplementedException("Class not implemented");  }
    TunnelHandler* getTunnelHandler() { throw framing::NotImplementedException("Class not implemented"); }
    ExecutionHandler* getExecutionHandler() { throw framing::NotImplementedException("Class not implemented"); }
    ConnectionHandler* getConnectionHandler() { throw framing::NotImplementedException("Class not implemented"); }
    SessionHandler* getSessionHandler() { throw framing::NotImplementedException("Class not implemented"); }
    Connection010Handler* getConnection010Handler() { throw framing::NotImplementedException("Class not implemented"); }
    Session010Handler* getSession010Handler() { throw framing::NotImplementedException("Class not implemented"); }

  private:
    //common base for utility methods etc that are specific to this adapter
    struct HandlerHelper : public HandlerImpl 
    {
        HandlerHelper(SemanticState& s) : HandlerImpl(s) {}

        Queue::shared_ptr getQueue(const string& name) const;
    };


    class ExchangeHandlerImpl :
        public Exchange010Handler,
        public HandlerHelper
    {
      public:
        ExchangeHandlerImpl(SemanticState& session) : HandlerHelper(session) {}
        
        void declare(const std::string& exchange, const std::string& type,
                     const std::string& alternateExchange, 
                     bool passive, bool durable, bool autoDelete, 
                     const qpid::framing::FieldTable& arguments); 
        void delete_(const std::string& exchange, bool ifUnused); 
        framing::Exchange010QueryResult query(const std::string& name);
        void bind(const std::string& queue, 
                  const std::string& exchange, const std::string& routingKey,
                  const qpid::framing::FieldTable& arguments); 
        void unbind(const std::string& queue,
                    const std::string& exchange,
                    const std::string& routingKey);
        framing::Exchange010BoundResult bound(const std::string& exchange,
                                           const std::string& queue,
                                           const std::string& routingKey,
                                           const framing::FieldTable& arguments);
      private:
        void checkType(shared_ptr<Exchange> exchange, const std::string& type);

        void checkAlternate(shared_ptr<Exchange> exchange,
                            shared_ptr<Exchange> alternate);
    };

    class QueueHandlerImpl : public Queue010Handler,
            public HandlerHelper, public OwnershipToken
    {
        Broker& broker;
        std::vector< boost::shared_ptr<Queue> > exclusiveQueues;

      public:
        QueueHandlerImpl(SemanticState& session);
        ~QueueHandlerImpl();
        
        void declare(const std::string& queue,
                     const std::string& alternateExchange, 
                     bool passive, bool durable, bool exclusive, 
                     bool autoDelete,
                     const qpid::framing::FieldTable& arguments); 
        void delete_(const std::string& queue,
                     bool ifUnused, bool ifEmpty);
        void purge(const std::string& queue); 
        framing::Queue010QueryResult query(const std::string& queue);
        bool isLocal(const ConnectionToken* t) const; 
    };

    class MessageHandlerImpl :
        public Message010Handler,
        public HandlerHelper
    {
        typedef boost::function<void(DeliveryId, DeliveryId)> RangedOperation;    
        RangedOperation releaseRedeliveredOp;
        RangedOperation releaseOp;
        RangedOperation rejectOp;
        RangedOperation acceptOp;

      public:
        MessageHandlerImpl(SemanticState& session);
        void transfer(const string& destination,
                      uint8_t acceptMode,
                      uint8_t acquireMode);
        
        void accept(const framing::SequenceSet& commands);
        
        void reject(const framing::SequenceSet& commands,
                    uint16_t code,
                    const string& text);
        
        void release(const framing::SequenceSet& commands,
                     bool setRedelivered);
        
        framing::Message010AcquireResult acquire(const framing::SequenceSet&);

        void subscribe(const string& queue,
                       const string& destination,
                       uint8_t acceptMode,
                       uint8_t acquireMode,
                       bool exclusive,
                       const string& resumeId,
                       uint64_t resumeTtl,
                       const framing::FieldTable& arguments);
        
        void cancel(const string& destination);
        
        void setFlowMode(const string& destination,
                         uint8_t flowMode);
        
        void flow(const string& destination,
                  uint8_t unit,
                  uint32_t value);
        
        void flush(const string& destination);
        
        void stop(const string& destination);
    
    };

    class ExecutionHandlerImpl : public Execution010Handler, public HandlerHelper
    {
    public:
        ExecutionHandlerImpl(SemanticState& session) : HandlerHelper(session) {}

        void sync();            
        void result(uint32_t commandId, const string& value);        
        void exception(uint16_t errorCode,
                       uint32_t commandId,
                       uint8_t classCode,
                       uint8_t commandCode,
                       uint8_t fieldIndex,
                       const std::string& description,
                       const framing::FieldTable& errorInfo);

    };

    class TxHandlerImpl : public Tx010Handler, public HandlerHelper
    {
      public:
        TxHandlerImpl(SemanticState& session) : HandlerHelper(session) {}
        
        void select();
        void commit();
        void rollback();
    };

    class DtxHandlerImpl : public Dtx010Handler, public HandlerHelper
    {
        std::string convert(const framing::Xid010& xid);

      public:
        DtxHandlerImpl(SemanticState& session) : HandlerHelper(session) {}

        void select();
            
        framing::Dtx010StartResult start(const framing::Xid010& xid,
                                         bool join,
                                         bool resume);
        
        framing::Dtx010EndResult end(const framing::Xid010& xid,
                                     bool fail,
                                     bool suspend);
        
        framing::Dtx010CommitResult commit(const framing::Xid010& xid,
                                           bool onePhase);
        
        void forget(const framing::Xid010& xid);
        
        framing::Dtx010GetTimeoutResult getTimeout(const framing::Xid010& xid);
        
        framing::Dtx010PrepareResult prepare(const framing::Xid010& xid);
        
        framing::Dtx010RecoverResult recover();
        
        framing::Dtx010RollbackResult rollback(const framing::Xid010& xid);
        
        void setTimeout(const framing::Xid010& xid, uint32_t timeout);        
    };

    ExchangeHandlerImpl exchangeImpl;
    QueueHandlerImpl queueImpl;
    MessageHandlerImpl messageImpl;
    ExecutionHandlerImpl executionImpl;
    TxHandlerImpl txImpl;
    DtxHandlerImpl dtxImpl;
};
}} // namespace qpid::broker



#endif  /*!_broker_SessionAdapter_h*/
