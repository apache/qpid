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
#include "Backup.h"
#include "BrokerReplicator.h"
#include "HaBroker.h"
#include "ReplicatingSubscription.h"
#include "Settings.h"
#include "qpid/Url.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/broker/Bridge.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/SessionHandler.h"
#include "qpid/broker/Link.h"
#include "qpid/framing/AMQP_ServerProxy.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/sys/SystemInfo.h"
#include "qpid/types/Variant.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace ha {

using namespace framing;
using namespace broker;
using types::Variant;
using std::string;

Backup::Backup(HaBroker& hb, const Settings& s) :
    logPrefix("Backup: "), haBroker(hb), broker(hb.getBroker()), settings(s)
{
    // Empty brokerUrl means delay initialization until seBrokertUrl() is called.
    if (!s.brokerUrl.empty()) initialize(Url(s.brokerUrl));
}

void Backup::initialize(const Url& brokers) {
    if (brokers.empty()) throw Url::Invalid("HA broker URL is empty");
    QPID_LOG(info, logPrefix << "Connecting to cluster, broker URL: " << brokers);
    string protocol = brokers[0].protocol.empty() ? "tcp" : brokers[0].protocol;
    types::Uuid uuid(true);
    // Declare the link
    std::pair<Link::shared_ptr, bool> result = broker.getLinks().declare(
        broker::QPID_NAME_PREFIX + string("ha.link.") + uuid.str(),
        brokers[0].host, brokers[0].port, protocol,
        false,                  // durable
        settings.mechanism, settings.username, settings.password,
        false);               // no amq.failover - don't want to use client URL.
    {
        sys::Mutex::ScopedLock l(lock);
        link = result.first;
        replicator.reset(new BrokerReplicator(haBroker, link));
        replicator->initialize();
        broker.getExchanges().registerExchange(replicator);
    }
    link->setUrl(brokers);          // Outside the lock, once set link doesn't change.
}

Backup::~Backup() {
    QPID_LOG(debug, logPrefix << "No longer a backup.");
    if (link) link->close();
    if (replicator.get()) {
        broker.getExchanges().destroy(replicator->getName());
        replicator->shutdown();
        replicator.reset();
    }
}

// Called via management.
void Backup::setBrokerUrl(const Url& url) {
    // Ignore empty URLs seen during start-up for some tests.
    if (url.empty()) return;
    bool linkSet = false;
    {
        sys::Mutex::ScopedLock l(lock);
        linkSet = link;
    }
    if (linkSet)
        link->setUrl(url);      // Outside lock, once set link doesn't change
    else
        initialize(url);        // Deferred initialization
}

void Backup::setStatus(BrokerStatus status) {
    switch (status) {
      case READY:
        QPID_LOG(notice, logPrefix << "Ready to become primary.");
        break;
      case CATCHUP:
        QPID_LOG(notice, logPrefix << "Catching up on primary, cannot be promoted.");
        break;
      default:
        // FIXME aconway 2012-12-07: fail
        assert(0);
    }
}

}} // namespace qpid::ha
