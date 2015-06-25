/*MGEN:commentPrefix=//*/
#ifndef _MANAGEMENT_PACKAGE_/*MGEN:Schema.PackageNameUpper*/_
#define _MANAGEMENT_PACKAGE_/*MGEN:Schema.PackageNameUpper*/_

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

/*MGEN:Root.Disclaimer*/

#include "qpid//*MGEN:Class.AgentHeaderLocation*//ManagementAgent.h"
/*MGEN:IF(Root.InBroker)*/
#include "qmf/BrokerImportExport.h"
/*MGEN:ENDIF*/

namespace qmf {
/*MGEN:Class.OpenNamespaces*/

class Package
{
  public:
    /*MGEN:Root.ExternMethod*/ Package (::qpid::management::ManagementAgent* agent);
    /*MGEN:Root.ExternMethod*/ ~Package () {}
};

}/*MGEN:Class.CloseNamespaces*/
            

#endif  /*!_MANAGEMENT_PACKAGE_/*MGEN:Schema.PackageNameUpper*/_*/
