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
/*MGEN:IF(Class.ExistOptionals)*/
    uint8_t presenceMask[/*MGEN:Class.PresenceMaskBytes*/];
/*MGEN:Class.PresenceMaskConstants*/
/*MGEN:ENDIF*/

    // Properties
/*MGEN:Class.ConfigDeclarations*/
    // Statistics
/*MGEN:Class.InstDeclarations*/
/*MGEN:IF(Class.ExistPerThreadStats)*/
    // Per-Thread Statistics
    struct PerThreadStats {
/*MGEN:Class.PerThreadDeclarations*/
    };

    struct PerThreadStats** perThreadStatsArray;

    inline struct PerThreadStats* getThreadStats() {
        int index = getThreadIndex();
        struct PerThreadStats* threadStats = perThreadStatsArray[index];
        if (threadStats == 0) {
            threadStats = new(PerThreadStats);
            perThreadStatsArray[index] = threadStats;
/*MGEN:Class.InitializePerThreadElements*/
        }
        return threadStats;
    }

    void aggregatePerThreadStats(struct PerThreadStats*);
/*MGEN:ENDIF*/
    // Private Methods
    static void writeSchema (qpid::framing::Buffer& buf);
    void writeProperties    (qpid::framing::Buffer& buf);
    void writeStatistics    (qpid::framing::Buffer& buf,
                             bool skipHeaders = false);
    void doMethod           (std::string            methodName,
                             qpid::framing::Buffer& inBuf,
                             qpid::framing::Buffer& outBuf);
    writeSchemaCall_t getWriteSchemaCall(void) { return writeSchema; }
/*MGEN:IF(Class.NoStatistics)*/
    // Stub for getInstChanged.  There are no statistics in this class.
    bool getInstChanged (void) { return false; }
/*MGEN:ENDIF*/
  public:

    /*MGEN:Class.NameCap*/ (ManagementAgent* agent,
                            Manageable* coreObject/*MGEN:Class.ParentArg*//*MGEN:Class.ConstructorArgs*/);
    ~/*MGEN:Class.NameCap*/ (void);

    /*MGEN:Class.SetGeneralReferenceDeclaration*/

    static void  registerClass  (ManagementAgent* agent);
    std::string& getPackageName (void) { return packageName; }
    std::string& getClassName   (void) { return className; }
    uint8_t*     getMd5Sum      (void) { return md5Sum; }

    // Method IDs
/*MGEN:Class.MethodIdDeclarations*/
    // Accessor Methods
/*MGEN:Class.AccessorMethods*/
    // Event Methods
/*MGEN:Class.EventMethodDecls*/
};

}}

#endif  /*!_MANAGEMENT_/*MGEN:Class.NameUpper*/_*/
