#ifndef _broker_Queue_h
#define _broker_Queue_h

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

#include "qpid/broker/BrokerImportExport.h"
#include "qpid/broker/OwnershipToken.h"
#include "qpid/broker/Consumer.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/Messages.h"
#include "qpid/broker/PersistableQueue.h"
#include "qpid/broker/QueuePolicy.h"
#include "qpid/broker/QueueBindings.h"
#include "qpid/broker/QueueListeners.h"
#include "qpid/broker/QueueObserver.h"
#include "qpid/broker/RateTracker.h"

#include "qpid/framing/FieldTable.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Timer.h"
#include "qpid/management/Manageable.h"
#include "qmf/org/apache/qpid/broker/Queue.h"
#include "qpid/framing/amqp_types.h"

#include <boost/shared_ptr.hpp>
#include <boost/intrusive_ptr.hpp>
#include <boost/enable_shared_from_this.hpp>

#include <list>
#include <vector>
#include <memory>
#include <deque>
#include <set>
#include <algorithm>

namespace qpid {
namespace broker {
class Broker;
class MessageStore;
class QueueEvents;
class QueueRegistry;
class TransactionContext;
class Exchange;

/**
 * The brokers representation of an amqp queue. Messages are
 * delivered to a queue from where they can be dispatched to
 * registered consumers or be stored until dequeued or until one
 * or more consumers registers.
 */
class Queue : public boost::enable_shared_from_this<Queue>,
              public PersistableQueue, public management::Manageable {

    struct UsageBarrier
    {
        Queue& parent;
        uint count;
                
        UsageBarrier(Queue&);
        bool acquire();
        void release();
        void destroy();
    };
            
    struct ScopedUse
    {
        UsageBarrier& barrier;
        const bool acquired;
        ScopedUse(UsageBarrier& b) : barrier(b), acquired(barrier.acquire()) {}
        ~ScopedUse() { if (acquired) barrier.release(); }
    };
            
    typedef std::set< boost::shared_ptr<QueueObserver> > Observers;
    enum ConsumeCode {NO_MESSAGES=0, CANT_CONSUME=1, CONSUMED=2};


    const std::string name;
    const bool autodelete;
    MessageStore* store;
    const OwnershipToken* owner;
    uint32_t consumerCount;
    OwnershipToken* exclusive;
    bool noLocal;
    bool persistLastNode;
    bool inLastNodeFailure;
    std::string traceId;
    std::vector<std::string> traceExclude;
    QueueListeners listeners;
    std::auto_ptr<Messages> messages;
    std::deque<QueuedMessage> pendingDequeues;//used to avoid dequeuing during recovery
    mutable qpid::sys::Mutex consumerLock;
    mutable qpid::sys::Monitor messageLock;
    mutable qpid::sys::Mutex ownershipLock;
    mutable uint64_t persistenceId;
    framing::FieldTable settings;
    std::auto_ptr<QueuePolicy> policy;
    bool policyExceeded;
    QueueBindings bindings;
    std::string alternateExchangeName;
    boost::shared_ptr<Exchange> alternateExchange;
    framing::SequenceNumber sequence;
    qmf::org::apache::qpid::broker::Queue* mgmtObject;
    RateTracker dequeueTracker;
    int eventMode;
    Observers observers;
    bool insertSeqNo;
    std::string seqNoKey;
    Broker* broker;
    bool deleted;
    UsageBarrier barrier;
    int autoDeleteTimeout;
    boost::intrusive_ptr<qpid::sys::TimerTask> autoDeleteTask;

    void push(boost::intrusive_ptr<Message>& msg, bool isRecovery=false);
    void setPolicy(std::auto_ptr<QueuePolicy> policy);
    bool seek(QueuedMessage& msg, Consumer::shared_ptr position);
    bool getNextMessage(QueuedMessage& msg, Consumer::shared_ptr c);
    ConsumeCode consumeNextMessage(QueuedMessage& msg, Consumer::shared_ptr c);
    bool browseNextMessage(QueuedMessage& msg, Consumer::shared_ptr c);
    void notifyListener();

    void removeListener(Consumer::shared_ptr);

    bool isExcluded(boost::intrusive_ptr<Message>& msg);

    void enqueued(const QueuedMessage& msg);
    void dequeued(const QueuedMessage& msg);
    void pop();
    void popAndDequeue();
    QueuedMessage getFront();
    void forcePersistent(QueuedMessage& msg);
    int getEventMode();
    void configureImpl(const qpid::framing::FieldTable& settings);

    inline void mgntEnqStats(const boost::intrusive_ptr<Message>& msg)
    {
        if (mgmtObject != 0) {
            mgmtObject->inc_msgTotalEnqueues ();
            mgmtObject->inc_byteTotalEnqueues (msg->contentSize ());
            if (msg->isPersistent ()) {
                mgmtObject->inc_msgPersistEnqueues ();
                mgmtObject->inc_bytePersistEnqueues (msg->contentSize ());
            }
        }
    }
    inline void mgntDeqStats(const boost::intrusive_ptr<Message>& msg)
    {
        if (mgmtObject != 0){
            mgmtObject->inc_msgTotalDequeues  ();
            mgmtObject->inc_byteTotalDequeues (msg->contentSize());
            if (msg->isPersistent ()){
                mgmtObject->inc_msgPersistDequeues ();
                mgmtObject->inc_bytePersistDequeues (msg->contentSize());
            }
        }
    }

    void checkNotDeleted();
    void notifyDeleted();

  public:

    typedef boost::shared_ptr<Queue> shared_ptr;

    typedef std::vector<shared_ptr> vector;

    QPID_BROKER_EXTERN Queue(const std::string& name,
                             bool autodelete = false, 
                             MessageStore* const store = 0, 
                             const OwnershipToken* const owner = 0,
                             management::Manageable* parent = 0,
                             Broker* broker = 0);
    QPID_BROKER_EXTERN ~Queue();

    QPID_BROKER_EXTERN bool dispatch(Consumer::shared_ptr);

    /**
     * Used to configure a new queue and create a persistent record
     * for it in store if required.
     */
    QPID_BROKER_EXTERN void create(const qpid::framing::FieldTable& settings);

    /**
     * Used to reconfigure a recovered queue (does not create
     * persistent record in store).
     */
    QPID_BROKER_EXTERN void configure(const qpid::framing::FieldTable& settings);
    void destroyed();
    QPID_BROKER_EXTERN void bound(const std::string& exchange,
                                  const std::string& key,
                                  const qpid::framing::FieldTable& args);
    //TODO: get unbind out of the public interface; only there for purposes of one unit test
    QPID_BROKER_EXTERN void unbind(ExchangeRegistry& exchanges);
    /**
     * Bind self to specified exchange, and record that binding for unbinding on delete.
     */
    bool bind(boost::shared_ptr<Exchange> exchange, const std::string& key,
              const qpid::framing::FieldTable& arguments=qpid::framing::FieldTable());

    QPID_BROKER_EXTERN bool acquire(const QueuedMessage& msg);
    QPID_BROKER_EXTERN bool acquireMessageAt(const qpid::framing::SequenceNumber& position, QueuedMessage& message);

    /**
     * Delivers a message to the queue. Will record it as
     * enqueued if persistent then process it.
     */
    QPID_BROKER_EXTERN void deliver(boost::intrusive_ptr<Message> msg);
    /**
     * Dispatches the messages immediately to a consumer if
     * one is available or stores it for later if not.
     */
    QPID_BROKER_EXTERN void process(boost::intrusive_ptr<Message>& msg);
    /**
     * Returns a message to the in-memory queue (due to lack
     * of acknowledegement from a receiver). If a consumer is
     * available it will be dispatched immediately, else it
     * will be returned to the front of the queue.
     */
    QPID_BROKER_EXTERN void requeue(const QueuedMessage& msg);
    /**
     * Used during recovery to add stored messages back to the queue
     */
    QPID_BROKER_EXTERN void recover(boost::intrusive_ptr<Message>& msg);

    QPID_BROKER_EXTERN void consume(Consumer::shared_ptr c,
                                    bool exclusive = false);
    QPID_BROKER_EXTERN void cancel(Consumer::shared_ptr c);

    uint32_t purge(const uint32_t purge_request=0, boost::shared_ptr<Exchange> dest=boost::shared_ptr<Exchange>()); //defaults to all messages 
    QPID_BROKER_EXTERN void purgeExpired();

    //move qty # of messages to destination Queue destq
    uint32_t move(const Queue::shared_ptr destq, uint32_t qty); 

    QPID_BROKER_EXTERN uint32_t getMessageCount() const;
    QPID_BROKER_EXTERN uint32_t getEnqueueCompleteMessageCount() const;
    QPID_BROKER_EXTERN uint32_t getConsumerCount() const;
    inline const std::string& getName() const { return name; }
    bool isExclusiveOwner(const OwnershipToken* const o) const;
    void releaseExclusiveOwnership();
    bool setExclusiveOwner(const OwnershipToken* const o);
    bool hasExclusiveConsumer() const;
    bool hasExclusiveOwner() const;
    inline bool isDurable() const { return store != 0; }
    inline const framing::FieldTable& getSettings() const { return settings; }
    inline bool isAutoDelete() const { return autodelete; }
    bool canAutoDelete() const;
    const QueueBindings& getBindings() const { return bindings; }

    /**
     * used to take messages from in memory and flush down to disk.
     */
    QPID_BROKER_EXTERN void setLastNodeFailure();
    QPID_BROKER_EXTERN void clearLastNodeFailure();

    bool enqueue(TransactionContext* ctxt, boost::intrusive_ptr<Message>& msg, bool suppressPolicyCheck = false);
    void enqueueAborted(boost::intrusive_ptr<Message> msg);
    /**
     * dequeue from store (only done once messages is acknowledged).  Dequeue
     * -may- complete asynchronously. This method returns 'false' if the
     * dequeue has completed, else 'true' if the dequeue is asynchronous.  If
     * the caller is interested in receiving notification when the asynchronous
     * dequeue completes, it may pass a pointer to a factory functor that
     * returns a shareable DequeueDoneCallback object.  If the dequeue is
     * completed synchronously, this pointer is ignored.  If the dequeue will
     * complete asynchronously, the factory is called to obtain a
     * DequeueDoneCallback.  When the dequeue completes, the
     * DequeueDoneCallback is invoked. The callback should be prepared to
     * execute on any thread.
     */
    class DequeueDoneCallback
    {
    public:
        virtual void operator()() = 0;
    };
    typedef boost::function<boost::shared_ptr<DequeueDoneCallback>()> DequeueDoneCallbackFactory;
    QPID_BROKER_EXTERN bool dequeue(TransactionContext* ctxt, const QueuedMessage& msg,
                                    DequeueDoneCallbackFactory *factory = 0);

    /** invoked by store to signal dequeue() has completed */
    QPID_BROKER_EXTERN void dequeueComplete(const boost::intrusive_ptr<PersistableMessage>& msg);

    /**
     * Inform the queue that a previous transactional dequeue
     * committed.
     */
    void dequeueCommitted(const QueuedMessage& msg);

    /**
     * Inform queue of messages that were enqueued, have since
     * been acquired but not yet accepted or released (and
     * thus are still logically on the queue) - used in
     * clustered broker.  
     */ 
    void updateEnqueued(const QueuedMessage& msg);

    /**
     * Test whether the specified message (identified by its
     * sequence/position), is still enqueued (note this
     * doesn't mean it is available for delivery as it may
     * have been delievered to a subscriber who has not yet
     * accepted it).
     */
    bool isEnqueued(const QueuedMessage& msg);
            
    /**
     * Gets the next available message 
     */
    QPID_BROKER_EXTERN QueuedMessage get();

    /** Get the message at position pos */
    QPID_BROKER_EXTERN QueuedMessage find(framing::SequenceNumber pos) const;

    const QueuePolicy* getPolicy();

    void setAlternateExchange(boost::shared_ptr<Exchange> exchange);
    boost::shared_ptr<Exchange> getAlternateExchange();
    bool isLocal(boost::intrusive_ptr<Message>& msg);

    //PersistableQueue support:
    uint64_t getPersistenceId() const;
    void setPersistenceId(uint64_t persistenceId) const;
    void encode(framing::Buffer& buffer) const;
    uint32_t encodedSize() const;

    /**
     * Restores a queue from encoded data (used in recovery)
     *
     * Note: restored queue will be neither auto-deleted or have an
     * exclusive owner
     */
    static Queue::shared_ptr restore(QueueRegistry& queues, framing::Buffer& buffer);
    static void tryAutoDelete(Broker& broker, Queue::shared_ptr);

    virtual void setExternalQueueStore(const boost::shared_ptr<ExternalQueueStore>& inst);

    // Manageable entry points
    management::ManagementObject* GetManagementObject (void) const;
    management::Manageable::status_t
    ManagementMethod (uint32_t methodId, management::Args& args, std::string& text);

    /** Apply f to each Message on the queue. */
    template <class F> void eachMessage(F f) {
        sys::Mutex::ScopedLock l(messageLock);
        messages->foreach(f);
    }

    /** Apply f to each QueueBinding on the queue */
    template <class F> void eachBinding(F f) {
        bindings.eachBinding(f);
    }

    /** Apply f to each Observer on the queue */
    template <class F> void eachObserver(F f) {
        std::for_each<Observers::iterator, F>(observers.begin(), observers.end(), f);
    }

    /** Set the position sequence number  for the next message on the queue.
     * Must be >= the current sequence number.
     * Used by cluster to replicate queues.
     */
    QPID_BROKER_EXTERN void setPosition(framing::SequenceNumber pos);
    /** return current position sequence number for the next message on the queue.
     */
    QPID_BROKER_EXTERN framing::SequenceNumber getPosition();
    void addObserver(boost::shared_ptr<QueueObserver>);
    QPID_BROKER_EXTERN void insertSequenceNumbers(const std::string& key);
    /**
     * Notify queue that recovery has completed.
     */
    void recoveryComplete(ExchangeRegistry& exchanges);

    // For cluster update
    QueueListeners& getListeners();
    Messages& getMessages();
    const Messages& getMessages() const;

    /**
     * Reserve space in policy for an enqueued message that
     * has been recovered in the prepared state (dtx only)
     */
    void recoverPrepared(boost::intrusive_ptr<Message>& msg);

    void flush();

    const Broker* getBroker();

 private:
    std::map< PersistableMessage *, boost::shared_ptr<DequeueDoneCallback> > pendingDequeueCallbacks;
};
}
}


#endif  /*!_broker_Queue_h*/
