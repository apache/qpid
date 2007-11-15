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
#include "qpid/framing/FieldTable.h"

using namespace qpid::management;
using namespace qpid::sys;
using namespace qpid::framing;

bool Vhost::schemaNeeded = true;

Vhost::Vhost (Manageable* _core, Manageable* _parent) :
    ManagementObject (_core, "vhost"), name("/")
{
    brokerRef = _parent->GetManagementObject ()->getObjectId ();
}

Vhost::~Vhost () {}

void Vhost::writeSchema (Buffer& buf)
{
    FieldTable ft;

    schemaNeeded = false;

    // Schema class header:
    buf.putShortString (className);  // Class Name
    buf.putShort       (2);          // Config Element Count
    buf.putShort       (0);          // Inst Element Count
    buf.putShort       (0);          // Method Count
    buf.putShort       (0);          // Event Count

    // Config Elements
    ft = FieldTable ();
    ft.setString ("name",   "brokerRef");
    ft.setInt    ("type",   TYPE_U64);
    ft.setInt    ("access", ACCESS_RC);
    ft.setInt    ("index",  1);
    ft.setString ("desc",   "Broker Reference");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString ("name",   "name");
    ft.setInt    ("type",   TYPE_SSTR);
    ft.setInt    ("access", ACCESS_RO);
    ft.setInt    ("index",  1);
    ft.setString ("desc",   "Name of virtual host");
    buf.put (ft);
}

void Vhost::writeConfig (Buffer& buf)
{
    configChanged = false;

    writeTimestamps    (buf);
    buf.putLongLong    (brokerRef);
    buf.putShortString (name);
}

