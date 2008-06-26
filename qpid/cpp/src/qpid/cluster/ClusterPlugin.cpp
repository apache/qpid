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

struct OptionValues {
    string name;
    string url;

    Url getUrl(uint16_t port) const {
        if (url.empty()) return Url::getIpAddressesUrl(port);
        return Url(url);
    }
};

// Note we update the values in a separate struct.
// This is to work around boost::program_options differences,
// older versions took a reference to the options, newer
// ones take a copy (or require a shared_ptr)
//
struct ClusterOptions : public Options {

    ClusterOptions(OptionValues* v) : Options("Cluster Options") {
        addOptions()
            ("cluster-name", optValue(v->name, "NAME"), "Name of cluster to join")
            ("cluster-url", optValue(v->url,"URL"),
             "URL of this broker, advertized to the cluster.\n"
             "Defaults to a URL listing all the local IP addresses\n");
    }
};

struct ClusterPlugin : public PluginT<Broker> {
    OptionValues values;
    boost::optional<Cluster> cluster;

    ClusterPlugin(const OptionValues& v) : values(v) {}
    
    void initializeT(Broker& broker) {
        cluster = boost::in_place(values.name, values.getUrl(broker.getPort()), boost::ref(broker));
        broker.getSessionManager().add(cluster->getObserver());	
    }
};

struct PluginFactory : public Plugin::FactoryT<Broker> {

    OptionValues values;
    ClusterOptions options;

    PluginFactory() : options(&values) {}

    Options* getOptions() { return &options; }

    boost::shared_ptr<Plugin> createT(Broker&) {
        // Only provide to a Broker, and only if the --cluster config is set.
        if (values.name.empty())
            return boost::shared_ptr<Plugin>();
        else
            return make_shared_ptr(new ClusterPlugin(values));
    }
};

static PluginFactory instance; // Static initialization.
    
}} // namespace qpid::cluster
