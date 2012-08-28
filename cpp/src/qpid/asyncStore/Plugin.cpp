/*
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
 */

/**
 * \file Plugin.cpp
 */

#include "qpid/asyncStore/Plugin.h"

#include "qpid/asyncStore/AsyncStoreImpl.h"
#include "qpid/broker/Broker.h"

namespace qpid {
namespace broker {

void
Plugin::earlyInitialize(Target& target) {
    Broker* broker = dynamic_cast<Broker*>(&target);
    if (!broker) return;
    DataDir& dataDir = broker->getDataDir ();
    if (m_options.m_storeDir.empty ())
    {
        if (!dataDir.isEnabled ())
            throw Exception ("asyncStore: If --data-dir is blank or --no-data-dir is specified, --store-dir must be present.");

        m_options.m_storeDir = dataDir.getPath ();
    }
    m_store.reset(new qpid::asyncStore::AsyncStoreImpl(broker->getPoller(), m_options));
    boost::shared_ptr<qpid::broker::AsyncStore> brokerAsyncStore(m_store);
    broker->setAsyncStore(brokerAsyncStore);
    boost::function<void()> fn = boost::bind(&Plugin::finalize, this);
    target.addFinalizer(fn);
    QPID_LOG(info, "asyncStore: Initialized using path " << m_options.m_storeDir);
}

void
Plugin::initialize(Target& target) {
    Broker* broker = dynamic_cast<Broker*>(&target);
    if (!broker || !m_store) return;

    // Not done in earlyInitialize as the Broker::isInCluster test won't work there.
    if (broker->isInCluster()) {
        QPID_LOG(info, "asyncStore: Part of cluster: Disabling management instrumentation");
    } else {
        QPID_LOG(info, "asyncStore: Enabling management instrumentation");
        m_store->initManagement(broker);
    }
}

void
Plugin::finalize() {
    m_store.reset();
}

qpid::Options*
Plugin::getOptions() {
    return &m_options;
}

}} // namespace qpid::broker
