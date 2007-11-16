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

#include <boost/assert.hpp>
#include <boost/ptr_container/ptr_vector.hpp>
#include <boost/ptr_container/ptr_deque.hpp>
#include <boost/bind.hpp>
#include <boost/scoped_ptr.hpp>

#include <sys/SessionContext.h>
#include <sys/SessionHandler.h>
#include <sys/SessionHandlerFactory.h>
#include <sys/Acceptor.h>
#include <sys/Socket.h>
#include <framing/Buffer.h>
#include <framing/AMQFrame.h>
#include <Exception.h>

#include "EventChannelConnection.h"

namespace qpid {
namespace sys {

using namespace qpid::framing;
using namespace std;

class EventChannelAcceptor : public Acceptor {
  public:


    EventChannelAcceptor(
        int16_t port_, int backlog, int nThreads, bool trace_
    );
        
    int getPort() const;
    
    void run(SessionHandlerFactory& factory);

    void shutdown();

  private:

    void accept();

    Mutex lock;
    Socket listener;
    const int port;
    const bool isTrace;
    bool isRunning;
    boost::ptr_vector<EventChannelConnection> connections;
    AcceptEvent acceptEvent;
    SessionHandlerFactory* factory;
    bool isShutdown;
    EventChannelThreads::shared_ptr threads;
};

Acceptor::shared_ptr Acceptor::create(
    int16_t port, int backlog, int threads, bool trace)
{
    return Acceptor::shared_ptr(
        new EventChannelAcceptor(port, backlog, threads, trace));
}

// Must define Acceptor virtual dtor.
Acceptor::~Acceptor() {}

EventChannelAcceptor::EventChannelAcceptor(
    int16_t port_, int backlog, int nThreads, bool trace_
) : listener(Socket::createTcp()),
    port(listener.listen(int(port_), backlog)),
    isTrace(trace_),
    isRunning(false),
    acceptEvent(listener.fd(),
                boost::bind(&EventChannelAcceptor::accept, this)),
    factory(0),
    isShutdown(false),
    threads(EventChannelThreads::create(EventChannel::create(), nThreads))
{ }
    
int EventChannelAcceptor::getPort() const {
    return port;                // Immutable no need for lock.
}
    
void EventChannelAcceptor::run(SessionHandlerFactory& f) {
    {
        Mutex::ScopedLock l(lock);
        if (!isRunning && !isShutdown) {
            isRunning = true;
            factory = &f;
            threads->post(acceptEvent);
        }
    }
    threads->join();            // Wait for shutdown.
}

void EventChannelAcceptor::shutdown() {
    bool doShutdown = false;
    {
        Mutex::ScopedLock l(lock);
        doShutdown = !isShutdown; // I'm the shutdown thread.
        isShutdown = true;
    }
    if (doShutdown) {
        ::close(acceptEvent.getDescriptor());
        threads->shutdown();
        for_each(connections.begin(), connections.end(),
                 boost::bind(&EventChannelConnection::close, _1));
    }
    threads->join();
}

void EventChannelAcceptor::accept()
{
    // No lock, we only post one accept event at a time.
    if (isShutdown)
        return;
    if (acceptEvent.getException()) {
        Exception::log(*acceptEvent.getException(),
                       "EventChannelAcceptor::accept");
        shutdown();
        return;
    }
    // TODO aconway 2006-11-29: Need to reap closed connections also.
    int fd = acceptEvent.getAcceptedDesscriptor();
    connections.push_back(
        new EventChannelConnection(threads, *factory, fd, fd, isTrace));
    threads->post(acceptEvent); // Keep accepting.
}

}} // namespace qpid::sys
