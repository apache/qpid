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

#include "Manageable.h"
#include "qpid/sys/Time.h"
#include "qpid/sys/Mutex.h"
#include <qpid/framing/Buffer.h>
#include <boost/shared_ptr.hpp>
#include <map>

namespace qpid { 
namespace management {

class Manageable;

class ManagementObject
{
  protected:
    
    uint64_t    createTime;
    uint64_t    destroyTime;
    uint64_t    objectId;
    bool        configChanged;
    bool        instChanged;
    bool        deleted;
    Manageable* coreObject;
    sys::RWlock accessLock;

    static const uint8_t TYPE_U8        = 1;
    static const uint8_t TYPE_U16       = 2;
    static const uint8_t TYPE_U32       = 3;
    static const uint8_t TYPE_U64       = 4;
    static const uint8_t TYPE_SSTR      = 6;
    static const uint8_t TYPE_LSTR      = 7;
    static const uint8_t TYPE_ABSTIME   = 8;
    static const uint8_t TYPE_DELTATIME = 9;
    static const uint8_t TYPE_REF       = 10;
    static const uint8_t TYPE_BOOL      = 11;
    static const uint8_t TYPE_FLOAT     = 12;
    static const uint8_t TYPE_DOUBLE    = 13;
    static const uint8_t TYPE_UUID      = 14;

    static const uint8_t ACCESS_RC = 1;
    static const uint8_t ACCESS_RW = 2;
    static const uint8_t ACCESS_RO = 3;

    static const uint8_t DIR_I     = 1;
    static const uint8_t DIR_O     = 2;
    static const uint8_t DIR_IO    = 3;

    static const uint8_t FLAG_CONFIG = 0x01;
    static const uint8_t FLAG_INDEX  = 0x02;
    static const uint8_t FLAG_END    = 0x80;

    void writeTimestamps (qpid::framing::Buffer& buf);

  public:
    typedef boost::shared_ptr<ManagementObject> shared_ptr;
    typedef void (*writeSchemaCall_t) (qpid::framing::Buffer&);

    ManagementObject (Manageable* _core) :
        destroyTime(0), objectId (0), configChanged(true),
        instChanged(true), deleted(false), coreObject(_core)
    { createTime = uint64_t (qpid::sys::Duration (qpid::sys::now ())); }
    virtual ~ManagementObject () {}

    virtual writeSchemaCall_t getWriteSchemaCall (void) = 0;
    virtual void writeConfig          (qpid::framing::Buffer& buf) = 0;
    virtual void writeInstrumentation (qpid::framing::Buffer& buf,
                                       bool skipHeaders = false) = 0;
    virtual void doMethod             (std::string            methodName,
                                       qpid::framing::Buffer& inBuf,
                                       qpid::framing::Buffer& outBuf) = 0;

    virtual std::string  getClassName   (void) = 0;
    virtual std::string  getPackageName (void) = 0;
    virtual uint8_t*     getMd5Sum      (void) = 0;

    void         setObjectId      (uint64_t oid) { objectId = oid; }
    uint64_t     getObjectId      (void) { return objectId; }
    inline  bool getConfigChanged (void) { return configChanged; }
    virtual bool getInstChanged   (void) { return instChanged; }
    inline  void setAllChanged    (void)
    {
        configChanged = true;
        instChanged   = true;
    }

    inline void resourceDestroy  (void) {
        destroyTime = uint64_t (qpid::sys::Duration (qpid::sys::now ()));
        deleted     = true;
    }
    bool isDeleted (void) { return deleted; }

};

typedef std::map<uint64_t,ManagementObject::shared_ptr> ManagementObjectMap;

}}
            


#endif  /*!_ManagementObject_*/
