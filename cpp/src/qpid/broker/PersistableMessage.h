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
#include <map>
#include <boost/intrusive_ptr.hpp>
#include "qpid/broker/BrokerImportExport.h"
#include "qpid/broker/Persistable.h"
#include "qpid/framing/amqp_types.h"
#include "qpid/framing/amqp_framing.h"
#include "qpid/sys/Mutex.h"
#include "qpid/broker/PersistableQueue.h"
#include "qpid/broker/AsyncCompletion.h"

namespace qpid {
namespace types {
class Variant;
}
namespace broker {

class MessageStore;
class Queue;

/**
 * Base class for persistable messages.
 */
class PersistableMessage : public Persistable
{
    /**
     * "Ingress" messages == messages sent _to_ the broker.
     * Tracks the number of outstanding asynchronous operations that must
     * complete before an inbound message can be considered fully received by the
     * broker.  E.g. all enqueues have completed, the message has been written
     * to store, credit has been replenished, etc. Once all outstanding
     * operations have completed, the transfer of this message from the client
     * may be considered complete.
     */
    boost::intrusive_ptr<AsyncCompletion> ingressCompletion;
    mutable uint64_t persistenceId;

  public:
    virtual ~PersistableMessage();
    PersistableMessage();

    void flush();

    QPID_BROKER_EXTERN void setStore(MessageStore*);

    virtual QPID_BROKER_EXTERN bool isPersistent() const = 0;

    /** track the progress of a message received by the broker - see ingressCompletion above */
    QPID_BROKER_INLINE_EXTERN bool isIngressComplete() { return ingressCompletion->isDone(); }
    QPID_BROKER_INLINE_EXTERN AsyncCompletion& getIngressCompletion() { return *ingressCompletion; }
    QPID_BROKER_INLINE_EXTERN void setIngressCompletion(boost::intrusive_ptr<AsyncCompletion> i) { ingressCompletion = i; }

    QPID_BROKER_INLINE_EXTERN void enqueueStart() { ingressCompletion->startCompleter(); }
    QPID_BROKER_INLINE_EXTERN void enqueueComplete() { ingressCompletion->finishCompleter(); }

    QPID_BROKER_EXTERN void enqueueAsync(PersistableQueue::shared_ptr queue,
                                         MessageStore* _store);


    QPID_BROKER_EXTERN bool isDequeueComplete();
    QPID_BROKER_EXTERN void dequeueComplete();
    QPID_BROKER_EXTERN void dequeueAsync(PersistableQueue::shared_ptr queue,
                                         MessageStore* _store);

    uint64_t getPersistenceId() const { return persistenceId; }
    void setPersistenceId(uint64_t _persistenceId) const { persistenceId = _persistenceId; }


    virtual void decodeHeader(framing::Buffer& buffer) = 0;
    virtual void decodeContent(framing::Buffer& buffer) = 0;
    virtual uint32_t encodedHeaderSize() const = 0;
    virtual boost::intrusive_ptr<PersistableMessage> merge(const std::map<std::string, qpid::types::Variant>& annotations) const = 0;
};

}}


#endif
