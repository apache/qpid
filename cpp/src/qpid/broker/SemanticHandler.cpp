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

#include "SemanticHandler.h"
#include "BrokerAdapter.h"
#include "qpid/framing/ChannelAdapter.h"
#include "qpid/framing/ChannelCloseOkBody.h"
#include "qpid/framing/ExecutionCompleteBody.h"
#include "qpid/framing/ExecutionResultBody.h"
#include "qpid/framing/InvocationVisitor.h"

using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;

SemanticHandler::SemanticHandler(ChannelId id, Connection& c) : 
    connection(c),
    channel(c, *this, id, &c.broker.getStore())
{
    init(id, connection.getOutput(), connection.getVersion());
    adapter = std::auto_ptr<BrokerAdapter>(new BrokerAdapter(channel, connection, connection.broker, *this));
}


void SemanticHandler::handle(framing::AMQFrame& frame) 
{    
    //TODO: assembly etc when move to 0-10 framing
    //
    //have potentially three separate tracks at this point:
    //
    // (1) execution controls
    // (2) commands
    // (3) data i.e. content-bearing commands
    //
    //framesets on each can be interleaved. framesets on the latter
    //two share a command-id sequence.
    //
    //need to decide what to do if a frame on the command track
    //arrives while a frameset on the data track is still
    //open. execute it (i.e. out-of order execution with respect to
    //the command id sequence) or queue it up.

    //if ready to execute (i.e. if segment is complete or frame is
    //message content):
    handleBody(frame.getBody());
}

//ChannelAdapter virtual methods:
void SemanticHandler::handleMethod(framing::AMQMethodBody* method)
{
    try {
        if (!method->invoke(this)) {
            //temporary hack until channel management is moved to its own handler:
            if (method->amqpClassId() != ChannelOpenBody::CLASS_ID) {
                ++(incoming.lwm);
            }

            //else do the usual:
            handleL4(method);
            //(if the frameset is complete) we can move the execution-mark
            //forward 
            
            //temporary hack until channel management is moved to its own handler:
            if (method->amqpClassId() != ChannelOpenBody::CLASS_ID) {
                //TODO: need to account for async store opreations
                //when this command is a message publication
                ++(incoming.hwm);                
            }
            
            //note: need to be more sophisticated than this if we execute
            //commands that arrive within an active message frameset (that
            //can't happen until 0-10 framing is implemented)
        }
    }catch(const std::exception& e){
        connection.close(541/*internal error*/, e.what(), method->amqpClassId(), method->amqpMethodId());
    }
}

void SemanticHandler::complete(uint32_t cumulative, const SequenceNumberSet& range) 
{
    //record: 
    SequenceNumber mark(cumulative);
    if (outgoing.lwm < mark) {
        outgoing.lwm = mark;
        //ack messages:
        channel.ackCumulative(mark.getValue());
        //std::cout << "[" << this << "] acknowledged: " << mark << std::endl;
    }
    if (range.size() % 2) { //must be even number        
        throw ConnectionException(530, "Received odd number of elements in ranged mark");
    } else {
        for (SequenceNumberSet::const_iterator i = range.begin(); i != range.end(); i++) {
            channel.ackRange((uint64_t) i->getValue(), (uint64_t) (++i)->getValue());
        }
    }
}

void SemanticHandler::flush()
{
    //flush doubles as a sync to begin with - send an execution.complete
    incoming.lwm = incoming.hwm;
    if (isOpen()) {
        Mutex::ScopedLock l(outLock);
        ChannelAdapter::send(ExecutionCompleteBody(getVersion(), incoming.hwm.getValue(), SequenceNumberSet()));
    }
}
void SemanticHandler::sync()
{
    //for now, just treat as flush; will need to get more clever when we deal with async publication
    flush();
}

void SemanticHandler::noop()
{
    //Do nothing... 
    //
    //is this an L3 control? or is it an L4 command? 
    //if the former, of what use is it?
    //if the latter it may contain a synch request... but its odd to have it in this class
}

void SemanticHandler::result(uint32_t /*command*/, const std::string& /*data*/)
{
    //never actually sent by client at present
}

void SemanticHandler::handleL4(framing::AMQMethodBody* method)
{
    try{
        if(getId() != 0 && !method->isA<ChannelOpenBody>() && !isOpen()) {
            if (!method->isA<ChannelCloseOkBody>()) {
                std::stringstream out;
                out << "Attempt to use unopened channel: " << getId();
                throw ConnectionException(504, out.str());
            }
        } else {
            InvocationVisitor v(adapter.get());
            method->accept(v);
            if (!v.wasHandled()) {
                throw ConnectionException(540, "Not implemented");
            } else if (v.hasResult()) {
                ChannelAdapter::send(ExecutionResultBody(getVersion(), incoming.lwm.getValue(), v.getResult()));
            }
        }
    }catch(const ChannelException& e){
        adapter->getProxy().getChannel().close(
            e.code, e.toString(),
            method->amqpClassId(), method->amqpMethodId());
        connection.closeChannel(getId());
    }catch(const ConnectionException& e){
        connection.close(e.code, e.toString(), method->amqpClassId(), method->amqpMethodId());
    }
}

bool SemanticHandler::isOpen() const 
{ 
    return channel.isOpen(); 
}

void SemanticHandler::handleHeader(qpid::framing::AMQHeaderBody* body) 
{
    channel.handleHeader(body);
}

void SemanticHandler::handleContent(qpid::framing::AMQContentBody* body) 
{
    channel.handleContent(body);
}

void SemanticHandler::handleHeartbeat(qpid::framing::AMQHeartbeatBody* body) 
{
    channel.handleHeartbeat(body);
}

DeliveryId SemanticHandler::deliver(Message::shared_ptr& msg, DeliveryToken::shared_ptr token)
{
    Mutex::ScopedLock l(outLock);
    SequenceNumber copy(outgoing.hwm);
    ++copy;
    msg->deliver(*this, copy.getValue(), token, connection.getFrameMax());
    //std::cout << "[" << this << "] delivered: " << outgoing.hwm.getValue() << std::endl;
    return outgoing.hwm.getValue();
}

void SemanticHandler::redeliver(Message::shared_ptr& msg, DeliveryToken::shared_ptr token, DeliveryId tag)
{
    msg->deliver(*this, tag, token, connection.getFrameMax());
}

void SemanticHandler::send(const AMQBody& body)
{
    Mutex::ScopedLock l(outLock);
    if (body.getMethod() && body.getMethod()->amqpClassId() != ChannelOpenBody::CLASS_ID) {
        //temporary hack until channel management is moved to its own handler:
        ++outgoing.hwm;
    }
    ChannelAdapter::send(body);
}
