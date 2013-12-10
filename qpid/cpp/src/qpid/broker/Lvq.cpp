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
#include "Lvq.h"
#include "MessageMap.h"
#include "qpid/sys/Monitor.h"

namespace qpid {
namespace broker {
Lvq::Lvq(const std::string& n, std::auto_ptr<MessageMap> m, const QueueSettings& s, MessageStore* const ms, management::Manageable* p, Broker* b)
    : Queue(n, s, ms, p, b), messageMap(*m)
{
    messages = m;
}

void Lvq::push(Message& message, bool isRecovery)
{
    QueueListeners::NotificationSet copy;
    Message old;
    bool removed;
    {
        qpid::sys::Mutex::ScopedLock locker(messageLock);
        message.setSequence(++sequence);
        interceptors.publish(message);
        removed = messageMap.update(message, old);
        listeners.populate(copy);
        observeEnqueue(message, locker);
        if (removed) {
            if (mgmtObject) {
                mgmtObject->inc_acquires();
                mgmtObject->inc_discardsLvq();
                if (brokerMgmtObject) {
                    brokerMgmtObject->inc_acquires();
                    brokerMgmtObject->inc_discardsLvq();
                }
            }
            observeDequeue(old, locker, 0/*can't be empty, so no need to check autodelete*/);
        }
    }
    copy.notify();
    if (removed) {
        if (isRecovery) pendingDequeues.push_back(old);
        else if (old.isPersistent())
            dequeueFromStore(old.getPersistentContext());//do outside of lock
    }
}
}} // namespace qpid::broker
