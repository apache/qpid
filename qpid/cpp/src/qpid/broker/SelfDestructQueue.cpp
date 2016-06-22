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
#include "SelfDestructQueue.h"
#include "AclModule.h"
#include "Broker.h"
#include "QueueDepth.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace broker {
SelfDestructQueue::SelfDestructQueue(const std::string& n, const QueueSettings& s, MessageStore* const ms, management::Manageable* p, Broker* b) : Queue(n, s, ms, p, b)
{
    QPID_LOG_CAT(debug, model, "Self-destruct queue created: " << name);
}
bool SelfDestructQueue::checkDepth(const QueueDepth& increment, const Message&)
{
    if (settings.maxDepth && (settings.maxDepth - current < increment)) {
        broker->getQueues().destroy(name);
        if (broker->getAcl())
            broker->getAcl()->recordDestroyQueue(name);
        QPID_LOG_CAT(debug, model, "Queue " << name << " deleted itself due to reaching limit: " << current << " (policy is " << settings.maxDepth << ")");
    }
    current += increment;
    return true;
}
}} // namespace qpid::broker
