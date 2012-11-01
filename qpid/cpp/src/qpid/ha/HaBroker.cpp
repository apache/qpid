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
#include "Backup.h"
#include "BackupConnectionExcluder.h"
#include "ConnectionObserver.h"
#include "HaBroker.h"
#include "Primary.h"
#include "QueueReplicator.h"
#include "ReplicatingSubscription.h"
#include "Settings.h"
#include "StatusCheck.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/Exception.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Link.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/SignalHandler.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/management/ManagementAgent.h"
#include "qpid/sys/SystemInfo.h"
#include "qpid/types/Uuid.h"
#include "qpid/framing/Uuid.h"
#include "qmf/org/apache/qpid/ha/Package.h"
#include "qmf/org/apache/qpid/ha/ArgsHaBrokerReplicate.h"
#include "qmf/org/apache/qpid/ha/ArgsHaBrokerSetBrokersUrl.h"
#include "qmf/org/apache/qpid/ha/ArgsHaBrokerSetPublicUrl.h"
#include "qmf/org/apache/qpid/ha/EventMembersUpdate.h"
#include "qpid/log/Statement.h"
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace ha {

namespace _qmf = ::qmf::org::apache::qpid::ha;
using namespace management;
using namespace std;
using types::Variant;
using types::Uuid;
using sys::Mutex;
using boost::shared_ptr;

// Called in Plugin::earlyInitialize
HaBroker::HaBroker(broker::Broker& b, const Settings& s)
    : logPrefix("Broker: "),
      broker(b),
      systemId(broker.getSystem()->getSystemId().data()),
      settings(s),
      observer(new ConnectionObserver(*this, systemId)),
      status(STANDALONE),
      membership(systemId),
      replicationTest(s.replicateDefault.get())
{
    // If we are joining a cluster we must start excluding clients now,
    // otherwise there's a window for a client to connect before we get to
    // initialize()
    if (settings.cluster) {
        QPID_LOG(debug, logPrefix << "Rejecting client connections.");
        shared_ptr<broker::ConnectionObserver> excluder(new BackupConnectionExcluder);
        observer->setObserver(excluder, "Backup: ");
        broker.getConnectionObservers().add(observer);
    }
}

// Called in Plugin::initialize
void HaBroker::initialize() {

    // FIXME aconway 2012-07-19: assumes there's a TCP transport with a meaningful port.
    brokerInfo = BrokerInfo(
        broker.getSystem()->getNodeName(),
        broker.getPort(broker::Broker::TCP_TRANSPORT),
        systemId);
    QPID_LOG(notice, logPrefix << "Initializing: " << brokerInfo);

    // Set up the management object.
    ManagementAgent* ma = broker.getManagementAgent();
    if (settings.cluster && !ma)
        throw Exception("Cannot start HA: management is disabled");
    _qmf::Package  packageInit(ma);
    mgmtObject = _qmf::HaBroker::shared_ptr(new _qmf::HaBroker(ma, this, "ha-broker"));
    mgmtObject->set_replicateDefault(settings.replicateDefault.str());
    mgmtObject->set_systemId(systemId);
    ma->addObject(mgmtObject);

    // Register a factory for replicating subscriptions.
    broker.getConsumerFactories().add(
        boost::shared_ptr<ReplicatingSubscription::Factory>(
            new ReplicatingSubscription::Factory()));

    // If we are in a cluster, start as backup in joining state.
    if (settings.cluster) {
        status = JOINING;
        backup.reset(new Backup(*this, settings));
        broker.getKnownBrokers = boost::bind(&HaBroker::getKnownBrokers, this);
        statusCheck.reset(new StatusCheck(logPrefix, broker.getLinkHearbeatInterval(), brokerInfo));
    }

    if (!settings.clientUrl.empty()) setClientUrl(Url(settings.clientUrl));
    if (!settings.brokerUrl.empty()) setBrokerUrl(Url(settings.brokerUrl));


    // NOTE: lock is not needed in a constructor, but create one
    // to pass to functions that have a ScopedLock parameter.
    Mutex::ScopedLock l(lock);
    statusChanged(l);
}

HaBroker::~HaBroker() {
    QPID_LOG(notice, logPrefix << "Shut down");
    broker.getConnectionObservers().remove(observer);
}

// Called from ManagementMethod on promote.
void HaBroker::recover() {
    boost::shared_ptr<Backup> b;
    {
        Mutex::ScopedLock l(lock);
        // No longer replicating, close link. Note: link must be closed before we
        // setStatus(RECOVERING) as that will remove our broker info from the
        // outgoing link properties so we won't recognize self-connects.
        b = backup;
        backup.reset();         // Reset in lock.
    }
    b.reset();                  // Call destructor outside of lock.
    BrokerInfo::Set backups;
    {
        Mutex::ScopedLock l(lock);
        setStatus(RECOVERING, l);
        backups = membership.otherBackups();
        membership.reset(brokerInfo);
        // Drop the lock, new Primary may call back on activate.
    }
    // Outside of lock, may call back on activate()
    primary.reset(new Primary(*this, backups)); // Starts primary-ready check.
}

// Called back from Primary active check.
void HaBroker::activate() { setStatus(ACTIVE); }

Manageable::status_t HaBroker::ManagementMethod (uint32_t methodId, Args& args, string&) {
    switch (methodId) {
      case _qmf::HaBroker::METHOD_PROMOTE: {
          switch (getStatus()) {
            case JOINING:
              if (statusCheck->canPromote())
                  recover();
              else {
                  QPID_LOG(error, logPrefix << "Cluster already active, cannot be promoted");
                  throw Exception("Cluster already active, cannot be promoted.");
              }
              break;
             case CATCHUP:
              QPID_LOG(error, logPrefix << "Still catching up, cannot be promoted.");
              throw Exception("Still catching up, cannot be promoted.");
              break;
            case READY: recover(); break;
            case RECOVERING: break;
            case ACTIVE: break;
            case STANDALONE: break;
          }
          break;
      }
      case _qmf::HaBroker::METHOD_SETBROKERSURL: {
          setBrokerUrl(Url(dynamic_cast<_qmf::ArgsHaBrokerSetBrokersUrl&>(args).i_url));
          break;
      }
      case _qmf::HaBroker::METHOD_SETPUBLICURL: {
          setClientUrl(Url(dynamic_cast<_qmf::ArgsHaBrokerSetPublicUrl&>(args).i_url));
          break;
      }
      case _qmf::HaBroker::METHOD_REPLICATE: {
          _qmf::ArgsHaBrokerReplicate& bq_args =
              dynamic_cast<_qmf::ArgsHaBrokerReplicate&>(args);
          QPID_LOG(debug, logPrefix << "Replicate individual queue "
                   << bq_args.i_queue << " from " << bq_args.i_broker);

          boost::shared_ptr<broker::Queue> queue = broker.getQueues().get(bq_args.i_queue);
          Url url(bq_args.i_broker);
          string protocol = url[0].protocol.empty() ? "tcp" : url[0].protocol;
          Uuid uuid(true);
          std::pair<broker::Link::shared_ptr, bool> result = broker.getLinks().declare(
              broker::QPID_NAME_PREFIX + string("ha.link.") + uuid.str(),
              url[0].host, url[0].port, protocol,
              false,              // durable
              settings.mechanism, settings.username, settings.password,
              false);           // no amq.failover - don't want to use client URL.
          boost::shared_ptr<broker::Link> link = result.first;
          link->setUrl(url);
          // Create a queue replicator
          boost::shared_ptr<QueueReplicator> qr(
              new QueueReplicator(*this, queue, link));
          qr->activate();
          broker.getExchanges().registerExchange(qr);
          break;
      }

      default:
        return Manageable::STATUS_UNKNOWN_METHOD;
    }
    return Manageable::STATUS_OK;
}

void HaBroker::setClientUrl(const Url& url) {
    Mutex::ScopedLock l(lock);
    if (url.empty()) throw Exception("Invalid empty URL for HA client failover");
    clientUrl = url;
    updateClientUrl(l);
}

void HaBroker::updateClientUrl(Mutex::ScopedLock&) {
    Url url = clientUrl.empty() ? brokerUrl : clientUrl;
    mgmtObject->set_publicUrl(url.str());
    knownBrokers.clear();
    knownBrokers.push_back(url);
    QPID_LOG(debug, logPrefix << "Setting client URL to: " << url);
}

void HaBroker::setBrokerUrl(const Url& url) {
    boost::shared_ptr<Backup> b;
    {
        Mutex::ScopedLock l(lock);
        brokerUrl = url;
        mgmtObject->set_brokersUrl(brokerUrl.str());
        QPID_LOG(info, logPrefix << "Broker URL set to: " << url);
        if (status == JOINING && statusCheck.get()) statusCheck->setUrl(url);
        // Updating broker URL also updates defaulted client URL:
        if (clientUrl.empty()) updateClientUrl(l);
        b = backup;
    }
    if (b) b->setBrokerUrl(url); // Oustside lock, avoid deadlock
}

std::vector<Url> HaBroker::getKnownBrokers() const {
    Mutex::ScopedLock l(lock);
    return knownBrokers;
}

void HaBroker::shutdown() {
    QPID_LOG(critical, logPrefix << "Critical error, shutting down.");
    broker.shutdown();
}

BrokerStatus HaBroker::getStatus() const {
    Mutex::ScopedLock l(lock);
    return status;
}

void HaBroker::setStatus(BrokerStatus newStatus) {
    Mutex::ScopedLock l(lock);
    setStatus(newStatus, l);
}

namespace {
bool checkTransition(BrokerStatus from, BrokerStatus to) {
    // Legal state transitions. Initial state is JOINING, ACTIVE is terminal.
    static const BrokerStatus TRANSITIONS[][2] = {
        { JOINING, CATCHUP },    // Connected to primary
        { JOINING, RECOVERING }, // Chosen as initial primary.
        { CATCHUP, READY },      // Caught up all queues, ready to take over.
        { READY, RECOVERING },   // Chosen as new primary
        { READY, CATCHUP },      // Timed out failing over, demoted to catch-up.
        { RECOVERING, ACTIVE }   // All expected backups are ready
    };
    static const size_t N = sizeof(TRANSITIONS)/sizeof(TRANSITIONS[0]);
    for (size_t i = 0; i < N; ++i) {
        if (TRANSITIONS[i][0] == from && TRANSITIONS[i][1] == to)
            return true;
    }
    return false;
}
} // namespace

void HaBroker::setStatus(BrokerStatus newStatus, Mutex::ScopedLock& l) {
    QPID_LOG(info, logPrefix << "Status change: "
             << printable(status) << " -> " << printable(newStatus));
    bool legal = checkTransition(status, newStatus);
    assert(legal);
    if (!legal) {
        QPID_LOG(critical, logPrefix << "Illegal state transition: "
                 << printable(status) << " -> " << printable(newStatus));
        shutdown();
    }
    status = newStatus;
    statusChanged(l);
}

void HaBroker::statusChanged(Mutex::ScopedLock& l) {
    mgmtObject->set_status(printable(status).str());
    brokerInfo.setStatus(status);
    setLinkProperties(l);
}

void HaBroker::membershipUpdated(Mutex::ScopedLock&) {
    QPID_LOG(info, logPrefix << "Membership changed: " <<  membership);
    Variant::List brokers = membership.asList();
    mgmtObject->set_members(brokers);
    broker.getManagementAgent()->raiseEvent(_qmf::EventMembersUpdate(brokers));
}

void HaBroker::setMembership(const Variant::List& brokers) {
    boost::shared_ptr<Backup> b;
    {
        Mutex::ScopedLock l(lock);
        membership.assign(brokers);
        QPID_LOG(info, logPrefix << "Membership update: " <<  membership);
        BrokerInfo info;
        // Update my status to what the primary says it is.  The primary can toggle
        // status between READY and CATCHUP based on the state of our subscriptions.
        if (membership.get(systemId, info) && status != info.getStatus()) {
            setStatus(info.getStatus(), l);
            b = backup;
        }
        membershipUpdated(l);
    }
    if (b) b->setStatus(status); // Oustside lock, avoid deadlock
}

void HaBroker::resetMembership(const BrokerInfo& b) {
    Mutex::ScopedLock l(lock);
    membership.reset(b);
    QPID_LOG(debug, logPrefix << "Membership reset to: " <<  membership);
    membershipUpdated(l);
}

void HaBroker::addBroker(const BrokerInfo& b) {
    Mutex::ScopedLock l(lock);
    membership.add(b);
    QPID_LOG(debug, logPrefix << "Membership add: " <<  b);
    membershipUpdated(l);
}

void HaBroker::removeBroker(const Uuid& id) {
    Mutex::ScopedLock l(lock);
    BrokerInfo info;
    if (membership.get(id, info)) {
        membership.remove(id);
        QPID_LOG(debug, logPrefix << "Membership remove: " <<  info);
        membershipUpdated(l);
    }
}

void HaBroker::setLinkProperties(Mutex::ScopedLock&) {
    framing::FieldTable linkProperties = broker.getLinkClientProperties();
    if (isBackup(status)) {
        // If this is a backup then any outgoing links are backup
        // links and need to be tagged.
        linkProperties.setTable(ConnectionObserver::BACKUP_TAG, brokerInfo.asFieldTable());
    }
    else {
        // If this is a primary then any outgoing links are federation links
        // and should not be tagged.
        linkProperties.erase(ConnectionObserver::BACKUP_TAG);
    }
    broker.setLinkClientProperties(linkProperties);
}

}} // namespace qpid::ha
