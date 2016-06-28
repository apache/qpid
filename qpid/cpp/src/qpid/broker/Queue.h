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
#include "qpid/broker/MessageInterceptor.h"
#include "qpid/broker/PersistableQueue.h"
#include "qpid/broker/QueueBindings.h"
#include "qpid/broker/QueueListeners.h"
#include "qpid/broker/QueueObservers.h"
#include "qpid/broker/QueueSettings.h"
#include "qpid/broker/TxOp.h"

#include "qpid/framing/FieldTable.h"
#include "qpid/framing/SequenceNumber.h"
#include "qpid/sys/AtomicCount.h"
#include "qpid/sys/AtomicValue.h"
#include "qpid/sys/Monitor.h"
#include "qpid/management/Manageable.h"
#include "qmf/org/apache/qpid/broker/Queue.h"
#include "qmf/org/apache/qpid/broker/Broker.h"
#include "qpid/framing/amqp_types.h"

#include <boost/scoped_ptr.hpp>
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
namespace sys {
class TimerTask;
}
namespace broker {
class Broker;
class Exchange;
class MessageStore;
class QueueDepth;
class QueueEvents;
class QueueRegistry;
class QueueFactory;
class Selector;
class TransactionContext;
class TxBuffer;
class MessageDistributor;

/**
 * The brokers representation of an amqp queue. Messages are
 * delivered to a queue from where they can be dispatched to
 * registered consumers or be stored until dequeued or until one
 * or more consumers registers.
 */
class Queue : public boost::enable_shared_from_this<Queue>,
              public PersistableQueue, public management::Manageable {
  public:
    typedef boost::function1<bool, const Message&> MessagePredicate;

    typedef boost::shared_ptr<Queue> shared_ptr;

  protected:
  friend struct AutoDeleteTask;
    struct UsageBarrier
    {
        Queue& parent;
        uint count;
        qpid::sys::Monitor usageLock;

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

    class TxPublish : public TxOp
    {
        Message message;
        boost::shared_ptr<Queue> queue;
        bool prepared;
      public:
        TxPublish(const Message&,boost::shared_ptr<Queue>);
        bool prepare(TransactionContext* ctxt) throw();
        void commit() throw();
        void rollback() throw();
        void callObserver(const boost::shared_ptr<TransactionObserver>&);
    };

    /**
     * This class tracks whether a queue is in use and how it is being
     * used.
     */
    class QueueUsers
    {
      public:
        QueueUsers();
        void addConsumer();
        void addBrowser();
        void addOther();
        void removeConsumer();
        void removeBrowser();
        void addLifecycleController();
        void removeLifecycleController();
        void removeOther();
        bool isUsed() const;
        uint32_t getSubscriberCount() const;
        bool hasConsumers() const;
        bool isInUseByController() const;
      private:
        uint32_t consumers;
        uint32_t browsers;
        uint32_t others;
        bool controller;
    };

    /**
     * This class is used to check - and if necessary trigger -
     * autodeletion when removing messages, as this could cause the
     * queue to become empty (which is one possible trigger for
     * autodeletion).
     *
     * The constructor and descructor should be called outside the
     * message lock. The check method should be called while holding
     * the message lock.
     */
    class ScopedAutoDelete
    {
      public:
        ScopedAutoDelete(Queue& q);
        void check(const sys::Mutex::ScopedLock& lock);
        ~ScopedAutoDelete();
      private:
        Queue& queue;
        bool eligible;
    };

    enum ConsumeCode {NO_MESSAGES=0, CANT_CONSUME=1, CONSUMED=2};
    typedef boost::function1<void, Message&> MessageFunctor;

    const std::string name;
    MessageStore* store;
    const OwnershipToken* owner;
    QueueUsers users;
    OwnershipToken* exclusive;
    std::vector<std::string> traceExclude;
    QueueListeners listeners;
    std::auto_ptr<Messages> messages;
    std::vector<Message> pendingDequeues;
    /** messageLock is used to keep the Queue's state consistent while processing message
     * events, such as message dispatch, enqueue, acquire, and dequeue.  It must be held
     * while updating certain members in order to keep these members consistent with
     * each other:
     *     o  messages
     *     o  sequence
     *     o  listeners
     *     o  allocator
     *     o  observeXXX() methods
     *     o  observers
     *     o  pendingDequeues  (TBD: move under separate lock)
     *     o  exclusive OwnershipToken (TBD: move under separate lock)
     *     o  consumerCount  (TBD: move under separate lock)
     *     o  Queue::UsageBarrier (TBD: move under separate lock)
     */
    mutable qpid::sys::Mutex messageLock;
    mutable uint64_t persistenceId;
    QueueSettings settings;
    qpid::framing::FieldTable encodableSettings;
    QueueDepth current;
    QueueBindings bindings;
    std::string alternateExchangeName;
    std::string userId; // queue owner for ACL quota purposes
    boost::shared_ptr<Exchange> alternateExchange;
    framing::SequenceNumber sequence;
    qmf::org::apache::qpid::broker::Queue::shared_ptr mgmtObject;
    qmf::org::apache::qpid::broker::Broker::shared_ptr brokerMgmtObject;
    sys::AtomicValue<uint32_t> dequeueSincePurge; // Count dequeues since last purge.
    int eventMode;
    QueueObservers observers;
    MessageInterceptors interceptors;
    std::string seqNoKey;
    Broker* broker;
    bool deleted;
    UsageBarrier barrier;
    boost::intrusive_ptr<qpid::sys::TimerTask> autoDeleteTask;
    boost::shared_ptr<MessageDistributor> allocator;
    boost::scoped_ptr<Selector> selector;
    qpid::sys::AtomicCount version;

    // Redirect source and target refer to each other. Only one is source.
    Queue::shared_ptr redirectPeer;
    bool redirectSource;

    bool checkAutoDelete(const qpid::sys::Mutex::ScopedLock&) const;
    bool isUnused(const qpid::sys::Mutex::ScopedLock&) const;
    bool isEmpty(const qpid::sys::Mutex::ScopedLock&) const;
    virtual void push(Message& msg, bool isRecovery=false);
    bool accept(const Message&);
    void process(Message& msg);
    bool enqueue(TransactionContext* ctxt, Message& msg);
    bool getNextMessage(Message& msg, Consumer::shared_ptr& c);

    void removeListener(Consumer::shared_ptr);

    bool isExcluded(const Message& msg);

    /** update queue observers, stats, policy, etc when the messages' state changes. Lock
     * must be held by caller */
    void observeEnqueue(const Message& msg, const sys::Mutex::ScopedLock& lock);
    void observeAcquire(const Message& msg, const sys::Mutex::ScopedLock& lock);
    void observeRequeue(const Message& msg, const sys::Mutex::ScopedLock& lock);
    void observeDequeue(const Message& msg, const sys::Mutex::ScopedLock& lock, ScopedAutoDelete*);
    void observeConsumerAdd( const Consumer&, const sys::Mutex::ScopedLock& lock);
    void observeConsumerRemove( const Consumer&, const sys::Mutex::ScopedLock& lock);

    bool acquire(const qpid::framing::SequenceNumber& position, Message& msg,
                 const qpid::sys::Mutex::ScopedLock& locker);

    int getEventMode();
    void dequeueFromStore(boost::intrusive_ptr<PersistableMessage>);
    void abandoned(const Message& message);
    bool checkNotDeleted(const Consumer::shared_ptr&);
    void notifyDeleted();

    /** Remove messages from the queue:
     *@param maxCount Maximum number of messages to remove, 0 means unlimited.
     *@param p Only remove messages for which p(msg) is true.
     *@param f Call f on each message that is removed.
     *@param st Use a cursor of this SubscriptionType to iterate messages to remove.
     *@param triggerAutoDelete If true removing messages may trigger aut-delete.
     *@param maxTests Max number of messages to test for removal, 0 means unlimited.
     *@return Number of messages removed.
     */
    uint32_t remove(uint32_t maxCount,
                    MessagePredicate p, MessageFunctor f,
                    SubscriptionType st,
                    bool triggerAutoDelete,
                    uint32_t maxTests=0);

    virtual bool checkDepth(const QueueDepth& increment, const Message&);
    void tryAutoDelete(long expectedVersion);
  public:

    typedef std::vector<shared_ptr> vector;

    QPID_BROKER_EXTERN Queue(const std::string& name,
                             const QueueSettings& settings = QueueSettings(),
                             MessageStore* const store = 0,
                             management::Manageable* parent = 0,
                             Broker* broker = 0);
    QPID_BROKER_EXTERN virtual ~Queue();

    /** allow the Consumer to consume or browse the next available message */
    QPID_BROKER_EXTERN bool dispatch(Consumer::shared_ptr);

    /** allow the Consumer to acquire a message that it has browsed.
     * @param msg - message to be acquired.
     * @return false if message is no longer available for acquire.
     */
    QPID_BROKER_EXTERN bool acquire(const QueueCursor& msg, const std::string& consumer);

    /**
     * Used to create a persistent record for the queue in store if required.
     */
    QPID_BROKER_EXTERN void create();

    QPID_BROKER_EXTERN void bound(const std::string& exchange,
                                  const std::string& key,
                                  const qpid::framing::FieldTable& args);
    //TODO: get unbind out of the public interface; only there for purposes of one unit test
    QPID_BROKER_EXTERN void unbind(ExchangeRegistry& exchanges);
    /**
     * Bind self to specified exchange, and record that binding for unbinding on delete.
     */
    QPID_BROKER_EXTERN bool bind(
        boost::shared_ptr<Exchange> exchange, const std::string& key,
        const qpid::framing::FieldTable& arguments=qpid::framing::FieldTable());

    /**
     * Removes (and dequeues) a message by its sequence number (used
     * for some broker features, e.g. queue replication)
     *
     * @param position the sequence number of the message to be dequeued.
     * @return true if the message is dequeued.
     */
    QPID_BROKER_EXTERN bool dequeueMessageAt(const qpid::framing::SequenceNumber& position);

    /**
     * Delivers a message to the queue or to overflow partner.
     */
    QPID_BROKER_EXTERN void deliver(Message, TxBuffer* = 0);
    /**
     * Delivers a message to the queue. Will record it as
     * enqueued if persistent then process it.
     */
  private:
    QPID_BROKER_EXTERN void deliverTo(Message, TxBuffer* = 0);
  public:
    /**
     * Merges message annotations for an in-memory message as a result of
     * a modified disposition outcome
     */
    QPID_BROKER_EXTERN void mergeMessageAnnotations(const QueueCursor& msg,
                                                    const qpid::types::Variant::Map& annotations);

    /**
     * Returns a message to the in-memory queue (due to lack
     * of acknowledegement from a receiver). If a consumer is
     * available it will be dispatched immediately, else it
     * will be returned to the front of the queue.
     */
    QPID_BROKER_EXTERN void release(const QueueCursor& msg, bool markRedelivered=true);
    QPID_BROKER_EXTERN void reject(const QueueCursor& msg);

    QPID_BROKER_EXTERN bool seek(QueueCursor&, MessagePredicate);
    QPID_BROKER_EXTERN bool seek(QueueCursor&, MessagePredicate, qpid::framing::SequenceNumber start);
    QPID_BROKER_EXTERN bool seek(QueueCursor&, qpid::framing::SequenceNumber start);
    /**
     * Used during recovery to add stored messages back to the queue
     */
    QPID_BROKER_EXTERN void recover(Message& msg);

    QPID_BROKER_EXTERN void consume(Consumer::shared_ptr c,
                                    bool exclusive = false,
                                    const framing::FieldTable& arguments = framing::FieldTable(),
                                    const std::string& connectionId=std::string(),
                                    const std::string& userId=std::string());

    QPID_BROKER_EXTERN void cancel(Consumer::shared_ptr c,
                                    const std::string& connectionId=std::string(),
                                    const std::string& userId=std::string());
    /**
     * Used to indicate that the queue is being used in some other
     * context than by a subscriber. The controlling flag should only
     * be set if the mode of use is the one that caused the queue to
     * be created.
     */
    QPID_BROKER_EXTERN void markInUse(bool controlling=false);
    QPID_BROKER_EXTERN void releaseFromUse(bool controlling=false, bool doDelete=true);

    QPID_BROKER_EXTERN uint32_t purge(const uint32_t purge_request=0,  //defaults to all messages
                   boost::shared_ptr<Exchange> dest=boost::shared_ptr<Exchange>(),
                   const ::qpid::types::Variant::Map *filter=0);
    QPID_BROKER_EXTERN void purgeExpired(sys::Duration);

    //move qty # of messages to destination Queue destq
    QPID_BROKER_EXTERN uint32_t move(
        const Queue::shared_ptr destq, uint32_t qty,
        const qpid::types::Variant::Map *filter=0);

    QPID_BROKER_EXTERN uint32_t getMessageCount() const;
    QPID_BROKER_EXTERN uint32_t getConsumerCount() const;
    inline const std::string& getName() const { return name; }
    QPID_BROKER_EXTERN bool isExclusiveOwner(const OwnershipToken* const o) const;
    QPID_BROKER_EXTERN void releaseExclusiveOwnership(bool immediateExpiry=false);
    QPID_BROKER_EXTERN bool setExclusiveOwner(const OwnershipToken* const o);
    QPID_BROKER_EXTERN bool hasExclusiveConsumer() const;
    QPID_BROKER_EXTERN bool hasExclusiveOwner() const;
    inline bool isDurable() const { return store != 0; }
    inline const QueueSettings& getSettings() const { return settings; }
    inline const qpid::framing::FieldTable& getEncodableSettings() const { return encodableSettings; }
    inline bool isAutoDelete() const { return settings.autodelete; }
    inline bool isBrowseOnly() const { return settings.isBrowseOnly; }
    QPID_BROKER_EXTERN bool canAutoDelete() const;
    QPID_BROKER_EXTERN void scheduleAutoDelete(bool immediate=false);
    QPID_BROKER_EXTERN bool isDeleted() const;
    const QueueBindings& getBindings() const { return bindings; }

    /**
     * Dequeue message referenced by cursor. If txn is specified, this will
     * occur only when txn is committed.
     */
    QPID_BROKER_EXTERN void dequeue(const QueueCursor& cursor, TxBuffer* txn);

    /**
     * dequeue from store (only done once messages is acknowledged)
     */
    QPID_BROKER_EXTERN void dequeue(TransactionContext* ctxt, const QueueCursor&);

    /**
     * Inform the queue that a previous transactional dequeue
     * committed.
     */
    void dequeueCommitted(const QueueCursor& msg);

    /** Get the message at position pos, returns true if found and sets msg */
    QPID_BROKER_EXTERN bool find(framing::SequenceNumber pos, Message& msg ) const;

    // Remember the queue's owner so acl quotas can be restored after restart
    void setOwningUser(std::string& _userId);
    void updateAclUserQueueCount();

    QPID_BROKER_EXTERN void setAlternateExchange(boost::shared_ptr<Exchange> exchange);
    QPID_BROKER_EXTERN boost::shared_ptr<Exchange> getAlternateExchange();
    QPID_BROKER_EXTERN bool isLocal(const Message& msg);

    //PersistableQueue support:
    QPID_BROKER_EXTERN uint64_t getPersistenceId() const;
    QPID_BROKER_EXTERN void setPersistenceId(uint64_t persistenceId) const;
    QPID_BROKER_EXTERN void encode(framing::Buffer& buffer) const;
    QPID_BROKER_EXTERN uint32_t encodedSize() const;

    /**
     * Restores a queue from encoded data (used in recovery)
     *
     * Note: restored queue will be neither auto-deleted or have an
     * exclusive owner
     */
    static Queue::shared_ptr restore(QueueRegistry& queues, framing::Buffer& buffer);

    virtual void setExternalQueueStore(ExternalQueueStore* inst);

    // Increment the rejected-by-consumer counter.
    QPID_BROKER_EXTERN void countRejected() const;

    // Manageable entry points
    QPID_BROKER_EXTERN management::ManagementObject::shared_ptr GetManagementObject(void) const;
    management::Manageable::status_t
    QPID_BROKER_EXTERN ManagementMethod (uint32_t methodId, management::Args& args, std::string& text);
    QPID_BROKER_EXTERN void query(::qpid::types::Variant::Map&) const;

    /** Apply f to each Message on the queue. */
    template <class F> void eachMessage(F f) {
        sys::Mutex::ScopedLock l(messageLock);
        messages->foreach(f);
    }

    /** Apply f to each QueueBinding on the queue */
    template <class F> void eachBinding(F f) {
        bindings.eachBinding(f);
    }

    /**
     * Set the sequence number for the back of the queue, the
     * next message enqueued will be pos+1.
     * If pos > getPosition() this creates a gap in the sequence numbers.
     * if pos < getPosition() the back of the queue is reset to pos,
     *
     * The _caller_ must ensure that any messages after pos have been dequeued.
     *
     * Used by HA code for queue replication.
     */
    QPID_BROKER_EXTERN void setPosition(framing::SequenceNumber pos);

    /**
     *@return sequence number for the back of the queue. The next message pushed
     * will be at getPosition()+1
     */
    QPID_BROKER_EXTERN framing::SequenceNumber getPosition();

    /**
     * Set front and back.
     * If the queue is empty then front = back+1 (the first message to
     * consume will be the next message pushed.)
     *
     *@param front = Position of first message to consume.
     *@param back = getPosition(), next message pushed will be getPosition()+1
     *@param type Subscription type to use to determine the front.
     */
    QPID_BROKER_EXTERN void getRange(
        framing::SequenceNumber& front, framing::SequenceNumber& back,
        SubscriptionType type=CONSUMER
    );


    QPID_BROKER_EXTERN void insertSequenceNumbers(const std::string& key);

    QPID_BROKER_EXTERN MessageInterceptors& getMessageInterceptors() { return interceptors; }
    QPID_BROKER_EXTERN QueueObservers& getObservers() { return observers; }

    /**
     * Notify queue that recovery has completed.
     */
    QPID_BROKER_EXTERN void recoveryComplete(ExchangeRegistry& exchanges);

    /**
     * Reserve space in policy for an enqueued message that
     * has been recovered in the prepared state (dtx only)
     */
    QPID_BROKER_EXTERN void recoverPrepared(const Message& msg);
    void enqueueAborted(const Message& msg);
    void enqueueCommited(Message& msg);
    void dequeueAborted(Message& msg);
    void dequeueCommited(const Message& msg);

    QPID_BROKER_EXTERN void flush();

    QPID_BROKER_EXTERN Broker* getBroker();

    uint32_t getDequeueSincePurge() { return dequeueSincePurge.get(); }
    QPID_BROKER_EXTERN void setDequeueSincePurge(uint32_t value);

    /** Add an argument to be included in management messages about this queue. */
    QPID_BROKER_EXTERN void addArgument(const std::string& key, const types::Variant& value);

    /**
     * Atomic Redirect
     */
    QPID_BROKER_EXTERN void setRedirectPeer ( Queue::shared_ptr peer, bool isSrc );
    QPID_BROKER_EXTERN Queue::shared_ptr getRedirectPeer() { return redirectPeer; }
    QPID_BROKER_EXTERN bool isRedirectSource() const { return redirectSource; }
    QPID_BROKER_EXTERN void setMgmtRedirectState( std::string peer, bool enabled, bool isSrc );

    //utility function
    static bool reroute(boost::shared_ptr<Exchange> e, const Message& m);
    static bool isExpired(const std::string& queueName, const Message&, qpid::sys::AbsTime);

  private:
    void destroyed();           // Only called by QueueRegistry::destroy()
  friend class QueueFactory;
  friend class QueueRegistry;
};
}
}


#endif  /*!_broker_Queue_h*/
