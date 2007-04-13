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
#include <iostream>
#include "ClientChannel.h"
#include "qpid/sys/Monitor.h"
#include "ClientMessage.h"
#include "qpid/QpidError.h"
#include "MethodBodyInstances.h"
#include "Connection.h"
#include "BasicMessageChannel.h"
#include "MessageMessageChannel.h"

// FIXME aconway 2007-01-26: Evaluate all throws, ensure consistent
// handling of errors that should close the connection or the channel.
// Make sure the user thread receives a connection in each case.
//
using namespace std;
using namespace boost;
using namespace qpid::client;
using namespace qpid::framing;
using namespace qpid::sys;

Channel::Channel(bool _transactional, u_int16_t _prefetch, InteropMode mode) :
    connection(0), prefetch(_prefetch), transactional(_transactional)
{
    switch (mode) {
      case AMQP_08: messaging.reset(new BasicMessageChannel(*this)); break;
      case AMQP_09: messaging.reset(new MessageMessageChannel(*this)); break;
      default: assert(0); QPID_ERROR(INTERNAL_ERROR, "Invalid interop-mode.");
    }
}

Channel::~Channel(){
    close();
}

void Channel::open(ChannelId id, Connection& con)
{
    if (isOpen())
        THROW_QPID_ERROR(INTERNAL_ERROR, "Attempt to re-open channel "+id);
    connection = &con;
    init(id, con, con.getVersion()); // ChannelAdapter initialization.
    string oob;
    if (id != 0) 
        sendAndReceive<ChannelOpenOkBody>(make_shared_ptr(new ChannelOpenBody(version, oob)));
}

void Channel::protocolInit(
    const std::string& uid, const std::string& pwd, const std::string& vhost) {
    assert(connection);
    responses.expect();
    connection->connector->init(); // Send ProtocolInit block.
    ConnectionStartBody::shared_ptr connectionStart =
        responses.receive<ConnectionStartBody>();
    
    FieldTable props;
    string mechanism("PLAIN");
    string response = ((char)0) + uid + ((char)0) + pwd;
    string locale("en_US");
    ConnectionTuneBody::shared_ptr proposal =
        sendAndReceive<ConnectionTuneBody>(
            make_shared_ptr(new ConnectionStartOkBody(
                version, connectionStart->getRequestId(),
                props, mechanism,
                response, locale)));

    /**
     * Assume for now that further challenges will not be required
     //receive connection.secure
     responses.receive(connection_secure));
     //send connection.secure-ok
     connection->send(new AMQFrame(0, new ConnectionSecureOkBody(response)));
    **/

    send(new ConnectionTuneOkBody(
             version, proposal->getRequestId(),
             proposal->getChannelMax(), connection->getMaxFrameSize(),
             proposal->getHeartbeat()));
    
    uint16_t heartbeat = proposal->getHeartbeat();
    connection->connector->setReadTimeout(heartbeat * 2);
    connection->connector->setWriteTimeout(heartbeat);

    // Send connection open.
    std::string capabilities;
    responses.expect();
    send(new ConnectionOpenBody(version, vhost, capabilities, true));
    //receive connection.open-ok (or redirect, but ignore that for now
    //esp. as using force=true).
    AMQMethodBody::shared_ptr openResponse = responses.receive();
    if(openResponse->isA<ConnectionOpenOkBody>()) {
        //ok
    }else if(openResponse->isA<ConnectionRedirectBody>()){
        //ignore for now
        ConnectionRedirectBody::shared_ptr redirect(
            shared_polymorphic_downcast<ConnectionRedirectBody>(openResponse));
        cout << "Received redirection to " << redirect->getHost()
             << endl;
    } else {
        THROW_QPID_ERROR(PROTOCOL_ERROR, "Bad response to Connection.open");
    }
}
    
bool Channel::isOpen() const { return connection; }

void Channel::setQos() {
    messaging->setQos();
}

void Channel::setPrefetch(uint16_t _prefetch){
    prefetch = _prefetch;
    setQos();
}

void Channel::declareExchange(Exchange& exchange, bool synch){
    string name = exchange.getName();
    string type = exchange.getType();
    FieldTable args;
    sendAndReceiveSync<ExchangeDeclareOkBody>(
        synch,
        make_shared_ptr(new ExchangeDeclareBody(
                            version, 0, name, type, false, false, false, false, !synch, args)));
}

void Channel::deleteExchange(Exchange& exchange, bool synch){
    string name = exchange.getName();
    sendAndReceiveSync<ExchangeDeleteOkBody>(
        synch,
        make_shared_ptr(new ExchangeDeleteBody(version, 0, name, false, !synch)));
}

void Channel::declareQueue(Queue& queue, bool synch){
    string name = queue.getName();
    FieldTable args;
    QueueDeclareOkBody::shared_ptr response =
        sendAndReceiveSync<QueueDeclareOkBody>(
            synch,
            make_shared_ptr(new QueueDeclareBody(
                version, 0, name, false/*passive*/, queue.isDurable(),
                queue.isExclusive(), queue.isAutoDelete(), !synch, args)));
    if(synch) {
        if(queue.getName().length() == 0)
            queue.setName(response->getQueue());
    }
}

void Channel::deleteQueue(Queue& queue, bool ifunused, bool ifempty, bool synch){
    //ticket, queue, ifunused, ifempty, nowait
    string name = queue.getName();
    sendAndReceiveSync<QueueDeleteOkBody>(
        synch,
        make_shared_ptr(new QueueDeleteBody(version, 0, name, ifunused, ifempty, !synch)));
}

