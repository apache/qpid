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
#include "HaBroker.h"
#include "Settings.h"
#include "qpid/Plugin.h"
#include "qpid/Options.h"
#include "qpid/broker/Broker.h"


namespace qpid {
namespace ha {

using namespace std;

struct Options : public qpid::Options {
    Settings& settings;
    Options(Settings& s) : qpid::Options("HA Options"), settings(s) {
        addOptions()
            ("ha-cluster", optValue(settings.cluster, "yes|no"),
             "Join a HA active/passive cluster.")
            ("ha-brokers", optValue(settings.brokerUrl,"URL"),
             "URL that backup brokers use to connect and fail over.")
            ("ha-public-brokers", optValue(settings.clientUrl,"URL"),
             "URL that clients use to connect and fail over, defaults to ha-brokers.")
            ("ha-replicate-default",
             optValue(settings.replicateDefault, "LEVEL"),
            "Replication level for creating queues and exchanges if there is no qpid.replicate argument supplied. LEVEL is 'none', 'configuration' or 'all'")
            ("ha-expected-backups", optValue(settings.expectedBackups, "N"),
             "Number of backups expected to be active in the HA cluster.")
            ("ha-username", optValue(settings.username, "USER"),
             "Username for connections between HA brokers")
            ("ha-password", optValue(settings.password, "PASS"),
             "Password for connections between HA brokers")
            ("ha-mechanism", optValue(settings.mechanism, "MECH"),
             "Authentication mechanism for connections between HA brokers")
            ;
    }
};

struct HaPlugin : public Plugin {

    Settings settings;
    Options options;
    auto_ptr<HaBroker> haBroker;

    HaPlugin() : options(settings) {}

    Options* getOptions() { return &options; }

    void earlyInitialize(Plugin::Target& ) {}

    void initialize(Plugin::Target& target) {
        broker::Broker* broker = dynamic_cast<broker::Broker*>(&target);
        if (broker) haBroker.reset(new ha::HaBroker(*broker, settings));
    }
};

static HaPlugin instance; // Static initialization.

}} // namespace qpid::ha
