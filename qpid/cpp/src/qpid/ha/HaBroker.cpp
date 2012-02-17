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
#include "qpid/log/Statement.h"

namespace qpid {
namespace ha {

namespace _qmf = ::qmf::org::apache::qpid::ha;
using namespace management;
using namespace std;

namespace {
Url url(const std::string& s, const std::string& id) {
    try {
        return Url(s);
    } catch (const std::exception& e) {
        throw Exception(Msg() << "Invalid URL for " << id << ": '" << s << "'");
    }
}

const std::string PRIMARY="primary";
const std::string BACKUP="backup";

} // namespace

HaBroker::HaBroker(broker::Broker& b, const Settings& s)
    : broker(b),
      settings(s),
      clientUrl(url(s.clientUrl, "ha-client-url")),
      brokerUrl(url(s.brokerUrl, "ha-broker-url")),
      mgmtObject(0)
{
    // FIXME aconway 2011-11-22: temporary hack to identify primary.
    bool primary = (settings.brokerUrl == PRIMARY);
    QPID_LOG(notice, "HA: " << (primary ? "Primary" : "Backup")
             << " initialized: client-url=" << clientUrl
             << " broker-url=" << brokerUrl);
    if (!primary) backup.reset(new Backup(broker, s));
    // Register a factory for replicating subscriptions.
    broker.getConsumerFactories().add(
        boost::shared_ptr<ReplicatingSubscription::Factory>(
            new ReplicatingSubscription::Factory()));
    // Register a connection excluder
    broker.getConnectionObservers().add(
        boost::shared_ptr<broker::ConnectionObserver>(
            new ConnectionExcluder(boost::bind(&HaBroker::isPrimary, this))));

    ManagementAgent* ma = broker.getManagementAgent();
    if (ma) {
        _qmf::Package  packageInit(ma);
        mgmtObject = new _qmf::HaBroker(ma, this);
        // FIXME aconway 2011-11-11: Placeholder - initialize cluster role.
        mgmtObject->set_status(isPrimary() ? PRIMARY : BACKUP);
        ma->addObject(mgmtObject);
    }
}

HaBroker::~HaBroker() {}

Manageable::status_t HaBroker::ManagementMethod (uint32_t methodId, Args& args, string&) {
    switch (methodId) {
      case _qmf::HaBroker::METHOD_SETSTATUS: {
          std::string status = dynamic_cast<_qmf::ArgsHaBrokerSetStatus&>(args).i_status;
          if (status == PRIMARY) {
              if (!isPrimary()) {
                  backup.reset();
                  QPID_LOG(notice, "HA Primary: promoted from backup");
              }
          } else if (status == BACKUP) {
              if (isPrimary()) {
                  backup.reset(new Backup(broker, settings));
                  QPID_LOG(notice, "HA Backup: demoted from primary.");
              }
          } else {
              QPID_LOG(error, "Attempt to set invalid HA status: " << status);
          }
          mgmtObject->set_status(status);
          break;
      }
      default:
        return Manageable::STATUS_UNKNOWN_METHOD;
    }
    return Manageable::STATUS_OK;
}

bool HaBroker::isPrimary() const {
    return !backup.get();       // TODO aconway 2012-01-18: temporary test.
}

}} // namespace qpid::ha
