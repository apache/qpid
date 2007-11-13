#ifndef _ManagementQueue_
#define _ManagementQueue_

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

#include "ManagementObject.h"

namespace qpid { 
namespace management {

const uint32_t MSG_MASK_TX      = 1;  // Transactional message
const uint32_t MSG_MASK_PERSIST = 2;  // Persistent message

class Queue : public ManagementObject
{
  private:

    static bool schemaNeeded;

    uint64_t    vhostRef;
    std::string name;
    bool        durable;
    bool        autoDelete;
  
    uint64_t  msgTotalEnqueues;     // Total messages enqueued
    uint64_t  msgTotalDequeues;     // Total messages dequeued
    uint64_t  msgTxEnqueues;        // Transactional messages enqueued
    uint64_t  msgTxDequeues;        // Transactional messages dequeued
    uint64_t  msgPersistEnqueues;   // Persistent messages enqueued
    uint64_t  msgPersistDequeues;   // Persistent messages dequeued
    
    uint32_t  msgDepth;             // Current size of queue in messages
    uint32_t  msgDepthLow;          // Low-water queue size, this interval
    uint32_t  msgDepthHigh;         // High-water queue size, this interval

    uint64_t  byteTotalEnqueues;    // Total messages enqueued
    uint64_t  byteTotalDequeues;    // Total messages dequeued
    uint64_t  byteTxEnqueues;       // Transactional messages enqueued
    uint64_t  byteTxDequeues;       // Transactional messages dequeued
    uint64_t  bytePersistEnqueues;  // Persistent messages enqueued
    uint64_t  bytePersistDequeues;  // Persistent messages dequeued

    uint32_t  byteDepth;            // Current size of queue in bytes
    uint32_t  byteDepthLow;         // Low-water mark this interval
    uint32_t  byteDepthHigh;        // High-water mark this interval
    
    uint64_t  enqueueTxStarts;      // Total enqueue transactions started 
    uint64_t  enqueueTxCommits;     // Total enqueue transactions committed
    uint64_t  enqueueTxRejects;     // Total enqueue transactions rejected
    
    uint32_t  enqueueTxCount;       // Current pending enqueue transactions
    uint32_t  enqueueTxCountLow;    // Low water mark this interval
    uint32_t  enqueueTxCountHigh;   // High water mark this interval
    
    uint64_t  dequeueTxStarts;      // Total dequeue transactions started 
    uint64_t  dequeueTxCommits;     // Total dequeue transactions committed
    uint64_t  dequeueTxRejects;     // Total dequeue transactions rejected
    
    uint32_t  dequeueTxCount;       // Current pending dequeue transactions
    uint32_t  dequeueTxCountLow;    // Low water mark this interval
    uint32_t  dequeueTxCountHigh;   // High water mark this interval
    
    uint32_t  consumers;            // Current consumers on queue
    uint32_t  consumersLow;         // Low water mark this interval
    uint32_t  consumersHigh;        // High water mark this interval

    uint16_t    getObjectType        (void) { return OBJECT_QUEUE; }
    std::string getObjectName        (void) { return "queue"; }
    void        writeSchema          (qpid::framing::Buffer& buf);
    void        writeConfig          (qpid::framing::Buffer& buf);
    void        writeInstrumentation (qpid::framing::Buffer& buf);
    bool        getSchemaNeeded      (void) { return schemaNeeded; }
    void        setSchemaNeeded      (void) { schemaNeeded = true; }
    void        doMethod             (std::string            /*methodName*/,
                                      qpid::framing::Buffer& /*inBuf*/,
                                      qpid::framing::Buffer& /*outBuf*/) {}

    inline void adjustQueueHiLo (void){
        if (msgDepth > msgDepthHigh) msgDepthHigh = msgDepth;
        if (msgDepth < msgDepthLow)  msgDepthLow  = msgDepth;

        if (byteDepth > byteDepthHigh) byteDepthHigh = byteDepth;
        if (byteDepth < byteDepthLow)  byteDepthLow  = byteDepth;
        instChanged = true;
    }
    
    inline void adjustTxHiLo (void){
        if (enqueueTxCount > enqueueTxCountHigh) enqueueTxCountHigh = enqueueTxCount;
        if (enqueueTxCount < enqueueTxCountLow)  enqueueTxCountLow  = enqueueTxCount;
        if (dequeueTxCount > dequeueTxCountHigh) dequeueTxCountHigh = dequeueTxCount;
        if (dequeueTxCount < dequeueTxCountLow)  dequeueTxCountLow  = dequeueTxCount;
        instChanged = true;
    }
    
    inline void adjustConsumerHiLo (void){
        if (consumers > consumersHigh) consumersHigh = consumers;
        if (consumers < consumersLow)  consumersLow  = consumers;
        instChanged = true;
    }

  public:

    typedef boost::shared_ptr<Queue> shared_ptr;

    Queue (Manageable* coreObject, Manageable* parentObject,
           const std::string& name, bool durable, bool autoDelete);
    ~Queue (void);

    // The following mask contents are used to describe enqueued or dequeued
    // messages when counting statistics.

    inline void enqueue (uint64_t bytes, uint32_t attrMask = 0){
        msgTotalEnqueues++;
        byteTotalEnqueues += bytes;
        
        if (attrMask & MSG_MASK_TX){
            msgTxEnqueues++;
            byteTxEnqueues += bytes;
        }
        
        if (attrMask & MSG_MASK_PERSIST){
            msgPersistEnqueues++;
            bytePersistEnqueues += bytes;
        }

        msgDepth++;
        byteDepth += bytes;
        adjustQueueHiLo ();
    }
    
    inline void dequeue (uint64_t bytes, uint32_t attrMask = 0){
        msgTotalDequeues++;
        byteTotalDequeues += bytes;

        if (attrMask & MSG_MASK_TX){
            msgTxDequeues++;
            byteTxDequeues += bytes;
        }
        
        if (attrMask & MSG_MASK_PERSIST){
            msgPersistDequeues++;
            bytePersistDequeues += bytes;
        }

        msgDepth--;
        byteDepth -= bytes;
        adjustQueueHiLo ();
    }
    
    inline void incConsumers (void){
        consumers++;
        adjustConsumerHiLo ();
    }
    
    inline void decConsumers (void){
        consumers--;
        adjustConsumerHiLo ();
    }
};

}}
            


#endif  /*!_ManagementQueue_*/
