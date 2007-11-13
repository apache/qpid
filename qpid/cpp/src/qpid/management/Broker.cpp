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
#include "Broker.h"
#include "ArgsBrokerEcho.h"

using namespace qpid::management;
using namespace qpid::sys;
using namespace qpid::framing;

bool Broker::schemaNeeded = true;

Broker::Broker (Manageable* _core, const Options& _conf) :
    ManagementObject (_core)
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
    schemaNeeded = false;

    schemaListBegin (buf);
    schemaItem (buf, TYPE_UINT32, "systemRef",     "System ID",                 true, true);
    schemaItem (buf, TYPE_UINT16, "port",          "TCP Port for AMQP Service", true, true);
    schemaItem (buf, TYPE_UINT16, "workerThreads", "Thread pool size", true);
    schemaItem (buf, TYPE_UINT16, "maxConns",      "Maximum allowed connections", true);
    schemaItem (buf, TYPE_UINT16, "connBacklog",
                "Connection backlog limit for listening socket", true);
    schemaItem (buf, TYPE_UINT32, "stagingThreshold",
                "Broker stages messages over this size to disk", true);
    schemaItem (buf, TYPE_STRING, "storeLib",        "Name of persistent storage library", true);
    schemaItem (buf, TYPE_UINT8,  "asyncStore",      "Use async persistent store", true);
    schemaItem (buf, TYPE_UINT16, "mgmtPubInterval", "Interval for management broadcasts", true);
    schemaItem (buf, TYPE_UINT32, "initialDiskPageSize",
                "Number of disk pages allocated for storage", true);
    schemaItem (buf, TYPE_UINT32, "initialPagesPerQueue",
                "Number of disk pages allocated per queue", true);
    schemaItem (buf, TYPE_STRING, "clusterName",
                "Name of cluster this server is a member of, zero-length for standalone server", true);
    schemaItem (buf, TYPE_STRING, "version", "Running software version", true);
    schemaListEnd (buf);
}

void Broker::writeConfig (Buffer& buf)
{
    configChanged = false;

    writeTimestamps    (buf);
    buf.putLong        (0);
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
    else
    {
        outBuf.putLong        (1);
        outBuf.putShortString ("Unknown Method");
    }
}

