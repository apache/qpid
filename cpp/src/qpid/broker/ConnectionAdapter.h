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
#ifndef _ConnectionAdapter_
#define _ConnectionAdapter_

#include <memory>
#include "qpid/framing/amqp_types.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/AMQP_ServerOperations.h"
#include "qpid/framing/AMQP_ClientProxy.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/Exception.h"

namespace qpid {
namespace broker {

class Connection;

class ConnectionAdapter : public framing::ChannelAdapter, public framing::AMQP_ServerOperations
{
    struct Handler : public framing::AMQP_ServerOperations::ConnectionHandler
    {
        framing::AMQP_ClientProxy proxy;
        framing::AMQP_ClientProxy::Connection client;
        Connection& connection;
    
        Handler(Connection& connection, ConnectionAdapter& adapter);
        void startOk(const qpid::framing::FieldTable& clientProperties,
                     const std::string& mechanism, const std::string& response,
                     const std::string& locale); 
        void secureOk(const std::string& response); 
        void tuneOk(uint16_t channelMax, uint32_t frameMax, uint16_t heartbeat); 
        void open(const std::string& virtualHost,
                  const std::string& capabilities, bool insist); 
        void close(uint16_t replyCode, const std::string& replyText,
                   uint16_t classId, uint16_t methodId); 
        void closeOk(); 
    };
    std::auto_ptr<Handler> handler;
  public:
    ConnectionAdapter(Connection& connection);
    void init(const framing::ProtocolInitiation& header);
    void close(framing::ReplyCode code, const std::string& text, framing::ClassId classId, framing::MethodId methodId);
    void handle(framing::AMQFrame& frame);

    //ChannelAdapter virtual methods:
    void handleMethod(framing::AMQMethodBody* method);
    bool isOpen() const { return true; } //channel 0 is always open
    //never needed:
    void handleHeader(framing::AMQHeaderBody*) {}
    void handleContent(framing::AMQContentBody*) {}
    void handleHeartbeat(framing::AMQHeartbeatBody*) {}

    //AMQP_ServerOperations:
    ConnectionHandler* getConnectionHandler();
    ChannelHandler* getChannelHandler() { throw ConnectionException(503, "Class can't be accessed over channel 0"); }
    BasicHandler* getBasicHandler() { throw ConnectionException(503, "Class can't be accessed over channel 0"); }
    ExchangeHandler* getExchangeHandler() { throw ConnectionException(503, "Class can't be accessed over channel 0"); }
    BindingHandler* getBindingHandler() { throw ConnectionException(503, "Class can't be accessed over channel 0"); }
    QueueHandler* getQueueHandler() { throw ConnectionException(503, "Class can't be accessed over channel 0"); }
    TxHandler* getTxHandler() { throw ConnectionException(503, "Class can't be accessed over channel 0"); }
    MessageHandler* getMessageHandler() { throw ConnectionException(503, "Class can't be accessed over channel 0"); }
    AccessHandler* getAccessHandler() { throw ConnectionException(503, "Class can't be accessed over channel 0"); }
    FileHandler* getFileHandler() { throw ConnectionException(503, "Class can't be accessed over channel 0"); }
    StreamHandler* getStreamHandler() { throw ConnectionException(503, "Class can't be accessed over channel 0"); }
    TunnelHandler* getTunnelHandler() { throw ConnectionException(503, "Class can't be accessed over channel 0"); }
    DtxCoordinationHandler* getDtxCoordinationHandler() { throw ConnectionException(503, "Class can't be accessed over channel 0"); }
    DtxDemarcationHandler* getDtxDemarcationHandler() { throw ConnectionException(503, "Class can't be accessed over channel 0"); }
    ExecutionHandler* getExecutionHandler() { throw ConnectionException(503, "Class can't be accessed over channel 0"); }
    SessionHandler* getSessionHandler() { throw ConnectionException(503, "Class can't be accessed over channel 0"); }
    framing::ProtocolVersion getVersion() const;
};


}}

#endif
