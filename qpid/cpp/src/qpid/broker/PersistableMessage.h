#ifndef _broker_PersistableMessage_h
#define _broker_PersistableMessage_h

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

#include <string>
#include <list>
#include <boost/shared_ptr.hpp>
#include <boost/weak_ptr.hpp>
#include "Persistable.h"
#include "qpid/framing/amqp_types.h"
#include "qpid/sys/Mutex.h"
#include "PersistableQueue.h"

namespace qpid {
namespace broker {

class MessageStore;

/**
 * Base class for persistable messages.
 */
class PersistableMessage : public Persistable
{
    typedef std::list< boost::weak_ptr<PersistableQueue> > syncList;
    sys::Mutex asyncEnqueueLock;
    sys::Mutex asyncDequeueLock;
    sys::Mutex storeLock;
	
    /**
     * Tracks the number of outstanding asynchronous enqueue
     * operations. When the message is enqueued asynchronously the
     * count is incremented; when that enqueue completes it is
     * decremented. Thus when it is 0, there are no outstanding
     * enqueues.
     */
    int asyncEnqueueCounter;

    /**
     * Tracks the number of outstanding asynchronous dequeue
     * operations. When the message is dequeued asynchronously the
     * count is incremented; when that dequeue completes it is
     * decremented. Thus when it is 0, there are no outstanding
     * dequeues.
     */
    int asyncDequeueCounter;

    bool contentReleased;
    syncList synclist;

  protected:
    /** Called when all enqueues are complete for this message. */
    virtual void allEnqueuesComplete() = 0;
    /** Called when all dequeues are complete for this message. */
    virtual void allDequeuesComplete() = 0;

    void setContentReleased();

    MessageStore* store;

  public:
    typedef boost::shared_ptr<PersistableMessage> shared_ptr;

    /**
     * @returns the size of the headers when encoded
     */
    virtual uint32_t encodedHeaderSize() const = 0;

    virtual ~PersistableMessage();

    PersistableMessage();

    void flush();
    
    bool isContentReleased() const;
	
    bool isEnqueueComplete();

    void enqueueComplete();

    void enqueueAsync(PersistableQueue::shared_ptr queue, MessageStore* _store);

    void enqueueAsync();

    bool isDequeueComplete();
    
    void dequeueComplete();

    void dequeueAsync(PersistableQueue::shared_ptr queue, MessageStore* _store);

    void dequeueAsync();
};

}}


#endif
