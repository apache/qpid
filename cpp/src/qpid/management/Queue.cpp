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

#include "qpid/log/Statement.h"
#include "Manageable.h" 
#include "Queue.h"

using namespace qpid::management;
using namespace qpid::sys;
using namespace qpid::framing;

bool Queue::schemaNeeded = true;

Queue::Queue (Manageable* _core, Manageable* _parent, 
              const std::string& _name,
              bool _durable, bool _autoDelete) :
    ManagementObject(_core), name(_name),
    durable(_durable), autoDelete(_autoDelete)
{
    vhostRef = _parent->GetManagementObject ()->getObjectId ();

    msgTotalEnqueues     = 0;
    msgTotalDequeues     = 0;
    msgTxEnqueues        = 0;
    msgTxDequeues        = 0;
    msgPersistEnqueues   = 0;
    msgPersistDequeues   = 0;

    msgDepth             = 0;
    msgDepthLow          = 0;
    msgDepthHigh         = 0;

    byteTotalEnqueues    = 0;
    byteTotalDequeues    = 0;
    byteTxEnqueues       = 0;
    byteTxDequeues       = 0;
    bytePersistEnqueues  = 0;
    bytePersistDequeues  = 0;
    
    byteDepth            = 0;
    byteDepthLow         = 0;
    byteDepthHigh        = 0;

    enqueueTxStarts      = 0;
    enqueueTxCommits     = 0;
    enqueueTxRejects     = 0;
    dequeueTxStarts      = 0;
    dequeueTxCommits     = 0;
    dequeueTxRejects     = 0;
    
    enqueueTxCount       = 0;
    enqueueTxCountLow    = 0;
    enqueueTxCountHigh   = 0;

    dequeueTxCount       = 0;
    dequeueTxCountLow    = 0;
    dequeueTxCountHigh   = 0;

    consumers            = 0;
    consumersLow         = 0;
    consumersHigh        = 0;
}

Queue::~Queue () {}

void Queue::writeSchema (Buffer& buf)
{
    schemaNeeded = false;

    schemaListBegin (buf);
    schemaItem (buf, TYPE_UINT64, "vhostRef",            "Virtual Host Ref", true);
    schemaItem (buf, TYPE_STRING, "name",                "Queue Name", true);
    schemaItem (buf, TYPE_BOOL,   "durable",             "Durable",    true);
    schemaItem (buf, TYPE_BOOL,   "autoDelete",          "AutoDelete", true);
    schemaItem (buf, TYPE_UINT64, "msgTotalEnqueues",    "Total messages enqueued");
    schemaItem (buf, TYPE_UINT64, "msgTotalDequeues",    "Total messages dequeued");
    schemaItem (buf, TYPE_UINT64, "msgTxnEnqueues",      "Transactional messages enqueued");
    schemaItem (buf, TYPE_UINT64, "msgTxnDequeues",      "Transactional messages dequeued");
    schemaItem (buf, TYPE_UINT64, "msgPersistEnqueues",  "Persistent messages enqueued");
    schemaItem (buf, TYPE_UINT64, "msgPersistDequeues",  "Persistent messages dequeued");
    schemaItem (buf, TYPE_UINT32, "msgDepth",            "Current size of queue in messages");
    schemaItem (buf, TYPE_UINT32, "msgDepthLow",         "Low-water queue size, this interval");
    schemaItem (buf, TYPE_UINT32, "msgDepthHigh",        "High-water queue size, this interval");
    schemaItem (buf, TYPE_UINT64, "byteTotalEnqueues",   "Total messages enqueued");
    schemaItem (buf, TYPE_UINT64, "byteTotalDequeues",   "Total messages dequeued");
    schemaItem (buf, TYPE_UINT64, "byteTxnEnqueues",     "Transactional messages enqueued");
    schemaItem (buf, TYPE_UINT64, "byteTxnDequeues",     "Transactional messages dequeued");
    schemaItem (buf, TYPE_UINT64, "bytePersistEnqueues", "Persistent messages enqueued");
    schemaItem (buf, TYPE_UINT64, "bytePersistDequeues", "Persistent messages dequeued");
    schemaItem (buf, TYPE_UINT32, "byteDepth",           "Current size of queue in bytes");
    schemaItem (buf, TYPE_UINT32, "byteDepthLow",        "Low-water mark this interval");
    schemaItem (buf, TYPE_UINT32, "byteDepthHigh",       "High-water mark this interval");
    schemaItem (buf, TYPE_UINT64, "enqueueTxnStarts",    "Total enqueue transactions started ");
    schemaItem (buf, TYPE_UINT64, "enqueueTxnCommits",   "Total enqueue transactions committed");
    schemaItem (buf, TYPE_UINT64, "enqueueTxnRejects",   "Total enqueue transactions rejected");
    schemaItem (buf, TYPE_UINT32, "enqueueTxnCount",     "Current pending enqueue transactions");
    schemaItem (buf, TYPE_UINT32, "enqueueTxnCountLow",  "Low water mark this interval");
    schemaItem (buf, TYPE_UINT32, "enqueueTxnCountHigh", "High water mark this interval");
    schemaItem (buf, TYPE_UINT64, "dequeueTxnStarts",    "Total dequeue transactions started ");
    schemaItem (buf, TYPE_UINT64, "dequeueTxnCommits",   "Total dequeue transactions committed");
    schemaItem (buf, TYPE_UINT64, "dequeueTxnRejects",   "Total dequeue transactions rejected");
    schemaItem (buf, TYPE_UINT32, "dequeueTxnCount",     "Current pending dequeue transactions");
    schemaItem (buf, TYPE_UINT32, "dequeueTxnCountLow",  "Transaction low water mark this interval");
    schemaItem (buf, TYPE_UINT32, "dequeueTxnCountHigh", "Transaction high water mark this interval");
    schemaItem (buf, TYPE_UINT32, "consumers",           "Current consumers on queue");
    schemaItem (buf, TYPE_UINT32, "consumersLow",        "Consumer low water mark this interval");
    schemaItem (buf, TYPE_UINT32, "consumersHigh",       "Consumer high water mark this interval");
    schemaListEnd (buf);
}

