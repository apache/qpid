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
#ifndef _QueuePolicy_
#define _QueuePolicy_

#include <deque>
#include <iostream>
#include <memory>
#include "QueuedMessage.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/sys/AtomicValue.h"
#include "qpid/sys/Mutex.h"

namespace qpid {
namespace broker {

class QueuePolicy
{
    static uint64_t defaultMaxSize;

    uint32_t maxCount;
    uint64_t maxSize;
    const std::string type;
    qpid::sys::AtomicValue<uint32_t> count;
    qpid::sys::AtomicValue<uint64_t> size;
    bool policyExceeded;
            
    static int getInt(const qpid::framing::FieldTable& settings, const std::string& key, int defaultValue);
    static std::string getType(const qpid::framing::FieldTable& settings);

  public:
    static const std::string maxCountKey;
    static const std::string maxSizeKey;
    static const std::string typeKey;
    static const std::string REJECT;
    static const std::string FLOW_TO_DISK;
    static const std::string RING;
    static const std::string RING_STRICT;            

    virtual ~QueuePolicy() {}
    void tryEnqueue(const QueuedMessage&);
    virtual void dequeued(const QueuedMessage&);
    virtual bool isEnqueued(const QueuedMessage&);
    virtual bool checkLimit(const QueuedMessage&);
    void update(qpid::framing::FieldTable& settings);
    uint32_t getMaxCount() const { return maxCount; }
    uint64_t getMaxSize() const { return maxSize; }           
    void encode(framing::Buffer& buffer) const;
    void decode ( framing::Buffer& buffer );
    uint32_t encodedSize() const;


    static std::auto_ptr<QueuePolicy> createQueuePolicy(const qpid::framing::FieldTable& settings);
    static std::auto_ptr<QueuePolicy> createQueuePolicy(uint32_t maxCount, uint64_t maxSize, const std::string& type = REJECT);
    static void setDefaultMaxSize(uint64_t);
    friend std::ostream& operator<<(std::ostream&, const QueuePolicy&);
  protected:
    QueuePolicy(uint32_t maxCount, uint64_t maxSize, const std::string& type = REJECT);

    virtual void enqueued(const QueuedMessage&);
    void enqueued(uint64_t size);
    void dequeued(uint64_t size);
};


class FlowToDiskPolicy : public QueuePolicy
{
  public:
    FlowToDiskPolicy(uint32_t maxCount, uint64_t maxSize);
    bool checkLimit(const QueuedMessage&);
};

class RingQueuePolicy : public QueuePolicy
{
  public:
    RingQueuePolicy(uint32_t maxCount, uint64_t maxSize, const std::string& type = RING);
    void enqueued(const QueuedMessage&);
    void dequeued(const QueuedMessage&);
    bool isEnqueued(const QueuedMessage&);
    bool checkLimit(const QueuedMessage&);
  private:
    typedef std::deque<QueuedMessage> Messages;
    qpid::sys::Mutex lock;
    Messages queue;
    const bool strict;
};

}}


#endif
