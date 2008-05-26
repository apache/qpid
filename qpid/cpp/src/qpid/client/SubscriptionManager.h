#ifndef QPID_CLIENT_SUBSCRIPTIONMANAGER_H
#define QPID_CLIENT_SUBSCRIPTIONMANAGER_H

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
#include "qpid/sys/Mutex.h"
#include <qpid/client/Dispatcher.h>
#include <qpid/client/Completion.h>
#include <qpid/client/Session.h>
#include <qpid/client/MessageListener.h>
#include <qpid/client/LocalQueue.h>
#include <qpid/sys/Runnable.h>

#include <set>
#include <sstream>

namespace qpid {
namespace client {

/**
 * Utility to assist with creating subscriptions.
 *  
 * \ingroup clientapi
 */
class SubscriptionManager : public sys::Runnable
{
    typedef sys::Mutex::ScopedLock Lock;
    typedef sys::Mutex::ScopedUnlock Unlock;

    void subscribeInternal(const std::string& q, const std::string& dest);
    
    qpid::client::Dispatcher dispatcher;
    qpid::client::Session session;
    uint32_t messages;
    uint32_t bytes;
    bool window;
    AckPolicy autoAck;
    bool acceptMode;
    bool acquireMode;
    bool autoStop;
    
  public:
    SubscriptionManager(const Session& session);
    
    /**
     * Subscribe a MessagesListener to receive messages from queue.
     * 
     *@param listener Listener object to receive messages.
     *@param queue Name of the queue to subscribe to.
     *@param tag Unique destination tag for the listener.
     * If not specified, the queue name is used.
     */
    void subscribe(MessageListener& listener,
                         const std::string& queue,
                         const std::string& tag=std::string());

    /**
     * Subscribe a LocalQueue to receive messages from queue.
     * 
     *@param queue Name of the queue to subscribe to.
     *@param tag Unique destination tag for the listener.
     * If not specified, the queue name is used.
     */
    void subscribe(LocalQueue& localQueue,
                   const std::string& queue,
                   const std::string& tag=std::string());

    /** Cancel a subscription. */
    void cancel(const std::string tag);

    /** Deliver messages until stop() is called. */
    void run();

    /** If set true, run() will stop when all subscriptions
     * are cancelled. If false, run will only stop when stop()
     * is called. True by default.
     */
    void setAutoStop(bool set=true);

    /** Cause run() to return */
    void stop();


    static const uint32_t UNLIMITED=0xFFFFFFFF;

    /** Set the flow control for destination tag.
     *@param tag: name of the destination.
     *@param messages: message credit.
     *@param bytes: byte credit.
     *@param window: if true use window-based flow control.
     */
    void setFlowControl(const std::string& tag, uint32_t messages,  uint32_t bytes, bool window=true);

    /** Set the initial flow control settings to be applied to each new subscribtion.
     *@param messages: message credit.
     *@param bytes: byte credit.
     *@param window: if true use window-based flow control.
     */
    void setFlowControl(uint32_t messages,  uint32_t bytes, bool window=true);

    /** Set the accept-mode for new subscriptions. Defaults to true.
     *@param required: if true messages must be confirmed by calling
     *Message::acknowledge() or automatically, see setAckPolicy()
     */
    void setAcceptMode(bool required);

    /** Set the acquire-mode for new subscriptions. Defaults to false.
     *@param acquire: if false messages pre-acquired, if true
     * messages are dequed on acknowledgement or on transfer 
	 * depending on acceptMode.
     */
    void setAcquireMode(bool acquire);

    /** Set the acknowledgement policy for new subscriptions.
     * Default is to acknowledge every message automatically.
     */
    void setAckPolicy(const AckPolicy& autoAck);
    /**
     *
     */
     AckPolicy& getAckPolicy();
};


}} // namespace qpid::client

#endif  /*!QPID_CLIENT_SUBSCRIPTIONMANAGER_H*/
