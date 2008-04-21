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

#include "SessionImpl.h"

#include "ConnectionImpl.h"
#include "Future.h"

#include "qpid/framing/all_method_bodies.h"
#include "qpid/framing/ClientInvoker.h"
#include "qpid/framing/constants.h"
#include "qpid/framing/FrameSet.h"
#include "qpid/framing/MethodContent.h"
#include "qpid/framing/SequenceSet.h"
#include "qpid/log/Statement.h"

#include <boost/bind.hpp>

namespace { const std::string OK="ok"; }

namespace qpid {
namespace client {

using namespace qpid::framing;

typedef sys::Monitor::ScopedLock  Lock;
typedef sys::Monitor::ScopedUnlock  UnLock;


SessionImpl::SessionImpl(shared_ptr<ConnectionImpl> conn,
                         uint16_t ch, uint64_t _maxFrameSize)
    : code(REPLY_SUCCESS),
      text(OK),
      state(INACTIVE),
      syncMode(false),
      detachedLifetime(0),
      maxFrameSize(_maxFrameSize),
      id(true),//generate unique uuid for each session
      name(id.str()),
      //TODO: may want to allow application defined names instead
      connection(conn),
      channel(ch),
      proxy(channel),
      nextIn(0),
      nextOut(0)
{
    channel.next = connection.get();
}

SessionImpl::~SessionImpl() {
    Lock l(state);
    if (state != DETACHED) {
        QPID_LOG(warning, "Detaching deleted session");
        setState(DETACHED);
        handleClosed();
        state.waitWaiters();
    }
    connection->erase(channel);
}

void SessionImpl::setSync(bool s) // user thread
{
    syncMode = s; //syncMode is volatile
}

bool SessionImpl::isSync() // user thread
{
    return syncMode; //syncMode is volatile
}

FrameSet::shared_ptr SessionImpl::get() // user thread
{
    // No lock here: pop does a blocking wait.
    return demux.getDefault()->pop();
}

const Uuid SessionImpl::getId() const //user thread
{
    return id; //id is immutable
}

void SessionImpl::open(uint32_t timeout) // user thread
{
    Lock l(state);
    if (state == INACTIVE) {
        setState(ATTACHING);
        proxy.attach(name, false);
        waitFor(ATTACHED);
        //TODO: timeout will not be set locally until get response to
        //confirm, should we wait for that?
        proxy.requestTimeout(timeout);
        proxy.commandPoint(nextOut, 0);
    } else {
        throw Exception("Open already called for this session");
    }
}

void SessionImpl::close() //user thread
{
    Lock l(state);
    if (detachedLifetime) {
        proxy.requestTimeout(0);
        //should we wait for the timeout response?
        detachedLifetime = 0;
    }
    detach();
    waitFor(DETACHED);
}

void SessionImpl::resume(shared_ptr<ConnectionImpl>) // user thread
{
    throw NotImplementedException("Resume not yet implemented by client!");
}

void SessionImpl::suspend() //user thread
{
    Lock l(state);
    detach();    
}

void SessionImpl::detach() //call with lock held 
{
    if (state == ATTACHED) {
        proxy.detach(name);
        setState(DETACHING);
    }
}


uint16_t SessionImpl::getChannel() const // user thread
{ 
    return channel; 
}

void SessionImpl::setChannel(uint16_t c) // user thread
{
    //channel will only ever be set when session is detached (and
    //about to be resumed)
    channel = c;
}

Demux& SessionImpl::getDemux()
{
    return demux;
}

void SessionImpl::waitForCompletion(const SequenceNumber& id)
{
    Lock l(state);
    waitForCompletionImpl(id);
}

void SessionImpl::waitForCompletionImpl(const SequenceNumber& id) //call with lock held
{
    while (incompleteOut.contains(id)) {
        checkOpen();
        state.wait();
    }
}

bool SessionImpl::isComplete(const SequenceNumber& id)
{
    Lock l(state);    
    return !incompleteOut.contains(id);
}

struct IsCompleteUpTo
{
    const SequenceNumber& id;
    bool result;

    IsCompleteUpTo(const SequenceNumber& _id) : id(_id), result(true) {}
    void operator()(const SequenceNumber& start, const SequenceNumber&)
    {
        if (start <= id) result = false;
    }

};

bool SessionImpl::isCompleteUpTo(const SequenceNumber& id)
{
    Lock l(state);
    //return false if incompleteOut contains anything less than id,
    //true otherwise
    IsCompleteUpTo f(id);
    incompleteIn.for_each(f);
    return f.result;
}

struct MarkCompleted 
{
    const SequenceNumber& id;
    SequenceSet& completedIn;

    MarkCompleted(const SequenceNumber& _id, SequenceSet& set) : id(_id), completedIn(set) {}

