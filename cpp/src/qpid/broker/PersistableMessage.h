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
#include "qpid/broker/BrokerImportExport.h"
#include "qpid/broker/Persistable.h"
#include "qpid/framing/amqp_types.h"
#include "qpid/sys/Mutex.h"
#include "qpid/broker/PersistableQueue.h"
#include "qpid/broker/AsyncCompletion.h"

namespace qpid {
namespace broker {

class MessageStore;

/**
 * Base class for persistable messages.
 */
class PersistableMessage : public Persistable
{
    typedef std::list< boost::weak_ptr<PersistableQueue> > syncList;
    sys::Mutex asyncDequeueLock;
    sys::Mutex storeLock;

    /**
     * "Ingress" messages == messages sent _to_ the broker.
     * Tracks the number of outstanding asynchronous operations that must
     * complete before an inbound message can be considered fully received by the
     * broker.  E.g. all enqueues have completed, the message has been written
     * to store, credit has been replenished, etc. Once all outstanding
     * operations have completed, the transfer of this message from the client
     * may be considered complete.
     */
    AsyncCompletion ingressCompletion;

    /**
     * Tracks the number of outstanding asynchronous dequeue
     * operations. When the message is dequeued asynchronously the
     * count is incremented; when that dequeue completes it is
     * decremented. Thus when it is 0, there are no outstanding
     * dequeues.
     */
    int asyncDequeueCounter;

    void dequeueAsync();

    syncList synclist;
    struct ContentReleaseState
    {
        bool blocked;
        bool requested;
        bool released;
        
        ContentReleaseState();
    };
    ContentReleaseState contentReleaseState;

  protected:
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
    
    QPID_BROKER_EXTERN bool isContentReleased() const;

    QPID_BROKER_EXTERN void setStore(MessageStore*);
    void requestContentRelease();
    void blockContentRelease();
    bool checkContentReleasable();
    bool isContentReleaseBlocked();
    bool isContentReleaseRequested();

    virtual QPID_BROKER_EXTERN bool isPersistent() const = 0;

    /** track the progress of a message received by the broker - see ingressCompletion above */
    QPID_BROKER_EXTERN bool isIngressComplete() { return ingressCompletion.isDone(); }
    QPID_BROKER_EXTERN AsyncCompletion& getIngressCompletion() { return ingressCompletion; }

    QPID_BROKER_EXTERN void enqueueStart() { ingressCompletion.startCompleter(); }
    QPID_BROKER_EXTERN void enqueueComplete() { ingressCompletion.finishCompleter(); }

    QPID_BROKER_EXTERN void enqueueAsync(PersistableQueue::shared_ptr queue,
                                         MessageStore* _store);


    QPID_BROKER_EXTERN bool isDequeueComplete();
    
    QPID_BROKER_EXTERN void dequeueComplete();

    QPID_BROKER_EXTERN void dequeueAsync(PersistableQueue::shared_ptr queue,
                                         MessageStore* _store);

    bool isStoredOnQueue(PersistableQueue::shared_ptr queue);
    
    void addToSyncList(PersistableQueue::shared_ptr queue, MessageStore* _store);
};

}}


#endif
