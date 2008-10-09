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
#include "FailoverListener.h"
#include "SessionBase_0_10Access.h"
#include "qpid/client/SubscriptionManager.h"

namespace qpid {
namespace client {

static const std::string AMQ_FAILOVER("amq.failover");

static Session makeSession(boost::shared_ptr<SessionImpl> si) {
    // Hold only a weak pointer to the ConnectionImpl so a
    // FailoverListener in a ConnectionImpl won't createa a shared_ptr
    // cycle.
    // 
    si->setWeakPtr(true);
    Session s;
    SessionBase_0_10Access(s).set(si);
    return s;
}

FailoverListener::FailoverListener() {}

void FailoverListener::start(const boost::shared_ptr<ConnectionImpl>& c) {
    Session session = makeSession(c->newSession(std::string(), 0));
    if (session.exchangeQuery(arg::name=AMQ_FAILOVER).getNotFound()) {
        session.close();
        return;
    }
    subscriptions.reset(new SubscriptionManager(session));
    std::string qname=AMQ_FAILOVER + "." + session.getId().getName();
    session.queueDeclare(arg::queue=qname, arg::exclusive=true, arg::autoDelete=true);
    session.exchangeBind(arg::queue=qname, arg::exchange=AMQ_FAILOVER);
    subscriptions->subscribe(*this, qname, FlowControl::unlimited());
    thread = sys::Thread(*subscriptions);
}

void FailoverListener::stop() {
    if (subscriptions.get()) subscriptions->stop();
    if (thread.id()) thread.join();
    if (subscriptions.get()) subscriptions->getSession().close();
    thread=sys::Thread();
    subscriptions.reset();
}    
FailoverListener::~FailoverListener() {
    stop();
}

void FailoverListener::received(Message& msg) {
    sys::Mutex::ScopedLock l(lock);
    knowBrokers.clear();
    framing::Array urlArray;
    msg.getHeaders().getArray("amq.failover", urlArray);
    for (framing::Array::ValueVector::const_iterator i = urlArray.begin(); i < urlArray.end(); ++i ) 
        knowBrokers.push_back(Url((*i)->get<std::string>()));
}

std::vector<Url> FailoverListener::getKnownBrokers() const {
    sys::Mutex::ScopedLock l(lock);
    return knowBrokers;
}

}} // namespace qpid::client
