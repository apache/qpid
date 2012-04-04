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
#include "Settings.h"
#include "ReplicatingSubscription.h"
#include "qpid/Exception.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Link.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/SignalHandler.h"
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

namespace {

const std::string STANDALONE="standalone";
const std::string CATCH_UP="catch-up";
const std::string BACKUP="backup";
const std::string PRIMARY="primary";

} // namespace


HaBroker::HaBroker(broker::Broker& b, const Settings& s)
    : broker(b),
      settings(s),
      mgmtObject(0)
{
    // Register a factory for replicating subscriptions.
    broker.getConsumerFactories().add(
        boost::shared_ptr<ReplicatingSubscription::Factory>(
            new ReplicatingSubscription::Factory()));

    broker.getKnownBrokers = boost::bind(&HaBroker::getKnownBrokers, this);

    ManagementAgent* ma = broker.getManagementAgent();
    if (!ma)
        throw Exception("Cannot start HA: management is disabled");
    _qmf::Package  packageInit(ma);
    mgmtObject = new _qmf::HaBroker(ma, this, "ha-broker");
    mgmtObject->set_status(BACKUP);
    mgmtObject->set_replicateDefault(str(settings.replicateDefault));
    ma->addObject(mgmtObject);

    // NOTE: lock is not needed in a constructor but we created it just to pass
    // to the set functions.
    sys::Mutex::ScopedLock l(lock);
    if (!settings.clientUrl.empty()) setClientUrl(Url(settings.clientUrl), l);
    if (!settings.brokerUrl.empty()) setBrokerUrl(Url(settings.brokerUrl), l);

    // If we are in a cluster, we start in backup mode.
    if (settings.cluster) backup.reset(new Backup(*this, s));
}

HaBroker::~HaBroker() {}

Manageable::status_t HaBroker::ManagementMethod (uint32_t methodId, Args& args, string&) {
    sys::Mutex::ScopedLock l(lock);
    switch (methodId) {
      case _qmf::HaBroker::METHOD_PROMOTE: {
          if (backup.get()) {   // I am a backup
              // NOTE: resetting backup allows client connections, so any
              // primary state should be set up here before backup.reset()
              backup.reset();
              QPID_LOG(notice, "HA: Primary promoted from backup");
              mgmtObject->set_status(PRIMARY);
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
          QPID_LOG(debug, "HA replicating individual queue "<< bq_args.i_queue << " from " << bq_args.i_broker);

          boost::shared_ptr<broker::Queue> queue = broker.getQueues().get(bq_args.i_queue);
          Url url(bq_args.i_broker);
          string protocol = url[0].protocol.empty() ? "tcp" : url[0].protocol;
          std::pair<broker::Link::shared_ptr, bool> result = broker.getLinks().declare(
              url[0].host, url[0].port, protocol,
              false,              // durable
              settings.mechanism, settings.username, settings.password);
          boost::shared_ptr<broker::Link> link = result.first;
          link->setUrl(url);
          // Create a queue replicator
          boost::shared_ptr<QueueReplicator> qr(new QueueReplicator(queue, link));
          broker.getExchanges().registerExchange(qr);
          qr->activate();
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
    assert(!url.empty());
    mgmtObject->set_publicBrokers(url.str());
    knownBrokers.clear();
    knownBrokers.push_back(url);
    QPID_LOG(debug, "HA: Setting client URL to: " << url);
}

void HaBroker::setBrokerUrl(const Url& url, const sys::Mutex::ScopedLock& l) {
    if (url.empty()) throw Exception("Invalid empty URL for HA broker failover");
    QPID_LOG(debug, "HA: Setting broker URL to: " << url);
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

void HaBroker::shutdown(const std::string& message) {
    QPID_LOG(critical, "Shutting down: " << message);
    broker.shutdown();
}

}} // namespace qpid::ha
