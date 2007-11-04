#ifndef _ManagementObjectVhost_
#define _ManagementObjectVhost_

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

class ManagementObjectVhost : public ManagementObject
{
  public:

    typedef boost::shared_ptr<ManagementObjectVhost> shared_ptr;

    ManagementObjectVhost  (uint32_t sysRef, const Options& conf);
    ~ManagementObjectVhost (void);

  private:

    static bool schemaNeeded;

    uint32_t    sysRef;
    std::string name;

    uint16_t    getObjectType        (void) { return OBJECT_VHOST; }
    std::string getObjectName        (void) { return "vhost"; }
    void        writeSchema          (qpid::framing::Buffer& buf);
    void        writeConfig          (qpid::framing::Buffer& buf);
    void        writeInstrumentation (qpid::framing::Buffer& /*buf*/) {}
    bool        getSchemaNeeded      (void) { return schemaNeeded; }
    void        setSchemaNeeded      (void) { schemaNeeded = true; }

    inline bool getInstChanged       (void) { return false; }
};

}}


#endif  /*!_ManagementObjectVhost_*/
