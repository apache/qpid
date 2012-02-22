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
#include "qpid/management/ManagementAgent.h"
#include "qmf/org/apache/qpid/ha/Package.h"
#include "qmf/org/apache/qpid/ha/ArgsHaBrokerSetClientAddresses.h"
#include "qmf/org/apache/qpid/ha/ArgsHaBrokerSetBrokerAddresses.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace ha {

namespace _qmf = ::qmf::org::apache::qpid::ha;
using namespace management;
using namespace std;

namespace {

const std::string PRIMARY="primary";
const std::string BACKUP="backup";

} // namespace


HaBroker::HaBroker(broker::Broker& b, const Settings& s)
    : broker(b),
      settings(s),
      backup(new Backup(b, s)),
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
    if (ma) {
        _qmf::Package  packageInit(ma);
        mgmtObject = new _qmf::HaBroker(ma, this, "ha-broker");
        mgmtObject->set_status(BACKUP);
        ma->addObject(mgmtObject);
    }
    sys::Mutex::ScopedLock l(lock);
    if (!settings.clientUrl.empty()) setClientUrl(Url(settings.clientUrl), l);
    if (!settings.brokerUrl.empty()) setBrokerUrl(Url(settings.brokerUrl), l);
}

HaBroker::~HaBroker() {}

Manageable::status_t HaBroker::ManagementMethod (uint32_t methodId, Args& args, string&) {
    sys::Mutex::ScopedLock l(lock);
    switch (methodId) {
      case _qmf::HaBroker::METHOD_PROMOTE: {
          if (backup.get()) {   // I am a backup
              // FIXME aconway 2012-01-26: create primary state before resetting backup
              // as that allows client connections.
              backup.reset();
              QPID_LOG(notice, "HA: Primary promoted from backup");
              mgmtObject->set_status(PRIMARY);
          }
          break;
      }
      case _qmf::HaBroker::METHOD_SETCLIENTADDRESSES: {
          setClientUrl(
              Url(dynamic_cast<_qmf::ArgsHaBrokerSetClientAddresses&>(args).
                  i_clientAddresses), l);
          break;
      }
      case _qmf::HaBroker::METHOD_SETBROKERADDRESSES: {
          setBrokerUrl(
              Url(dynamic_cast<_qmf::ArgsHaBrokerSetBrokerAddresses&>(args)
                  .i_brokerAddresses), l);
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
    mgmtObject->set_clientAddresses(url.str());
    knownBrokers.clear();
    knownBrokers.push_back(url);
    QPID_LOG(debug, "HA: Setting client URL to: " << url);
}

void HaBroker::setBrokerUrl(const Url& url, const sys::Mutex::ScopedLock& l) {
    if (url.empty()) throw Exception("Invalid empty URL for HA broker failover");
    QPID_LOG(debug, "HA: Setting broker URL to: " << url);
    brokerUrl = url;
    mgmtObject->set_brokerAddresses(brokerUrl.str());
    if (backup.get()) backup->setBrokerUrl(brokerUrl);
    // Updating broker URL also updates defaulted client URL:
    if (clientUrl.empty()) updateClientUrl(l);
}

std::vector<Url> HaBroker::getKnownBrokers() const {
    return knownBrokers;
}

}} // namespace qpid::ha
