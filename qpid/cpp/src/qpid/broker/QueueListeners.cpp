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
#include "QueueListeners.h"
#include <boost/bind.hpp>

namespace qpid {
namespace broker {

void QueueListeners::addListener(Consumer::shared_ptr c)
{
    if (c->preAcquires()) {
        consumers.insert(c);
    } else {
        browsers.insert(c);
    }
}

void QueueListeners::removeListener(Consumer::shared_ptr c)
{
    if (c->preAcquires()) {
        consumers.erase(c);
    } else {
        browsers.erase(c);
    }
}

void QueueListeners::populate(NotificationSet& set)
{
    if (!consumers.empty()) {
        set.consumer = *consumers.begin();
        consumers.erase(consumers.begin());
    } else {
        browsers.swap(set.browsers);
    }
}

void QueueListeners::NotificationSet::notify()
{
    if (consumer) consumer->notify();
    else for_each(browsers.begin(), browsers.end(), boost::mem_fn(&Consumer::notify));
}

}} // namespace qpid::broker
