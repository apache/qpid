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
#ifndef _Subscription_
#define _Subscription_

#include "SubscriptionManager.h"
#include <qpid/client/Dispatcher.h>
#include <qpid/client/Session.h>
#include <qpid/client/MessageListener.h>
#include <set>
#include <sstream>


namespace qpid {
namespace client {

SubscriptionManager::SubscriptionManager(Session_0_10& s)
    : dispatcher(s), session(s), messages(1), bytes(UNLIMITED), autoStop(true)
{}

std::string SubscriptionManager::uniqueTag(const std::string& tag) {
    // Make unique tag.
    int count=1;
    std::string unique=tag;
    while (subscriptions.find(tag) != subscriptions.end()) {
        std::ostringstream s;
        s << tag << "-" << count++;
        unique=s.str();
    }
    subscriptions.insert(unique);
    return tag;
}

std::string SubscriptionManager::subscribe(
    MessageListener& listener, const std::string& q, const std::string& t)
{
    std::string tag=uniqueTag(t);
    using namespace arg;
    session.messageSubscribe(arg::queue=q, arg::destination=tag);
    flowLimits(tag, messages, bytes);
    dispatcher.listen(tag, &listener);
    return tag;
}

void SubscriptionManager::flowLimits(
    const std::string& tag, uint32_t messages,  uint32_t bytes) {
    session.messageFlow(tag, 0, messages); 
    session.messageFlow(tag, 1, bytes);
}

void SubscriptionManager::flowLimits(uint32_t m,  uint32_t b) {
    messages=m;
    bytes=b;
}

void SubscriptionManager::cancel(const std::string tag)
{
    if (subscriptions.erase(tag)) {
        dispatcher.cancel(tag);
        session.messageCancel(tag);
        if (autoStop && subscriptions.empty()) stop();
    }
}

void SubscriptionManager::run(bool autoStop_)
{
    autoStop=autoStop_;
    if (autoStop && subscriptions.empty()) return;
    dispatcher.run();
}

void SubscriptionManager::stop()
{
    dispatcher.stop();
}

}} // namespace qpid::client

#endif
