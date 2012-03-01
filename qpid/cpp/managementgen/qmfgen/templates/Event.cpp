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
#include "qpid//*MGEN:Event.AgentHeaderLocation*//ManagementAgent.h"
#include "Event/*MGEN:Event.NameCap*/.h"

using namespace qmf::/*MGEN:Event.Namespace*/;
using           qpid::management::ManagementAgent;
using           qpid::management::Manageable;
using           qpid::management::ManagementObject;
using           qpid::management::Args;
using           qpid::management::Mutex;
using           std::string;

string  Event/*MGEN:Event.NameCap*/::packageName  = string ("/*MGEN:Event.NamePackageLower*/");
string  Event/*MGEN:Event.NameCap*/::eventName    = string ("/*MGEN:Event.Name*/");
uint8_t Event/*MGEN:Event.NameCap*/::md5Sum[16]   =
    {/*MGEN:Event.SchemaMD5*/};

Event/*MGEN:Event.NameCap*/::Event/*MGEN:Event.NameCap*/ (/*MGEN:Event.ConstructorArgs*/) :
    /*MGEN:Event.ConstructorInits*/
{}

namespace {
    const string NAME("name");
    const string TYPE("type");
    const string DESC("desc");
    const string ARGCOUNT("argCount");
    const string ARGS("args");
}

void Event/*MGEN:Event.NameCap*/::registerSelf(ManagementAgent* agent)
{
    agent->registerEvent(packageName, eventName, md5Sum, writeSchema);
}

void Event/*MGEN:Event.NameCap*/::writeSchema (std::string& schema)
{
    const int _bufSize = 65536;
    char _msgChars[_bufSize];
    ::qpid::management::Buffer buf(_msgChars, _bufSize);
    ::qpid::types::Variant::Map ft;

    // Schema class header:
    buf.putOctet       (CLASS_KIND_EVENT);
    buf.putShortString (packageName); // Package Name
    buf.putShortString (eventName);   // Event Name
    buf.putBin128      (md5Sum);      // Schema Hash
    buf.putShort       (/*MGEN:Event.ArgCount*/); // Argument Count

    // Arguments
/*MGEN:Event.ArgSchema*/
    {
        uint32_t _len = buf.getPosition();
        buf.reset();
        buf.getRawData(schema, _len);
    }
}

void Event/*MGEN:Event.NameCap*/::encode(std::string& _sBuf) const
{
    const int _bufSize=65536;
    char _msgChars[_bufSize];
    ::qpid::management::Buffer buf(_msgChars, _bufSize);

/*MGEN:Event.ArgEncodes*/

    uint32_t _bufLen = buf.getPosition();
    buf.reset();

    buf.getRawData(_sBuf, _bufLen);
}

void Event/*MGEN:Event.NameCap*/::mapEncode(::qpid::types::Variant::Map& map) const
{
    using namespace ::qpid::types;
/*MGEN:Event.ArgMap*/
}

bool Event/*MGEN:Event.NameCap*/::match(const std::string& evt, const std::string& pkg)
{
    return eventName == evt && packageName == pkg;
}
