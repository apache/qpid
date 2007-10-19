#ifndef _ManagementObject_
#define _ManagementObject_

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

#include "qpid/sys/Time.h"
#include <qpid/framing/Buffer.h>
#include <boost/shared_ptr.hpp>
#include <list>

namespace qpid { 
namespace broker {

using namespace qpid::framing;

const uint16_t OBJECT_BROKER    = 1;
const uint16_t OBJECT_SERVER    = 2;
const uint16_t OBJECT_QUEUE     = 3;
const uint16_t OBJECT_EXCHANGE  = 4;
const uint16_t OBJECT_BINDING   = 5;

class ManagementObject
{
  private:
  
    qpid::sys::AbsTime       createTime;
    qpid::sys::AbsTime       destroyTime;

  protected:
    
    bool  configChanged;
    bool  instChanged;
    
    static const uint8_t TYPE_UINT8  = 1;
    static const uint8_t TYPE_UINT16 = 2;
    static const uint8_t TYPE_UINT32 = 3;
    static const uint8_t TYPE_UINT64 = 4;
    static const uint8_t TYPE_BOOL   = 5;
    static const uint8_t TYPE_STRING = 6;
    
    void schemaItem (Buffer&     buf,
		     uint8_t     typeCode,
		     std::string name,
		     std::string description,
		     bool        isConfig = false);
    void schemaListEnd (Buffer & buf);

  public:
    typedef boost::shared_ptr<ManagementObject> shared_ptr;

    ManagementObject () : configChanged(true), instChanged(true) { createTime = qpid::sys::now (); }
    virtual ~ManagementObject () {}

    virtual uint16_t    getObjectType        (void)        = 0;
    virtual std::string getObjectName        (void)        = 0;
    virtual void        writeSchema          (Buffer& buf) = 0;
    virtual void        writeConfig          (Buffer& buf) = 0;
    virtual void        writeInstrumentation (Buffer& buf) = 0;
    virtual bool        getSchemaNeeded      (void)        = 0;
    
    inline bool getConfigChanged (void) { return configChanged; }
    inline bool getInstChanged   (void) { return instChanged; }
    inline void resourceDestroy  (void) { destroyTime = qpid::sys::now (); }

};

 typedef std::list<ManagementObject::shared_ptr> ManagementObjectList;

}}
            


#endif  /*!_ManagementObject_*/
