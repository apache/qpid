
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


#include "qpid/broker/PersistableMessage.h"
#include "qpid/broker/Queue.h"
#include <iostream>

using namespace qpid::broker;

namespace qpid {
namespace broker {

PersistableMessage::~PersistableMessage() {}
PersistableMessage::PersistableMessage() : ingressCompletion(0), persistenceId(0) {}

void PersistableMessage::setIngressCompletion(boost::intrusive_ptr<IngressCompletion> i)
{
    ingressCompletion = i.get();
    /**
     * What follows is a hack to account for the fact that the
     * AsyncCompletion to use may be, but is not always, this same
     * object.
     *
     * This is hopefully temporary, and allows the store interface to
     * remain unchanged without requiring another object to be allocated
     * for every message.
     *
     * The case in question is where a message previously passed to
     * the store is modified by some other queue onto which it is
     * pushed, and then again persisted to the store. These will be
     * two separate PersistableMessage instances (since the latter now
     * has different content), but need to share the same
     * AsyncCompletion (since they refer to the same incoming transfer
     * command).
     */
    if (static_cast<RefCounted*>(ingressCompletion) != static_cast<RefCounted*>(this)) {
        holder = i;
    }
}

void PersistableMessage::enqueueAsync(boost::shared_ptr<Queue> q)
{
    enqueueStart();
    ingressCompletion->enqueueAsync(q);
}

void PersistableMessage::dequeueComplete() {}

}}


