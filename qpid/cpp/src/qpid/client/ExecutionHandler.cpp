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

#include "ExecutionHandler.h"
#include "qpid/Exception.h"
#include "qpid/framing/BasicDeliverBody.h"
#include "qpid/framing/MessageTransferBody.h"

using namespace qpid::client;
using namespace qpid::framing;
using namespace boost;

bool isMessageMethod(AMQMethodBody::shared_ptr method)
{
    return method->isA<BasicDeliverBody>() || method->isA<MessageTransferBody>() || method->isA<BasicGetOkBody>();
}

bool isMessageMethod(AMQBody::shared_ptr body)
{
    return body->type() == METHOD_BODY && isMessageMethod(shared_polymorphic_cast<AMQMethodBody>(body));
}

bool isContentFrame(AMQFrame& frame)
{
    AMQBody::shared_ptr body = frame.getBody();
    uint8_t type = body->type();
    return type == HEADER_BODY || type == CONTENT_BODY || isMessageMethod(body); 
}

bool invoke(AMQBody::shared_ptr body, Invocable* target)
{
    return body->type() == METHOD_BODY && shared_polymorphic_cast<AMQMethodBody>(body)->invoke(target);
}

ExecutionHandler::ExecutionHandler(uint64_t _maxFrameSize) : 
    version(framing::highestProtocolVersion), maxFrameSize(_maxFrameSize) {}

//incoming:
void ExecutionHandler::handle(AMQFrame& frame)
{
    AMQBody::shared_ptr body = frame.getBody();
    if (!invoke(body, this)) {
        if (isContentFrame(frame)) {
            if (!arriving) {
                arriving = ReceivedContent::shared_ptr(new ReceivedContent(++incoming.hwm));
            }
            arriving->append(body);
            if (arriving->isComplete()) {
                received.push(arriving);
                arriving.reset();
            }
        } else {
            ++incoming.hwm;    
            correlation.receive(shared_polymorphic_cast<AMQMethodBody>(body));
        }        
    }
}

void ExecutionHandler::complete(uint32_t cumulative, SequenceNumberSet range)
{
    SequenceNumber mark(cumulative);
    if (outgoing.lwm < mark) {
        outgoing.lwm = mark;
        completion.completed(outgoing.lwm);
    }
    if (range.size() % 2) { //must be even number        
        throw ConnectionException(530, "Received odd number of elements in ranged mark");
    } else {
        //TODO: need to manage (record and accumulate) ranges such
        //that we can implictly move the mark when appropriate

        //TODO: signal listeners of early notification?         
    }
}

void ExecutionHandler::flush()
{
    //send completion
    incoming.lwm = incoming.hwm;
    //make_shared_ptr(new ExecutionCompleteBody(getVersion(), incoming.hwm.getValue(), SequenceNumberSet())));
}

void ExecutionHandler::sendFlush()
{
    AMQFrame frame(version, 0, make_shared_ptr(new ExecutionFlushBody(version)));
    out(frame);        
}

void ExecutionHandler::send(AMQBody::shared_ptr command, CompletionTracker::Listener f, Correlator::Listener g)
{
    //allocate id:
    ++outgoing.hwm;
    //register listeners if necessary:
    if (f) {
        completion.listen(outgoing.hwm, f);
    }
    if (g) {
        correlation.listen(g);
    }

    AMQFrame frame(version, 0/*id will be filled in be channel handler*/, command);
    out(frame);
}

void ExecutionHandler::sendContent(AMQBody::shared_ptr command, const BasicHeaderProperties& headers, const std::string& data, 
                                   CompletionTracker::Listener f, Correlator::Listener g)
{
    send(command, f, g);

    AMQHeaderBody::shared_ptr header(new AMQHeaderBody(BASIC));
    BasicHeaderProperties::copy(*static_cast<BasicHeaderProperties*>(header->getProperties()), headers);
    header->setContentSize(data.size());
    AMQFrame h(version, 0, header);
    out(h);

    u_int64_t data_length = data.length();
    if(data_length > 0){
        //frame itself uses 8 bytes
        u_int32_t frag_size = maxFrameSize - 8;
        if(data_length < frag_size){
            AMQFrame frame(version, 0, make_shared_ptr(new AMQContentBody(data)));
            out(frame);
        }else{
            u_int32_t offset = 0;
            u_int32_t remaining = data_length - offset;
            while (remaining > 0) {
                u_int32_t length = remaining > frag_size ? frag_size : remaining;
                string frag(data.substr(offset, length));
                AMQFrame frame(version, 0, make_shared_ptr(new AMQContentBody(frag)));
                out(frame);
                offset += length;
                remaining = data_length - offset;
            }
        }
    }
}
