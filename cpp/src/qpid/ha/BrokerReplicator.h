#ifndef QPID_HA_REPLICATOR_H
#define QPID_HA_REPLICATOR_H

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

#include "types.h"
#include "ReplicationTest.h"
#include "AlternateExchangeSetter.h"
#include "qpid/Address.h"
#include "qpid/broker/Exchange.h"
#include "qpid/types/Variant.h"
#include "qpid/management/ManagementObject.h"
#include <boost/shared_ptr.hpp>
#include <boost/enable_shared_from_this.hpp>
#include <set>

namespace qpid {

namespace broker {
class Broker;
class Link;
class Bridge;
class SessionHandler;
class Connection;
}

namespace framing {
class FieldTable;
}

namespace ha {
class HaBroker;
class QueueReplicator;

/**
 * Replicate configuration on a backup broker.
 *
 * Implemented as an exchange that subscribes to receive QMF
 * configuration events from the primary. It configures local queues
 * exchanges and bindings to replicate the primary.
 * It also creates QueueReplicators for newly replicated queues.
 *
 * THREAD UNSAFE: Only called in Link connection thread, no need for locking.
 *
 */
class BrokerReplicator : public broker::Exchange,
                         public boost::enable_shared_from_this<BrokerReplicator>
{
  public:
    BrokerReplicator(HaBroker&, const boost::shared_ptr<broker::Link>&);
    ~BrokerReplicator();

    void initialize();

    // Exchange methods
    std::string getType() const;
    bool bind(boost::shared_ptr<broker::Queue>, const std::string&, const framing::FieldTable*);
    bool unbind(boost::shared_ptr<broker::Queue>, const std::string&, const framing::FieldTable*);
    void route(broker::Deliverable&);
    bool isBound(boost::shared_ptr<broker::Queue>, const std::string* const, const framing::FieldTable* const);
    void shutdown();

  private:
    typedef boost::shared_ptr<QueueReplicator> QueueReplicatorPtr;
    typedef std::pair<boost::shared_ptr<broker::Queue>, bool> CreateQueueResult;
    typedef std::pair<boost::shared_ptr<broker::Exchange>, bool> CreateExchangeResult;

    typedef std::pair<std::string,std::string> EventKey;
    typedef void (BrokerReplicator::*DispatchFunction)(types::Variant::Map&);
    typedef std::map<EventKey, DispatchFunction> EventDispatchMap;

    typedef std::map<std::string, QueueReplicatorPtr> QueueReplicatorMap;


    /** Keep track of queues and exchanges that need to be cleaned up. */
    class Cleaner {
      public:
        Cleaner(BrokerReplicator&);

        /** Scan for existing queues and exchanges. */
        void start();

        // Forget a queue/exchange that does not need cleaning
        void forgetExchange(const std::string& name);
        void forgetQueue(const std::string& name);
        // Clean up queues/exchange that are no longer on primary
        void cleanExchanges();
        void cleanQueues();

      private:
        typedef std::set<std::string> Names;

        // add a queue/exchange that may need cleaning.
        void addExchange(boost::shared_ptr<broker::Exchange>);
        void addQueue(boost::shared_ptr<broker::Queue>);

        BrokerReplicator& brokerReplicator;
        Names queues, exchanges;
    };
  friend class Cleaner;

    class ErrorListener;
    class ConnectionObserver;

    void initializeBridge(broker::Bridge&, broker::SessionHandler&);

    void doEventQueueDeclare(types::Variant::Map& values);
    void doEventQueueDelete(types::Variant::Map& values);
    void doEventExchangeDeclare(types::Variant::Map& values);
    void doEventExchangeDelete(types::Variant::Map& values);
    void doEventBind(types::Variant::Map&);
    void doEventUnbind(types::Variant::Map&);
    void doEventMembersUpdate(types::Variant::Map&);
    void doEventSubscribe(types::Variant::Map&);

    void doResponseQueue(types::Variant::Map& values);
    void doResponseExchange(types::Variant::Map& values);
    void doResponseBind(types::Variant::Map& values);
    void doResponseHaBroker(types::Variant::Map& values);

    QueueReplicatorPtr findQueueReplicator(const std::string& qname);
    QueueReplicatorPtr startQueueReplicator(const boost::shared_ptr<broker::Queue>&);

    QueueReplicatorPtr replicateQueue(
        const std::string& name,
        bool durable,
        bool autodelete,
        const qpid::framing::FieldTable& arguments,
        const std::string& alternateExchange);

    CreateExchangeResult createExchange(
        const std::string& name,
        const std::string& type,
        bool durable,
        const qpid::framing::FieldTable& args,
        const std::string& alternateExchange);

    bool deactivate(boost::shared_ptr<broker::Exchange> ex, bool destroy);
    void deleteQueue(const std::string& name, bool purge=true);
    void deleteExchange(const std::string& name);

    void autoDeleteCheck(boost::shared_ptr<broker::Exchange>, std::set<std::string>&);
    void disconnected();

    std::string logPrefix;
    std::string userId, remoteHost;
    ReplicationTest replicationTest;
    HaBroker& haBroker;
    broker::Broker& broker;
    boost::shared_ptr<broker::Link> link;
    bool initialized;
    AlternateExchangeSetter alternates;
    qpid::Address primary;
    typedef std::set<std::string> StringSet;
    StringSet replicatedExchanges; // exchanges that have been replicated.
    Cleaner cleaner;
    broker::Connection* connection;
    EventDispatchMap dispatch;
};
}} // namespace qpid::broker

#endif  /*!QPID_HA_REPLICATOR_H*/
