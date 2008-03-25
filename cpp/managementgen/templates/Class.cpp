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
#include "qpid/framing/FieldTable.h"
#include "qpid/management/Manageable.h" 
#include "/*MGEN:Class.NameCap*/.h"
/*MGEN:Class.MethodArgIncludes*/

using namespace qpid::management;
using namespace qpid::sys;
using namespace qpid::framing;
using           std::string;

string  /*MGEN:Class.NameCap*/::packageName  = string ("/*MGEN:Class.NamePackageLower*/");
string  /*MGEN:Class.NameCap*/::className    = string ("/*MGEN:Class.NameLower*/");
uint8_t /*MGEN:Class.NameCap*/::md5Sum[16]   =
    {/*MGEN:Class.SchemaMD5*/};
bool    /*MGEN:Class.NameCap*/::firstInst    = true;

/*MGEN:Class.NameCap*/::/*MGEN:Class.NameCap*/ (Manageable* _core/*MGEN:Class.ParentArg*//*MGEN:Class.ConstructorArgs*/) :
    ManagementObject(_core)
    /*MGEN:Class.ConstructorInits*/
{
    /*MGEN:Class.ParentRefAssignment*/
/*MGEN:Class.InitializeElements*/
}

/*MGEN:Class.NameCap*/::~/*MGEN:Class.NameCap*/ () {}

namespace {
    const string NAME("name");
    const string TYPE("type");
    const string ACCESS("access");
    const string INDEX("index");
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

bool /*MGEN:Class.NameCap*/::firstInstance (void)
{
    Mutex::ScopedLock alock(accessorLock);
    if (firstInst)
    {
        firstInst = false;
        return true;
    }

    return false;
}

void /*MGEN:Class.NameCap*/::writeSchema (Buffer& buf)
{
    FieldTable ft;

    // Schema class header:
    buf.putShortString (packageName); // Package Name
    buf.putShortString (className);   // Class Name
    buf.putBin128      (md5Sum);      // Schema Hash
    buf.putShort       (/*MGEN:Class.ConfigCount*/); // Config Element Count
    buf.putShort       (/*MGEN:Class.InstCount*/); // Inst Element Count
    buf.putShort       (/*MGEN:Class.MethodCount*/); // Method Count
    buf.putShort       (/*MGEN:Class.EventCount*/); // Event Count

    // Config Elements
/*MGEN:Class.ConfigElementSchema*/
    // Inst Elements
/*MGEN:Class.InstElementSchema*/
    // Methods
/*MGEN:Class.MethodSchema*/
    // Events
/*MGEN:Class.EventSchema*/
}

void /*MGEN:Class.NameCap*/::writeConfig (Buffer& buf)
{
    configChanged = false;

    writeTimestamps (buf);
/*MGEN:Class.WriteConfig*/
}

void /*MGEN:Class.NameCap*/::writeInstrumentation (Buffer& buf, bool skipHeaders)
{
    instChanged = false;

    if (!skipHeaders)
        writeTimestamps (buf);
/*MGEN:Class.WriteInst*/

    // Maintenance of hi-lo statistics
/*MGEN:Class.HiLoStatResets*/
}

void /*MGEN:Class.NameCap*/::doMethod (/*MGEN:Class.DoMethodArgs*/)
{
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;
/*MGEN:Class.MethodHandlers*/
    outBuf.putLong        (status);
    outBuf.putShortString (Manageable::StatusText (status));
}

