#ifndef QPID_CLIENT_SUBSCRIPTIONSETTINGS_H
#define QPID_CLIENT_SUBSCRIPTIONSETTINGS_H

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
#include "qpid/client/FlowControl.h"
#include "qpid/framing/enum.h"

namespace qpid {
namespace client {

/** Bring AMQP enum definitions for message class into this namespace. */
using namespace qpid::framing::message;

enum CompletionMode {
    MANUAL_COMPLETION = 0,
    COMPLETE_ON_DELIVERY = 1,
    COMPLETE_ON_ACCEPT = 2
};
/**
 * Settings for a subscription.
 */
struct SubscriptionSettings
{
    SubscriptionSettings(
        FlowControl flow=FlowControl::unlimited(),
        AcceptMode accept=ACCEPT_MODE_EXPLICIT,
        AcquireMode acquire=ACQUIRE_MODE_PRE_ACQUIRED,
        unsigned int autoAck_=1,
        CompletionMode completion=COMPLETE_ON_DELIVERY
    ) : flowControl(flow), acceptMode(accept), acquireMode(acquire), autoAck(autoAck_), completionMode(completion), exclusive(false) {}
                         
    FlowControl flowControl;    ///@< Flow control settings. @see FlowControl
    AcceptMode acceptMode;      ///@< ACCEPT_MODE_EXPLICIT or ACCEPT_MODE_NONE
    AcquireMode acquireMode;    ///@< ACQUIRE_MODE_PRE_ACQUIRED or ACQUIRE_MODE_NOT_ACQUIRED

    /** Automatically acknowledge (accept) batches of autoAck
     *  messages. 0 means no automatic acknowledgement. This has no
     *  effect for messsages received for a subscription with
     *  ACCEPT_MODE_NODE.*/
    unsigned int autoAck;
    /**
     * In windowing mode, completion of a message will cause the
     * credit used up by that message to be reallocated. The
     * subscriptions completion mode controls how completion is
     * managed.
     * 
     * If set to COMPLETE_ON_DELIVERY (which is the default), messages
     * will be marked as completed once they have been received. The
     * server will be explicitly notified of all completed messages
     * for the session when the next accept is sent through the
     * subscription (either explictly or through autAck). However the
     * server may also periodically request information on the
     * completed messages.
     * 
     * If set to COMPLETE_ON_ACCEPT, messages will be marked as
     * completed once they are accepted (via the Subscription class)
     * and the server will also be notified of all completed messages
     * for the session.
     * 
     * If set to MANUAL_COMPLETION the application is responsible for
     * completing messages (@see Session::markCompleted()).
     */
    CompletionMode completionMode;
    /**
     * If set, requests that no other subscriber be allowed to access
     * the queue while this subscription is active.
     */
    bool exclusive;
};

}} // namespace qpid::client

#endif  /*!QPID_CLIENT_SUBSCRIPTIONSETTINGS_H*/
