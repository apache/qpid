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
#include "Dispatcher.h"

#include "qpid/client/Session.h"
#include "qpid/framing/FrameSet.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/log/Statement.h"
#include "BlockingQueue.h"
#include "Message.h"

using qpid::framing::FrameSet;
using qpid::framing::MessageTransferBody;
using qpid::sys::Mutex;
using qpid::sys::ScopedLock;
using qpid::sys::Thread;

namespace qpid {
namespace client {

    Subscriber::Subscriber(Session& s, MessageListener* l, bool a, uint f) : session(s), listener(l), autoAck(a), ackFrequency(f), count(0) {}

void Subscriber::received(Message& msg)
{
    if (listener) {
        listener->received(msg);
        if (autoAck) {
            bool send = (++count >= ackFrequency);
            msg.acknowledge(session, true, send);
            if (send) count = 0;
        }
    }
}


    Dispatcher::Dispatcher(Session& s, const std::string& q) : session(s), queue(q), running(false), stopped(false)
{
}

void Dispatcher::start()
{
    worker = Thread(this);
}

void Dispatcher::run()
{    
    BlockingQueue<FrameSet::shared_ptr>& q = queue.empty() ? 
        session.execution().getDemux().getDefault() : 
        session.execution().getDemux().get(queue); 

    startRunning();
    stopped = false;
    while (!isStopped()) {
        FrameSet::shared_ptr content = q.pop();
        if (content->isA<MessageTransferBody>()) {
            Message msg(*content);
            Subscriber::shared_ptr listener = find(msg.getDestination());
            if (!listener) {
                QPID_LOG(error, "No message listener set: " << content->getMethod());                                        
            } else {
                listener->received(msg);
            }
        } else {
            if (handler.get()) {
                handler->handle(*content);
            } else {
                QPID_LOG(error, "Unhandled method: " << content->getMethod());                                        
            }
        }
    }
    stopRunning();
}

void Dispatcher::stop()
{
    ScopedLock<Mutex> l(lock);
    stopped = true;
}

bool Dispatcher::isStopped()
{
    ScopedLock<Mutex> l(lock);
    return stopped;
}

/**
 * Prevent concurrent threads invoking run.
 */
void Dispatcher::startRunning()
{
    ScopedLock<Mutex> l(lock);
    if (running) {
        throw Exception("Dispatcher is already running.");
    }
    running = true;
}

void Dispatcher::stopRunning()
{
    ScopedLock<Mutex> l(lock);
    running = false;
}

Subscriber::shared_ptr Dispatcher::find(const std::string& name)
{
    ScopedLock<Mutex> l(lock);
    Listeners::iterator i = listeners.find(name);
    if (i == listeners.end()) {
        return defaultListener;
    }
    return i->second;
}

void Dispatcher::listen(MessageListener* listener, bool autoAck, uint ackFrequency)
{
    ScopedLock<Mutex> l(lock);
    defaultListener = Subscriber::shared_ptr(new Subscriber(session, listener, autoAck, ackFrequency));
}

void Dispatcher::listen(const std::string& destination, MessageListener* listener, bool autoAck, uint ackFrequency)
{
    ScopedLock<Mutex> l(lock);
    listeners[destination] = Subscriber::shared_ptr(new Subscriber(session, listener, autoAck, ackFrequency));
}

void Dispatcher::cancel(const std::string& destination)
{
    ScopedLock<Mutex> l(lock);
    listeners.erase(destination);
}

}}
