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
/*MGEN:IF(Root.InBroker)*/
#include "qmf/BrokerImportExport.h"
/*MGEN:ENDIF*/

namespace qmf {
/*MGEN:Event.OpenNamespaces*/

/*MGEN:Root.ExternClass*/ class Event/*MGEN:Event.NameCap*/ : public ::qpid::management::ManagementEvent
{
  private:
    static void writeSchema (std::string& schema);
    static uint8_t md5Sum[MD5_LEN];
    /*MGEN:Root.ExternMethod*/ static std::string packageName;
    /*MGEN:Root.ExternMethod*/ static std::string eventName;

/*MGEN:Event.ArgDeclarations*/

  public:
    writeSchemaCall_t getWriteSchemaCall(void) { return writeSchema; }

    /*MGEN:Root.ExternMethod*/ Event/*MGEN:Event.NameCap*/(/*MGEN:Event.ConstructorArgs*/);
    /*MGEN:Root.ExternMethod*/ ~Event/*MGEN:Event.NameCap*/() {};

    static void registerSelf(::qpid::management::ManagementAgent* agent);
    std::string& getPackageName() const { return packageName; }
    std::string& getEventName() const { return eventName; }
    uint8_t* getMd5Sum() const { return md5Sum; }
    uint8_t getSeverity() const { return /*MGEN:Event.Severity*/; }
    /*MGEN:Root.ExternMethod*/ void encode(std::string& buffer) const;
    /*MGEN:Root.ExternMethod*/ void mapEncode(::qpid::types::Variant::Map& map) const;

    /*MGEN:Root.ExternMethod*/ static bool match(const std::string& evt, const std::string& pkg);
    static std::pair<std::string,std::string> getFullName() {
        return std::make_pair(packageName, eventName);
    }
};

}/*MGEN:Event.CloseNamespaces*/

#endif  /*!_MANAGEMENT_/*MGEN:Event.NameUpper*/_*/
