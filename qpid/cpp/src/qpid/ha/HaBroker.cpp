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
Url url(const std::string& s, const std::string& id) {
    try {
        // Allow the URL to be empty, used in tests that set the URL
        // after starting broker
        return s.empty() ? Url() : Url(s);
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
      backup(new Backup(b, s)),
      mgmtObject(0)
{
    // Register a factory for replicating subscriptions.
    broker.getConsumerFactories().add(
        boost::shared_ptr<ReplicatingSubscription::Factory>(
            new ReplicatingSubscription::Factory()));

    ManagementAgent* ma = broker.getManagementAgent();
    if (!ma)
        throw Exception("Cannot start HA: management is disabled");
    if (ma) {
        _qmf::Package  packageInit(ma);
        mgmtObject = new _qmf::HaBroker(ma, this);
        mgmtObject->set_status(BACKUP);
        ma->addObject(mgmtObject);
    }
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
          string url = dynamic_cast<_qmf::ArgsHaBrokerSetClientAddresses&>(args)
              .i_clientAddresses;
          mgmtObject->set_clientAddresses(url);
          // FIXME aconway 2012-01-30: upate status for new URL
          break;
      }
      case _qmf::HaBroker::METHOD_SETBROKERADDRESSES: {
          string url = dynamic_cast<_qmf::ArgsHaBrokerSetBrokerAddresses&>(args)
              .i_brokerAddresses;
          mgmtObject->set_brokerAddresses(url);
          if (backup.get()) backup->setUrl(Url(url));
          break;
      }
      default:
        return Manageable::STATUS_UNKNOWN_METHOD;
    }
    return Manageable::STATUS_OK;
}

}} // namespace qpid::ha
