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
#include "qpid/client/amqp0_10/IncomingMessages.h"
#include "qpid/client/amqp0_10/AddressResolution.h"
#include "qpid/client/amqp0_10/Codecs.h"
#include "qpid/client/SessionImpl.h"
#include "qpid/client/SessionBase_0_10Access.h"
#include "qpid/log/Statement.h"
#include "qpid/messaging/Address.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/Variant.h"
#include "qpid/framing/DeliveryProperties.h"
#include "qpid/framing/FrameSet.h"
#include "qpid/framing/MessageProperties.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/framing/enum.h"

namespace qpid {
namespace client {
namespace amqp0_10 {

using namespace qpid::framing;
using namespace qpid::framing::message;
using qpid::sys::AbsTime;
using qpid::sys::Duration;
using qpid::messaging::Variant;

namespace {
const std::string EMPTY_STRING;


struct GetNone : IncomingMessages::Handler
{
    bool accept(IncomingMessages::MessageTransfer&) { return false; }
};

struct GetAny : IncomingMessages::Handler
{
    bool accept(IncomingMessages::MessageTransfer& transfer)
    { 
        transfer.retrieve(0);
        return true;
    }
};

struct MatchAndTrack
{
    const std::string destination;
    SequenceSet ids;

    MatchAndTrack(const std::string& d) : destination(d) {}

    bool operator()(boost::shared_ptr<qpid::framing::FrameSet> command)
    {
        if (command->as<MessageTransferBody>()->getDestination() == destination) {
            ids.add(command->getId());
            return true;
        } else {
            return false;
        }
    }
};
}

IncomingMessages::IncomingMessages(qpid::client::AsyncSession s) : 
    session(s), 
    incoming(SessionBase_0_10Access(session).get()->getDemux().getDefault()) {}

bool IncomingMessages::get(Handler& handler, Duration timeout)
{
    //search through received list for any transfer of interest:
    for (FrameSetQueue::iterator i = received.begin(); i != received.end(); i++)
    {
        MessageTransfer transfer(*i, *this);
        if (handler.accept(transfer)) {
            received.erase(i);
            return true;
        }
    }
    //none found, check incoming:
    return process(&handler, timeout);
}

void IncomingMessages::accept()
{
    session.messageAccept(unaccepted);
    unaccepted.clear();
}

void IncomingMessages::releaseAll()
{
    //first process any received messages...
    while (!received.empty()) {
        retrieve(received.front(), 0);
        received.pop_front();
    }
    //then pump out any available messages from incoming queue...
    GetAny handler;
    while (process(&handler, 0)) ;
    //now release all messages
    session.messageRelease(unaccepted);
    unaccepted.clear();
}

void IncomingMessages::releasePending(const std::string& destination)
{
    //first pump all available messages from incoming to received...
    while (process(0, 0)) ;

    //now remove all messages for this destination from received list, recording their ids...
    MatchAndTrack match(destination);
    for (FrameSetQueue::iterator i = received.begin(); i != received.end(); i = match(*i) ? received.erase(i) : ++i) ;
    //now release those messages
    session.messageRelease(match.ids);
}

/**
 * Get a frameset from session queue, waiting for up to the specified
 * duration and returning true if this could be achieved, false
 * otherwise. If a destination is supplied, only return a message for
 * that destination. In this case messages from other destinations
 * will be held on a received queue.
 */
bool IncomingMessages::process(Handler* handler, qpid::sys::Duration duration)
{
    AbsTime deadline(AbsTime::now(), duration);
    FrameSet::shared_ptr content;
    for (Duration timeout = duration; incoming->pop(content, timeout); timeout = Duration(AbsTime::now(), deadline)) {
        if (content->isA<MessageTransferBody>()) {
            MessageTransfer transfer(content, *this);
            if (handler && handler->accept(transfer)) {
                QPID_LOG(debug, "Delivered " << *content->getMethod());
                return true;
            } else {
                //received message for another destination, keep for later
                QPID_LOG(debug, "Pushed " << *content->getMethod() << " to received queue");
                received.push_back(content);
            }
        } else {
            //TODO: handle other types of commands (e.g. message-accept, message-flow etc)
        }
    }
    return false;
}

void populate(qpid::messaging::Message& message, FrameSet& command);

/**
 * Called when message is retrieved; records retrieval for subsequent
 * acceptance, marks the command as completed and converts command to
 * message if message is required
 */
void IncomingMessages::retrieve(FrameSetPtr command, qpid::messaging::Message* message)
{
    if (message) {
        populate(*message, *command);
    }
    const MessageTransferBody* transfer = command->as<MessageTransferBody>(); 
    if (transfer->getAcquireMode() == ACQUIRE_MODE_PRE_ACQUIRED && transfer->getAcceptMode() == ACCEPT_MODE_EXPLICIT) {
        unaccepted.add(command->getId());
    }
    session.markCompleted(command->getId(), false, false);
}

IncomingMessages::MessageTransfer::MessageTransfer(FrameSetPtr c, IncomingMessages& p) : content(c), parent(p) {}

const std::string& IncomingMessages::MessageTransfer::getDestination()
{
    return content->as<MessageTransferBody>()->getDestination();
}
void IncomingMessages::MessageTransfer::retrieve(qpid::messaging::Message* message)
{
    parent.retrieve(content, message);
}

void translate(const FieldTable& from, Variant::Map& to);//implemented in Codecs.cpp

void populateHeaders(qpid::messaging::Message& message, 
                     const DeliveryProperties* deliveryProperties, 
                     const MessageProperties* messageProperties)
{
    if (deliveryProperties) {
        message.setSubject(deliveryProperties->getRoutingKey());
        //TODO: convert other delivery properties
    }
    if (messageProperties) {
        message.setContentType(messageProperties->getContentType());
        if (messageProperties->hasReplyTo()) {
            message.setReplyTo(AddressResolution::convert(messageProperties->getReplyTo()));
        }
        translate(messageProperties->getApplicationHeaders(), message.getHeaders());
        //TODO: convert other message properties
    }
}

void populateHeaders(qpid::messaging::Message& message, const AMQHeaderBody* headers)
{
    populateHeaders(message, headers->get<DeliveryProperties>(), headers->get<MessageProperties>());
}

void populate(qpid::messaging::Message& message, FrameSet& command)
{
    //need to be able to link the message back to the transfer it was delivered by
    //e.g. for rejecting. TODO: hide this from API
    uint32_t commandId = command.getId();
    message.setInternalId(reinterpret_cast<void*>(commandId));
        
    command.getContent(message.getBytes());

    populateHeaders(message, command.getHeaders());
        
    //decode content if necessary
    if (message.getContentType() == ListCodec::contentType) {
        ListCodec codec;
        message.decode(codec);
    } else if (message.getContentType() == MapCodec::contentType) {
        MapCodec codec;
        message.decode(codec);
    }
}


}}} // namespace qpid::client::amqp0_10
