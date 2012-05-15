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
#include "ConnectionExcluder.h"
#include "HaBroker.h"
#include "Primary.h"
#include "Settings.h"
#include "ReplicatingSubscription.h"
#include "qpid/Exception.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Link.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/SignalHandler.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/management/ManagementAgent.h"
#include "qmf/org/apache/qpid/ha/Package.h"
#include "qmf/org/apache/qpid/ha/ArgsHaBrokerReplicate.h"
#include "qmf/org/apache/qpid/ha/ArgsHaBrokerSetBrokers.h"
#include "qmf/org/apache/qpid/ha/ArgsHaBrokerSetPublicBrokers.h"
#include "qmf/org/apache/qpid/ha/ArgsHaBrokerSetExpectedBackups.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace ha {

namespace _qmf = ::qmf::org::apache::qpid::ha;
using namespace management;
using namespace std;

HaBroker::HaBroker(broker::Broker& b, const Settings& s)
    : logPrefix(status),
      broker(b),
      settings(s),
      mgmtObject(0),
      status(STANDALONE),
      excluder(new ConnectionExcluder(logPrefix))
{
    // Set up the management object.
    ManagementAgent* ma = broker.getManagementAgent();
    if (settings.cluster && !ma)
        throw Exception("Cannot start HA: management is disabled");
    _qmf::Package  packageInit(ma);
    mgmtObject = new _qmf::HaBroker(ma, this, "ha-broker");
    mgmtObject->set_replicateDefault(settings.replicateDefault.str());
    ma->addObject(mgmtObject);

    // Register a factory for replicating subscriptions.
    broker.getConsumerFactories().add(
        boost::shared_ptr<ReplicatingSubscription::Factory>(
            new ReplicatingSubscription::Factory(*this)));

    // If we are in a cluster, start as backup in joining state.
    if (settings.cluster) {
        status = JOINING;
        backup.reset(new Backup(*this, s));
        broker.getConnectionObservers().add(excluder);
        broker.getKnownBrokers = boost::bind(&HaBroker::getKnownBrokers, this);
    }

    // NOTE: lock is not needed in a constructor, but create one
    // to pass to functions that have a ScopedLock parameter.
    sys::Mutex::ScopedLock l(lock);
    if (!settings.clientUrl.empty()) setClientUrl(Url(settings.clientUrl), l);
    if (!settings.brokerUrl.empty()) setBrokerUrl(Url(settings.brokerUrl), l);
    statusChanged(l);
}

HaBroker::~HaBroker() {}

void HaBroker::recover(sys::Mutex::ScopedLock&) {
    setStatus(RECOVERING);
    backup.reset();                    // No longer replicating, close link.
    primary.reset(new Primary(*this)); // Starts primary-ready check.
}

// Called back from Primary ready check.
void HaBroker::activate() {
    sys::Mutex::ScopedLock l(lock);
    activate(l);
}

void HaBroker::activate(sys::Mutex::ScopedLock&) {
    setStatus(ACTIVE);
    broker.getConnectionObservers().remove(excluder); // This allows client connections.
}

ReplicateLevel HaBroker::replicateLevel(const std::string& str) {
    Enum<ReplicateLevel> rl;
    if (rl.parseNoThrow(str)) return ReplicateLevel(rl.get());
    else return getSettings().replicateDefault.get();
}

ReplicateLevel HaBroker::replicateLevel(const framing::FieldTable& f) {
    if (f.isSet(QPID_REPLICATE))
        return replicateLevel(f.getAsString(QPID_REPLICATE));
    else
        return getSettings().replicateDefault.get();
}

ReplicateLevel HaBroker::replicateLevel(const types::Variant::Map& m) {
    types::Variant::Map::const_iterator i = m.find(QPID_REPLICATE);
    if (i != m.end())
        return replicateLevel(i->second.asString());
    else
        return getSettings().replicateDefault.get();
}

Manageable::status_t HaBroker::ManagementMethod (uint32_t methodId, Args& args, string&) {
    sys::Mutex::ScopedLock l(lock);
    switch (methodId) {
      case _qmf::HaBroker::METHOD_PROMOTE: {
          switch (status) {
            case JOINING: activate(l); break;
            case CATCHUP:
              // FIXME aconway 2012-04-27: don't allow promotion in catch-up
              // QPID_LOG(error, logPrefix << "Still catching up, cannot be promoted.");
              // throw Exception("Still catching up, cannot be promoted.");
              recover(l);
              break;
            case READY: recover(l); break;
            case RECOVERING: break;
            case ACTIVE: break;
            case STANDALONE: break;
          }
          break;
      }
      case _qmf::HaBroker::METHOD_SETBROKERS: {
          setBrokerUrl(Url(dynamic_cast<_qmf::ArgsHaBrokerSetBrokers&>(args).i_url), l);
          break;
      }
      case _qmf::HaBroker::METHOD_SETPUBLICBROKERS: {
          setClientUrl(Url(dynamic_cast<_qmf::ArgsHaBrokerSetPublicBrokers&>(args).i_url), l);
          break;
      }
      case _qmf::HaBroker::METHOD_SETEXPECTEDBACKUPS: {
          setExpectedBackups(dynamic_cast<_qmf::ArgsHaBrokerSetExpectedBackups&>(args).i_expectedBackups, l);
          break;
      }
      case _qmf::HaBroker::METHOD_REPLICATE: {
          _qmf::ArgsHaBrokerReplicate& bq_args =
              dynamic_cast<_qmf::ArgsHaBrokerReplicate&>(args);
          QPID_LOG(debug, logPrefix << "replicate individual queue "
                   << bq_args.i_queue << " from " << bq_args.i_broker);

          boost::shared_ptr<broker::Queue> queue = broker.getQueues().get(bq_args.i_queue);
          Url url(bq_args.i_broker);
          string protocol = url[0].protocol.empty() ? "tcp" : url[0].protocol;
          framing::Uuid uuid(true);
          std::pair<broker::Link::shared_ptr, bool> result = broker.getLinks().declare(
              broker::QPID_NAME_PREFIX + string("ha.link.") + uuid.str(),
              url[0].host, url[0].port, protocol,
              false,              // durable
              settings.mechanism, settings.username, settings.password);
          boost::shared_ptr<broker::Link> link = result.first;
          link->setUrl(url);
          // Create a queue replicator
          boost::shared_ptr<QueueReplicator> qr(
              new QueueReplicator(LogPrefix(*this, queue->getName()), queue, link));
          qr->activate();
          broker.getExchanges().registerExchange(qr);
          break;
      }

      default:
        return Manageable::STATUS_UNKNOWN_METHOD;
    }
    return Manageable::STATUS_OK;
}

