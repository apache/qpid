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
#include "qpid/framing/FieldTable.h"
#include "Manageable.h" 
#include "Queue.h"

using namespace qpid::management;
using namespace qpid::sys;
using namespace qpid::framing;

bool Queue::schemaNeeded = true;

Queue::Queue (Manageable* _core, Manageable* _parent, 
              const std::string& _name,
              bool _durable, bool _autoDelete) :
    ManagementObject(_core, "queue"), name(_name),
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
    FieldTable ft;

    schemaNeeded = false;

    // Schema class header:
    buf.putShortString (className);  // Class Name
    buf.putShort       (4);          // Config Element Count
    buf.putShort       (33);         // Inst Element Count
    buf.putShort       (0);          // Method Count
    buf.putShort       (0);          // Event Count

    // Config Elements
    ft = FieldTable ();
    ft.setString ("name",   "vhostRef");
    ft.setInt    ("type",   TYPE_U64);
    ft.setInt    ("access", ACCESS_RO);
    ft.setInt    ("index",  1);
    ft.setString ("desc",   "Virtual Host Ref");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString ("name",   "name");
    ft.setInt    ("type",   TYPE_SSTR);
    ft.setInt    ("access", ACCESS_RO);
    ft.setInt    ("index",  1);
    ft.setString ("desc",   "Queue Name");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString ("name",   "durable");
    ft.setInt    ("type",   TYPE_U8);
    ft.setInt    ("access", ACCESS_RO);
    ft.setInt    ("index",  0);
    ft.setString ("desc",   "Durable");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString ("name",   "autoDelete");
    ft.setInt    ("type",   TYPE_U8);
    ft.setInt    ("access", ACCESS_RO);
    ft.setInt    ("index",  0);
    ft.setString ("desc",   "AutoDelete");
    buf.put (ft);

    // Inst Elements
    ft = FieldTable ();
    ft.setString    ("name", "msgTotalEnqueues");
    ft.setInt       ("type", TYPE_U64);
    ft.setString    ("desc", "Total messages enqueued");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "msgTotalDequeues");
    ft.setInt       ("type", TYPE_U64);
    ft.setString    ("desc", "Total messages dequeued");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "msgTxnEnqueues");
    ft.setInt       ("type", TYPE_U64);
    ft.setString    ("desc", "Transactional messages enqueued");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "msgTxnDequeues");
    ft.setInt       ("type", TYPE_U64);
    ft.setString    ("desc", "Transactional messages dequeued");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "msgPersistEnqueues");
    ft.setInt       ("type", TYPE_U64);
    ft.setString    ("desc", "Persistent messages enqueued");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "msgPersistDequeues");
    ft.setInt       ("type", TYPE_U64);
    ft.setString    ("desc", "Persistent messages dequeued");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "msgDepth");
    ft.setInt       ("type", TYPE_U32);
    ft.setString    ("desc", "Current size of queue in messages");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "msgDepthLow");
    ft.setInt       ("type", TYPE_U32);
    ft.setString    ("desc", "Low-water queue size, this interval");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "msgDepthHigh");
    ft.setInt       ("type", TYPE_U32);
    ft.setString    ("desc", "High-water queue size, this interval");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "byteTotalEnqueues");
    ft.setInt       ("type", TYPE_U64);
    ft.setString    ("desc", "Total messages enqueued");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "byteTotalDequeues");
    ft.setInt       ("type", TYPE_U64);
    ft.setString    ("desc", "Total messages dequeued");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "byteTxnEnqueues");
    ft.setInt       ("type", TYPE_U64);
    ft.setString    ("desc", "Transactional messages enqueued");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "byteTxnDequeues");
    ft.setInt       ("type", TYPE_U64);
    ft.setString    ("desc", "Transactional messages dequeued");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "bytePersistEnqueues");
    ft.setInt       ("type", TYPE_U64);
    ft.setString    ("desc", "Persistent messages enqueued");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "bytePersistDequeues");
    ft.setInt       ("type", TYPE_U64);
    ft.setString    ("desc", "Persistent messages dequeued");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "byteDepth");
    ft.setInt       ("type", TYPE_U32);
    ft.setString    ("desc", "Current size of queue in bytes");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "byteDepthLow");
    ft.setInt       ("type", TYPE_U32);
    ft.setString    ("desc", "Low-water mark this interval");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "byteDepthHigh");
    ft.setInt       ("type", TYPE_U32);
    ft.setString    ("desc", "High-water mark this interval");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "enqueueTxnStarts");
    ft.setInt       ("type", TYPE_U64);
    ft.setString    ("desc", "Total enqueue transactions started ");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "enqueueTxnCommits");
    ft.setInt       ("type", TYPE_U64);
    ft.setString    ("desc", "Total enqueue transactions committed");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "enqueueTxnRejects");
    ft.setInt       ("type", TYPE_U64);
    ft.setString    ("desc", "Total enqueue transactions rejected");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "enqueueTxnCount");
    ft.setInt       ("type", TYPE_U32);
    ft.setString    ("desc", "Current pending enqueue transactions");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "enqueueTxnCountLow");
    ft.setInt       ("type", TYPE_U32);
    ft.setString    ("desc", "Low water mark this interval");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "enqueueTxnCountHigh");
    ft.setInt       ("type", TYPE_U32);
    ft.setString    ("desc", "High water mark this interval");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "dequeueTxnStarts");
    ft.setInt       ("type", TYPE_U64);
    ft.setString    ("desc", "Total dequeue transactions started ");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "dequeueTxnCommits");
    ft.setInt       ("type", TYPE_U64);
    ft.setString    ("desc", "Total dequeue transactions committed");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "dequeueTxnRejects");
    ft.setInt       ("type", TYPE_U64);
    ft.setString    ("desc", "Total dequeue transactions rejected");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "dequeueTxnCount");
    ft.setInt       ("type", TYPE_U32);
    ft.setString    ("desc", "Current pending dequeue transactions");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "dequeueTxnCountLow");
    ft.setInt       ("type", TYPE_U32);
    ft.setString    ("desc", "Transaction low water mark this interval");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "dequeueTxnCountHigh");
    ft.setInt       ("type", TYPE_U32);
    ft.setString    ("desc", "Transaction high water mark this interval");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "consumers");
    ft.setInt       ("type", TYPE_U32);
    ft.setString    ("desc", "Current consumers on queue");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "consumersLow");
    ft.setInt       ("type", TYPE_U32);
    ft.setString    ("desc", "Consumer low water mark this interval");
    buf.put (ft);

    ft = FieldTable ();
    ft.setString    ("name", "consumersHigh");
    ft.setInt       ("type", TYPE_U32);
    ft.setString    ("desc", "Consumer high water mark this interval");
    buf.put (ft);
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
