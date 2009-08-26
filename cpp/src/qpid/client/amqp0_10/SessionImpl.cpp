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
#include "qpid/client/amqp0_10/SessionImpl.h"
#include "qpid/client/amqp0_10/ReceiverImpl.h"
#include "qpid/client/amqp0_10/SenderImpl.h"
#include "qpid/client/amqp0_10/MessageSource.h"
#include "qpid/client/amqp0_10/MessageSink.h"
#include "qpid/client/PrivateImplRef.h"
#include "qpid/Exception.h"
#include "qpid/log/Statement.h"
#include "qpid/messaging/Address.h"
#include "qpid/messaging/Filter.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/MessageImpl.h"
#include "qpid/messaging/MessageListener.h"
#include "qpid/messaging/Sender.h"
#include "qpid/messaging/Receiver.h"
#include "qpid/messaging/Session.h"
#include "qpid/framing/reply_exceptions.h"
#include <boost/format.hpp>
#include <boost/function.hpp>
#include <boost/intrusive_ptr.hpp>

using qpid::messaging::Filter;
using qpid::messaging::MessageImplAccess;
using qpid::messaging::Sender;
using qpid::messaging::Receiver;
using qpid::messaging::VariantMap;

namespace qpid {
namespace client {
namespace amqp0_10 {

SessionImpl::SessionImpl(qpid::client::Session s) : session(s), incoming(session) {}


void SessionImpl::commit()
{
    qpid::sys::Mutex::ScopedLock l(lock);
    incoming.accept();
    session.txCommit();
}

void SessionImpl::rollback()
{
    qpid::sys::Mutex::ScopedLock l(lock);
    for (Receivers::iterator i = receivers.begin(); i != receivers.end(); ++i) i->second.stop();
    //ensure that stop has been processed and all previously sent
    //messages are available for release:                   
    session.sync();
    incoming.releaseAll();
    session.txRollback();    
    for (Receivers::iterator i = receivers.begin(); i != receivers.end(); ++i) i->second.start();
}

void SessionImpl::acknowledge()
{
    qpid::sys::Mutex::ScopedLock l(lock);
    incoming.accept();
}

void SessionImpl::reject(qpid::messaging::Message& m)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    SequenceSet set;
    set.add(MessageImplAccess::get(m).getInternalId());
    session.messageReject(set);
}

void SessionImpl::close()
{
    session.close();
}

void translate(const VariantMap& options, SubscriptionSettings& settings)
{
    //TODO: fill this out
    VariantMap::const_iterator i = options.find("auto_acknowledge");
    if (i != options.end()) {
        settings.autoAck = i->second.asInt32();
    }
}

template <class T, class S> boost::intrusive_ptr<S> getImplPtr(T& t)
{
    return boost::dynamic_pointer_cast<S>(qpid::client::PrivateImplRef<T>::get(t));
}

template <class T> void getFreeKey(std::string& key, T& map)
{
    std::string name = key;
    int count = 1;
    for (typename T::const_iterator i = map.find(name); i != map.end(); i = map.find(name)) {
        name = (boost::format("%1%_%2%") % key % ++count).str();
    }
    key = name;
}

Sender SessionImpl::createSender(const qpid::messaging::Address& address, const VariantMap& options)
{ 
    qpid::sys::Mutex::ScopedLock l(lock);
    std::auto_ptr<MessageSink> sink = resolver.resolveSink(session, address, options);
    std::string name = address;
    getFreeKey(name, senders);
    Sender sender(new SenderImpl(*this, name, sink));
    getImplPtr<Sender, SenderImpl>(sender)->setSession(session);
    senders[name] = sender;
    return sender;
}
Receiver SessionImpl::createReceiver(const qpid::messaging::Address& address, const VariantMap& options)
{ 
    return addReceiver(address, 0, options);
}
Receiver SessionImpl::createReceiver(const qpid::messaging::Address& address, const Filter& filter, const VariantMap& options)
{ 
    return addReceiver(address, &filter, options);
}

Receiver SessionImpl::addReceiver(const qpid::messaging::Address& address, const Filter* filter, const VariantMap& options)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    std::auto_ptr<MessageSource> source = resolver.resolveSource(session, address, filter, options);
    std::string name = address;
    getFreeKey(name, receivers);
    Receiver receiver(new ReceiverImpl(*this, name, source));
    getImplPtr<Receiver, ReceiverImpl>(receiver)->setSession(session);
    receivers[name] = receiver;
    return receiver;
}

qpid::messaging::Address SessionImpl::createTempQueue(const std::string& baseName)
{
    std::string name = baseName + std::string("_") + session.getId().getName();
    session.queueDeclare(arg::queue=name, arg::exclusive=true, arg::autoDelete=true);
    return qpid::messaging::Address(name);
}

SessionImpl& SessionImpl::convert(qpid::messaging::Session& s)
{
    boost::intrusive_ptr<SessionImpl> impl = getImplPtr<qpid::messaging::Session, SessionImpl>(s);
    if (!impl) {
        throw qpid::Exception(QPID_MSG("Configuration error; require qpid::client::amqp0_10::SessionImpl"));
    }
    return *impl;
}

namespace {

struct IncomingMessageHandler : IncomingMessages::Handler
{
    typedef boost::function1<bool, IncomingMessages::MessageTransfer&> Callback;
    Callback callback;

