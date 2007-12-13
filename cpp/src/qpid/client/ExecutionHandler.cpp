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
#include "qpid/framing/ServerInvoker.h"

using namespace qpid::client;
using namespace qpid::framing;
using namespace boost;
using qpid::sys::Mutex;

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

ExecutionHandler::ExecutionHandler(uint64_t _maxFrameSize) : 
    version(framing::highestProtocolVersion), maxFrameSize(_maxFrameSize) {}

//incoming:
void ExecutionHandler::handle(AMQFrame& frame)
{
    if (!invoke(*this, *frame.getBody())) {
        if (!arriving) {
            arriving = FrameSet::shared_ptr(new FrameSet(++incomingCounter));
        }
        arriving->append(frame);
        if (arriving->isComplete()) {
            if (arriving->isContentBearing() || !correlation.receive(arriving->getMethod())) {
                demux.handle(arriving);
            }        
            arriving.reset();
        }
    }
}

void ExecutionHandler::complete(uint32_t cumulative, const SequenceNumberSet& range)
{
    if (range.size() % 2) { //must be even number        
        throw NotAllowedException(QPID_MSG("Received odd number of elements in ranged mark"));
    } else {
        SequenceNumber mark(cumulative);        
        {
            Mutex::ScopedLock l(lock);
            outgoingCompletionStatus.update(mark, range);
        }
        if (completionListener) completionListener();
        completion.completed(outgoingCompletionStatus.mark);
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
    Mutex::ScopedLock l(lock);
    if (point > outgoingCompletionStatus.mark) {
        sendFlushRequest();
    }        
}

void ExecutionHandler::sendFlushRequest()
{
    Mutex::ScopedLock l(lock);
    AMQFrame frame(in_place<ExecutionFlushBody>());
    out(frame);
}

void ExecutionHandler::syncTo(const framing::SequenceNumber& point)
{
    Mutex::ScopedLock l(lock);
    if (point > outgoingCompletionStatus.mark) {
        sendSyncRequest();
    }        
}


void ExecutionHandler::sendSyncRequest()
{
    Mutex::ScopedLock l(lock);
    AMQFrame frame(in_place<ExecutionSyncBody>());
    out(frame);
}

void ExecutionHandler::completed(const SequenceNumber& id, bool cumulative, bool send)
{
    {
        Mutex::ScopedLock l(lock);
        if (id > incomingCompletionStatus.mark) {
            if (cumulative) {
                incomingCompletionStatus.update(incomingCompletionStatus.mark, id);
            } else {
                incomingCompletionStatus.update(id, id);            
            }
        }
    }
    if (send) {
        sendCompletion();
    }    
}


void ExecutionHandler::sendCompletion()
{
    Mutex::ScopedLock l(lock);
    SequenceNumberSet range;
    incomingCompletionStatus.collectRanges(range);
    AMQFrame frame(
        in_place<ExecutionCompleteBody>(
            version, incomingCompletionStatus.mark.getValue(), range));
    out(frame);    
}

SequenceNumber ExecutionHandler::lastSent() const { return outgoingCounter; }

SequenceNumber ExecutionHandler::send(const AMQBody& command, CompletionTracker::ResultListener listener)
{
    Mutex::ScopedLock l(lock);
    return send(command, listener, false);
}

SequenceNumber ExecutionHandler::send(const AMQBody& command, CompletionTracker::ResultListener l, bool hasContent)
{
    SequenceNumber id = ++outgoingCounter;
    if(l) {
        completion.listenForResult(id, l);
    }
    AMQFrame frame(command);
    if (hasContent) {
        frame.setEof(false);
    }
    out(frame);
    return id;
}

SequenceNumber ExecutionHandler::send(const AMQBody& command, const MethodContent& content, 
                                      CompletionTracker::ResultListener listener)
{
    Mutex::ScopedLock l(lock);
    SequenceNumber id = send(command, listener, true);
    sendContent(content);
    return id;
}

void ExecutionHandler::sendContent(const MethodContent& content)
{
    AMQFrame header(content.getHeader());
    header.setBof(false);
    u_int64_t data_length = content.getData().length();
    if(data_length > 0){
        header.setEof(false);
        out(header);   
        u_int32_t frag_size = maxFrameSize - AMQFrame::frameOverhead();
        if(data_length < frag_size){
            AMQFrame frame(in_place<AMQContentBody>(content.getData()));
            frame.setBof(false);
            out(frame);
        }else{
            u_int32_t offset = 0;
            u_int32_t remaining = data_length - offset;
            while (remaining > 0) {
                u_int32_t length = remaining > frag_size ? frag_size : remaining;
                string frag(content.getData().substr(offset, length));
                AMQFrame frame(in_place<AMQContentBody>(frag));
                frame.setBof(false);
                if (offset > 0) {
                    frame.setBos(false);
                }
                offset += length;
                remaining = data_length - offset;
                if (remaining) {
                    frame.setEos(false);
                    frame.setEof(false);
                }
                out(frame);
            }
        }
    } else {
        out(header);   
    }
}

bool ExecutionHandler::isComplete(const SequenceNumber& id)
{
    Mutex::ScopedLock l(lock);
    return outgoingCompletionStatus.covers(id);
}

bool ExecutionHandler::isCompleteUpTo(const SequenceNumber& id)
{
    Mutex::ScopedLock l(lock);
    return outgoingCompletionStatus.mark >= id;
}

void ExecutionHandler::setCompletionListener(boost::function<void()> l)
{
    completionListener = l;
}
