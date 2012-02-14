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
#include "Settings.h"
#include "BrokerReplicator.h"
#include "ReplicatingSubscription.h"
#include "ConnectionExcluder.h"
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
#include "qpid/types/Variant.h"

namespace qpid {
namespace ha {

using namespace framing;
using namespace broker;
using types::Variant;
using std::string;

Backup::Backup(broker::Broker& b, const Settings& s) :
    broker(b), settings(s), excluder(new ConnectionExcluder())
{
    Url url(s.brokerUrl);
    string protocol = url[0].protocol.empty() ? "tcp" : url[0].protocol;

    // Declare the link
    std::pair<Link::shared_ptr, bool> result = broker.getLinks().declare(
        url[0].host, url[0].port, protocol,
        false,              // durable
        s.mechanism, s.username, s.password);
    assert(result.second);  // FIXME aconway 2011-11-23: error handling
    link = result.first;

    replicator.reset(new BrokerReplicator(link));
    broker.getExchanges().registerExchange(replicator);

    broker.getConnectionObservers().add(excluder);
}

Backup::~Backup() {
    broker.getExchanges().destroy(replicator->getName());
    broker.getConnectionObservers().remove(excluder); // Allows client connections.
}

}} // namespace qpid::ha
