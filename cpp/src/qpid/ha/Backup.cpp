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

namespace qpid {
namespace ha {

using namespace framing;
using namespace broker;
using types::Variant;
using std::string;

Backup::Backup(HaBroker& hb, const Settings& s) :
    logPrefix(hb), haBroker(hb), broker(hb.getBroker()), settings(s)
{
    // Empty brokerUrl means delay initialization until seBrokertUrl() is called.
    if (!s.brokerUrl.empty()) initialize(Url(s.brokerUrl));
}

bool Backup::isSelf(const Address& a) const {
    return sys::SystemInfo::isLocalHost(a.host) &&
        a.port == haBroker.getBroker().getPort(a.protocol);
}

Url Backup::linkUrl(const Url& brokers) const {
    return brokers;
    /** FIXME aconway 2012-05-29: Problems with self-test, false positives.
    // linkUrl contains only the addresses of *other* brokers, not this one.
    Url url;
    for (Url::const_iterator i = brokers.begin(); i != brokers.end(); ++i)
        if (!isSelf(*i)) url.push_back(*i);
    if (url.empty()) throw Url::Invalid("HA Backup failover URL is empty");
    QPID_LOG(debug, logPrefix << "Backup failover URL (excluding self): " << url);
    return url;
    */
}

void Backup::initialize(const Url& brokers) {
    if (brokers.empty()) throw Url::Invalid("HA broker URL is empty");
    QPID_LOG(info, logPrefix << "Backup broker URL: " << brokers);
    sys::Mutex::ScopedLock l(lock);
    Url url = linkUrl(brokers);
    string protocol = url[0].protocol.empty() ? "tcp" : url[0].protocol;
    framing::Uuid uuid(true);
    // Declare the link
    std::pair<Link::shared_ptr, bool> result = broker.getLinks().declare(
        broker::QPID_NAME_PREFIX + string("ha.link.") + uuid.str(),
        url[0].host, url[0].port, protocol,
        false,                  // durable
        settings.mechanism, settings.username, settings.password,
        false);                 // amq.failover
    link = result.first;
    link->setUrl(url);
    replicator.reset(new BrokerReplicator(haBroker, link));
    broker.getExchanges().registerExchange(replicator);
}

Backup::~Backup() {
    if (link) link->close();
    if (replicator.get()) broker.getExchanges().destroy(replicator->getName());
    replicator.reset();
}


void Backup::setBrokerUrl(const Url& url) {
    // Ignore empty URLs seen during start-up for some tests.
    if (url.empty()) return;
    sys::Mutex::ScopedLock l(lock);
    if (link) {
        QPID_LOG(info, logPrefix << "Broker URL set to: " << url);
        link->setUrl(linkUrl(url));
    }
    else initialize(url);        // Deferred initialization
}

}} // namespace qpid::ha
