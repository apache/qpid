/*MGEN:commentPrefix=//*/
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

#include "qpid/management/Manageable.h"
#include "qpid/management/Buffer.h"
#include "qpid/types/Variant.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid//*MGEN:Class.AgentHeaderLocation*//ManagementAgent.h"
#include "/*MGEN:Class.NameCap*/.h"
/*MGEN:Class.MethodArgIncludes*/
/*MGEN:IF(Root.GenLogs)*/
#include "qpid/log/Statement.h"
/*MGEN:ENDIF*/
#include <iostream>
#include <sstream>
#include <string.h>

using namespace qmf::/*MGEN:Class.Namespace*/;
using           qpid::management::ManagementAgent;
using           qpid::management::Manageable;
using           qpid::management::ManagementObject;
using           qpid::management::Args;
using           qpid::management::Mutex;
using           std::string;

string  /*MGEN:Class.NameCap*/::packageName  = string ("/*MGEN:Class.NamePackageLower*/");
string  /*MGEN:Class.NameCap*/::className    = string ("/*MGEN:Class.NameLower*/");
uint8_t /*MGEN:Class.NameCap*/::md5Sum[MD5_LEN]   =
    {/*MGEN:Class.SchemaMD5*/};

/*MGEN:Class.NameCap*/::/*MGEN:Class.NameCap*/ (ManagementAgent*, Manageable* _core/*MGEN:Class.ParentArg*//*MGEN:Class.ConstructorArgs*/) :
    ManagementObject(_core)/*MGEN:Class.ConstructorInits*/
{
    /*MGEN:Class.ParentRefAssignment*/
/*MGEN:Class.InitializeElements*/
/*MGEN:IF(Class.ExistOptionals)*/
    // Optional properties start out not-present
    for (uint8_t idx = 0; idx < /*MGEN:Class.PresenceMaskBytes*/; idx++)
        presenceMask[idx] = 0;
/*MGEN:ENDIF*/
/*MGEN:IF(Class.ExistPerThreadStats)*/
    perThreadStatsArray = new struct PerThreadStats*[maxThreads];
    for (int idx = 0; idx < maxThreads; idx++)
        perThreadStatsArray[idx] = 0;
/*MGEN:ENDIF*/
/*MGEN:IF(Root.GenLogs)*/
    QPID_LOG_CAT(trace, model, "Mgmt create " << className
        << ". id:" << getKey());
/*MGEN:ENDIF*/
}

/*MGEN:Class.NameCap*/::~/*MGEN:Class.NameCap*/ ()
{
/*MGEN:IF(Class.ExistPerThreadStats)*/
    for (int idx = 0; idx < maxThreads; idx++)
        if (perThreadStatsArray[idx] != 0)
            delete perThreadStatsArray[idx];
    delete[] perThreadStatsArray;
/*MGEN:ENDIF*/
}

void /*MGEN:Class.NameCap*/::debugStats (const std::string& comment)
{
/*MGEN:IF(Root.GenLogs)*/
    bool logEnabled;
    QPID_LOG_TEST_CAT(trace, model, logEnabled);
    if (logEnabled)
    {
        ::qpid::types::Variant::Map map;
        mapEncodeValues(map, false, true);
        QPID_LOG_CAT(trace, model, "Mgmt " << comment << ((comment!="")?(" "):("")) << className
            << ". id:" << getKey()
            << " Statistics: " << map);
    }
/*MGEN:ENDIF*/
}


namespace {
    const string NAME("name");
    const string TYPE("type");
    const string ACCESS("access");
    const string IS_INDEX("index");
    const string IS_OPTIONAL("optional");
    const string UNIT("unit");
    const string MIN("min");
    const string MAX("max");
    const string MAXLEN("maxlen");
    const string DESC("desc");
    const string ARGCOUNT("argCount");
    const string ARGS("args");
    const string DIR("dir");
    const string DEFAULT("default");
}

void /*MGEN:Class.NameCap*/::registerSelf(ManagementAgent* agent)
{
    agent->registerClass(packageName, className, md5Sum, writeSchema);
}

