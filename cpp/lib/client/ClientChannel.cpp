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
#include <ClientChannel.h>
#include <sys/Monitor.h>
#include <ClientMessage.h>
#include <QpidError.h>
#include <MethodBodyInstances.h>
#include "Connection.h"
#include "AMQP_ServerProxy.h"

// FIXME aconway 2007-01-26: Evaluate all throws, ensure consistent
// handling of errors that should close the connection or the channel.
// Make sure the user thread receives a connection in each case.
//

using namespace boost;          //to use dynamic_pointer_cast
using namespace qpid::client;
using namespace qpid::framing;
using namespace qpid::sys;

const std::string Channel::OK("OK");

Channel::Channel(bool _transactional, u_int16_t _prefetch) :
    connection(0), 
    incoming(0),
    prefetch(_prefetch), 
    transactional(_transactional)
{ }

Channel::~Channel(){
    close();
}

AMQP_ServerProxy& Channel::brokerProxy() {
    assert(proxy.get());
    return *proxy;
}

void Channel::open(ChannelId id, Connection& con)
{
    if (isOpen())
        THROW_QPID_ERROR(INTERNAL_ERROR, "Attempt to re-open channel "+id);
    connection = &con;
    init(id, con, con.getVersion()); // ChannelAdapter initialization.
    proxy.reset(new AMQP_ServerProxy(*this));
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
    // TODO aconway 2007-01-26: Move client over to proxy model,
    // symmetric with server.
    ConnectionTuneBody::shared_ptr proposal =
        sendAndReceive<ConnectionTuneBody>(
            new ConnectionStartOkBody(
                version, responses.getRequestId(), props, mechanism, response, locale));

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
    
    u_int16_t heartbeat = proposal->getHeartbeat();
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
        std::cout << "Received redirection to " << redirect->getHost()
                  << std::endl;
    } else {
        THROW_QPID_ERROR(PROTOCOL_ERROR, "Bad response");
    }
}
    
bool Channel::isOpen() const { return connection; }

void Channel::setPrefetch(u_int16_t _prefetch){
    prefetch = _prefetch;
    setQos();
}

