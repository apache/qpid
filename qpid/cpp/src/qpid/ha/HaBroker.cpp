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
#include "HaBroker.h"
#include "Settings.h"
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
} // namespace

HaBroker::HaBroker(broker::Broker& b, const Settings& s)
    : broker(b), 
      clientUrl(url(s.clientUrl, "ha-client-url")),
      brokerUrl(url(s.brokerUrl, "ha-broker-url")),
      mgmtObject(0)
{
    ManagementAgent* ma = broker.getManagementAgent();
    if (ma) {
        _qmf::Package  packageInit(ma);
        mgmtObject = new _qmf::HaBroker(ma, this);
        // FIXME aconway 2011-11-11: Placeholder - initialize cluster role.
        mgmtObject->set_status("solo");
        ma->addObject(mgmtObject);
    }
    QPID_LOG(notice, "HA: broker initialized, client-url=" << clientUrl
             << ", broker-url=" << brokerUrl);
    backup.reset(new Backup(broker, s));
}

HaBroker::~HaBroker() {}

Manageable::status_t HaBroker::ManagementMethod (uint32_t methodId, Args& args, string&) {
    switch (methodId) {
      case _qmf::HaBroker::METHOD_SETSTATUS: {
          std::string status = dynamic_cast<_qmf::ArgsHaBrokerSetStatus&>(args).i_status;
          // FIXME aconway 2011-11-11: placeholder, validate & execute status change.
          mgmtObject->set_status(status);
          break;
      }
      default:
        return Manageable::STATUS_UNKNOWN_METHOD;
    }
    return Manageable::STATUS_OK;
}

}} // namespace qpid::ha
