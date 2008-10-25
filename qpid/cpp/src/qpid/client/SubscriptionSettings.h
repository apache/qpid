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

/**
 * Settings for a subscription.
 */
struct SubscriptionSettings
{
    SubscriptionSettings(
        FlowControl flow=FlowControl::unlimited(),
        AcceptMode accept=ACCEPT_MODE_EXPLICIT,
        AcquireMode acquire=ACQUIRE_MODE_PRE_ACQUIRED,
        unsigned int autoAck_=1
    ) : flowControl(flow), acceptMode(accept), acquireMode(acquire), autoAck(autoAck_) {}
                         
    FlowControl flowControl;    ///@< Flow control settings. @see FlowControl
    AcceptMode acceptMode;      ///@< ACCEPT_MODE_EXPLICIT or ACCEPT_MODE_NONE
    AcquireMode acquireMode;    ///@< ACQUIRE_MODE_PRE_ACQUIRED or ACQUIRE_MODE_NOT_ACQUIRED

    /** Automatically acknowledge (acquire and accept) batches of autoAck messages.
     * 0 means no automatic acknowledgement. What it means to "acknowledge" a message depends on
     * acceptMode and acquireMode:
     *  - ACCEPT_MODE_NONE and ACQUIRE_MODE_PRE_ACQUIRED: do nothing
     *  - ACCEPT_MODE_NONE and ACQUIRE_MODE_NOT_ACQUIRED: send an "acquire" command
     *  - ACCEPT_MODE_EXPLICIT and ACQUIRE_MODE_PRE_ACQUIRED: send "accept" command
     *  - ACCEPT_MODE_EXPLICIT and ACQUIRE_MODE_NOT_ACQUIRED: send "acquire" and "accept" commands
     */
    unsigned int autoAck;
};

}} // namespace qpid::client

#endif  /*!QPID_CLIENT_SUBSCRIPTIONSETTINGS_H*/