    void operator()(const SequenceNumber& start, const SequenceNumber& end)
    {
        if (id >= end) {
            completedIn.add(start, end);
        } else if (id >= start) { 
            completedIn.add(start, id);
        }
    }

};

void SessionImpl::markCompleted(const SequenceNumber& id, bool cumulative, bool notifyPeer)
{
    Lock l(state);
    if (cumulative) {        
        //everything in incompleteIn less than or equal to id is now complete
        MarkCompleted f(id, completedIn);
        incompleteIn.for_each(f);
        //make sure id itself is in
        completedIn.add(id);
        //then remove anything thats completed from the incomplete set
        incompleteIn.remove(completedIn);
    } else if (incompleteIn.contains(id)) {
        incompleteIn.remove(id);
        completedIn.add(id);            
    }
    if (notifyPeer) {
        sendCompletion();
    }    
}

/**
 * Called by ConnectionImpl to notify active sessions when connection
 * is explictly closed
 */
void SessionImpl::connectionClosed(uint16_t _code, const std::string& _text) 
{
    Lock l(state);
    code = _code;
    text = _text;
    setState(DETACHED);
    handleClosed();
}

/**
 * Called by ConnectionImpl to notify active sessions when connection
 * is disconnected
 */
void SessionImpl::connectionBroke(uint16_t _code, const std::string& _text) 
{
    connectionClosed(_code, _text);
}

Future SessionImpl::send(const AMQBody& command)
{
    return sendCommand(command);
}

Future SessionImpl::send(const AMQBody& command, const MethodContent& content)
{
    return sendCommand(command, &content);
}

Future SessionImpl::sendCommand(const AMQBody& command, const MethodContent* content)
{
    Lock l(state);
    checkOpen();
    SequenceNumber id = nextOut++;
    incompleteOut.add(id);
    
    if (syncMode) command.getMethod()->setSync(syncMode);
    Future f(id);
    if (command.getMethod()->resultExpected()) {        
        //result listener must be set before the command is sent
        f.setFutureResult(results.listenForResult(id));
    }
    AMQFrame frame(command);
    if (content) {
        frame.setEof(false);
    }
    handleOut(frame);
    if (content) {
        sendContent(*content);
    }
    if (syncMode) {
        waitForCompletionImpl(id);
    }
    return f;
}

void SessionImpl::sendContent(const MethodContent& content)
{
    AMQFrame header(content.getHeader());
    header.setBof(false);
    u_int64_t data_length = content.getData().length();
    if(data_length > 0){
        header.setEof(false);
        handleOut(header);   
        /*Note: end of frame marker included in overhead but not in size*/
        const u_int32_t frag_size = maxFrameSize - (AMQFrame::frameOverhead() - 1); 

        if(data_length < frag_size){
            AMQFrame frame(in_place<AMQContentBody>(content.getData()));
            frame.setBof(false);
            handleOut(frame);
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
                handleOut(frame);
            }
        }
    } else {
        handleOut(header);   
    }
}


bool isMessageMethod(AMQMethodBody* method)
{
    return method->isA<MessageTransferBody>();
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

void SessionImpl::handleIn(AMQFrame& frame) // network thread
{
    try {
        if (!invoke(static_cast<SessionHandler&>(*this), *frame.getBody())) {
            if (invoke(static_cast<ExecutionHandler&>(*this), *frame.getBody())) {
                //make sure the command id sequence and completion
                //tracking takes account of execution commands
                Lock l(state);
                completedIn.add(nextIn++);            
            } else {
                //if not handled by this class, its for the application:
                deliver(frame);
            }
        }
    } catch (const SessionException& e) {
        //TODO: proper 0-10 exception handling
        QPID_LOG(error, "Session exception:" << e.what());
        Lock l(state);
        code = e.code;
        text = e.what();
    }
}

void SessionImpl::handleOut(AMQFrame& frame) // user thread
{
    channel.handle(frame);
}

void SessionImpl::deliver(AMQFrame& frame) // network thread
{
    if (!arriving) {
        arriving = FrameSet::shared_ptr(new FrameSet(nextIn++));
    }
    arriving->append(frame);
    if (arriving->isComplete()) {
        //message.transfers will be marked completed only when 'acked'
        //as completion affects flow control; other commands will be
        //considered completed as soon as processed here
        if (arriving->isA<MessageTransferBody>()) {
            Lock l(state);
            incompleteIn.add(arriving->getId());
        } else {
            Lock l(state);
            completedIn.add(arriving->getId());
        }
        demux.handle(arriving);
        arriving.reset();
    }
}

//control handler methods (called by network thread when controls are
//received from peer):

void SessionImpl::attach(const std::string& /*name*/, bool /*force*/)
{
    throw NotImplementedException("Client does not support attach");
}

void SessionImpl::attached(const std::string& _name)
{
    Lock l(state);
    if (name != _name) throw InternalErrorException("Incorrect session name");
    setState(ATTACHED);
}

void SessionImpl::detach(const std::string& _name)
{
    Lock l(state);
    if (name != _name) throw InternalErrorException("Incorrect session name");
    setState(DETACHED);
    QPID_LOG(info, "Session detached by peer: " << name);
}

void SessionImpl::detached(const std::string& _name, uint8_t _code)
{
    Lock l(state);
    if (name != _name) throw InternalErrorException("Incorrect session name");
    setState(DETACHED);
    if (_code) {
        //TODO: make sure this works with execution.exception - don't
        //want to overwrite the code from that
        QPID_LOG(error, "Session detached by peer: " << name << " " << code);
        code = _code;
        text = "Session detached by peer";
    }
    if (detachedLifetime == 0) {
        handleClosed();
    }
}

void SessionImpl::requestTimeout(uint32_t t)
{
    Lock l(state);
    detachedLifetime = t;
    proxy.timeout(t);
}

void SessionImpl::timeout(uint32_t t)
{
    Lock l(state);
    detachedLifetime = t;
}

void SessionImpl::commandPoint(const framing::SequenceNumber& id, uint64_t offset)
{
    if (offset) throw NotImplementedException("Non-zero byte offset not yet supported for command-point");
    
    Lock l(state);
    nextIn = id;
}

void SessionImpl::expected(const framing::SequenceSet& commands, const framing::Array& fragments)
{
    if (!commands.empty() || fragments.size()) {
        throw NotImplementedException("Session resumption not yet supported");
    }
}

void SessionImpl::confirmed(const framing::SequenceSet& /*commands*/, const framing::Array& /*fragments*/)
{
    //don't really care too much about this yet
}

void SessionImpl::completed(const framing::SequenceSet& commands, bool timelyReply)
{
    Lock l(state);
    incompleteOut.remove(commands);
    state.notify();//notify any waiters of completion
    completedOut.add(commands);
    //notify any waiting results of completion
    results.completed(commands);

    if (timelyReply) {
        proxy.knownCompleted(completedOut);
        completedOut.clear();
    }
}

void SessionImpl::knownCompleted(const framing::SequenceSet& commands)
{
    Lock l(state);
    completedIn.remove(commands);
}

void SessionImpl::flush(bool expected, bool confirmed, bool completed)
{
    Lock l(state);
    if (expected) {
        proxy.expected(SequenceSet(nextIn), Array());
    }
    if (confirmed) {
        proxy.confirmed(completedIn, Array());
    }
    if (completed) {
        proxy.completed(completedIn, true);
    }
}

void SessionImpl::sendCompletion()
{
    Lock l(state);
    sendCompletionImpl();
}

void SessionImpl::sendCompletionImpl()
{
    proxy.completed(completedIn, true);
}

void SessionImpl::gap(const framing::SequenceSet& /*commands*/)
{
    throw NotImplementedException("gap not yet supported");
}



void SessionImpl::sync() {}

void SessionImpl::result(uint32_t commandId, const std::string& value)
{
    Lock l(state);
    results.received(commandId, value);
}

void SessionImpl::exception(uint16_t errorCode,
                            uint32_t commandId,
                            uint8_t classCode,
                            uint8_t commandCode,
                            uint8_t /*fieldIndex*/,
                            const std::string& description,
                            const framing::FieldTable& /*errorInfo*/)
{
    QPID_LOG(warning, "Exception received from peer: " << errorCode << ":" << description 
             << " [caused by " << commandId << " " << classCode << ":" << commandCode << "]");

    Lock l(state);
    code = errorCode;
    text = description;
    if (detachedLifetime) {
        proxy.requestTimeout(0);
        //should we wait for the timeout response?
        detachedLifetime = 0;
    }
}


//private utility methods:

inline void SessionImpl::setState(State s) //call with lock held
{
    state = s;
}

inline void SessionImpl::waitFor(State s) //call with lock held
{
    // We can be DETACHED at any time
    state.waitFor(States(s, DETACHED));
    check();
}

void SessionImpl::check() const  //call with lock held.
{
    if (code != REPLY_SUCCESS) {
        throwReplyException(code, text);
    }
}

void SessionImpl::checkOpen() const  //call with lock held.
{
    check();
    if (state != ATTACHED) {
        throwReplyException(0, "Session isn't attached");
    }
}

void SessionImpl::assertOpen() const
{
    Lock l(state);
    checkOpen();
}

void SessionImpl::handleClosed()
{
    QPID_LOG(info, "SessionImpl::handleClosed(): entering");
    demux.close();
    results.close();
    QPID_LOG(info, "SessionImpl::handleClosed(): returning");
}

}}
