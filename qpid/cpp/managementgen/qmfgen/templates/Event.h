/*MGEN:commentPrefix=//*/
#ifndef _MANAGEMENT_/*MGEN:Event.NameUpper*/_
#define _MANAGEMENT_/*MGEN:Event.NameUpper*/_

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

#include "qpid/management/ManagementEvent.h"
#include "qmf/BrokerImportExport.h"

namespace qmf {
/*MGEN:Event.OpenNamespaces*/

QPID_BROKER_CLASS_EXTERN class Event/*MGEN:Event.NameCap*/ : public ::qpid::management::ManagementEvent
{
  private:
    static void writeSchema (std::string& schema);
    static uint8_t md5Sum[MD5_LEN];
    static std::string packageName;
    static std::string eventName;

/*MGEN:Event.ArgDeclarations*/

  public:
    writeSchemaCall_t getWriteSchemaCall(void) { return writeSchema; }

    QPID_BROKER_EXTERN Event/*MGEN:Event.NameCap*/(/*MGEN:Event.ConstructorArgs*/);
    QPID_BROKER_EXTERN ~Event/*MGEN:Event.NameCap*/() {};

    static void registerSelf(::qpid::management::ManagementAgent* agent);
    std::string& getPackageName() const { return packageName; }
    std::string& getEventName() const { return eventName; }
    uint8_t* getMd5Sum() const { return md5Sum; }
    uint8_t getSeverity() const { return /*MGEN:Event.Severity*/; }
    QPID_BROKER_EXTERN void encode(std::string& buffer) const;
    QPID_BROKER_EXTERN void mapEncode(::qpid::types::Variant::Map& map) const;

    QPID_BROKER_EXTERN static bool match(const std::string& evt, const std::string& pkg);
    static std::pair<std::string,std::string> getFullName() {
        return std::make_pair(packageName, eventName);
    }
};

}/*MGEN:Event.CloseNamespaces*/

#endif  /*!_MANAGEMENT_/*MGEN:Event.NameUpper*/_*/
