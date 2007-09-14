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
#include <boost/shared_ptr.hpp>
#include "Persistable.h"
#include "qpid/framing/amqp_types.h"
#include "qpid/sys/Monitor.h"

namespace qpid {
namespace broker {

/**
 * The interface messages must expose to the MessageStore in order to
 * be persistable.
 */
class PersistableMessage : public Persistable
{
    sys::Monitor asyncEnqueueLock;
    sys::Monitor asyncDequeueLock;

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

public:
    typedef boost::shared_ptr<PersistableMessage> shared_ptr;

    /**
     * @returns the size of the headers when encoded
     */
    virtual uint32_t encodedHeaderSize() const = 0;

    virtual ~PersistableMessage() {};

    PersistableMessage(): 
    	asyncEnqueueCounter(0), 
    	asyncDequeueCounter(0) 
	{}
    
    inline void waitForEnqueueComplete() {
        sys::ScopedLock<sys::Monitor> l(asyncEnqueueLock);
        while (asyncEnqueueCounter > 0) {
            asyncEnqueueLock.wait();
        }
    }

    inline bool isEnqueueComplete() {
        sys::ScopedLock<sys::Monitor> l(asyncEnqueueLock);
        return asyncEnqueueCounter == 0;
    }

    inline void enqueueComplete() {
        sys::ScopedLock<sys::Monitor> l(asyncEnqueueLock);
        if (asyncEnqueueCounter > 0) {
            if (--asyncEnqueueCounter == 0) {
                asyncEnqueueLock.notify();
            }
        }
    }

    inline void enqueueAsync() { 
        sys::ScopedLock<sys::Monitor> l(asyncEnqueueLock);
        asyncEnqueueCounter++; 
    }

    inline bool isDequeueComplete() { 
        sys::ScopedLock<sys::Monitor> l(asyncDequeueLock);
        return asyncDequeueCounter == 0;
    }
    
    inline void dequeueComplete() { 
        sys::ScopedLock<sys::Monitor> l(asyncDequeueLock);
        if (asyncDequeueCounter > 0) {
            if (--asyncDequeueCounter == 0) {
                asyncDequeueLock.notify();
            }
        }
    }

    inline void waitForDequeueComplete() {
        sys::ScopedLock<sys::Monitor> l(asyncDequeueLock);
        while (asyncDequeueCounter > 0) {
            asyncDequeueLock.wait();
        }
    }

    inline void dequeueAsync() { 
        sys::ScopedLock<sys::Monitor> l(asyncDequeueLock);
        asyncDequeueCounter++; 
    }

    
};

}}


#endif
