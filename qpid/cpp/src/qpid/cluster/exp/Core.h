#ifndef QPID_CLUSTER_CORE_H
#define QPID_CLUSTER_CORE_H

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

#include "LockedMap.h"
#include "Group.h"
#include "Settings.h"
#include "qpid/cluster/types.h"
#include "qpid/cluster/Cpg.h"
#include "qpid/broker/QueuedMessage.h"
#include "qpid/sys/Time.h"
#include <boost/intrusive_ptr.hpp>
#include <string>
#include <memory>

// TODO aconway 2010-10-19: experimental cluster code.

namespace qpid {

namespace framing{
class AMQBody;
}

namespace broker {
class Broker;
}

namespace cluster {
class EventHandler;
class BrokerContext;

/**
 * Cluster core.
 * 
 * Holds together the various objects that implement cluster behavior,
 * and holds state that is shared by multiple components.
 *
 * Thread safe: called from broker broker threads and CPG dispatch threads.
 */
class Core
{
  public:
    typedef std::vector<boost::intrusive_ptr<Group> > Groups;

    /** Constructed during Plugin::earlyInitialize() */
    Core(const Settings&, broker::Broker&);

    /** Called during Plugin::initialize() */
    void initialize();

    /** Shut down broker due to fatal error. Caller should log a critical message */
    void fatal();

    broker::Broker& getBroker() { return broker; }
    BrokerContext& getBrokerContext() { return *brokerHandler; }

    const Settings& getSettings() const { return settings; }

    Group& getGroup(size_t hashValue);
    Group& getGroup(const std::string& queueName);

  private:
    broker::Broker& broker;
    BrokerContext* brokerHandler; // Handles broker events.
    Settings settings;
    Groups groups;
};
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_CORE_H*/
