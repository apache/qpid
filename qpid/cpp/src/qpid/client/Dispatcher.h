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
#ifndef _Dispatcher_
#define _Dispatcher_

#include <map>
#include <memory>
#include <string>
#include <boost/shared_ptr.hpp>
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Thread.h"
#include "MessageListener.h"

namespace qpid {
namespace client {

class Session_0_10;

class Subscriber : public MessageListener
{
    Session_0_10& session;
    MessageListener* const listener;
    const bool autoAck;
    const uint ackBatchSize;
    uint count;

public:
    typedef boost::shared_ptr<Subscriber> shared_ptr;
    Subscriber(Session_0_10& session, MessageListener* listener, bool autoAck = true, uint ackBatchSize = 1);
    void received(Message& msg);
    
};

typedef framing::Handler<framing::FrameSet> FrameSetHandler;

class Dispatcher : public sys::Runnable
{
    typedef std::map<std::string, Subscriber::shared_ptr> Listeners;
    sys::Mutex lock;
    sys::Thread worker;
    Session_0_10& session;
    const std::string queue;
    bool running;
    bool stopped;
    Listeners listeners;
    Subscriber::shared_ptr defaultListener;
    std::auto_ptr<FrameSetHandler> handler;

    Subscriber::shared_ptr find(const std::string& name);
    void startRunning();
    void stopRunning();
    bool isStopped();

public:
    Dispatcher(Session_0_10& session, const std::string& queue = "");

    void start();
    void run();
    void stop();

    void listen(MessageListener* listener, bool autoAck = true, uint ackBatchSize = 1);
    void listen(const std::string& destination, MessageListener* listener, bool autoAck = true, uint ackBatchSize = 1);
    void cancel(const std::string& destination);
};

}}

#endif
