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
#include "qpid/broker/Broker.h"
#include "qpid/framing/HandlerUpdater.h"
#include "qpid/cluster/Cluster.h"
#include "qpid/Plugin.h"
#include "qpid/Options.h"

namespace qpid {
namespace cluster {

using namespace std;

struct ClusterPluginProvider : public PluginProvider {

    struct ClusterOptions : public Options {
        string clusterName;
        ClusterOptions() {
            addOptions()
                ("cluster", optValue(clusterName, "NAME"),
                 "Join the cluster named NAME");
        }
    };

    ClusterOptions options;
    shared_ptr<Cluster> cluster;

    Options* getOptions() {
        return &options;
    }

    void provide(PluginUser& user) {
        broker::Broker* broker = dynamic_cast<broker::Broker*>(&user);
        // Only provide to a Broker, and only if the --cluster config is set.
        if (broker && !options.clusterName.empty()) {
            assert(!cluster); // A process can only belong to one cluster.
            cluster.reset(new Cluster(options.clusterName, broker->getUrl()));
            // FIXME aconway 2007-06-29: register HandlerUpdater.
        }
    }
};

static ClusterPluginProvider instance; // Static initialization.
    
}} // namespace qpid::cluster