void HaBroker::setClientUrl(const Url& url, const sys::Mutex::ScopedLock& l) {
    if (url.empty()) throw Exception("Invalid empty URL for HA client failover");
    clientUrl = url;
    updateClientUrl(l);
}

void HaBroker::updateClientUrl(const sys::Mutex::ScopedLock&) {
    Url url = clientUrl.empty() ? brokerUrl : clientUrl;
    if (url.empty()) throw Url::Invalid("HA client URL is empty");
    mgmtObject->set_publicBrokers(url.str());
    knownBrokers.clear();
    knownBrokers.push_back(url);
    QPID_LOG(debug, logPrefix << "Setting client URL to: " << url);
}

void HaBroker::setBrokerUrl(const Url& url, const sys::Mutex::ScopedLock& l) {
    if (url.empty()) throw Url::Invalid("HA broker URL is empty");
    brokerUrl = url;
    mgmtObject->set_brokers(brokerUrl.str());
    if (backup.get()) backup->setBrokerUrl(brokerUrl);
    // Updating broker URL also updates defaulted client URL:
    if (clientUrl.empty()) updateClientUrl(l);
}

void HaBroker::setExpectedBackups(size_t n, const sys::Mutex::ScopedLock&) {
    expectedBackups = n;
    mgmtObject->set_expectedBackups(n);
}

std::vector<Url> HaBroker::getKnownBrokers() const {
    return knownBrokers;
}

void HaBroker::shutdown() {
    QPID_LOG(critical, logPrefix << "Critical error, shutting down.");
    broker.shutdown();
}

BrokerStatus HaBroker::getStatus() const {
    sys::Mutex::ScopedLock l(lock);
    return status;
}

void HaBroker::setStatus(BrokerStatus newStatus) {
    sys::Mutex::ScopedLock l(lock);
    setStatus(newStatus, l);
}

namespace {
bool checkTransition(BrokerStatus from, BrokerStatus to) {
    // Legal state transitions. Initial state is JOINING, ACTIVE is terminal.
    static const BrokerStatus TRANSITIONS[][2] = {
        { CATCHUP, RECOVERING }, // FIXME aconway 2012-04-27: illegal transition, allow while fixing behavior
        { JOINING, CATCHUP },   // Connected to primary
        { JOINING, ACTIVE },    // Chosen as initial primary.
        { CATCHUP, READY },     // Caught up all queues, ready to take over.
        { READY, RECOVERING },   // Chosen as new primary
        { RECOVERING, ACTIVE }
    };
    static const size_t N = sizeof(TRANSITIONS)/sizeof(TRANSITIONS[0]);
    for (size_t i = 0; i < N; ++i) {
        if (TRANSITIONS[i][0] == from && TRANSITIONS[i][1] == to)
            return true;
    }
    return false;
}
} // namespace

void HaBroker::setStatus(BrokerStatus newStatus, sys::Mutex::ScopedLock& l) {
    QPID_LOG(notice, logPrefix << "Status change: "
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

void HaBroker::statusChanged(sys::Mutex::ScopedLock&) {
    mgmtObject->set_status(printable(status).str());
    // Set the backup-related properties for newly created links.
    framing::FieldTable ft = broker.getLinkClientProperties();
    if (isBackup(status))
        ft.setInt(ConnectionExcluder::BACKUP_TAG, 1);
    else
        ft.erase(ConnectionExcluder::BACKUP_TAG);
    broker.setLinkClientProperties(ft);
}

void HaBroker::activatedBackup(const std::string& queue) {
    sys::Mutex::ScopedLock l(lock);
    activeBackups.insert(queue);
}

void HaBroker::deactivatedBackup(const std::string& queue) {
    sys::Mutex::ScopedLock l(lock);
    activeBackups.erase(queue);
}

std::set<std::string> HaBroker::getActiveBackups() const {
    sys::Mutex::ScopedLock l(lock);
    return activeBackups;
}

}} // namespace qpid::ha
