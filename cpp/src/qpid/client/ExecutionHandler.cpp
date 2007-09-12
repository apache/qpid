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
#include "qpid/framing/AMQP_HighestVersion.h"
#include "qpid/framing/all_method_bodies.h"

using namespace qpid::client;
using namespace qpid::framing;
using namespace boost;

bool isMessageMethod(AMQMethodBody* method)
{
    return method->isA<BasicDeliverBody>() || method->isA<MessageTransferBody>() || method->isA<BasicGetOkBody>();
}

bool isMessageMethod(AMQBody* body)
{
    AMQMethodBody* method=body->getMethod();
    return method && isMessageMethod(method);
}

bool isContentFrame(AMQFrame& frame)
{
    AMQBody* body = frame.getBody();
    uint8_t type = body->type();
    return type == HEADER_BODY || type == CONTENT_BODY || isMessageMethod(body); 
}

bool invoke(AMQBody* body, Invocable* target)
{
    AMQMethodBody* method=body->getMethod();
    return method && method->invoke(target);
}

ExecutionHandler::ExecutionHandler(uint64_t _maxFrameSize) : 
    version(framing::highestProtocolVersion), maxFrameSize(_maxFrameSize) {}

//incoming:
void ExecutionHandler::handle(AMQFrame& frame)
{
    AMQBody* body = frame.getBody();
    if (!invoke(body, this)) {
        if (isContentFrame(frame)) {
            if (!arriving) {
                arriving = FrameSet::shared_ptr(new FrameSet(++incoming.hwm));
            }
            arriving->append(frame);
            if (arriving->isComplete()) {
                demux.handle(arriving);
                arriving.reset();
            }
        } else {
            ++incoming.hwm;    
            correlation.receive(body->getMethod());
        }        
    }
}

void ExecutionHandler::complete(uint32_t cumulative, const SequenceNumberSet& range)
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
    sendCompletion();
}

void ExecutionHandler::noop()
{
    //do nothing
}

void ExecutionHandler::result(uint32_t command, const std::string& data)
{
    completion.received(command, data);
}

void ExecutionHandler::sync()
{
    //TODO: implement - need to note the mark requested and then
    //remember to send a response when that point is reached
}

void ExecutionHandler::flushTo(const framing::SequenceNumber& point)
{
    if (point > outgoing.lwm) {
        sendFlushRequest();
    }        
}

void ExecutionHandler::sendFlushRequest()
{
    AMQFrame frame(0, ExecutionFlushBody());
    out(frame);
}

void ExecutionHandler::syncTo(const framing::SequenceNumber& point)
{
    if (point > outgoing.lwm) {
        sendSyncRequest();
    }        
}


void ExecutionHandler::sendSyncRequest()
{
    AMQFrame frame(0, ExecutionSyncBody());
    out(frame);
}

void ExecutionHandler::completed(const SequenceNumber& id, bool cumulative, bool send)
{
    if (id > completionStatus.mark) {
        if (cumulative) {
            completionStatus.update(completionStatus.mark, id);
        } else {
            completionStatus.update(id, id);            
        }
    }
    if (send) {
        sendCompletion();
    }    
}


void ExecutionHandler::sendCompletion()
{
    SequenceNumberSet range;
    completionStatus.collectRanges(range);
    AMQFrame frame(0, ExecutionCompleteBody(version, completionStatus.mark.getValue(), range));
    out(frame);    
}

SequenceNumber ExecutionHandler::send(const AMQBody& command, CompletionTracker::ResultListener l)
{
    SequenceNumber id = ++outgoing.hwm;
    if(l) {
        completion.listenForResult(id, l);
    }
    AMQFrame frame(0/*channel will be filled in be channel handler*/, command);
    out(frame);
    return id;
}

SequenceNumber ExecutionHandler::send(const AMQBody& command, const MethodContent& content, 
                                      CompletionTracker::ResultListener l)
{
    SequenceNumber id = send(command, l);
    sendContent(content);
    return id;
}

void ExecutionHandler::sendContent(const MethodContent& content)
{
    AMQFrame header(0, content.getHeader());
    out(header);

    u_int64_t data_length = content.getData().length();
    if(data_length > 0){
        //frame itself uses 8 bytes
        u_int32_t frag_size = maxFrameSize - 8;
        if(data_length < frag_size){
            AMQFrame frame(0, AMQContentBody(content.getData()));
            out(frame);
        }else{
            u_int32_t offset = 0;
            u_int32_t remaining = data_length - offset;
            while (remaining > 0) {
                u_int32_t length = remaining > frag_size ? frag_size : remaining;
                string frag(content.getData().substr(offset, length));
                AMQFrame frame(0, AMQContentBody(frag));
                out(frame);
                offset += length;
                remaining = data_length - offset;
            }
        }
    }
}
