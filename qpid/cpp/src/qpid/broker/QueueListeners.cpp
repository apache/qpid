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
#include "qpid/broker/QueueListeners.h"
#include <boost/bind.hpp>

namespace qpid {
namespace broker {

void QueueListeners::addListener(Consumer::shared_ptr c)
{
    if (!c->inListeners) {
        if (c->acquires) {
            add(consumers, c);
        } else {
            add(browsers, c);
        }
        c->inListeners = true;
    }
}

void QueueListeners::removeListener(Consumer::shared_ptr c)
{
    if (c->inListeners) {
        if (c->acquires) {
            remove(consumers, c);
        } else {
            remove(browsers, c);
        }
        c->inListeners = false;
    }
}

void QueueListeners::populate(NotificationSet& set)
{
    if (consumers.size()) {
        set.consumer = consumers.front();
        consumers.pop_front();
        set.consumer->inListeners = false;
    }
    // Don't swap the deques, hang on to the memory allocated.
    set.browsers = browsers;
    browsers.clear();
    for (Listeners::iterator i = set.browsers.begin(); i != set.browsers.end(); i++)
        (*i)->inListeners = false;
}

void QueueListeners::add(Listeners& listeners, Consumer::shared_ptr c)
{
    listeners.push_back(c);
}

void QueueListeners::remove(Listeners& listeners, Consumer::shared_ptr c)
{
    Listeners::iterator i = std::find(listeners.begin(), listeners.end(), c);
    if (i != listeners.end()) listeners.erase(i);
}

void QueueListeners::NotificationSet::notify()
{
    if (consumer) consumer->notify();
    std::for_each(browsers.begin(), browsers.end(), boost::mem_fn(&Consumer::notify));
}

void QueueListeners::ListenerSet::notifyAll()
{
    std::for_each(listeners.begin(), listeners.end(), boost::mem_fn(&Consumer::notify));
}

void QueueListeners::snapshot(ListenerSet& set)
{
    set.listeners.insert(set.listeners.end(), consumers.begin(), consumers.end());
    set.listeners.insert(set.listeners.end(), browsers.begin(), browsers.end());
}

}} // namespace qpid::broker
