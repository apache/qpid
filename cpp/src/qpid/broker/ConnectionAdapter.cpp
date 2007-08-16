
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

#include "ConnectionAdapter.h"
#include "Connection.h"

using namespace qpid;
using namespace qpid::broker;
using qpid::framing::ReplyCode;
using qpid::framing::ClassId;
using qpid::framing::MethodId;
using qpid::framing::FieldTable;

void ConnectionAdapter::init(const framing::ProtocolInitiation& header) {
    ChannelAdapter::init(0, handler->connection.getOutput(), handler->connection.getVersion());
    FieldTable properties;
    string mechanisms("PLAIN");
    string locales("en_US");
    handler->client.start(header.getMajor(), header.getMinor(), properties, mechanisms, locales);
}

void ConnectionAdapter::close(ReplyCode code, const string& text, ClassId classId, MethodId methodId)
{
    handler->client.close(code, text, classId, methodId);
}

void ConnectionAdapter::handleMethod(framing::AMQMethodBody* method)
{
    try{
        method->invoke(*this);
    }catch(ConnectionException& e){
        handler->client.close(e.code, e.toString(), method->amqpClassId(), method->amqpMethodId());
    }catch(std::exception& e){
        handler->client.close(541/*internal error*/, e.what(), method->amqpClassId(), method->amqpMethodId());
    }
}

framing::AMQP_ServerOperations::ConnectionHandler* ConnectionAdapter::getConnectionHandler() 
{ 
    return handler.get(); 
}

framing::ProtocolVersion ConnectionAdapter::getVersion() const 
{ 
    return handler->connection.getVersion(); 
}

void ConnectionAdapter::handle(framing::AMQFrame& frame)
{
    getHandlers().in->handle(frame);
}

ConnectionAdapter::ConnectionAdapter(Connection& connection) 
{
    handler = std::auto_ptr<Handler>(new Handler(connection, *this));
}

Handler::Handler(Connection& c, ConnectionAdapter& a) : 
    proxy(a), client(proxy.getConnection()), connection(c) {}


void Handler::startOk(const FieldTable& /*clientProperties*/,
    const string& /*mechanism*/, 
    const string& /*response*/, const string& /*locale*/)
{
    client.tune(framing::CHANNEL_MAX, connection.getFrameMax(), connection.getHeartbeat());
}
        
void Handler::secureOk(const string& /*response*/){}
        
void Handler::tuneOk(uint16_t /*channelmax*/,
    uint32_t framemax, uint16_t heartbeat)
{
    connection.setFrameMax(framemax);
    connection.setHeartbeat(heartbeat);
}
        
void Handler::open(const string& /*virtualHost*/,
    const string& /*capabilities*/, bool /*insist*/)
{
    string knownhosts;
    client.openOk(knownhosts);
}

        
void Handler::close(uint16_t /*replyCode*/, const string& /*replyText*/, 
    uint16_t /*classId*/, uint16_t /*methodId*/)
{
    client.closeOk();
    connection.getOutput().close();
} 
        
void Handler::closeOk(){
    connection.getOutput().close();
} 
