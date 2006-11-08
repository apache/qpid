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
#include "qpid/client/Channel.h"
#include "qpid/sys/Monitor.h"
#include "qpid/client/Message.h"
#include "qpid/QpidError.h"

using namespace boost;          //to use dynamic_pointer_cast
using namespace qpid::client;
using namespace qpid::framing;
using namespace qpid::sys;

Channel::Channel(bool _transactional, u_int16_t _prefetch) :
    id(0),
    con(0), 
    out(0), 
    incoming(0),
    closed(true),
    prefetch(_prefetch), 
    transactional(_transactional)
{ }

Channel::~Channel(){
    stop();
}

void Channel::setPrefetch(u_int16_t _prefetch){
    prefetch = _prefetch;
    if(con != 0 && out != 0){
        setQos();
    }
}

void Channel::setQos(){
    sendAndReceive(new AMQFrame(id, new BasicQosBody(0, prefetch, false)), basic_qos_ok);
    if(transactional){
        sendAndReceive(new AMQFrame(id, new TxSelectBody()), tx_select_ok);
    }
}

void Channel::declareExchange(Exchange& exchange, bool synch){
    string name = exchange.getName();
    string type = exchange.getType();
    FieldTable args;
    AMQFrame*  frame = new AMQFrame(id, new ExchangeDeclareBody(0, name, type, false, false, false, false, !synch, args));
    if(synch){
        sendAndReceive(frame, exchange_declare_ok);
    }else{
        out->send(frame);
    }
}

void Channel::deleteExchange(Exchange& exchange, bool synch){
    string name = exchange.getName();
    AMQFrame*  frame = new AMQFrame(id, new ExchangeDeleteBody(0, name, false, !synch));
    if(synch){
        sendAndReceive(frame, exchange_delete_ok);
    }else{
        out->send(frame);
    }
}

void Channel::declareQueue(Queue& queue, bool synch){
    string name = queue.getName();
    FieldTable args;
    AMQFrame*  frame = new AMQFrame(id, new QueueDeclareBody(0, name, false, false, 
                                                             queue.isExclusive(), 
                                                             queue.isAutoDelete(), !synch, args));
    if(synch){
        sendAndReceive(frame, queue_declare_ok);
        if(queue.getName().length() == 0){
            QueueDeclareOkBody::shared_ptr response = 
                dynamic_pointer_cast<QueueDeclareOkBody, AMQMethodBody>(responses.getResponse());
            queue.setName(response->getQueue());
        }
    }else{
        out->send(frame);
    }
}

void Channel::deleteQueue(Queue& queue, bool ifunused, bool ifempty, bool synch){
    //ticket, queue, ifunused, ifempty, nowait
    string name = queue.getName();
    AMQFrame*  frame = new AMQFrame(id, new QueueDeleteBody(0, name, ifunused, ifempty, !synch));
    if(synch){
        sendAndReceive(frame, queue_delete_ok);
    }else{
        out->send(frame);
    }
}

void Channel::bind(const Exchange& exchange, const Queue& queue, const std::string& key, const FieldTable& args, bool synch){
    string e = exchange.getName();
    string q = queue.getName();
    AMQFrame*  frame = new AMQFrame(id, new QueueBindBody(0, q, e, key,!synch, args));
    if(synch){
        sendAndReceive(frame, queue_bind_ok);
    }else{
        out->send(frame);
    }
}

void Channel::consume(Queue& queue, std::string& tag, MessageListener* listener, 
                      int ackMode, bool noLocal, bool synch){

    string q = queue.getName();
    AMQFrame* frame = new AMQFrame(id, new BasicConsumeBody(0, q, (string&) tag, noLocal, ackMode == NO_ACK, false, !synch));
    if(synch){
        sendAndReceive(frame, basic_consume_ok);
        BasicConsumeOkBody::shared_ptr response = dynamic_pointer_cast<BasicConsumeOkBody, AMQMethodBody>(responses.getResponse());
        tag = response->getConsumerTag();
    }else{
        out->send(frame);
    }
    Consumer* c = new Consumer();
    c->listener = listener;
    c->ackMode = ackMode;
    c->lastDeliveryTag = 0;
    consumers[tag] = c;
}