void Queue::writeConfig (Buffer& buf)
{
    configChanged = false;

    writeTimestamps    (buf);
    buf.putLongLong    (vhostRef);
    buf.putShortString (name);
    buf.putOctet       (durable    ? 1 : 0);
    buf.putOctet       (autoDelete ? 1 : 0);
}

void Queue::writeInstrumentation (Buffer& buf)
{
    instChanged = false;

    writeTimestamps (buf);
    buf.putLongLong (msgTotalEnqueues);
    buf.putLongLong (msgTotalDequeues);
    buf.putLongLong (msgTxEnqueues);
    buf.putLongLong (msgTxDequeues);
    buf.putLongLong (msgPersistEnqueues);
    buf.putLongLong (msgPersistDequeues);
    buf.putLong     (msgDepth);
    buf.putLong     (msgDepthLow);
    buf.putLong     (msgDepthHigh);
    buf.putLongLong (byteTotalEnqueues);
    buf.putLongLong (byteTotalDequeues);
    buf.putLongLong (byteTxEnqueues);
    buf.putLongLong (byteTxDequeues);
    buf.putLongLong (bytePersistEnqueues);
    buf.putLongLong (bytePersistDequeues);
    buf.putLong     (byteDepth);
    buf.putLong     (byteDepthLow);
    buf.putLong     (byteDepthHigh);
    buf.putLongLong (enqueueTxStarts);
    buf.putLongLong (enqueueTxCommits);
    buf.putLongLong (enqueueTxRejects);
    buf.putLong     (enqueueTxCount);
    buf.putLong     (enqueueTxCountLow);
    buf.putLong     (enqueueTxCountHigh);
    buf.putLongLong (dequeueTxStarts);
    buf.putLongLong (dequeueTxCommits);
    buf.putLongLong (dequeueTxRejects);
    buf.putLong     (dequeueTxCount);
    buf.putLong     (dequeueTxCountLow);
    buf.putLong     (dequeueTxCountHigh);
    buf.putLong     (consumers);
    buf.putLong     (consumersLow);
    buf.putLong     (consumersHigh);

    msgDepthLow        = msgDepth;
    msgDepthHigh       = msgDepth;
    byteDepthLow       = byteDepth;
    byteDepthHigh      = byteDepth;
    enqueueTxCountLow  = enqueueTxCount;
    enqueueTxCountHigh = enqueueTxCount;
    dequeueTxCountLow  = dequeueTxCount;
    dequeueTxCountHigh = dequeueTxCount;
    consumersLow       = consumers;
    consumersHigh      = consumers;
}