void /*MGEN:Class.NameCap*/::writeSchema (std::string& schema)
{
    const int _bufSize=65536;
    char _msgChars[_bufSize];
    ::qpid::management::Buffer buf(_msgChars, _bufSize);
    ::qpid::types::Variant::Map ft;

    // Schema class header:
    buf.putOctet       (CLASS_KIND_TABLE);
    buf.putShortString (packageName); // Package Name
    buf.putShortString (className);   // Class Name
    buf.putBin128      (md5Sum);      // Schema Hash
    buf.putShort       (/*MGEN:Class.ConfigCount*/); // Config Element Count
    buf.putShort       (/*MGEN:Class.InstCount*/); // Inst Element Count
    buf.putShort       (/*MGEN:Class.MethodCount*/); // Method Count

    // Properties
/*MGEN:Class.PropertySchema*/
    // Statistics
/*MGEN:Class.StatisticSchema*/
    // Methods
/*MGEN:Class.MethodSchema*/
    {
        uint32_t _len = buf.getPosition();
        buf.reset();
        buf.getRawData(schema, _len);
    }
}

/*MGEN:IF(Class.ExistPerThreadStats)*/
void /*MGEN:Class.NameCap*/::aggregatePerThreadStats(struct PerThreadStats* totals) const
{
/*MGEN:Class.InitializeTotalPerThreadStats*/
    for (int idx = 0; idx < maxThreads; idx++) {
        struct PerThreadStats* threadStats = perThreadStatsArray[idx];
        if (threadStats != 0) {
/*MGEN:Class.AggregatePerThreadStats*/
        }
    }
}
/*MGEN:ENDIF*/

/*MGEN:IF(Root.GenQMFv1)*/
uint32_t /*MGEN:Class.NameCap*/::writePropertiesSize() const
{
    uint32_t size = writeTimestampsSize();
/*MGEN:IF(Class.ExistOptionals)*/
    size += /*MGEN:Class.PresenceMaskBytes*/;
/*MGEN:ENDIF*/
/*MGEN:Class.SizeProperties*/
    return size;
}

void /*MGEN:Class.NameCap*/::readProperties (const std::string& _sBuf)
{
    char *_tmpBuf = new char[_sBuf.length()];
    memcpy(_tmpBuf, _sBuf.data(), _sBuf.length());
    ::qpid::management::Buffer buf(_tmpBuf, _sBuf.length());
    Mutex::ScopedLock mutex(accessLock);

    {
        std::string _tbuf;
        buf.getRawData(_tbuf, writeTimestampsSize());
        readTimestamps(_tbuf);
    }

/*MGEN:IF(Class.ExistOptionals)*/
    for (uint8_t idx = 0; idx < /*MGEN:Class.PresenceMaskBytes*/; idx++)
        presenceMask[idx] = buf.getOctet();
/*MGEN:ENDIF*/
/*MGEN:Class.ReadProperties*/

    delete [] _tmpBuf;
}

void /*MGEN:Class.NameCap*/::writeProperties (std::string& _sBuf) const
{
    const int _bufSize=65536;
    char _msgChars[_bufSize];
    ::qpid::management::Buffer buf(_msgChars, _bufSize);

    Mutex::ScopedLock mutex(accessLock);
    configChanged = false;

    {
        std::string _tbuf;
        writeTimestamps(_tbuf);
        buf.putRawData(_tbuf);
    }


/*MGEN:IF(Class.ExistOptionals)*/
    for (uint8_t idx = 0; idx < /*MGEN:Class.PresenceMaskBytes*/; idx++)
        buf.putOctet(presenceMask[idx]);
/*MGEN:ENDIF*/
/*MGEN:Class.WriteProperties*/

    uint32_t _bufLen = buf.getPosition();
    buf.reset();

    buf.getRawData(_sBuf, _bufLen);
}

