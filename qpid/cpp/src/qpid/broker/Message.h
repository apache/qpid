#ifndef _broker_Message_h
#define _broker_Message_h

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

#include "PersistableMessage.h"
#include "MessageAdapter.h"
#include "qpid/framing/amqp_types.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Time.h"
#include "qpid/shared_ptr.h"
#include <boost/function.hpp>
#include <string>
#include <vector>

namespace qpid {
	
namespace framing {
class FieldTable;
class SequenceNumber;
}
	
namespace broker {
class ConnectionToken;
class Exchange;
class ExchangeRegistry;
class MessageStore;
class Queue;

class Message : public PersistableMessage {
public:
    typedef boost::function<void (const boost::intrusive_ptr<Message>&)> MessageCallback;
    
    Message(const framing::SequenceNumber& id = framing::SequenceNumber());
    ~Message();
        
    uint64_t getPersistenceId() const { return persistenceId; }
    void setPersistenceId(uint64_t _persistenceId) const { persistenceId = _persistenceId; }

    bool getRedelivered() const { return redelivered; }
    void redeliver() { redelivered = true; }

    const ConnectionToken* getPublisher() const {  return publisher; }
    void setPublisher(ConnectionToken* p) {  publisher = p; }

    const framing::SequenceNumber& getCommandId() { return frames.getId(); }

    uint64_t contentSize() const;

    std::string getRoutingKey() const;
    const boost::shared_ptr<Exchange> getExchange(ExchangeRegistry&) const;
    std::string getExchangeName() const;
    bool isImmediate() const;
    const framing::FieldTable* getApplicationHeaders() const;
    bool isPersistent();
    bool requiresAccept();
    void setTimestamp();
    bool hasExpired() const;

    framing::FrameSet& getFrames() { return frames; } 
    const framing::FrameSet& getFrames() const { return frames; } 

    template <class T> T* getProperties() {
        qpid::framing::AMQHeaderBody* p = frames.getHeaders();
        return p->get<T>(true);
    }

    template <class T> const T* getProperties() const {
        qpid::framing::AMQHeaderBody* p = frames.getHeaders();
        return p->get<T>(true);
    }

    template <class T> const T* hasProperties() const {
        const qpid::framing::AMQHeaderBody* p = frames.getHeaders();
        return p->get<T>();
    }

    template <class T> const T* getMethod() const {
        return frames.as<T>();
    }

    template <class T> bool isA() const {
        return frames.isA<T>();
    }

    uint32_t getRequiredCredit() const;

    void encode(framing::Buffer& buffer) const;
    void encodeContent(framing::Buffer& buffer) const;

    /**
     * @returns the size of the buffer needed to encode this
     * message in its entirety
     */
    uint32_t encodedSize() const;
    /**
     * @returns the size of the buffer needed to encode the
     * 'header' of this message (not just the header frame,
     * but other meta data e.g.routing key and exchange)
     */
    uint32_t encodedHeaderSize() const;
    uint32_t encodedContentSize() const;

    void decodeHeader(framing::Buffer& buffer);
    void decodeContent(framing::Buffer& buffer);
            
    /**
     * Releases the in-memory content data held by this
     * message. Must pass in a store from which the data can
     * be reloaded.
     */
    void releaseContent(MessageStore* store);
    void destroy();

    bool getContentFrame(const Queue& queue, framing::AMQFrame& frame, uint16_t maxContentSize, uint64_t offset) const;
    void sendContent(const Queue& queue, framing::FrameHandler& out, uint16_t maxFrameSize) const;
    void sendHeader(framing::FrameHandler& out, uint16_t maxFrameSize) const;

    bool isContentLoaded() const;

    bool isExcluded(const std::vector<std::string>& excludes) const;
    void addTraceId(const std::string& id);
	
	void forcePersistent();
    
    boost::intrusive_ptr<Message>& getReplacementMessage(const Queue* qfor) const;
    void setReplacementMessage(boost::intrusive_ptr<Message> msg, const Queue* qfor);

    /** Call cb when enqueue is complete, may call immediately. Holds cb by reference. */
    void setEnqueueCompleteCallback(MessageCallback& cb);
    void resetEnqueueCompleteCallback();

    /** Call cb when dequeue is complete, may call immediately. Holds cb by reference. */
    void setDequeueCompleteCallback(MessageCallback& cb);
    void resetDequeueCompleteCallback();

  private:
    typedef std::map<const Queue*,boost::intrusive_ptr<Message> > Replacement;

    MessageAdapter& getAdapter() const;
    void allEnqueuesComplete();
    void allDequeuesComplete();

    mutable sys::Mutex lock;
    framing::FrameSet frames;
    mutable boost::shared_ptr<Exchange> exchange;
    mutable uint64_t persistenceId;
    bool redelivered;
    bool loaded;
    bool staged;
	bool forcePersistentPolicy; // used to force message as durable, via a broker policy
    ConnectionToken* publisher;
    mutable MessageAdapter* adapter;
    qpid::sys::AbsTime expiration;

    static TransferAdapter TRANSFER;

    mutable Replacement replacement;
    mutable boost::intrusive_ptr<Message> empty;
    MessageCallback* enqueueCallback;
    MessageCallback* dequeueCallback;
};

}}


#endif
