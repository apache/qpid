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
            ("ha-enable", optValue(settings.enabled, "yes|no"), "Enable High Availability features")
            ("ha-client-url", optValue(settings.clientUrl,"URL"), "URL that clients use to connect and fail over.")
            ("ha-broker-url", optValue(settings.brokerUrl,"URL"), "URL that backup brokers use to connect and fail over.")
            ("ha-username", optValue(settings.username, "USER"), "Username for connections between brokers")
            ("ha-password", optValue(settings.password, "PASS"), "Password for connections between brokers")
            ("ha-mechanism", optValue(settings.mechanism, "MECH"), "Authentication mechanism for connections between brokers")
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
        if (broker && settings.enabled) {
            QPID_LOG(info, "HA plugin enabled");
            haBroker.reset(new ha::HaBroker(*broker));
        } else
            QPID_LOG(info, "HA plugin disabled");
    }
};

static HaPlugin instance; // Static initialization.

}} // namespace qpid::ha
