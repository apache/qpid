/*MGEN:commentPrefix=//*/
#ifndef _MANAGEMENT_/*MGEN:Class.NameUpper*/_
#define _MANAGEMENT_/*MGEN:Class.NameUpper*/_

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

#include "qpid/management/ManagementObject.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/Uuid.h"

namespace qpid {
namespace management {

class /*MGEN:Class.NameCap*/ : public ManagementObject
{
  private:

    static std::string packageName;
    static std::string className;
    static uint8_t     md5Sum[16];

    // Configuration Elements
/*MGEN:Class.ConfigDeclarations*/
    // Instrumentation Elements
/*MGEN:Class.InstDeclarations*/
    // Private Methods
    static void writeSchema   (qpid::framing::Buffer& buf);
    void writeConfig          (qpid::framing::Buffer& buf);
    void writeInstrumentation (qpid::framing::Buffer& buf,
                               bool skipHeaders = false);
    void doMethod             (std::string            methodName,
                               qpid::framing::Buffer& inBuf,
                               qpid::framing::Buffer& outBuf);
    writeSchemaCall_t getWriteSchemaCall (void) { return writeSchema; }

/*MGEN:Class.InstChangedStub*/
  public:

    friend class Package/*MGEN:Class.NamePackageCap*/;
    typedef boost::shared_ptr</*MGEN:Class.NameCap*/> shared_ptr;

    /*MGEN:Class.NameCap*/ (Manageable* coreObject/*MGEN:Class.ParentArg*//*MGEN:Class.ConstructorArgs*/);
    ~/*MGEN:Class.NameCap*/ (void);

    std::string getPackageName (void) { return packageName; }
    std::string getClassName   (void) { return className; }
    uint8_t*    getMd5Sum      (void) { return md5Sum; }

    // Method IDs
/*MGEN:Class.MethodIdDeclarations*/
    // Accessor Methods
/*MGEN:Class.AccessorMethods*/
};

}}
            

#endif  /*!_MANAGEMENT_/*MGEN:Class.NameUpper*/_*/
