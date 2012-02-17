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
#include "HaBroker.h"
#include "qpid/broker/Broker.h"
#include "qpid/management/ManagementAgent.h"
#include "qmf/org/apache/qpid/ha/Package.h"

namespace qpid {
namespace ha {

namespace _qmf = ::qmf::org::apache::qpid::ha;
using namespace management;
using namespace std;

HaBroker::HaBroker(broker::Broker& b) : broker(b), mgmtObject(0) {
    ManagementAgent* ma = broker.getManagementAgent();
    if (ma) {
        _qmf::Package  packageInit(ma);
        mgmtObject = new _qmf::HaBroker(ma, this);
        // FIXME aconway 2011-11-11: Placeholder - initialize cluster role.
        mgmtObject->set_status("solo");
        ma->addObject(mgmtObject);
    }
}

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
