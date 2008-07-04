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
using broker::Broker;


// Note we update the values in a separate struct.
// This is to work around boost::program_options differences,
// older versions took a reference to the options, newer
// ones take a copy (or require a shared_ptr)
//
struct ClusterOptions : public Options {
    std::string name;
    std::string url;

    ClusterOptions() : Options("Cluster Options") {
        addOptions()
            ("cluster-name", optValue(name,""), "Cluster identifier")
            ("cluster-url", optValue(url,"URL"),
             "URL of this broker, advertized to the cluster.\n"
             "Defaults to a URL listing all the local IP addresses\n")
            ;
    }
};

struct ClusterPlugin : public PluginT<Broker> {
    ClusterOptions options;
    boost::optional<Cluster> cluster;

    ClusterPlugin(const ClusterOptions& opts) : options(opts) {}
    
    void initializeT(Broker& broker) { // FIXME aconway 2008-07-01: drop T suffix.
        Url url = options.url.empty() ? Url::getIpAddressesUrl(broker.getPort()) : Url(options.url);
        cluster = boost::in_place(options.name, url, boost::ref(broker));
        broker.getConnectionManager().add(cluster->getObserver());	// FIXME aconway 2008-07-01: to Cluster ctor
    }
};

struct PluginFactory : public Plugin::FactoryT<Broker> {

    ClusterOptions options;

    Options* getOptions() { return &options; }

    boost::shared_ptr<Plugin> createT(Broker&) {
        if (options.name.empty()) { // No cluster name, don't initialize cluster.
            return boost::shared_ptr<Plugin>();
        }
        else
            return make_shared_ptr(new ClusterPlugin(options));
    }
};

static PluginFactory instance; // Static initialization.
    
}} // namespace qpid::cluster
