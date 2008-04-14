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

#include "System.h"
#include "qpid/management/ManagementAgent.h"
#include <sys/utsname.h>

using namespace qpid::broker;
using qpid::management::ManagementAgent;

System::System ()
{
    ManagementAgent::shared_ptr agent = ManagementAgent::getAgent ();

    if (agent.get () != 0)
    {
        mgmtObject = management::System::shared_ptr
            (new management::System (this, "host"));
        struct utsname _uname;
        if (uname (&_uname) == 0)
        {
            mgmtObject->set_osName   (std::string (_uname.sysname));
            mgmtObject->set_nodeName (std::string (_uname.nodename));
            mgmtObject->set_release  (std::string (_uname.release));
            mgmtObject->set_version  (std::string (_uname.version));
            mgmtObject->set_machine  (std::string (_uname.machine));
        }

        agent->addObject (mgmtObject, 3, 0);
    }
}

