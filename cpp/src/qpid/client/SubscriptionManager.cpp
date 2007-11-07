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
#include <qpid/client/Session_0_10.h>
#include <qpid/client/MessageListener.h>
#include <set>
#include <sstream>


namespace qpid {
namespace client {

SubscriptionManager::SubscriptionManager(Session_0_10& s)
    : dispatcher(s), session(s),
      messages(UNLIMITED), bytes(UNLIMITED), window(true)
{}

void SubscriptionManager::subscribe(
    MessageListener& listener, const std::string& q, const std::string& t)
{
    std::string tag=t.empty() ? q:t;
    dispatcher.listen(tag, &listener);
    session.messageSubscribe(arg::queue=q, arg::destination=tag);
    setFlowControl(tag, messages, bytes, window);
}

void SubscriptionManager::subscribe(
    LocalQueue& lq, const std::string& q, const std::string& t)
{
    std::string tag=t.empty() ? q:t;
    lq.session=session;
    lq.queue=session.execution().getDemux().add(tag, ByTransferDest(tag));
    session.messageSubscribe(arg::queue=q, arg::destination=tag);
    setFlowControl(tag, messages, bytes, window);
}

void SubscriptionManager::setFlowControl(
    const std::string& tag, uint32_t messages,  uint32_t bytes, bool window)
{
    session.messageFlowMode(tag, window); 
    session.messageFlow(tag, 0, messages); 
    session.messageFlow(tag, 1, bytes);
}

void SubscriptionManager::setFlowControl(
    uint32_t messages_,  uint32_t bytes_, bool window_)
{
    messages=messages_;
    bytes=bytes_;
    window=window_;
}

void SubscriptionManager::cancel(const std::string tag)
{
    dispatcher.cancel(tag);
    session.messageCancel(tag);
}

void SubscriptionManager::run(bool autoStop)
{
    dispatcher.setAutoStop(autoStop);
    dispatcher.run();
}

void SubscriptionManager::stop()
{
    dispatcher.stop();
}

}} // namespace qpid::client

#endif
