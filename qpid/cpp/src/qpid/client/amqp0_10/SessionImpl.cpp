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
#include "qpid/client/amqp0_10/ConnectionImpl.h"
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

SessionImpl::SessionImpl(ConnectionImpl& c) : connection(c) {}


void SessionImpl::sync()
{
    retry<Sync>();
}

void SessionImpl::flush()
{
    retry<Flush>();
}

void SessionImpl::commit()
{
    if (!execute<Commit>()) {
        throw Exception();//TODO: what type?
    }
}

void SessionImpl::rollback()
{
    //If the session fails during this operation, the transaction will
    //be rolled back anyway.
    execute<Rollback>();
}

void SessionImpl::acknowledge()
{
    //Should probably throw an exception on failure here, or indicate
    //it through a return type at least. Failure means that the
    //message may be redelivered; i.e. the application cannot delete
    //any state necessary for preventing reprocessing of the message
    execute<Acknowledge>();
}

void SessionImpl::reject(qpid::messaging::Message& m)
{
    //Possibly want to somehow indicate failure here as well. Less
    //clear need as compared to acknowledge however.
    execute1<Reject>(m);
}

void SessionImpl::close()
{
    connection.closed(*this);
    session.close();
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


void SessionImpl::setSession(qpid::client::Session s)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    session = s;
    incoming.setSession(session);
    for (Receivers::iterator i = receivers.begin(); i != receivers.end(); ++i) {
        getImplPtr<Receiver, ReceiverImpl>(i->second)->init(session, resolver);
    }
    for (Senders::iterator i = senders.begin(); i != senders.end(); ++i) {
        getImplPtr<Sender, SenderImpl>(i->second)->init(session, resolver);
    }
}

struct SessionImpl::CreateReceiver : Command
{
    qpid::messaging::Receiver result;
    const qpid::messaging::Address& address;
    const Filter* filter;
    const qpid::messaging::Variant::Map& options;
    
    CreateReceiver(SessionImpl& i, const qpid::messaging::Address& a, const Filter* f, 
                   const qpid::messaging::Variant::Map& o) :
        Command(i), address(a), filter(f), options(o) {}
    void operator()() { result = impl.createReceiverImpl(address, filter, options); }
};

Receiver SessionImpl::createReceiver(const qpid::messaging::Address& address, const VariantMap& options)
{ 
    CreateReceiver f(*this, address, 0, options);
    while (!execute(f)) {}
    return f.result;
}

Receiver SessionImpl::createReceiver(const qpid::messaging::Address& address, 
                                     const Filter& filter, const VariantMap& options)
{ 
    CreateReceiver f(*this, address, &filter, options);
    while (!execute(f)) {}
    return f.result;
}

Receiver SessionImpl::createReceiverImpl(const qpid::messaging::Address& address,
                                         const Filter* filter, const VariantMap& options)
{
    std::string name = address;
    getFreeKey(name, receivers);
    Receiver receiver(new ReceiverImpl(*this, name, address, filter, options));
    getImplPtr<Receiver, ReceiverImpl>(receiver)->init(session, resolver);
    receivers[name] = receiver;
    return receiver;
}

struct SessionImpl::CreateSender : Command
{
    qpid::messaging::Sender result;
    const qpid::messaging::Address& address;
    const qpid::messaging::Variant::Map& options;
    
    CreateSender(SessionImpl& i, const qpid::messaging::Address& a,
                 const qpid::messaging::Variant::Map& o) :
        Command(i), address(a), options(o) {}
    void operator()() { result = impl.createSenderImpl(address, options); }
};

Sender SessionImpl::createSender(const qpid::messaging::Address& address, const VariantMap& options)
{
    CreateSender f(*this, address, options);
    while (!execute(f)) {}
    return f.result;
}

Sender SessionImpl::createSenderImpl(const qpid::messaging::Address& address, const VariantMap& options)
{ 
    std::string name = address;
    getFreeKey(name, senders);
    Sender sender(new SenderImpl(*this, name, address, options));
    getImplPtr<Sender, SenderImpl>(sender)->init(session, resolver);
    senders[name] = sender;
    return sender;
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
    return incoming.get(handler, timeout);
}