void /*MGEN:Class.NameCap*/::writeStatistics (std::string& _sBuf, bool skipHeaders)
{
    const int _bufSize=65536;
    char _msgChars[_bufSize];
    ::qpid::management::Buffer buf(_msgChars, _bufSize);

    Mutex::ScopedLock mutex(accessLock);
    instChanged = false;
/*MGEN:IF(Class.ExistPerThreadAssign)*/
    for (int idx = 0; idx < maxThreads; idx++) {
        struct PerThreadStats* threadStats = perThreadStatsArray[idx];
        if (threadStats != 0) {
/*MGEN:Class.PerThreadAssign*/
        }
    }
/*MGEN:ENDIF*/
/*MGEN:IF(Class.ExistPerThreadStats)*/
    struct PerThreadStats totals;
    aggregatePerThreadStats(&totals);
/*MGEN:ENDIF*/
/*MGEN:Class.Assign*/
    if (!skipHeaders) {
        std::string _tbuf;
        writeTimestamps (_tbuf);
        buf.putRawData(_tbuf);
    }

/*MGEN:Class.WriteStatistics*/

    // Maintenance of hi-lo statistics
/*MGEN:Class.HiLoStatResets*/
/*MGEN:IF(Class.ExistPerThreadResets)*/
    for (int idx = 0; idx < maxThreads; idx++) {
        struct PerThreadStats* threadStats = perThreadStatsArray[idx];
        if (threadStats != 0) {
/*MGEN:Class.PerThreadHiLoStatResets*/
        }
    }
/*MGEN:ENDIF*/

    uint32_t _bufLen = buf.getPosition();
    buf.reset();

    buf.getRawData(_sBuf, _bufLen);
}

void /*MGEN:Class.NameCap*/::doMethod (/*MGEN:Class.DoMethodArgs*/)
{
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;
    std::string          text;

    bool _matched = false;

    const int _bufSize=65536;
    char _msgChars[_bufSize];
    ::qpid::management::Buffer outBuf(_msgChars, _bufSize);

/*MGEN:Class.MethodHandlers*/

    if (!_matched) {
        outBuf.putLong(status);
        outBuf.putShortString(Manageable::StatusText(status, text));
    }

    uint32_t _bufLen = outBuf.getPosition();
    outBuf.reset();

    outBuf.getRawData(outStr, _bufLen);
}
/*MGEN:ENDIF*/
std::string /*MGEN:Class.NameCap*/::getKey() const
{
    std::stringstream key;

/*MGEN:Class.PrimaryKey*/
    return key.str();
}


void /*MGEN:Class.NameCap*/::mapEncodeValues (::qpid::types::Variant::Map& _map,
                                              bool includeProperties,
                                              bool includeStatistics)
{
    using namespace ::qpid::types;
    Mutex::ScopedLock mutex(accessLock);

    if (includeProperties) {
        configChanged = false;
/*MGEN:Class.MapEncodeProperties*/
    }

    if (includeStatistics) {
        instChanged = false;
/*MGEN:IF(Class.ExistPerThreadAssign)*/
        for (int idx = 0; idx < maxThreads; idx++) {
            struct PerThreadStats* threadStats = perThreadStatsArray[idx];
            if (threadStats != 0) {
/*MGEN:Class.PerThreadAssign*/
            }
        }
/*MGEN:ENDIF*/
/*MGEN:IF(Class.ExistPerThreadStats)*/
        struct PerThreadStats totals;
        aggregatePerThreadStats(&totals);
/*MGEN:ENDIF*/
/*MGEN:Class.Assign*/

/*MGEN:Class.MapEncodeStatistics*/

    // Maintenance of hi-lo statistics
/*MGEN:Class.HiLoStatResets*/
/*MGEN:IF(Class.ExistPerThreadResets)*/
        for (int idx = 0; idx < maxThreads; idx++) {
            struct PerThreadStats* threadStats = perThreadStatsArray[idx];
            if (threadStats != 0) {
/*MGEN:Class.PerThreadHiLoStatResets*/
            }
        }
/*MGEN:ENDIF*/
    }
}

void /*MGEN:Class.NameCap*/::mapDecodeValues (const ::qpid::types::Variant::Map& _map)
{
    ::qpid::types::Variant::Map::const_iterator _i;
    Mutex::ScopedLock mutex(accessLock);
/*MGEN:IF(Class.ExistOptionals)*/
    bool _found;
/*MGEN:ENDIF*/
/*MGEN:Class.MapDecodeProperties*/
}

void /*MGEN:Class.NameCap*/::doMethod (/*MGEN:Class.DoMapMethodArgs*/)
{
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;
    std::string          text;

/*MGEN:Class.MapMethodHandlers*/
    outMap["_status_code"] = (uint32_t) status;
    outMap["_status_text"] = Manageable::StatusText(status, text);
}
