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
#include "OwnershipToken.h"
#include "Consumer.h"
#include "Message.h"
#include "PersistableQueue.h"
#include "QueuePolicy.h"
#include "QueueBindings.h"
#include "QueueListeners.h"
#include "RateTracker.h"

#include "qpid/framing/FieldTable.h"
#include "qpid/sys/Monitor.h"
#include "qpid/management/Manageable.h"
#include "qmf/org/apache/qpid/broker/Queue.h"
#include "qpid/framing/amqp_types.h"

#include <boost/shared_ptr.hpp>
#include <boost/enable_shared_from_this.hpp>
#include <boost/intrusive_ptr.hpp>

#include <list>
#include <vector>
#include <memory>
#include <deque>
#include <algorithm>

namespace qpid {
    namespace broker {
        class Broker;
        class MessageStore;
        class QueueEvents;
        class QueueRegistry;
        class TransactionContext;
        class Exchange;

        using std::string;

        /**
         * The brokers representation of an amqp queue. Messages are
         * delivered to a queue from where they can be dispatched to
         * registered consumers or be stored until dequeued or until one
         * or more consumers registers.
         */
        class Queue : public boost::enable_shared_from_this<Queue>,
            public PersistableQueue, public management::Manageable {

            typedef std::deque<QueuedMessage> Messages;
            typedef std::map<string,boost::intrusive_ptr<Message> > LVQ;
            enum ConsumeCode {NO_MESSAGES=0, CANT_CONSUME=1, CONSUMED=2};

            const string name;
            const bool autodelete;
            MessageStore* store;
            const OwnershipToken* owner;
            uint32_t consumerCount;
            OwnershipToken* exclusive;
            bool noLocal;
            bool lastValueQueue;
            bool lastValueQueueNoBrowse;
            bool persistLastNode;
            bool inLastNodeFailure;
            std::string traceId;
            std::vector<std::string> traceExclude;
            QueueListeners listeners;
            Messages messages;
            LVQ lvq;
            mutable qpid::sys::Mutex consumerLock;
            mutable qpid::sys::Mutex messageLock;
            mutable qpid::sys::Mutex ownershipLock;
            mutable uint64_t persistenceId;
            framing::FieldTable settings;
            std::auto_ptr<QueuePolicy> policy;
            bool policyExceeded;
            QueueBindings bindings;
            boost::shared_ptr<Exchange> alternateExchange;
            framing::SequenceNumber sequence;
            qmf::org::apache::qpid::broker::Queue* mgmtObject;
            RateTracker dequeueTracker;
            int eventMode;
            QueueEvents* eventMgr;

            void push(boost::intrusive_ptr<Message>& msg);
            void setPolicy(std::auto_ptr<QueuePolicy> policy);
            bool seek(QueuedMessage& msg, Consumer::shared_ptr position);
            bool getNextMessage(QueuedMessage& msg, Consumer::shared_ptr c);
            ConsumeCode consumeNextMessage(QueuedMessage& msg, Consumer::shared_ptr c);
            bool browseNextMessage(QueuedMessage& msg, Consumer::shared_ptr c);
            void notifyListener();

            void removeListener(Consumer::shared_ptr);

            bool isExcluded(boost::intrusive_ptr<Message>& msg);

            void dequeued(const QueuedMessage& msg);
            void popAndDequeue();
            QueuedMessage getFront();
            QueuedMessage& checkLvqReplace(QueuedMessage& msg);
            void clearLVQIndex(const QueuedMessage& msg);
            
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

        public:

            virtual void notifyDurableIOComplete();
            typedef boost::shared_ptr<Queue> shared_ptr;

            typedef std::vector<shared_ptr> vector;

            Queue(const string& name, bool autodelete = false, 
                  MessageStore* const store = 0, 
                  const OwnershipToken* const owner = 0,
                  management::Manageable* parent = 0);
            ~Queue();

            bool dispatch(Consumer::shared_ptr);
            /**
             * Check whether there would be a message available for
             * dispatch to this consumer. If not, the consumer will be
             * notified of events that may have changed this
             * situation.
             */
            bool checkForMessages(Consumer::shared_ptr);

            void create(const qpid::framing::FieldTable& settings);

            // "recovering" means we are doing a MessageStore recovery.
            void configure(const qpid::framing::FieldTable& settings, bool recovering = false);
            void destroy();
            void bound(const string& exchange, const string& key, const qpid::framing::FieldTable& args);
            void unbind(ExchangeRegistry& exchanges, Queue::shared_ptr shared_ref);

            bool acquire(const QueuedMessage& msg);
            bool acquireMessageAt(const qpid::framing::SequenceNumber& position, QueuedMessage& message);

            /**
             * Delivers a message to the queue. Will record it as
             * enqueued if persistent then process it.
             */
            void deliver(boost::intrusive_ptr<Message>& msg);
            /**
             * Dispatches the messages immediately to a consumer if
             * one is available or stores it for later if not.
             */
            void process(boost::intrusive_ptr<Message>& msg);
            /**
             * Returns a message to the in-memory queue (due to lack
             * of acknowledegement from a receiver). If a consumer is
             * available it will be dispatched immediately, else it
             * will be returned to the front of the queue.
             */
            void requeue(const QueuedMessage& msg);
            /**
             * Used during recovery to add stored messages back to the queue
             */
            void recover(boost::intrusive_ptr<Message>& msg);

            void consume(Consumer::shared_ptr c, bool exclusive = false);
            void cancel(Consumer::shared_ptr c);

            uint32_t purge(const uint32_t purge_request = 0); //defaults to all messages 
            void purgeExpired();

            //move qty # of messages to destination Queue destq
            uint32_t move(const Queue::shared_ptr destq, uint32_t qty); 

            uint32_t getMessageCount() const;
            uint32_t getConsumerCount() const;
            inline const string& getName() const { return name; }
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
            void setLastNodeFailure();
            void clearLastNodeFailure();

            bool enqueue(TransactionContext* ctxt, boost::intrusive_ptr<Message> msg);
            /**
             * dequeue from store (only done once messages is acknowledged)
             */
            bool dequeue(TransactionContext* ctxt, const QueuedMessage &msg);
            /**
             * Inform the queue that a previous transactional dequeue
             * committed.
             */
            void dequeueCommitted(const QueuedMessage& msg);

            /**
             * Gets the next available message 
             */
            QueuedMessage get();

            /** Get the message at position pos */
            QueuedMessage find(framing::SequenceNumber pos) const;

            const QueuePolicy* getPolicy();

            void setAlternateExchange(boost::shared_ptr<Exchange> exchange);
            boost::shared_ptr<Exchange> getAlternateExchange();
            bool isLocal(boost::intrusive_ptr<Message>& msg);

            //PersistableQueue support:
            uint64_t getPersistenceId() const;
            void setPersistenceId(uint64_t persistenceId) const;
            void encode(framing::Buffer& buffer) const;
            uint32_t encodedSize() const;

            // "recovering" means we are doing a MessageStore recovery.
            static Queue::shared_ptr decode(QueueRegistry& queues, framing::Buffer& buffer, bool recovering = false );
            static void tryAutoDelete(Broker& broker, Queue::shared_ptr);

            virtual void setExternalQueueStore(ExternalQueueStore* inst);

            // Manageable entry points
            management::ManagementObject* GetManagementObject (void) const;
            management::Manageable::status_t
                ManagementMethod (uint32_t methodId, management::Args& args, std::string& text);

            /** Apply f to each Message on the queue. */
            template <class F> void eachMessage(F f) const {
                sys::Mutex::ScopedLock l(messageLock);
                std::for_each(messages.begin(), messages.end(), f);
            }

            /** Apply f to each QueueBinding on the queue */
            template <class F> void eachBinding(F f) {
                bindings.eachBinding(f);
            }

            bool releaseMessageContent(const QueuedMessage&);

            void popMsg(QueuedMessage& qmsg);

            /** Set the position sequence number  for the next message on the queue.
             * Must be >= the current sequence number.
             * Used by cluster to replicate queues.
             */
            void setPosition(framing::SequenceNumber pos);
            int getEventMode();
            void setQueueEventManager(QueueEvents&);
        };
    }
}


#endif  /*!_broker_Queue_h*/