void Channel::bind(const Exchange& exchange, const Queue& queue, const std::string& key, const FieldTable& args, bool synch){
    string e = exchange.getName();
    string q = queue.getName();
    sendAndReceiveSync<QueueBindOkBody>(
        synch,
        make_shared_ptr(new QueueBindBody(version, 0, q, e, key,!synch, args)));
}

void Channel::commit(){
    sendAndReceive<TxCommitOkBody>(make_shared_ptr(new TxCommitBody(version)));
}

void Channel::rollback(){
    sendAndReceive<TxRollbackOkBody>(make_shared_ptr(new TxRollbackBody(version)));
}

void Channel::handleMethodInContext(
    AMQMethodBody::shared_ptr method, const MethodContext&)
{
    // TODO aconway 2007-03-23: Special case for consume OK as it
    // is both an expected response and needs handling in this thread.
    // Need to review & reationalize the client-side processing model.
    if (method->isA<BasicConsumeOkBody>()) {
        messaging->handle(method);
        responses.signalResponse(method);
        return;
    }
    if(responses.isWaiting()) {
        responses.signalResponse(method);
        return;
    }
    try {
        switch (method->amqpClassId()) {
          case MessageOkBody::CLASS_ID: 
          case BasicGetOkBody::CLASS_ID: messaging->handle(method); break;
          case ChannelCloseBody::CLASS_ID: handleChannel(method); break;
          case ConnectionCloseBody::CLASS_ID: handleConnection(method); break;
          default: throw UnknownMethod();
        }
    }
    catch (const UnknownMethod&) {
                connection->close(
                    504, "Unknown method",
                    method->amqpClassId(), method->amqpMethodId());
            }
        }

void Channel::handleChannel(AMQMethodBody::shared_ptr method) {
    switch (method->amqpMethodId()) {
      case ChannelCloseBody::METHOD_ID:
        peerClose(shared_polymorphic_downcast<ChannelCloseBody>(method));
        return;
      case ChannelFlowBody::METHOD_ID:
        // FIXME aconway 2007-02-22: Not yet implemented.
        return;
    }
    throw UnknownMethod();
}

void Channel::handleConnection(AMQMethodBody::shared_ptr method) {
    if (method->amqpMethodId() == ConnectionCloseBody::METHOD_ID) {
        connection->close();
        return;
    } 
    throw UnknownMethod();
}

void Channel::handleHeader(AMQHeaderBody::shared_ptr body){
    messaging->handle(body);
}
    
void Channel::handleContent(AMQContentBody::shared_ptr body){
    messaging->handle(body);
}

void Channel::handleHeartbeat(AMQHeartbeatBody::shared_ptr /*body*/){
    THROW_QPID_ERROR(PROTOCOL_ERROR + 504, "Channel received heartbeat");
}

void Channel::start(){
    dispatcher = Thread(*messaging);
}

// Close called by local application.
void Channel::close(
    uint16_t code, const std::string& text,
    ClassId classId, MethodId methodId)
{
    if (isOpen()) {
        try {
            if (getId() != 0) {
                sendAndReceive<ChannelCloseOkBody>(
                    make_shared_ptr(new ChannelCloseBody(
                                        version, code, text, classId, methodId)));
            }
            static_cast<ConnectionForChannel*>(connection)->erase(getId()); 
            closeInternal();
        } catch (...) {
            static_cast<ConnectionForChannel*>(connection)->erase(getId()); 
            closeInternal();
            throw;
        }
    }
}

// Channel closed by peer.
void Channel::peerClose(ChannelCloseBody::shared_ptr) {
    assert(isOpen());
    closeInternal();
}

void Channel::closeInternal() {
    if (isOpen());
    {
        messaging->close();
        connection = 0;
        // A 0 response means we are closed.
        responses.signalResponse(AMQMethodBody::shared_ptr());
    }
    dispatcher.join();        
}

AMQMethodBody::shared_ptr Channel::sendAndReceive(
    AMQMethodBody::shared_ptr toSend, ClassId c, MethodId m)
{
    responses.expect();
    send(toSend);
    return responses.receive(c, m);
}

AMQMethodBody::shared_ptr Channel::sendAndReceiveSync(
    bool sync, AMQMethodBody::shared_ptr body, ClassId c, MethodId m)
{
    if(sync)
        return sendAndReceive(body, c, m);
    else {
        send(body);
        return AMQMethodBody::shared_ptr();
    }
}

void Channel::consume(
    Queue& queue, std::string& tag, MessageListener* listener, 
    AckMode ackMode, bool noLocal, bool synch, const FieldTable* fields) {
    messaging->consume(queue, tag, listener, ackMode, noLocal, synch, fields);
}
        
void Channel::cancel(const std::string& tag, bool synch) {
    messaging->cancel(tag, synch);
}

bool Channel::get(Message& msg, const Queue& queue, AckMode ackMode) {
    return messaging->get(msg, queue, ackMode);
}

void Channel::publish(const Message& msg, const Exchange& exchange,
                      const std::string& routingKey, 
                      bool mandatory, bool immediate) {
    messaging->publish(msg, exchange, routingKey, mandatory, immediate);
}

void Channel::setReturnedMessageHandler(ReturnedMessageHandler* handler) {
    messaging->setReturnedMessageHandler(handler);
}

void Channel::run() {
    messaging->run();
}

