/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

#include "qpid/broker/Broker.h"
#include "qpid/Plugin.h"
#include "qpid/Options.h"
#include "qpid/DataDir.h"
#include "qpid/log/Statement.h"
#include "qpid/legacystore/MessageStoreImpl.h"

using mrg::msgstore::MessageStoreImpl;

namespace qpid {
namespace broker {

using namespace std;

struct StorePlugin : public Plugin {

    MessageStoreImpl::StoreOptions options;
    boost::shared_ptr<MessageStoreImpl> store;

    Options* getOptions() { return &options; }

    void earlyInitialize (Plugin::Target& target)
    {
        Broker* broker = dynamic_cast<Broker*>(&target);
        if (!broker) return;
        store.reset(new MessageStoreImpl(broker));
        const DataDir& dataDir = broker->getDataDir ();
        if (options.storeDir.empty ())
        {
            if (!dataDir.isEnabled ())
                throw Exception ("msgstore: If --data-dir is blank or --no-data-dir is specified, --store-dir must be present.");

            options.storeDir = dataDir.getPath ();
        }
        store->init(&options);
        boost::shared_ptr<qpid::broker::MessageStore> brokerStore(store);
        broker->setStore(brokerStore);
        target.addFinalizer(boost::bind(&StorePlugin::finalize, this));
    }

    void initialize(Plugin::Target& target)
    {
        Broker* broker = dynamic_cast<Broker*>(&target);
        if (!broker) return;
        if (!store) return;
        QPID_LOG(info, "Enabling management instrumentation for the store.");
        store->initManagement();
    }

    void finalize()
    {
        store.reset();
    }

    const char* id() {return "StorePlugin";}
};

static StorePlugin instance; // Static initialization.

}} // namespace qpid::broker
