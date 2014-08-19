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
#include "IdSetter.h"
#include "Primary.h"
#include "QueueReplicator.h"
#include "ReplicatingSubscription.h"
#include "Settings.h"
#include "StandAlone.h"
#include "QueueSnapshot.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/assert.h"
#include "qpid/Exception.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/BrokerObserver.h"
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
using boost::dynamic_pointer_cast;

// In a HaBroker we always need to add QueueSnapshot and IdSetter to each queue
// because we don't know in advance which queues might be used for stand-alone
// replication.
//
// TODO aconway 2013-12-13: Can we restrict this to queues identified as replicated?
//
class HaBroker::BrokerObserver : public broker::BrokerObserver {
  public:
    BrokerObserver(const LogPrefix& lp) : logPrefix(lp) {}

    void queueCreate(const boost::shared_ptr<broker::Queue>& q) {
        q->getObservers().add(boost::shared_ptr<QueueSnapshot>(new QueueSnapshot));
        q->getMessageInterceptors().add(
            boost::shared_ptr<IdSetter>(new IdSetter(logPrefix, q->getName())));
    }

  private:
    const LogPrefix& logPrefix;
};

// Called in Plugin::earlyInitialize
HaBroker::HaBroker(broker::Broker& b, const Settings& s)
    : systemId(b.getSystem()->getSystemId().data()),
      settings(s),
      userId(s.username+"@"+b.getRealm()),
      broker(b),
      observer(new ConnectionObserver(*this, systemId)),
      role(new StandAlone),
      membership(BrokerInfo(systemId, STANDALONE), *this), // Sets logPrefix
      failoverExchange(new FailoverExchange(*b.GetVhostObject(), b))
{
    // If we are joining a cluster we must start excluding clients now,
    // otherwise there's a window for a client to connect before we get to
    // initialize()
    if (settings.cluster) {
        shared_ptr<broker::ConnectionObserver> excluder(new BackupConnectionExcluder(logPrefix));
        observer->setObserver(excluder);
        broker.getConnectionObservers().add(observer);
        broker.getExchanges().registerExchange(failoverExchange);
    }
    broker.getBrokerObservers().add(boost::shared_ptr<BrokerObserver>(new BrokerObserver(logPrefix)));
}

namespace {
const std::string NONE("none");
bool isNone(const std::string& x) { return x.empty() || x == NONE; }
}

// Called in Plugin::initialize
void HaBroker::initialize() {
    if (settings.cluster) {
        QPID_LOG(info, logPrefix << "Starting HA broker");
        membership.setStatus(JOINING);
    }

    // Set up the management object.
    ManagementAgent* ma = broker.getManagementAgent();
    if (settings.cluster && !ma)
        throw Exception("Cannot start HA: management is disabled");
    _qmf::Package  packageInit(ma);
    mgmtObject = _qmf::HaBroker::shared_ptr(new _qmf::HaBroker(ma, this, "ha-broker"));
    mgmtObject->set_replicateDefault(settings.replicateDefault.str());
    mgmtObject->set_systemId(systemId);
    ma->addObject(mgmtObject);
    membership.setMgmtObject(mgmtObject);

    // Register a factory for replicating subscriptions.
    broker.getConsumerFactories().add(
        shared_ptr<ReplicatingSubscription::Factory>(
            new ReplicatingSubscription::Factory(*this)));

    // If we are in a cluster, start as backup in joining state.
    if (settings.cluster) {
        assert(membership.getStatus() == JOINING);
        role.reset(new Backup(*this, settings));
        broker.getKnownBrokers = boost::bind(&HaBroker::getKnownBrokers, this);
        if (!isNone(settings.publicUrl)) setPublicUrl(Url(settings.publicUrl));
        if (!isNone(settings.brokerUrl)) setBrokerUrl(Url(settings.brokerUrl));
    }
}

HaBroker::~HaBroker() {
    broker.getConnectionObservers().remove(observer);
}

Manageable::status_t HaBroker::ManagementMethod (uint32_t methodId, Args& args, string&) {
    switch (methodId) {
      case _qmf::HaBroker::METHOD_PROMOTE: {
        Role* r = role->promote();
        if (r) role.reset(r);
        break;
      }
      case _qmf::HaBroker::METHOD_SETBROKERSURL: {
          setBrokerUrl(Url(dynamic_cast<_qmf::ArgsHaBrokerSetBrokersUrl&>(args).i_url));
          break;
      }
      case _qmf::HaBroker::METHOD_SETPUBLICURL: {
          setPublicUrl(Url(dynamic_cast<_qmf::ArgsHaBrokerSetPublicUrl&>(args).i_url));
          break;
      }
      case _qmf::HaBroker::METHOD_REPLICATE: {
          _qmf::ArgsHaBrokerReplicate& bq_args =
              dynamic_cast<_qmf::ArgsHaBrokerReplicate&>(args);
          QPID_LOG(debug, logPrefix << "Replicate individual queue "
                   << bq_args.i_queue << " from " << bq_args.i_broker);

          shared_ptr<broker::Queue> queue = broker.getQueues().get(bq_args.i_queue);
          Url url(bq_args.i_broker);
          string protocol = url[0].protocol.empty() ? "tcp" : url[0].protocol;
          Uuid uuid(true);
          std::pair<broker::Link::shared_ptr, bool> result = broker.getLinks().declare(
              broker::QPID_NAME_PREFIX + string("ha.link.") + uuid.str(),
              url[0].host, url[0].port, protocol,
              false,              // durable
              settings.mechanism, settings.username, settings.password,
              false);           // no amq.failover - don't want to use client URL.
          shared_ptr<broker::Link> link = result.first;
          link->setUrl(url);
          // Create a queue replicator
          shared_ptr<QueueReplicator> qr(QueueReplicator::create(*this, queue, link));
          broker.getExchanges().registerExchange(qr);
          break;
      }

      default:
        return Manageable::STATUS_UNKNOWN_METHOD;
    }
    return Manageable::STATUS_OK;
}

void HaBroker::setPublicUrl(const Url& url) {
    Mutex::ScopedLock l(lock);
    publicUrl = url;
    mgmtObject->set_publicUrl(url.str());
    knownBrokers.clear();
    knownBrokers.push_back(url);
    vector<Url> urls(1, url);
    failoverExchange->updateUrls(urls);
    QPID_LOG(debug, logPrefix << "Public URL set to: " << url);
}

void HaBroker::setBrokerUrl(const Url& url) {
    {
        Mutex::ScopedLock l(lock);
        brokerUrl = url;
        mgmtObject->set_brokersUrl(brokerUrl.str());
        QPID_LOG(info, logPrefix << "Brokers URL set to: " << url);
    }
    role->setBrokerUrl(url); // Oustside lock
}

std::vector<Url> HaBroker::getKnownBrokers() const {
    Mutex::ScopedLock l(lock);
    return knownBrokers;
}

void HaBroker::shutdown(const std::string& message) {
    QPID_LOG(critical, logPrefix << "Shutting down: " << message);
    broker.shutdown();
    throw Exception(message);
}

BrokerStatus HaBroker::getStatus() const {
    return membership.getStatus();
}

void HaBroker::setAddress(const Address& a) {
    QPID_LOG(info, logPrefix << "Set self address to: " << a);
    membership.setSelfAddress(a);
}

boost::shared_ptr<QueueReplicator> HaBroker::findQueueReplicator(const std::string& queueName) {
    return boost::dynamic_pointer_cast<QueueReplicator>(
        broker.getExchanges().find(QueueReplicator::replicatorName(queueName)));
}

}} // namespace qpid::ha
