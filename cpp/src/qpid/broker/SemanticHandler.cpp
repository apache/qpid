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
#include "Session.h"
#include "SessionAdapter.h"
#include "BrokerAdapter.h"
#include "MessageDelivery.h"
#include "Connection.h"
#include "Session.h"
#include "qpid/framing/ExecutionCompleteBody.h"
#include "qpid/framing/ExecutionResultBody.h"
#include "qpid/framing/ChannelOpenBody.h"
#include "qpid/framing/InvocationVisitor.h"

#include <boost/format.hpp>

using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;

SemanticHandler::SemanticHandler(Session& s) :
    session(s),
    connection(s.getAdapter()->getConnection()),
    adapter(s, static_cast<ChannelAdapter&>(*this))
{
    init(s.getAdapter()->getChannel(), s.out, 0);
}

void SemanticHandler::handle(framing::AMQFrame& frame) 
{    
    //TODO: assembly for method and headers

    //have potentially three separate tracks at this point:
    //
    // (1) execution controls
    // (2) commands
    // (3) data i.e. content-bearing commands
    //
    //framesets on each can be interleaved. framesets on the latter
    //two share a command-id sequence. controls on the first track are
    //used to communicate details about that command-id sequence.
    //
    //need to decide what to do if a frame on the command track
    //arrives while a frameset on the data track is still
    //open. execute it (i.e. out-of order execution with respect to
    //the command id sequence) or queue it up?

    TrackId track = getTrack(frame);//will be replaced by field in 0-10 frame header
        
    switch(track) {   
      case EXECUTION_CONTROL_TRACK:
        handleL3(frame.getMethod());
        break;
      case MODEL_COMMAND_TRACK:
        handleCommand(frame.getMethod());
        break;
      case MODEL_CONTENT_TRACK:
        handleContent(frame);
        break;
    }
}

void SemanticHandler::complete(uint32_t cumulative, const SequenceNumberSet& range) 
{
    //record: 
    SequenceNumber mark(cumulative);
    if (outgoing.lwm < mark) {
        outgoing.lwm = mark;
        //ack messages:
        session.ackCumulative(mark.getValue());
    }
    if (range.size() % 2) { //must be even number        
        throw ConnectionException(530, "Received odd number of elements in ranged mark");
    } else {
        for (SequenceNumberSet::const_iterator i = range.begin(); i != range.end(); i++) {
            session.ackRange((uint64_t) i->getValue(), (uint64_t) (++i)->getValue());
        }
    }
}

void SemanticHandler::flush()
{
    //flush doubles as a sync to begin with - send an execution.complete
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

void SemanticHandler::handleCommand(framing::AMQMethodBody* method)
{
    ++(incoming.lwm);                        
    InvocationVisitor v(&adapter);
    method->accept(v);
    //TODO: need to account for async store operations and interleaving
    ++(incoming.hwm);                                    
    
    if (!v.wasHandled()) {
        throw ConnectionException(540, "Not implemented");
    } else if (v.hasResult()) {
        ChannelAdapter::send(ExecutionResultBody(getVersion(), incoming.lwm.getValue(), v.getResult()));
    }
}

void SemanticHandler::handleL3(framing::AMQMethodBody* method)
{
    if (!method->invoke(this)) {
        throw ConnectionException(540, "Not implemented");
    }
}

void SemanticHandler::handleContent(AMQFrame& frame)
{
    Message::shared_ptr msg(msgBuilder.getMessage());
    if (!msg) {//start of frameset will be indicated by frame flags
        msgBuilder.start(++(incoming.lwm));
        msg = msgBuilder.getMessage();
    }
    msgBuilder.handle(frame);
    if (msg->getFrames().isComplete()) {//end of frameset will be indicated by frame flags
        msg->setPublisher(&connection);
        session.handle(msg);
        msgBuilder.end();
        //TODO: need to account for async store operations and interleaving
        ++(incoming.hwm);                
    }
}

bool SemanticHandler::isOpen() const {
    // FIXME aconway 2007-08-30: remove.
    return true;
}

DeliveryId SemanticHandler::deliver(Message::shared_ptr& msg, DeliveryToken::shared_ptr token)
{
    Mutex::ScopedLock l(outLock);
    //SequenceNumber copy(outgoing.hwm);
    //++copy;
    MessageDelivery::deliver(msg, *this, ++outgoing.hwm, token, connection.getFrameMax());
    return outgoing.hwm;
    //return outgoing.hwm.getValue();
}

void SemanticHandler::redeliver(Message::shared_ptr& msg, DeliveryToken::shared_ptr token, DeliveryId tag)
{
    MessageDelivery::deliver(msg, *this, tag, token, connection.getFrameMax());
}

void SemanticHandler::send(const AMQBody& body)
{
    Mutex::ScopedLock l(outLock);
    // FIXME aconway 2007-08-31: SessionAdapter should not send
    // channel/session commands  via the semantic handler, it should shortcut
    // directly to its own output handler. That will make the CLASS_ID
    // part of the test unnecessary.
    // 
    if (body.getMethod() &&
        body.getMethod()->amqpClassId() != ChannelOpenBody::CLASS_ID)
    {
        ++outgoing.hwm;
    }
    ChannelAdapter::send(body);
}

SemanticHandler::TrackId SemanticHandler::getTrack(const AMQFrame& frame)
{
    //will be replaced by field in 0-10 frame header
    uint8_t type = frame.getBody()->type();
    uint16_t classId;
    switch(type) {
      case METHOD_BODY:
        if (frame.castBody<AMQMethodBody>()->isContentBearing()) {
            return MODEL_CONTENT_TRACK;
        }

        classId = frame.castBody<AMQMethodBody>()->amqpClassId();
        switch (classId) {
          case ExecutionCompleteBody::CLASS_ID:
            return EXECUTION_CONTROL_TRACK;
        }

        return MODEL_COMMAND_TRACK;
      case HEADER_BODY:
      case CONTENT_BODY:
        return MODEL_CONTENT_TRACK;
    }
    throw Exception("Could not determine track");
}

//ChannelAdapter virtual methods, no longer used:
void SemanticHandler::handleMethod(framing::AMQMethodBody*){}

void SemanticHandler::handleHeader(qpid::framing::AMQHeaderBody*) {}

void SemanticHandler::handleContent(qpid::framing::AMQContentBody*) {}

void SemanticHandler::handleHeartbeat(qpid::framing::AMQHeartbeatBody*) {}
