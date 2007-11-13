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
#include "Vhost.h"

using namespace qpid::management;
using namespace qpid::sys;
using namespace qpid::framing;

bool Vhost::schemaNeeded = true;

Vhost::Vhost (Manageable* _core, Manageable* _parent) :
    ManagementObject (_core), name("/")
{
    brokerRef = _parent->GetManagementObject ()->getObjectId ();
}

Vhost::~Vhost () {}

void Vhost::writeSchema (Buffer& buf)
{
    schemaNeeded = false;

    schemaListBegin (buf);
    schemaItem (buf, TYPE_UINT64, "brokerRef", "Broker Reference" ,    true);
    schemaItem (buf, TYPE_STRING, "name",      "Name of virtual host", true);
    schemaListEnd (buf);
}

void Vhost::writeConfig (Buffer& buf)
{
    configChanged = false;

    writeTimestamps    (buf);
    buf.putLongLong    (brokerRef);
    buf.putShortString (name);
}

