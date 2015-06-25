#ifndef _BrokerSystem_
#define _BrokerSystem_

//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
// 
//   http://www.apache.org/licenses/LICENSE-2.0
// 
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

#include "qpid/management/Manageable.h"
#include "qpid/framing/Uuid.h"
#include "qmf/org/apache/qpid/broker/System.h"
#include <boost/shared_ptr.hpp>
#include <string>

namespace qpid { 
namespace broker {

class Broker;

class System : public management::Manageable
{
  private:

    qmf::org::apache::qpid::broker::System::shared_ptr mgmtObject;
    framing::Uuid systemId;
    std::string osName, nodeName, release, version, machine;

  public:

    typedef boost::shared_ptr<System> shared_ptr;

    System (std::string _dataDir, Broker* broker = 0);

    ~System ();

    management::ManagementObject::shared_ptr GetManagementObject(void) const
    { return mgmtObject; }


    /** Persistent UUID assigned by the management system to this broker. */
    framing::Uuid getSystemId() const  { return systemId; }
    /** Returns the OS name; e.g., GNU/Linux or Windows */
    std::string getOsName() const { return osName; }
    /** Returns the node name. Usually the same as the host name. */
    std::string getNodeName() const { return nodeName; }
    /** Returns the OS release identifier. */
    std::string getRelease() const { return release; }
    /** Returns the OS release version (kernel, build, sp, etc.) */
    std::string getVersion() const { return version; }
    /** Returns the hardware type. */
    std::string getMachine() const { return machine; }
};

}}

#endif  /*!_BrokerSystem_*/
