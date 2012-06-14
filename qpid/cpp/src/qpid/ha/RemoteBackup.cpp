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
#include "RemoteBackup.h"
#include "QueueGuard.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueueRegistry.h"
#include <boost/bind.hpp>

namespace qpid {
namespace ha {

using sys::Mutex;

RemoteBackup::RemoteBackup(
    const BrokerInfo& info, broker::Broker& broker, ReplicationTest rt, bool cg) :
    logPrefix("Primary remote backup "+info.getLogId()+": "), brokerInfo(info), replicationTest(rt),
    createGuards(cg)
{
    QPID_LOG(debug, logPrefix << "Guarding queues for backup broker.");
    // FIXME aconway 2012-06-12: potential deadlocks, this is called inside ConnectionObserver::opened.
    broker.getQueues().eachQueue(boost::bind(&RemoteBackup::initialQueue, this, _1));
}

RemoteBackup::~RemoteBackup() {
    for (GuardMap::iterator i = guards.begin(); i != guards.end(); ++i)
        i->second->cancel();
}

bool RemoteBackup::isReady() {
    return initialQueues.empty();
}

void RemoteBackup::initialQueue(const QueuePtr& q) {
    if (replicationTest.isReplicated(ALL, *q)) initialQueues.insert(q);
    queueCreate(q);
}

RemoteBackup::GuardPtr RemoteBackup::guard(const QueuePtr& q) {
    if (!createGuards) return RemoteBackup::GuardPtr();
    GuardMap::iterator i = guards.find(q);
    if (i == guards.end()) {
        assert(0);
        throw Exception(logPrefix+": Cannot find queue guard: "+q->getName());
    }
    GuardPtr guard = i->second;
    guards.erase(i);
    return guard;
}

namespace {
typedef std::set<boost::shared_ptr<broker::Queue> > QS;
struct QueueSetPrinter {
    const QS& qs;
    QueueSetPrinter(const QS& q) : qs(q) {}
};
std::ostream& operator<<(std::ostream& o, const QueueSetPrinter& qp) {
    for (QS::const_iterator i = qp.qs.begin(); i != qp.qs.end(); ++i)
        o << (*i)->getName() << " ";
    return o;
}
}

void RemoteBackup::ready(const QueuePtr& q) {
    initialQueues.erase(q);
    QPID_LOG(debug, logPrefix << "Queue ready: " << q->getName() << " remaining unready: " << QueueSetPrinter(initialQueues));
    if (isReady()) QPID_LOG(debug, logPrefix << "All queues ready");
}

// Called via ConfigurationObserver
void RemoteBackup::queueCreate(const QueuePtr& q) {
    if (createGuards && replicationTest.isReplicated(ALL, *q))
        guards[q].reset(new QueueGuard(*q, brokerInfo));
}

// Called via ConfigurationObserver
void RemoteBackup::queueDestroy(const QueuePtr& q) {
    initialQueues.erase(q);
    GuardMap::iterator i = guards.find(q);
    if (i != guards.end()) {
        i->second->cancel();
        guards.erase(i);
    }
}

}} // namespace qpid::ha
