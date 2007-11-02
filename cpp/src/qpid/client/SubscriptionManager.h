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

#include <qpid/client/Dispatcher.h>
#include <qpid/client/Session_0_10.h>
#include <qpid/client/MessageListener.h>
#include <set>
#include <sstream>

namespace qpid {
namespace client {

struct TagNotUniqueException : public qpid::Exception {
    TagNotUniqueException() {}
};

class SubscriptionManager
{
    std::set<std::string> subscriptions;
    qpid::client::Dispatcher dispatcher;
    qpid::client::Session_0_10& session;
    std::string uniqueTag(const std::string&);
    uint32_t messages;
    uint32_t bytes;
    bool autoStop;

public:
    SubscriptionManager(Session_0_10& session);
    
    /**
     * Subscribe listener to receive messages from queue.
     *@param listener Listener object to receive messages.
     *@param queue Name of the queue to subscribe to.
     *@param tag Unique destination tag for the listener.
     * If not specified a unique tag will be generted based on the queue name.
     *@return Destination tag.
     *@exception TagNotUniqueException if there is already a subscription
     * with the same tag.
     */
    std::string subscribe(MessageListener& listener,
                          const std::string& queue,
                          const std::string& tag=std::string());

    /** Cancel a subscription. */
    void cancel(const std::string tag);
    
    qpid::client::Dispatcher& getDispatcher() { return dispatcher; }
    size_t size() { return subscriptions.size(); }

    /** Deliver messages until stop() is called.
     *@param autoStop If true, return when all subscriptions are cancelled.
     */
    void run(bool autoStop=true);

    /** Cause run() to return */
    void stop();

    static const uint32_t UNLIMITED=0xFFFFFFFF;

    /** Set the flow control limits for subscriber with tag.
     * UNLIMITED means no limit.
     */
    void flowLimits(const std::string& tag, uint32_t messages,  uint32_t bytes);

    /** Set the initial flow control limits for new subscribers */
    void flowLimits(uint32_t messages,  uint32_t bytes);
};


}} // namespace qpid::client

#endif  /*!QPID_CLIENT_SUBSCRIPTIONMANAGER_H*/
