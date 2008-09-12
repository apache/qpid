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

#include "qpid/broker/Broker.h"
#include "qpid/Plugin.h"
#include "qpid/Options.h"
#include "qpid/shared_ptr.h"
#include "qpid/log/Statement.h"

#include <boost/utility/in_place_factory.hpp>

namespace qpid {
namespace cluster {

using namespace std;

struct ClusterValues {
    string name;
    string url;

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
            ;
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
        broker::Broker* broker = dynamic_cast<broker::Broker*>(&target);
        if (!broker || values.name.empty()) return;  // Only if --cluster-name option was specified.
        cluster = new Cluster(values.name, values.getUrl(broker->getPort()), *broker);
        broker->setConnectionFactory(
            boost::shared_ptr<sys::ConnectionCodec::Factory>(
                new ConnectionCodec::Factory(broker->getConnectionFactory(), *cluster)));
    }

    void earlyInitialize(Plugin::Target&) {}
};

static ClusterPlugin instance; // Static initialization.

// For test purposes.
Cluster& getGlobalCluster() { assert(instance.cluster); return *instance.cluster; }
    
}} // namespace qpid::cluster
