/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "Connection.h"
#include "ConnectionCodec.h"

#include "qpid/cluster/Cluster.h"
#include "qpid/cluster/ConnectionCodec.h"
#include "qpid/cluster/UpdateClient.h"

#include "qpid/broker/Broker.h"
#include "qpid/Plugin.h"
#include "qpid/Options.h"
#include "qpid/shared_ptr.h"
#include "qpid/sys/AtomicValue.h"
#include "qpid/log/Statement.h"

#include "qpid/management/ManagementBroker.h"
#include "qpid/management/IdAllocator.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/SessionState.h"

#include <boost/utility/in_place_factory.hpp>
#include <boost/scoped_ptr.hpp>

namespace qpid {
namespace cluster {

using namespace std;
using broker::Broker;
using management::IdAllocator;
using management::ManagementAgent;
using management::ManagementBroker;

struct ClusterValues {
    string name;
    string url;
    bool quorum;
    size_t readMax, writeEstimate;

    ClusterValues() : quorum(false), readMax(10), writeEstimate(64) {}
  
    Url getUrl(uint16_t port) const {
        if (url.empty()) return Url::getIpAddressesUrl(port);
        return Url(url);
    }
};

/** Note separating options from values to work around boost version differences.
 *  Old boost takes a reference to options objects, but new boost makes a copy.
 *  New boost allows a shared_ptr but that's not compatible with old boost.
 */
struct ClusterOptions : public Options {
    ClusterValues& values; 

    ClusterOptions(ClusterValues& v) : Options("Cluster Options"), values(v) {
        addOptions()
            ("cluster-name", optValue(values.name, "NAME"), "Name of cluster to join")
            ("cluster-url", optValue(values.url,"URL"),
             "URL of this broker, advertized to the cluster.\n"
             "Defaults to a URL listing all the local IP addresses\n")
#if HAVE_LIBCMAN
            ("cluster-cman", optValue(values.quorum), "Integrate with Cluster Manager (CMAN) cluster.")
#endif
            ("cluster-read-max", optValue(values.readMax,"N"),
             "Experimental: Limit per-client-connection queue of read buffers. 0=no limit.")
            ("cluster-write-estimate", optValue(values.writeEstimate, "Kb"),
             "Experimental: initial estimate for connection write rate per multicast cycle");
    }
};

struct UpdateClientIdAllocator : management::IdAllocator
{
    qpid::sys::AtomicValue<uint64_t> sequence;

    UpdateClientIdAllocator() : sequence(0x4000000000000000LL) {}

    uint64_t getIdFor(management::Manageable* m)
    {
        if (isUpdateQueue(m) || isUpdateExchange(m) || isUpdateSession(m) || isUpdateBinding(m)) {
            return ++sequence;
        } else {
            return 0;
        }
    }

    bool isUpdateQueue(management::Manageable* manageable)
    {
        qpid::broker::Queue* queue = dynamic_cast<qpid::broker::Queue*>(manageable);
        return queue && queue->getName() == UpdateClient::UPDATE;
    }

    bool isUpdateExchange(management::Manageable* manageable)
    {
        qpid::broker::Exchange* exchange = dynamic_cast<qpid::broker::Exchange*>(manageable);
        return exchange && exchange->getName() == UpdateClient::UPDATE;
    }

    bool isUpdateSession(management::Manageable* manageable)
    {
        broker::SessionState* session = dynamic_cast<broker::SessionState*>(manageable);
        return session && session->getId().getName() == UpdateClient::UPDATE;
    }

    bool isUpdateBinding(management::Manageable* manageable)
    {
        broker::Exchange::Binding* binding = dynamic_cast<broker::Exchange::Binding*>(manageable);
        return binding && binding->queue->getName() == UpdateClient::UPDATE;
    }
};

struct ClusterPlugin : public Plugin {

    ClusterValues values;
    ClusterOptions options;
    Cluster* cluster;
    boost::scoped_ptr<ConnectionCodec::Factory> factory;

    ClusterPlugin() : options(values), cluster(0) {}

    Options* getOptions() { return &options; }

    void initialize(Plugin::Target& target) {
        if (values.name.empty()) return; // Only if --cluster-name option was specified.
        Broker* broker = dynamic_cast<Broker*>(&target);
        if (!broker) return;
        cluster = new Cluster(
            values.name,
            values.getUrl(broker->getPort(Broker::TCP_TRANSPORT)),
            *broker,
            values.quorum,
            values.readMax, values.writeEstimate*1024
        );
        broker->setConnectionFactory(
            boost::shared_ptr<sys::ConnectionCodec::Factory>(
                new ConnectionCodec::Factory(broker->getConnectionFactory(), *cluster)));
        broker->getExchanges().registerExchange(cluster->getFailoverExchange());
        ManagementBroker* mgmt = dynamic_cast<ManagementBroker*>(ManagementAgent::Singleton::getInstance());
        if (mgmt) {
            std::auto_ptr<IdAllocator> allocator(new UpdateClientIdAllocator());
            mgmt->setAllocator(allocator);
        }
    }

    void earlyInitialize(Plugin::Target&) {}
};

static ClusterPlugin instance; // Static initialization.

}} // namespace qpid::cluster
