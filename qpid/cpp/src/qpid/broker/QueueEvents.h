#ifndef QPID_BROKER_QUEUEEVENTS_H
#define QPID_BROKER_QUEUEEVENTS_H

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

#include "QueuedMessage.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/PollableQueue.h"
#include <map>
#include <string>
#include <boost/function.hpp>

namespace qpid {
namespace broker {

/**
 * Event manager for queue events. Allows queues to indicate when
 * events have occured; allows listeners to register for notification
 * of this. The notification happens asynchronously, in a separate
 * thread.
 */
class QueueEvents
{
  public:
    enum EventType {ENQUEUE, DEQUEUE};

    struct Event
    {
        EventType type;
        QueuedMessage msg;

        Event(EventType, const QueuedMessage&);
    };

    typedef boost::function<void (Event)> EventListener;

    QueueEvents(const boost::shared_ptr<sys::Poller>& poller);
    ~QueueEvents();
    void enqueued(const QueuedMessage&);
    void dequeued(const QueuedMessage&);
    void registerListener(const std::string& id, const EventListener&);
    void unregisterListener(const std::string& id);
    //process all outstanding events
    void shutdown();
  private:
    typedef qpid::sys::PollableQueue<Event> EventQueue;
    typedef std::map<std::string, EventListener> Listeners;

    EventQueue eventQueue;
    Listeners listeners;
    qpid::sys::Mutex lock;//protect listeners from concurrent access
    
    void handle(EventQueue::Queue& e);

};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_QUEUEEVENTS_H*/
