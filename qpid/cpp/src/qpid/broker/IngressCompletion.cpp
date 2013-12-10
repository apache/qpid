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
#include "IngressCompletion.h"
#include "Queue.h"

namespace qpid {
namespace broker {
IngressCompletion::~IngressCompletion() {}

void IngressCompletion::enqueueAsync(boost::shared_ptr<Queue> q)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    queues.push_back(q);
}

void IngressCompletion::flush()
{
    Queues copy;
    {
        qpid::sys::Mutex::ScopedLock l(lock);
        queues.swap(copy);
    }
    for (Queues::const_iterator i = copy.begin(); i != copy.end(); ++i) {
        boost::shared_ptr<Queue> q(i->lock());
        if (q) {
            q->flush();
        }
    }
}
}} // namespace qpid::broker
