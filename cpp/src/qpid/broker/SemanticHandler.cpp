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
#include "SessionHandler.h"
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

SemanticHandler::SemanticHandler(Session& s) : HandlerImpl(s) {}

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
        getSession().ackCumulative(mark.getValue());
    }
    if (range.size() % 2) { //must be even number        
        throw ConnectionException(530, "Received odd number of elements in ranged mark");
    } else {
        for (SequenceNumberSet::const_iterator i = range.begin(); i != range.end(); i++) {
            getSession().ackRange((uint64_t) i->getValue(), (uint64_t) (++i)->getValue());
        }
    }
}

void SemanticHandler::sendCompletion()
{
    SequenceNumber mark = incoming.getMark();
    SequenceNumberSet range = incoming.getRange();
    Mutex::ScopedLock l(outLock);
    assert(getSessionHandler()); 
    getProxy().getExecution().complete(mark.getValue(), range);
}
void SemanticHandler::flush()
{
    incoming.flush();
    sendCompletion();
}
void SemanticHandler::sync()
{
    incoming.sync();
    sendCompletion();
}

void SemanticHandler::noop()
{
    incoming.noop();
}

void SemanticHandler::result(uint32_t /*command*/, const std::string& /*data*/)
{
    //never actually sent by client at present
}

void SemanticHandler::handleCommand(framing::AMQMethodBody* method)
{
    SequenceNumber id = incoming.next();
    BrokerAdapter adapter(getSession());
    InvocationVisitor v(&adapter);
    method->accept(v);
    incoming.complete(id);                                    
    
    if (!v.wasHandled()) {
        throw ConnectionException(540, "Not implemented");
    } else if (v.hasResult()) {
        getProxy().getExecution().result(id.getValue(), v.getResult());
    }
    //TODO: if (method->isSync()) { incoming.synch(id); sendCompletion(); }
    //TODO: if window gets too large send unsolicited completion
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
        msgBuilder.start(incoming.next());
        msg = msgBuilder.getMessage();
    }
    msgBuilder.handle(frame);
    if (msg->getFrames().isComplete()) {//end of frameset will be indicated by frame flags
        msg->setPublisher(&getConnection());
        getSession().handle(msg);        
        msgBuilder.end();
        incoming.track(msg);
        //TODO: if (msg.getMethod().isSync()) { incoming.synch(msg.getCommandId()); sendCompletion(); }
    }
}

DeliveryId SemanticHandler::deliver(Message::shared_ptr& msg, DeliveryToken::shared_ptr token)
{
    Mutex::ScopedLock l(outLock);
    MessageDelivery::deliver(msg, getSessionHandler()->out, ++outgoing.hwm, token, getConnection().getFrameMax());
    return outgoing.hwm;
}

void SemanticHandler::redeliver(Message::shared_ptr& msg, DeliveryToken::shared_ptr token, DeliveryId tag)
{
    MessageDelivery::deliver(msg, getSessionHandler()->out, tag, token, getConnection().getFrameMax());
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