void Channel::cancel(std::string& tag, bool synch){
    Consumer* c = consumers[tag];
    if(c->ackMode == LAZY_ACK && c->lastDeliveryTag > 0){
        out->send(new AMQFrame(id, new BasicAckBody(c->lastDeliveryTag, true)));
    }

    AMQFrame*  frame = new AMQFrame(id, new BasicCancelBody((string&) tag, !synch));
    if(synch){
        sendAndReceive(frame, basic_cancel_ok);
    }else{
        out->send(frame);
    }
    consumers.erase(tag);
    if(c != 0){
        delete c;
    }
}

void Channel::cancelAll(){
    for(consumer_iterator i = consumers.begin(); i != consumers.end(); i = consumers.begin()){
        Consumer* c = i->second;
        if((c->ackMode == LAZY_ACK || c->ackMode == AUTO_ACK) && c->lastDeliveryTag > 0){
            out->send(new AMQFrame(id, new BasicAckBody(c->lastDeliveryTag, true)));
        }
        consumers.erase(i);
        delete c;
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

bool Channel::get(Message& msg, const Queue& queue, int ackMode){
    string name = queue.getName();
    AMQFrame*  frame = new AMQFrame(id, new BasicGetBody(0, name, ackMode));
    responses.expect();
    out->send(frame);
    responses.waitForResponse();
    AMQMethodBody::shared_ptr response = responses.getResponse();
    if(basic_get_ok.match(response.get())){
        if(incoming != 0){
            std::cout << "Existing message not complete" << std::endl;
            THROW_QPID_ERROR(PROTOCOL_ERROR + 504, "Existing message not complete");
        }else{
            incoming = new IncomingMessage(dynamic_pointer_cast<BasicGetOkBody, AMQMethodBody>(response));
        }
        retrieve(msg);
        return true;
    }if(basic_get_empty.match(response.get())){
        return false;
    }else{
        THROW_QPID_ERROR(PROTOCOL_ERROR + 500, "Unexpected response to basic.get.");
    }
}

    
void Channel::publish(Message& msg, const Exchange& exchange, const std::string& routingKey, bool mandatory, bool immediate){
    string e = exchange.getName();
    string key = routingKey;

    out->send(new AMQFrame(id, new BasicPublishBody(0, e, key, mandatory, immediate)));
    //break msg up into header frame and content frame(s) and send these
    string data = msg.getData();
    msg.header->setContentSize(data.length());
    AMQBody::shared_ptr body(static_pointer_cast<AMQBody, AMQHeaderBody>(msg.header));
    out->send(new AMQFrame(id, body));
    
    int data_length = data.length();
    if(data_length > 0){
        //TODO fragmentation of messages, need to know max frame size for connection
        int frag_size = con->getMaxFrameSize() - 4;
        if(data_length < frag_size){
            out->send(new AMQFrame(id, new AMQContentBody(data)));
        }else{
            int frag_count = data_length / frag_size;
            for(int i = 0; i < frag_count; i++){
                int pos = i*frag_size;
                int len = i < frag_count - 1 ? frag_size : data_length - pos;
                string frag(data.substr(pos, len));
                out->send(new AMQFrame(id, new AMQContentBody(frag)));          
            }
        }
    }
}
    
void Channel::commit(){
    AMQFrame*  frame = new AMQFrame(id, new TxCommitBody());
    sendAndReceive(frame, tx_commit_ok);
}

void Channel::rollback(){
    AMQFrame*  frame = new AMQFrame(id, new TxRollbackBody());
    sendAndReceive(frame, tx_rollback_ok);
}
    
void Channel::handleMethod(AMQMethodBody::shared_ptr body){
    //channel.flow, channel.close, basic.deliver, basic.return or a response to a synchronous request
    if(responses.isWaiting()){
        responses.signalResponse(body);
    }else if(basic_deliver.match(body.get())){
        if(incoming != 0){
            std::cout << "Existing message not complete [deliveryTag=" << incoming->getDeliveryTag() << "]" << std::endl;
            THROW_QPID_ERROR(PROTOCOL_ERROR + 504, "Existing message not complete");
        }else{
            incoming = new IncomingMessage(dynamic_pointer_cast<BasicDeliverBody, AMQMethodBody>(body));
        }
    }else if(basic_return.match(body.get())){
        if(incoming != 0){
            std::cout << "Existing message not complete" << std::endl;
            THROW_QPID_ERROR(PROTOCOL_ERROR + 504, "Existing message not complete");
        }else{
            incoming = new IncomingMessage(dynamic_pointer_cast<BasicReturnBody, AMQMethodBody>(body));
        }
    }else if(channel_close.match(body.get())){
        con->removeChannel(this);
        //need to signal application that channel has been closed through exception

    }else if(channel_flow.match(body.get())){
        
    }else{
        //signal error
        std::cout << "Unhandled method: " << *body << std::endl;
        THROW_QPID_ERROR(PROTOCOL_ERROR + 504, "Unhandled method");
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

void Channel::stop(){
    {
        Monitor::ScopedLock l(dispatchMonitor);
        closed = true;
        dispatchMonitor.notify();
    }
    dispatcher.join();        
}

void Channel::run(){
    dispatch();
}

void Channel::enqueue(){
    if(incoming->isResponse()){
        Monitor::ScopedLock l(retrievalMonitor);
        retrieved = incoming;
        retrievalMonitor.notify();
    }else{
        Monitor::ScopedLock l(dispatchMonitor);
        messages.push(incoming);
        dispatchMonitor.notify();
    }
    incoming = 0;
}

IncomingMessage* Channel::dequeue(){
    Monitor::ScopedLock l(dispatchMonitor);
    while(messages.empty() && !closed){
        dispatchMonitor.wait();
    }    
    IncomingMessage* msg = 0;
    if(!messages.empty()){
        msg = messages.front();
        messages.pop();
    }
    return msg; 
}

void Channel::deliver(Consumer* consumer, Message& msg){
    //record delivery tag:
    consumer->lastDeliveryTag = msg.getDeliveryTag();

    //allow registered listener to handle the message
    consumer->listener->received(msg);

    //if the handler calls close on the channel or connection while
    //handling this message, then consumer will now have been deleted.
    if(!closed){
        bool multiple(false);
        switch(consumer->ackMode){
        case LAZY_ACK: 
            multiple = true;
            if(++(consumer->count) < prefetch) break;
            //else drop-through
        case AUTO_ACK:
            out->send(new AMQFrame(id, new BasicAckBody(msg.getDeliveryTag(), multiple)));
            consumer->lastDeliveryTag = 0;
        }
    }

    //as it stands, transactionality is entirely orthogonal to ack
    //mode, though the acks will not be processed by the broker under
    //a transaction until it commits.
}

void Channel::dispatch(){
    while(!closed){
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
                
                if(consumers[tag] == 0){
                    //signal error
                    std::cout << "Unknown consumer: " << tag << std::endl;
                }else{
                    deliver(consumers[tag], msg);
                }
            }
            delete incomingMsg;
        }
    }
}

void Channel::setReturnedMessageHandler(ReturnedMessageHandler* handler){
    returnsHandler = handler;
}

void Channel::sendAndReceive(AMQFrame* frame, const AMQMethodBody& body){
    responses.expect();
    out->send(frame);
    responses.receive(body);
}

void Channel::close(){
    if(con != 0){
        con->closeChannel(this);
    }
}
