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

namespace qpid { 
namespace management {

class /*MGEN:Class.NameCap*/ : public ManagementObject
{
  private:

    static bool schemaNeeded;

    // Configuration Elements
/*MGEN:Class.ConfigDeclarations*/
    // Instrumentation Elements
/*MGEN:Class.InstDeclarations*/
    // Private Methods
    std::string getObjectName (void) { return "/*MGEN:Class.NameLower*/"; }
    void writeSchema          (qpid::framing::Buffer& buf);
    void writeConfig          (qpid::framing::Buffer& buf);
    void writeInstrumentation (qpid::framing::Buffer& buf);
    bool getSchemaNeeded      (void) { return schemaNeeded; }
    void setSchemaNeeded      (void) { schemaNeeded = true; }
    void doMethod             (std::string            methodName,
                               qpid::framing::Buffer& inBuf,
                               qpid::framing::Buffer& outBuf);

/*MGEN:Class.InstChangedStub*/
  public:

    typedef boost::shared_ptr</*MGEN:Class.NameCap*/> shared_ptr;

    /*MGEN:Class.NameCap*/ (Manageable* coreObject, Manageable* parentObject,
        /*MGEN:Class.ConstructorArgs*/);
    ~/*MGEN:Class.NameCap*/ (void);

    // Method IDs
/*MGEN:Class.MethodIdDeclarations*/
    // Accessor Methods
/*MGEN:Class.AccessorMethods*/
};

}}
            

#endif  /*!_MANAGEMENT_/*MGEN:Class.NameUpper*/_*/
