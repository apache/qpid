#ifndef _ManagementObjectBroker_
#define _ManagementObjectBroker_

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

#include "ManagementObject.h"
#include "qpid/Options.h"
#include "boost/shared_ptr.hpp"

namespace qpid { 
namespace broker {

class ManagementObjectBroker : public ManagementObject
{
  public:

    typedef boost::shared_ptr<ManagementObjectBroker> shared_ptr;

    ManagementObjectBroker  (const Options& conf);
    ~ManagementObjectBroker (void);

  private:

    static bool schemaNeeded;

    std::string objectName;

    std::string sysId;
    uint16_t    port;
    uint16_t    workerThreads;
    uint16_t    maxConns;
    uint16_t    connBacklog;
    uint32_t    stagingThreshold;
    std::string storeLib;
    bool        asyncStore;
    uint16_t    mgmtPubInterval;
    uint32_t    initialDiskPageSize;
    uint32_t    initialPagesPerQueue;
    std::string clusterName;
    std::string version;

    uint16_t    getObjectType        (void) { return OBJECT_BROKER; }
    std::string getObjectName        (void) { return objectName; }
    void        writeSchema          (Buffer& buf);
    void        writeConfig          (Buffer& buf);
    void        writeInstrumentation (Buffer& /*buf*/) {}
    bool        getSchemaNeeded      (void) { return schemaNeeded; }
    void        setSchemaNeeded      (void) { schemaNeeded = true; }

    inline bool getInstChanged       (void) { return false; }
};

}}


#endif  /*!_ManagementObjectBroker_*/
