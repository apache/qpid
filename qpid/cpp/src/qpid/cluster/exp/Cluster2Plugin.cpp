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

#include <qpid/Options.h>
#include <qpid/broker/Broker.h>
#include "Core.h"

namespace qpid {
namespace cluster {
using broker::Broker;

// TODO aconway 2010-10-19: experimental new cluster code.

/**
 * Plugin for the cluster.
 */
struct Cluster2Plugin : public Plugin {
    struct Opts : public Options {
        Settings& settings;
        Opts(Settings& s) : Options("Cluster Options"), settings(s) {
            addOptions()
                ("cluster2-name", optValue(settings.name, "NAME"), "Name of cluster to join")
                ("consume-lock", optValue(settings.consumeLockMicros, "uS"), "Maximum time a broker can hold the consume lock on a shared queue, in microseconds.");
                // TODO aconway 2010-10-19: copy across other options from ClusterPlugin.h
        }
    };

    Settings settings;
    Opts options;
    Core* core;                 // Core deletes itself on shutdown.

    Cluster2Plugin() : options(settings), core(0) {}

    Options* getOptions() { return &options; }

    void earlyInitialize(Plugin::Target& target) {
        if (settings.name.empty()) return;
        Broker* broker = dynamic_cast<Broker*>(&target);
        if (!broker) return;
        core = new Core(settings, *broker);
    }

    void initialize(Plugin::Target& target) {
        Broker* broker = dynamic_cast<Broker*>(&target);
        if (broker && core) core->initialize();
    }
};

static Cluster2Plugin instance; // Static initialization.

}} // namespace qpid::cluster
