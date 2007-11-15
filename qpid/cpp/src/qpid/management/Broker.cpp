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
 
#include "config.h"
#include "qpid/broker/Broker.h"
#include "qpid/framing/FieldTable.h"
#include "Broker.h"
#include "ArgsBrokerEcho.h"

using namespace qpid::management;
using namespace qpid::sys;
using namespace qpid::framing;

bool Broker::schemaNeeded = true;

Broker::Broker (Manageable* _core, const Options& _conf) :
    ManagementObject (_core, "broker")
{
    broker::Broker::Options& conf = (broker::Broker::Options&) _conf;

    sysId = "sysId";
    port                 = conf.port;
    workerThreads        = conf.workerThreads;
    maxConns             = conf.maxConnections;
    connBacklog          = conf.connectionBacklog;
    stagingThreshold     = conf.stagingThreshold;
    storeLib             = conf.store;
    asyncStore           = conf.storeAsync;
    mgmtPubInterval      = conf.mgmtPubInterval;
    initialDiskPageSize  = 0;
    initialPagesPerQueue = 0;
    clusterName          = "";
    version              = PACKAGE_VERSION;
}

Broker::~Broker () {}

void Broker::writeSchema (Buffer& buf)
{
    FieldTable ft;
    FieldTable arg;

    schemaNeeded = false;

    // Schema class header:
    buf.putShortString (className);  // Class Name
    buf.putShort       (13);         // Config Element Count
    buf.putShort       (0);          // Inst Element Count
    buf.putShort       (1);          // Method Count
    buf.putShort       (0);          // Event Count

    // Config Elements
    ft = FieldTable ();
    ft.setString ("name",   "systemRef");
    ft.setInt    ("type",   TYPE_U64);
    ft.setInt    ("access", ACCESS_RC);
    ft.setInt    ("index",  1);
    ft.setString ("desc",   "System ID");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString ("name",   "port");
    ft.setInt    ("type",   TYPE_U16);
    ft.setInt    ("access", ACCESS_RC);
    ft.setInt    ("index",  1);
    ft.setString ("desc",   "TCP Port for AMQP Service");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString ("name",   "workerThreads");
    ft.setInt    ("type",   TYPE_U16);
    ft.setInt    ("access", ACCESS_RO);
    ft.setInt    ("index",  0);
    ft.setString ("desc",   "Thread pool size");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString ("name",   "maxConns");
    ft.setInt    ("type",   TYPE_U16);
    ft.setInt    ("access", ACCESS_RO);
    ft.setInt    ("index",  0);
    ft.setString ("desc",   "Maximum allowed connections");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString ("name",   "connBacklog");
    ft.setInt    ("type",   TYPE_U16);
    ft.setInt    ("access", ACCESS_RO);
    ft.setInt    ("index",  0);
    ft.setString ("desc",   "Connection backlog limit for listening socket");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString ("name",   "stagingThreshold");
    ft.setInt    ("type",   TYPE_U32);
    ft.setInt    ("access", ACCESS_RO);
    ft.setInt    ("index",  0);
    ft.setString ("desc",   "Broker stages messages over this size to disk");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString ("name",   "storeLib");
    ft.setInt    ("type",   TYPE_SSTR);
    ft.setInt    ("access", ACCESS_RO);
    ft.setInt    ("index",  0);
    ft.setString ("desc",   "Name of persistent storage library");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString ("name",   "asyncStore");
    ft.setInt    ("type",   TYPE_U8);
    ft.setInt    ("access", ACCESS_RO);
    ft.setInt    ("index",  0);
    ft.setString ("desc",   "Use async persistent store");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString ("name",   "mgmtPubInterval");
    ft.setInt    ("type",   TYPE_U16);
    ft.setInt    ("access", ACCESS_RW);
    ft.setInt    ("index",  0);
    ft.setInt    ("min",    1);
    ft.setString ("unit",   "second");
    ft.setString ("desc",   "Interval for management broadcasts");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString ("name",   "initialDiskPageSize");
    ft.setInt    ("type",   TYPE_U32);
    ft.setInt    ("access", ACCESS_RO);
    ft.setInt    ("index",  0);
    ft.setString ("desc",   "Number of disk pages allocated for storage");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString ("name",   "initialPagesPerQueue");
    ft.setInt    ("type",   TYPE_U32);
    ft.setInt    ("access", ACCESS_RO);
    ft.setInt    ("index",  0);
    ft.setString ("desc",   "Number of disk pages allocated per queue");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString ("name",   "clusterName");
    ft.setInt    ("type",   TYPE_SSTR);
    ft.setInt    ("access", ACCESS_RO);
    ft.setInt    ("index",  0);
    ft.setString ("desc",   "Name of cluster this server is a member of, zero-length for standalone server");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString ("name",   "version");
    ft.setInt    ("type",   TYPE_SSTR);
    ft.setInt    ("access", ACCESS_RO);
    ft.setInt    ("index",  0);
    ft.setString ("desc",   "Running software version");
    buf.put (ft);

    // Inst Elements

    return;  // TODO - Remove

    // Methods
    ft = FieldTable ();
    ft.setString ("name",   "echo");
    ft.setInt    ("args",   2);
 
    arg = FieldTable ();
    arg.setString ("name", "sequence");
    arg.setInt    ("type",  TYPE_U32);
    arg.setInt    ("dir",   DIR_IO);
    ft.setTable   ("arg",   arg);

    arg = FieldTable ();
    arg.setString ("name", "body");
    arg.setInt    ("type",  TYPE_LSTR);
    arg.setInt    ("dir",   DIR_IO);
    ft.setTable   ("arg",   arg);

    buf.put (ft);

   // Events
}

void Broker::writeConfig (Buffer& buf)
{
    configChanged = false;

    writeTimestamps    (buf);
    buf.putLongLong    (0);
    buf.putShort       (port);
    buf.putShort       (workerThreads);
    buf.putShort       (maxConns);
    buf.putShort       (connBacklog);
    buf.putLong        (stagingThreshold);
    buf.putShortString (storeLib);
    buf.putOctet       (asyncStore ? 1 : 0);
    buf.putShort       (mgmtPubInterval);
    buf.putLong        (initialDiskPageSize);
    buf.putLong        (initialPagesPerQueue);
    buf.putShortString (clusterName);
    buf.putShortString (version);
}

void Broker::doMethod (string  methodName,
                       Buffer& inBuf,
                       Buffer& outBuf)
{
    if (methodName.compare ("echo") == 0)
    {
        ArgsBrokerEcho args;
        uint32_t       result;

        args.io_sequence = inBuf.getLong ();
        inBuf.getLongString (args.io_body);

        result = coreObject->ManagementMethod (1, args);

        outBuf.putLong        (result);
        outBuf.putShortString ("OK");
        outBuf.putLong        (args.io_sequence);
        outBuf.putLongString  (args.io_body);
    }

    // TODO - Remove this method prior to beta
    else if (methodName.compare ("crash") == 0)
    {
        assert (0);
    }
    else
    {
        outBuf.putLong        (1);
        outBuf.putShortString ("Unknown Method");
    }
}