bool SessionImpl::get(ReceiverImpl& receiver, qpid::messaging::Message& message, qpid::sys::Duration timeout)
{
    IncomingMessageHandler handler(boost::bind(&SessionImpl::accept, this, &receiver, &message, false, _1));
    return getIncoming(handler, timeout);
}

bool SessionImpl::dispatch(qpid::sys::Duration timeout)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    while (true) {
        try {
            qpid::messaging::Message message;
            IncomingMessageHandler handler(boost::bind(&SessionImpl::acceptAny, this, &message, true, _1));
            return getIncoming(handler, timeout);
        } catch (TransportFailure&) {
            reconnect();
        }
    }
}

bool SessionImpl::fetch(qpid::messaging::Message& message, qpid::sys::Duration timeout)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    while (true) {
        try {
            IncomingMessageHandler handler(boost::bind(&SessionImpl::acceptAny, this, &message, false, _1));
            return getIncoming(handler, timeout);
        } catch (TransportFailure&) {
            reconnect();
        }
    }
}

uint32_t SessionImpl::available()
{
    return get1<Available, uint32_t>((const std::string*) 0);
}
uint32_t SessionImpl::available(const std::string& destination)
{
    return get1<Available, uint32_t>(&destination);
}

struct SessionImpl::Available : Command
{
    const std::string* destination;
    uint32_t result;
    
    Available(SessionImpl& i, const std::string* d) : Command(i), destination(d), result(0) {}
    void operator()() { result = impl.availableImpl(destination); }
};

uint32_t SessionImpl::availableImpl(const std::string* destination)
{
    if (destination) {
        return incoming.available(*destination);
    } else {
        return incoming.available();
    }
}

uint32_t SessionImpl::pendingAck()
{
    return get1<PendingAck, uint32_t>((const std::string*) 0);
}

uint32_t SessionImpl::pendingAck(const std::string& destination)
{
    return get1<PendingAck, uint32_t>(&destination);
}

struct SessionImpl::PendingAck : Command
{
    const std::string* destination;
    uint32_t result;
    
    PendingAck(SessionImpl& i, const std::string* d) : Command(i), destination(d), result(0) {}
    void operator()() { result = impl.pendingAckImpl(destination); }
};

uint32_t SessionImpl::pendingAckImpl(const std::string* destination)
{
    if (destination) {
        return incoming.pendingAccept(*destination);
    } else {
        return incoming.pendingAccept();
    }
}

void SessionImpl::syncImpl()
{
    session.sync();
}

void SessionImpl::flushImpl()
{
    session.flush();
}


void SessionImpl::commitImpl()
{
    incoming.accept();
    session.txCommit();
}

void SessionImpl::rollbackImpl()
{
    for (Receivers::iterator i = receivers.begin(); i != receivers.end(); ++i) i->second.stop();
    //ensure that stop has been processed and all previously sent
    //messages are available for release:                   
    session.sync();
    incoming.releaseAll();
    session.txRollback();    
    for (Receivers::iterator i = receivers.begin(); i != receivers.end(); ++i) i->second.start();
}

void SessionImpl::acknowledgeImpl()
{
    incoming.accept();
}

void SessionImpl::rejectImpl(qpid::messaging::Message& m)
{
    SequenceSet set;
    set.add(MessageImplAccess::get(m).getInternalId());
    session.messageReject(set);
}

qpid::messaging::Message SessionImpl::fetch(qpid::sys::Duration timeout) 
{
    qpid::messaging::Message result;
    if (!fetch(result, timeout)) throw Receiver::NoMessageAvailable();
    return result;
}

void SessionImpl::receiverCancelled(const std::string& name)
{
    receivers.erase(name);
    session.sync();
    incoming.releasePending(name);
}

void SessionImpl::senderCancelled(const std::string& name)
{
    senders.erase(name);
}

void SessionImpl::reconnect()
{
    connection.reconnect();    
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
