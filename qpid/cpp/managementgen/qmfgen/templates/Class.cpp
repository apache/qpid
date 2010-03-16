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

#include "qpid/log/Statement.h"
#include "qpid/management/Manageable.h"
#include "qpid//*MGEN:Class.AgentHeaderLocation*//ManagementAgent.h"
#include "/*MGEN:Class.NameCap*/.h"
/*MGEN:Class.MethodArgIncludes*/
#include <iostream>

using namespace qmf::/*MGEN:Class.Namespace*/;
using namespace qpid::messaging;
using           qpid::management::ManagementAgent;
using           qpid::management::Manageable;
using           qpid::management::ManagementObject;
using           qpid::management::Args;
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

void /*MGEN:Class.NameCap*/::writeSchema (::qpid::messaging::VariantMap& map)
{
    ::qpid::messaging::Variant::Map _sid;
    ::qpid::messaging::Variant::Map _props;
    ::qpid::messaging::Variant::Map _stats;
    ::qpid::messaging::Variant::Map _methods;

    _sid["_type"] = CLASS_KIND_TABLE;
    _sid["_package_name"] = packageName;
    _sid["_class_name"] = className;
    _sid["_hash_str"] = std::string((const char *)md5Sum, sizeof(md5Sum));
    map["_schema_id"] = _sid;

    map["_config_ct"] = /*MGEN:Class.ConfigCount*/;
    map["_inst_ct"] = /*MGEN:Class.InstCount*/;
    map["_method_ct"] = /*MGEN:Class.MethodCount*/;

    // Properties
/*MGEN:Class.PropertySchemaMap*/
    if (!_props.empty())
        map["_properties"] = _props;

    // Statistics
/*MGEN:Class.StatisticSchemaMap*/
    if (!_stats.empty())
        map["_statistics"] = _stats;

    // Methods
/*MGEN:Class.MethodSchemaMap*/
    if (!_methods.empty())
        map["_methods"] = _methods;
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


std::string /*MGEN:Class.NameCap*/::getKey() const
{
    std::stringstream key;

/*MGEN:Class.PrimaryKey*/
    return key.str();
}



void /*MGEN:Class.NameCap*/::mapEncodeValues (::qpid::messaging::VariantMap& _map,
                                              bool includeProperties,
                                              bool includeStatistics)
{
    using namespace ::qpid::messaging;
    ::qpid::sys::Mutex::ScopedLock mutex(accessLock);

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

void /*MGEN:Class.NameCap*/::mapDecodeValues (const ::qpid::messaging::VariantMap& _map)
{
    ::qpid::messaging::VariantMap::const_iterator _i;
    ::qpid::sys::Mutex::ScopedLock mutex(accessLock);
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
    outMap["_status_code"] = status;
    outMap["_status_text"] = Manageable::StatusText(status, text);
}
