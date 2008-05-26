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

#include "qpid/framing/FrameSet.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/BlockingQueue.h"
#include "Message.h"

#include <boost/state_saver.hpp>

using qpid::framing::FrameSet;
using qpid::framing::MessageTransferBody;
using qpid::sys::Mutex;
using qpid::sys::ScopedLock;
using qpid::sys::Thread;

namespace qpid {
namespace client {

Subscriber::Subscriber(const Session& s, MessageListener* l, AckPolicy a)
  : session(s), listener(l), autoAck(a) {}

void Subscriber::received(Message& msg)
{
    if (listener) {
        listener->received(msg);
        autoAck.ack(msg, session);
    }
}

Dispatcher::Dispatcher(const Session& s, const std::string& q)
    : session(s), running(false), autoStop(true)
{
    queue = q.empty() ? 
        session.getExecution().getDemux().getDefault() : 
        session.getExecution().getDemux().get(q); 
}

void Dispatcher::start()
{
    worker = Thread(this);
}

void Dispatcher::run()
{
    Mutex::ScopedLock l(lock);
    if (running) 
        throw Exception("Dispatcher is already running.");
    boost::state_saver<bool>  reset(running); // Reset to false on exit.
    running = true;
    try {
        while (!queue->isClosed()) {
            Mutex::ScopedUnlock u(lock);
            FrameSet::shared_ptr content = queue->pop();
            if (content->isA<MessageTransferBody>()) {
                Message msg(*content);
                Subscriber::shared_ptr listener = find(msg.getDestination());
                if (!listener) {
                    QPID_LOG(error, "No listener found for destination " << msg.getDestination());
                } else {
                    assert(listener);
                    listener->received(msg);
                }
            } else {
                if (handler.get()) {
                    handler->handle(*content);
                } else {
                    QPID_LOG(warning, "No handler found for " << *(content->getMethod()));
                }
            }
        }
        sync(session).sync(); // Make sure all our acks are received before returning.
    }
    catch (const ClosedException&) {} //ignore it and return
    catch (const std::exception& e) {
        QPID_LOG(error, "Exception in client dispatch thread: " << e.what());
    }
}

void Dispatcher::stop()
{
    ScopedLock<Mutex> l(lock);
    queue->close();             // Will interrupt thread blocked in pop()
}

void Dispatcher::setAutoStop(bool b)
{
    ScopedLock<Mutex> l(lock);
    autoStop = b;
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

void Dispatcher::listen(
    MessageListener* listener, AckPolicy autoAck
)
{
    ScopedLock<Mutex> l(lock);
    defaultListener = Subscriber::shared_ptr(
        new Subscriber(session, listener, autoAck));
}

void Dispatcher::listen(const std::string& destination, MessageListener* listener, AckPolicy autoAck)
{
    ScopedLock<Mutex> l(lock);
    listeners[destination] = Subscriber::shared_ptr(
        new Subscriber(session, listener, autoAck));
}

void Dispatcher::cancel(const std::string& destination)
{
    ScopedLock<Mutex> l(lock);
    listeners.erase(destination);
    if (autoStop && listeners.empty())
        queue->close();
}

}}
