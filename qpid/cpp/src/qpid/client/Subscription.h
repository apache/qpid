#ifndef QPID_CLIENT_SUBSCRIPTION_H
#define QPID_CLIENT_SUBSCRIPTION_H

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

#include "qpid/client/Session.h"
#include "qpid/client/SubscriptionSettings.h"
#include "qpid/client/Handle.h"
#include "qpid/client/Message.h"

namespace qpid {
namespace client {

class SubscriptionImpl;
class SubscriptionManager;

/**
 * A handle to an active subscription. Provides methods to query the subscription status
 * and control acknowledgement (acquire and accept) of messages.
 */
class Subscription : public Handle<SubscriptionImpl> {
  public:
    Subscription(SubscriptionImpl* si=0) : Handle<SubscriptionImpl>(si) {}
    
    /** The name of the subsctription, used as the "destination" for messages from the broker.
     * Usually the same as the queue name but can be set differently.
     */
    std::string getName() const;

    /** Name of the queue this subscription subscribes to */
    std::string getQueue() const;

    /** Get the flow control and acknowledgement settings for this subscription */
    const SubscriptionSettings& getSettings() const;

    /** Set the flow control parameters */
    void setFlowControl(const FlowControl&);

    /** Automatically acknowledge (acquire and accept) batches of n messages.
     * You can disable auto-acknowledgement by setting n=0, and use acquire() and accept()
     * to manually acquire and accept messages.
     */
    void setAutoAck(unsigned int n);

    /** Get the set of ID's for messages received by this subscription but not yet acquired.
     * This will always be empty if getSettings().acquireMode=ACQUIRE_MODE_PRE_ACQUIRED
     */
    SequenceSet getUnacquired() const;

    /** Get the set of ID's for messages received by this subscription but not yet accepted. */
    SequenceSet getUnaccepted() const;

    /** Acquire messageIds and remove them from the unacquired set.
     * oAdd them to the unaccepted set if getSettings().acceptMode == ACCEPT_MODE_EXPLICIT.
     */
    void acquire(const SequenceSet& messageIds);

    /** Accept messageIds and remove them from the unaccepted set.
     *@pre messageIds is a subset of getUnaccepted()
     */
    void accept(const SequenceSet& messageIds);

    /** Release messageIds and remove them from the unaccepted set.
     *@pre messageIds is a subset of getUnaccepted()
     */
    void release(const SequenceSet& messageIds);

    /* Acquire a single message */
    void acquire(const Message& m) { acquire(SequenceSet(m.getId())); }

    /* Accept a single message */
    void accept(const Message& m) { accept(SequenceSet(m.getId())); }

    /* Release a single message */
    void release(const Message& m) { release(SequenceSet(m.getId())); }

    /** Get the session associated with this subscription */
    Session getSession() const;

    /** Get the subscription manager associated with this subscription */
    SubscriptionManager& getSubscriptionManager() const;

    /** Cancel the subscription. */
    void cancel();

    /** Grant the specified amount of message credit */
    void grantMessageCredit(uint32_t);

    /** Grant the specified amount of byte credit */
    void grantByteCredit(uint32_t);
};
}} // namespace qpid::client

#endif  /*!QPID_CLIENT_SUBSCRIPTION_H*/