    IncomingMessageHandler(Callback c) : callback(c) {}

    bool accept(IncomingMessages::MessageTransfer& transfer)
    {
        return callback(transfer);
    }
};

}

bool SessionImpl::accept(ReceiverImpl* receiver, 
                         qpid::messaging::Message* message, 
                         bool isDispatch, 
                         IncomingMessages::MessageTransfer& transfer)
{
    if (receiver->getName() == transfer.getDestination()) {
        transfer.retrieve(message);
        if (isDispatch) {
            qpid::sys::Mutex::ScopedUnlock u(lock);
            qpid::messaging::MessageListener* listener = receiver->getListener();
            if (listener) listener->received(*message);
        }
        receiver->received(*message);
        return true;
    } else {
        return false;
    }
}

bool SessionImpl::acceptAny(qpid::messaging::Message* message, bool isDispatch, IncomingMessages::MessageTransfer& transfer)
{
    Receivers::iterator i = receivers.find(transfer.getDestination());
    if (i == receivers.end()) {
        QPID_LOG(error, "Received message for unknown destination " << transfer.getDestination());
        return false;
    } else {
        boost::intrusive_ptr<ReceiverImpl> receiver = getImplPtr<Receiver, ReceiverImpl>(i->second);
        return receiver && (!isDispatch || receiver->getListener()) && accept(receiver.get(), message, isDispatch, transfer);
    }
}

bool SessionImpl::getIncoming(IncomingMessages::Handler& handler, qpid::sys::Duration timeout)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    return incoming.get(handler, timeout);
}

bool SessionImpl::dispatch(qpid::sys::Duration timeout)
{
    qpid::messaging::Message message;
    IncomingMessageHandler handler(boost::bind(&SessionImpl::acceptAny, this, &message, true, _1));
    return getIncoming(handler, timeout);
}

bool SessionImpl::get(ReceiverImpl& receiver, qpid::messaging::Message& message, qpid::sys::Duration timeout)
{
    IncomingMessageHandler handler(boost::bind(&SessionImpl::accept, this, &receiver, &message, false, _1));
    return getIncoming(handler, timeout);
}

bool SessionImpl::fetch(qpid::messaging::Message& message, qpid::sys::Duration timeout)
{
    IncomingMessageHandler handler(boost::bind(&SessionImpl::acceptAny, this, &message, false, _1));
    return getIncoming(handler, timeout);
}

qpid::messaging::Message SessionImpl::fetch(qpid::sys::Duration timeout) 
{
    qpid::messaging::Message result;
    if (!fetch(result, timeout)) throw Receiver::NoMessageAvailable();
    return result;
}

void SessionImpl::receiverCancelled(const std::string& name)
{
    {
        qpid::sys::Mutex::ScopedLock l(lock);
        receivers.erase(name);
    }
    session.sync();
    incoming.releasePending(name);
}

void SessionImpl::senderCancelled(const std::string& name)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    senders.erase(name);
}

void SessionImpl::sync()
{
    session.sync();
}

void SessionImpl::flush()
{
    session.flush();
}

void* SessionImpl::getLastConfirmedSent()
{
    return 0;
}

void* SessionImpl::getLastConfirmedAcknowledged()
{
    return 0;
}

}}} // namespace qpid::client::amqp0_10