void Channel::setQos(){
    sendAndReceive<BasicQosOkBody>(
        new BasicQosBody(version, 0, prefetch, false));
    if(transactional){
        sendAndReceive<TxSelectOkBody>(new TxSelectBody(version));
    }
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
    if (synch) {
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

void Channel::consume(
    Queue& queue, std::string& tag, MessageListener* listener, 
    int ackMode, bool noLocal, bool synch, const FieldTable* fields)
{
    string q = queue.getName();
    sendAndReceiveSync<BasicConsumeOkBody>(
        synch,
        new BasicConsumeBody(
            version, 0, q, tag, noLocal, ackMode == NO_ACK, false, !synch,
            fields ? *fields : FieldTable()));
    if (synch) {
        BasicConsumeOkBody::shared_ptr response =
            shared_polymorphic_downcast<BasicConsumeOkBody>(
                responses.getResponse());
        tag = response->getConsumerTag();
    }
    Consumer& c = consumers[tag];
    c.listener = listener;
    c.ackMode = ackMode;
    c.lastDeliveryTag = 0;
}

void Channel::cancel(const std::string& tag, bool synch) {
    ConsumerMap::iterator i = consumers.find(tag);
    if (i != consumers.end()) {
        Consumer& c = i->second;
        if(c.ackMode == LAZY_ACK && c.lastDeliveryTag > 0) 
            send(new BasicAckBody(version, c.lastDeliveryTag, true));
        sendAndReceiveSync<BasicCancelOkBody>(
            synch, new BasicCancelBody(version, tag, !synch));
        consumers.erase(tag);
    }
}

void Channel::cancelAll(){
    while(!consumers.empty()) {
        Consumer c = consumers.begin()->second;
        consumers.erase(consumers.begin());
        if ((c.ackMode == LAZY_ACK || c.ackMode == AUTO_ACK)
            && c.lastDeliveryTag > 0)
        {
            // Let exceptions propagate, if one fails no point
            // trying the rest. NB no memory leaks if we do,
            // ConsumerMap holds values, not pointers.
            // 
            send(new BasicAckBody(version, c.lastDeliveryTag, true));
        }
    }
}

void Channel::retrieve(Message& msg){
    Monitor::ScopedLock l(retrievalMonitor);
    while(retrieved == 0){
        retrievalMonitor.wait();
    }

    msg.header = retrieved->getHeader();
    msg.deliveryTag = retrieved->getDeliveryTag();
    retrieved->getData(msg.data);
    delete retrieved;
    retrieved = 0;
}

bool Channel::get(Message& msg, const Queue& queue, int ackMode) {
    string name = queue.getName();
    responses.expect();
    send(new BasicGetBody(version, 0, name, ackMode));
    responses.waitForResponse();
    AMQMethodBody::shared_ptr response = responses.getResponse();
    if(response->isA<BasicGetOkBody>()) {
        if(incoming != 0){
            std::cout << "Existing message not complete" << std::endl;
            // FIXME aconway 2007-01-26: close the connection? the channel?
            THROW_QPID_ERROR(PROTOCOL_ERROR + 504, "Existing message not complete");
        }else{
            incoming = new IncomingMessage(dynamic_pointer_cast<BasicGetOkBody, AMQMethodBody>(response));
        }
        retrieve(msg);
        return true;
    }if(response->isA<BasicGetEmptyBody>()){
        return false;
    }else{
        // FIXME aconway 2007-01-26: must close the connection.
        THROW_QPID_ERROR(PROTOCOL_ERROR+504, "Unexpected frame");
    }
}

    
void Channel::publish(Message& msg, const Exchange& exchange, const std::string& routingKey, bool mandatory, bool immediate){
    // FIXME aconway 2007-01-30: Rework for message class.
    
    string e = exchange.getName();
    string key = routingKey;

    send(new BasicPublishBody(version, 0, e, key, mandatory, immediate));
    //break msg up into header frame and content frame(s) and send these
    string data = msg.getData();
    msg.header->setContentSize(data.length());
    send(msg.header);
    
    u_int64_t data_length = data.length();
    if(data_length > 0){
        u_int32_t frag_size = connection->getMaxFrameSize() - 8;//frame itself uses 8 bytes
        if(data_length < frag_size){
            send(new AMQContentBody(data));
        }else{
            u_int32_t offset = 0;
            u_int32_t remaining = data_length - offset;
            while (remaining > 0) {
                u_int32_t length = remaining > frag_size ? frag_size : remaining;
                string frag(data.substr(offset, length));
                send(new AMQContentBody(frag));                          
                
                offset += length;
                remaining = data_length - offset;
            }
        }
    }
}
    
void Channel::commit(){
    sendAndReceive<TxCommitOkBody>(new TxCommitBody(version));
}

void Channel::rollback(){
    sendAndReceive<TxRollbackOkBody>(new TxRollbackBody(version));
}

void Channel::handleMethodInContext(
    AMQMethodBody::shared_ptr body, const MethodContext&)
{
    //channel.flow, channel.close, basic.deliver, basic.return or a
    //response to a synchronous request
    if(responses.isWaiting()){
        responses.signalResponse(body);
    }else if(body->isA<BasicDeliverBody>()) {
        if(incoming != 0){
            THROW_QPID_ERROR(PROTOCOL_ERROR + 504, "Existing message not complete");
            std::cout << "Existing message not complete [deliveryTag=" << incoming->getDeliveryTag() << "]" << std::endl;
            THROW_QPID_ERROR(PROTOCOL_ERROR + 504, "Existing message not complete");
        }else{
            incoming = new IncomingMessage(dynamic_pointer_cast<BasicDeliverBody, AMQMethodBody>(body));
        }
    }else if(body->isA<BasicReturnBody>()){
        if(incoming != 0){
            std::cout << "Existing message not complete" << std::endl;
            THROW_QPID_ERROR(PROTOCOL_ERROR + 504, "Existing message not complete");
        }else{
            incoming = new IncomingMessage(dynamic_pointer_cast<BasicReturnBody, AMQMethodBody>(body));
        }
    }else if(body->isA<ChannelCloseBody>()){
        peerClose(shared_polymorphic_downcast<ChannelCloseBody>(body));
    }else if(body->isA<ChannelFlowBody>()){
        // TODO aconway 2007-01-24: 
    }else if(body->isA<ConnectionCloseBody>()){
        connection->close();
    }else{
        connection->close(
            504, "Unrecognised method",
            body->amqpClassId(), body->amqpMethodId());
    }
}

void Channel::handleHeader(AMQHeaderBody::shared_ptr body){
    if(incoming == 0){
        //handle invalid frame sequence
        std::cout << "Invalid message sequence: got header before return or deliver." << std::endl;
        THROW_QPID_ERROR(PROTOCOL_ERROR + 504, "Invalid message sequence: got header before return or deliver.");
    }else{
        incoming->setHeader(body);
        if(incoming->isComplete()){ 
            enqueue();            
        }
    }           
}
    
void Channel::handleContent(AMQContentBody::shared_ptr body){
    if(incoming == 0){
        //handle invalid frame sequence
        std::cout << "Invalid message sequence: got content before return or deliver." << std::endl;
        THROW_QPID_ERROR(PROTOCOL_ERROR + 504, "Invalid message sequence: got content before return or deliver.");
    }else{
        incoming->addContent(body);
        if(incoming->isComplete()){
            enqueue();
        }
    }           
}
    
void Channel::handleHeartbeat(AMQHeartbeatBody::shared_ptr /*body*/){
    THROW_QPID_ERROR(PROTOCOL_ERROR + 504, "Channel received heartbeat");
}

void Channel::start(){
    dispatcher = Thread(this);
}

void Channel::run(){
    dispatch();
}

void Channel::enqueue(){
    Monitor::ScopedLock l(retrievalMonitor);
    if(incoming->isResponse()){
        retrieved = incoming;
        retrievalMonitor.notify();
    }else{
        messages.push(incoming);
        dispatchMonitor.notify();
    }
    incoming = 0;
}

IncomingMessage* Channel::dequeue(){
    Monitor::ScopedLock l(dispatchMonitor);
    while(messages.empty() && isOpen()){
        dispatchMonitor.wait();
    }    
    IncomingMessage* msg = 0;
    if(!messages.empty()){
        msg = messages.front();
        messages.pop();
    }
    return msg; 
}

void Channel::deliver(Consumer& consumer, Message& msg){
    //record delivery tag:
    consumer.lastDeliveryTag = msg.getDeliveryTag();

    //allow registered listener to handle the message
    consumer.listener->received(msg);

    //if the handler calls close on the channel or connection while
    //handling this message, then consumer will now have been deleted.
    if(isOpen()){
        bool multiple(false);
        switch(consumer.ackMode){
          case LAZY_ACK: 
            multiple = true;
            if(++(consumer.count) < prefetch) break;
            //else drop-through
          case AUTO_ACK:
            send(new BasicAckBody(version, msg.getDeliveryTag(), multiple));
            consumer.lastDeliveryTag = 0;
        }
    }

    //as it stands, transactionality is entirely orthogonal to ack
    //mode, though the acks will not be processed by the broker under
    //a transaction until it commits.
}

void Channel::dispatch(){
    while(isOpen()){
        IncomingMessage* incomingMsg = dequeue();
        if(incomingMsg){
            //Note: msg is currently only valid for duration of this call
            Message msg(incomingMsg->getHeader());
            incomingMsg->getData(msg.data);
            if(incomingMsg->isReturn()){
                if(returnsHandler == 0){
                    //print warning to log/console
                    std::cout << "Message returned: " << msg.getData() << std::endl;
                }else{
                    returnsHandler->returned(msg);
                }
            }else{
                msg.deliveryTag = incomingMsg->getDeliveryTag();
                std::string tag = incomingMsg->getConsumerTag();
                
                if(consumers.find(tag) == consumers.end())
                    std::cout << "Unknown consumer: " << tag << std::endl;
                else
                    deliver(consumers[tag], msg);
            }
            delete incomingMsg;
        }
    }
}

void Channel::setReturnedMessageHandler(ReturnedMessageHandler* handler){
    returnsHandler = handler;
}

// Close called by local application.
void Channel::close(
    u_int16_t code, const std::string& text,
    ClassId classId, MethodId methodId)
{
    if (getId() != 0 && isOpen()) {
        try {
            sendAndReceive<ChannelCloseOkBody>(
                new ChannelCloseBody(version, code, text, classId, methodId));
            cancelAll();
            closeInternal();
        } catch (...) {
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
    assert(isOpen());
    {
        Monitor::ScopedLock l(dispatchMonitor);
        static_cast<ConnectionForChannel*>(connection)->erase(getId()); 
        connection = 0;
        // A 0 response means we are closed.
        responses.signalResponse(AMQMethodBody::shared_ptr());
        dispatchMonitor.notify();
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


