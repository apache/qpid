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
#include <qpid/framing/Uuid.h>
#include <set>
#include <sstream>


namespace qpid {
namespace client {

SubscriptionManager::SubscriptionManager(const Session& s)
    : dispatcher(s), session(s),
      flowControl(UNLIMITED, UNLIMITED, false),
      acceptMode(0), acquireMode(0),
      autoStop(true)
{}

void SubscriptionManager::subscribeInternal(
    const std::string& q, const std::string& dest, const FlowControl& fc)
{
    session.messageSubscribe( 
        arg::queue=q, arg::destination=dest,
        arg::acceptMode=acceptMode, arg::acquireMode=acquireMode);
    if (fc.messages || fc.bytes) // No need to set if all 0.
        setFlowControl(dest, fc);
}

void SubscriptionManager::subscribe(
    MessageListener& listener, const std::string& q, const std::string& d)
{
    subscribe(listener, q, getFlowControl(), d);
}

void SubscriptionManager::subscribe(
    MessageListener& listener, const std::string& q, const FlowControl& fc, const std::string& d)
{
    std::string dest=d.empty() ? q:d;
    dispatcher.listen(dest, &listener, autoAck);
    return subscribeInternal(q, dest, fc);
}

void SubscriptionManager::subscribe(
    LocalQueue& lq, const std::string& q, const std::string& d)
{
    subscribe(lq, q, getFlowControl(), d);
}

void SubscriptionManager::subscribe(
    LocalQueue& lq, const std::string& q, const FlowControl& fc, const std::string& d)
{
    std::string dest=d.empty() ? q:d;
    lq.session=session;
    lq.queue=session.getExecution().getDemux().add(dest, ByTransferDest(dest));
    return subscribeInternal(q, dest, fc);
}

void SubscriptionManager::setFlowControl(
    const std::string& dest, uint32_t messages,  uint32_t bytes, bool window)
{
    session.messageSetFlowMode(dest, window); 
    session.messageFlow(dest, 0, messages); 
    session.messageFlow(dest, 1, bytes);
    session.sync();
}

void SubscriptionManager::setFlowControl(const std::string& dest, const FlowControl& fc) {
    setFlowControl(dest, fc.messages, fc.bytes, fc.window);
}

void SubscriptionManager::setFlowControl(const FlowControl& fc) { flowControl=fc; }

void SubscriptionManager::setFlowControl(
    uint32_t messages_,  uint32_t bytes_, bool window_)
{
    setFlowControl(FlowControl(messages_, bytes_, window_));
}

const FlowControl& SubscriptionManager::getFlowControl() const { return flowControl; }

void SubscriptionManager::setAcceptMode(bool c) { acceptMode=c; }

void SubscriptionManager::setAcquireMode(bool a) { acquireMode=a; }

void SubscriptionManager::setAckPolicy(const AckPolicy& a) { autoAck=a; }

AckPolicy& SubscriptionManager::getAckPolicy() { return autoAck; } 

void SubscriptionManager::cancel(const std::string dest)
{
    sync(session).messageCancel(dest);
    dispatcher.cancel(dest);
}

void SubscriptionManager::setAutoStop(bool set) { autoStop=set; }

void SubscriptionManager::run()
{
    dispatcher.setAutoStop(autoStop);
    dispatcher.run();
}

void SubscriptionManager::stop()
{
    dispatcher.stop();
}

Message SubscriptionManager::get(const std::string& queue) {
    LocalQueue lq;
    subscribe(lq, queue, FlowControl::messageCredit(1), framing::Uuid(true).str());
    return lq.get();
}

}} // namespace qpid::client

#endif
