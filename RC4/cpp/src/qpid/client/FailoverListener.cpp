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
#include "qpid/framing/Uuid.h"
#include "qpid/log/Statement.h"
#include "qpid/log/Helpers.h"

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

FailoverListener::FailoverListener(const boost::shared_ptr<ConnectionImpl>& c, const std::vector<Url>& initUrls)
  : knownBrokers(initUrls) 
 {
    // Special versions used to mark cluster catch-up connections
    // which do not need a FailoverListener
    if (c->getVersion().getMajor() >= 0x80)  {
        QPID_LOG(debug, "No failover listener for catch-up connection.");
        return;
    }

    Session session = makeSession(c->newSession(AMQ_FAILOVER+framing::Uuid(true).str(), 0));
    if (session.exchangeQuery(arg::name=AMQ_FAILOVER).getNotFound()) {
        session.close();
        return;
    }
    subscriptions.reset(new SubscriptionManager(session));
    std::string qname=session.getId().getName();
    session.queueDeclare(arg::queue=qname, arg::exclusive=true, arg::autoDelete=true);
    session.exchangeBind(arg::queue=qname, arg::exchange=AMQ_FAILOVER);
    subscriptions->subscribe(*this, qname, SubscriptionSettings(FlowControl::unlimited(), ACCEPT_MODE_NONE));
    thread = sys::Thread(*this);
}

void FailoverListener::run() 
{
    try {
        subscriptions->run();
    } catch (const TransportFailure&) {
    } catch (const std::exception& e) {
        QPID_LOG(error, QPID_MSG(e.what()));
    }
}

FailoverListener::~FailoverListener() {
    try { stop(); }
    catch (const std::exception& /*e*/) {}
}

void FailoverListener::stop() {
    if (subscriptions.get()) 
        subscriptions->stop();

    if (thread.id() == sys::Thread::current().id()) {
        // FIXME aconway 2008-10-16: this can happen if ConnectionImpl
        // dtor runs when my session drops its weak pointer lock.
        // For now, leak subscriptions to prevent a core if we delete
        // without joining.
        subscriptions.release();
    }
    else if (thread.id()) {
        thread.join();
        thread=sys::Thread();
        subscriptions.reset();  // Safe to delete after join.
    }
}

void FailoverListener::received(Message& msg) {
    sys::Mutex::ScopedLock l(lock);
    knownBrokers.clear();
    framing::Array urlArray;
    msg.getHeaders().getArray("amq.failover", urlArray);
    for (framing::Array::ValueVector::const_iterator i = urlArray.begin(); i != urlArray.end(); ++i ) 
        knownBrokers.push_back(Url((*i)->get<std::string>()));
    QPID_LOG(info, "Known-brokers update: " << log::formatList(knownBrokers));
}

std::vector<Url> FailoverListener::getKnownBrokers() const {
    sys::Mutex::ScopedLock l(lock);
    return knownBrokers;
}

}} // namespace qpid::client
