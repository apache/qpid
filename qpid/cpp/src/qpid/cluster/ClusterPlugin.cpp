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

#include "ConnectionInterceptor.h"


#include "qpid/broker/Broker.h"
#include "qpid/cluster/Cluster.h"
#include "qpid/Plugin.h"
#include "qpid/Options.h"
#include "qpid/shared_ptr.h"

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
    boost::intrusive_ptr<Cluster> cluster;

    ClusterPlugin() : options(values) {}

    Options* getOptions() { return &options; }

    void init(broker::Broker& b) {
        if (values.name.empty()) return;  // Only if --cluster-name option was specified.
        if (cluster) throw Exception("Cluster plugin cannot be initialized twice in one process.");
        cluster = new Cluster(values.name, values.getUrl(b.getPort()), b);
        b.addFinalizer(boost::bind(&ClusterPlugin::shutdown, this));
    }

    template <class T> void init(T& t) {
        if (cluster) cluster->initialize(t);
    }
    
    template <class T> bool init(Plugin::Target& target) {
        T* t = dynamic_cast<T*>(&target);
        if (t) init(*t);
        return t;
    }

    void earlyInitialize(Plugin::Target&) {}

    void initialize(Plugin::Target& target) {
        if (init<broker::Broker>(target)) return;
        if (!cluster) return;   // Remaining plugins only valid if cluster initialized.
        if (init<broker::Connection>(target)) return;
    }

    void shutdown() { cluster = 0; }
};

static ClusterPlugin instance; // Static initialization.

// For test purposes.
boost::intrusive_ptr<Cluster> getGlobalCluster() { return instance.cluster; }
    
}} // namespace qpid::cluster
