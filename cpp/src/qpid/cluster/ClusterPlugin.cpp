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
#include <boost/program_options/value_semantic.hpp>



#include "qpid/broker/Broker.h"
#include "qpid/cluster/Cluster.h"
#include "qpid/Plugin.h"
#include "qpid/Options.h"
#include "qpid/shared_ptr.h"

#include <boost/optional.hpp>
#include <boost/utility/in_place_factory.hpp>


namespace qpid {
namespace cluster {

using namespace std;

struct ClusterOptions : public Options {
    string name;
    string url;

    ClusterOptions() : Options("Cluster Options") {
        addOptions()
            ("cluster-name", optValue(name, "NAME"), "Name of cluster to join")
            ("cluster-url", optValue(url,"URL"),
             "URL of this broker, advertized to the cluster.\n"
             "Defaults to a URL listing all the local IP addresses\n");
    }

    Url getUrl(uint16_t port) const {
        if (url.empty()) return Url::getIpAddressesUrl(port);
        return Url(url);
    }
};

struct ClusterPlugin : public Plugin {

    ClusterOptions options;
    boost::optional<Cluster> cluster;

    Options* getOptions() { return &options; }

    void earlyInitialize(Plugin::Target&) {}

    void initialize(Plugin::Target& target) {
        broker::Broker* broker = dynamic_cast<broker::Broker*>(&target);
        // Only provide to a Broker, and only if the --cluster config is set.
        if (broker && !options.name.empty()) {
            assert(!cluster); // A process can only belong to one cluster.
            cluster = boost::in_place(options.name,
                                      options.getUrl(broker->getPort()),
                                      boost::ref(*broker));
            // FIXME aconway 2008-02-01: Add observer.
        }
    }
};

static ClusterPlugin instance; // Static initialization.
    
}} // namespace qpid::cluster
