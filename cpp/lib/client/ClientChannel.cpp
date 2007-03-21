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
#include <ClientChannel.h>
#include <sys/Monitor.h>
#include <ClientMessage.h>
#include <QpidError.h>
#include <MethodBodyInstances.h>
#include "Connection.h"
#include "BasicMessageChannel.h"
// FIXME aconway 2007-03-21: 
//#include "MessageMessageChannel.h"

// FIXME aconway 2007-01-26: Evaluate all throws, ensure consistent
// handling of errors that should close the connection or the channel.
// Make sure the user thread receives a connection in each case.
//
using namespace std;
using namespace boost;
using namespace qpid::client;
using namespace qpid::framing;
using namespace qpid::sys;

Channel::Channel(bool _transactional, u_int16_t _prefetch,
                  MessageChannel* impl) :
    // FIXME aconway 2007-03-21: MessageMessageChannel
    messaging(impl ? impl : new BasicMessageChannel(*this)),
    connection(0), 
    prefetch(_prefetch), 
    transactional(_transactional)
{ }

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
        sendAndReceive<ChannelOpenOkBody>(new ChannelOpenBody(version, oob));
}

void Channel::protocolInit(
    const std::string& uid, const std::string& pwd, const std::string& vhost) {
    assert(connection);
    responses.expect();
    connection->connector->init(); // Send ProtocolInit block.
    responses.receive<ConnectionStartBody>();
    
    FieldTable props;
    string mechanism("PLAIN");
    string response = ((char)0) + uid + ((char)0) + pwd;
    string locale("en_US");
    ConnectionTuneBody::shared_ptr proposal =
        sendAndReceive<ConnectionTuneBody>(
            new ConnectionStartOkBody(
                version, responses.getRequestId(), props, mechanism,
                response, locale));

    /**
     * Assume for now that further challenges will not be required
     //receive connection.secure
     responses.receive(connection_secure));
     //send connection.secure-ok
     connection->send(new AMQFrame(0, new ConnectionSecureOkBody(response)));
    **/

    send(new ConnectionTuneOkBody(
             version, responses.getRequestId(), proposal->getChannelMax(), connection->getMaxFrameSize(),
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
    responses.waitForResponse();
    if(responses.validate<ConnectionOpenOkBody>()) {
        //ok
    }else if(responses.validate<ConnectionRedirectBody>()){
        //ignore for now
        ConnectionRedirectBody::shared_ptr redirect(
            shared_polymorphic_downcast<ConnectionRedirectBody>(
                responses.getResponse()));
        cout << "Received redirection to " << redirect->getHost()
             << endl;
    } else {
        THROW_QPID_ERROR(PROTOCOL_ERROR, "Bad response");
    }
}
    
bool Channel::isOpen() const { return connection; }

void Channel::setQos() {
    messaging->setQos();
    // FIXME aconway 2007-02-22: message
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
        new ExchangeDeclareBody(
            version, 0, name, type, false, false, false, false, !synch, args));
}

void Channel::deleteExchange(Exchange& exchange, bool synch){
    string name = exchange.getName();
    sendAndReceiveSync<ExchangeDeleteOkBody>(
        synch,
        new ExchangeDeleteBody(version, 0, name, false, !synch));
}

void Channel::declareQueue(Queue& queue, bool synch){
    string name = queue.getName();
    FieldTable args;
    sendAndReceiveSync<QueueDeclareOkBody>(
        synch,
        new QueueDeclareBody(
            version, 0, name, false/*passive*/, queue.isDurable(),
            queue.isExclusive(), queue.isAutoDelete(), !synch, args));        
    if(synch){
        if(queue.getName().length() == 0){
            QueueDeclareOkBody::shared_ptr response = 
                shared_polymorphic_downcast<QueueDeclareOkBody>(
                    responses.getResponse());
            queue.setName(response->getQueue());
        }
    }
}

void Channel::deleteQueue(Queue& queue, bool ifunused, bool ifempty, bool synch){
    //ticket, queue, ifunused, ifempty, nowait
    string name = queue.getName();
    sendAndReceiveSync<QueueDeleteOkBody>(
        synch,
        new QueueDeleteBody(version, 0, name, ifunused, ifempty, !synch));
}

void Channel::bind(const Exchange& exchange, const Queue& queue, const std::string& key, const FieldTable& args, bool synch){
    string e = exchange.getName();
    string q = queue.getName();
    sendAndReceiveSync<QueueBindOkBody>(
        synch,
        new QueueBindBody(version, 0, q, e, key,!synch, args));
}

void Channel::commit(){
    sendAndReceive<TxCommitOkBody>(new TxCommitBody(version));
}

void Channel::rollback(){
    sendAndReceive<TxRollbackOkBody>(new TxRollbackBody(version));
}

void Channel::handleMethodInContext(
    AMQMethodBody::shared_ptr method, const MethodContext&)
{
    if(responses.isWaiting()) {
        responses.signalResponse(method);
        return;
    }
    try {
        switch (method->amqpClassId()) {
          case BasicDeliverBody::CLASS_ID: messaging->handle(method); break;
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
                    new ChannelCloseBody(
                        version, code, text, classId, methodId));
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
    // FIXME aconway 2007-01-26: How to throw the proper exception
    // to the application thread?
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

void Channel::sendAndReceive(AMQMethodBody* toSend, ClassId c, MethodId m)
{
    responses.expect();
    send(toSend);
    responses.receive(c, m);
}

void Channel::sendAndReceiveSync(
    bool sync, AMQMethodBody* body, ClassId c, MethodId m)
{
    if(sync)
        sendAndReceive(body, c, m);
    else
        send(body);
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

