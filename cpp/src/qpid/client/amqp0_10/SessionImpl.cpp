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
#include "qpid/client/SessionBase_0_10Access.h"
#include "qpid/client/SessionImpl.h"
#include "qpid/messaging/PrivateImplRef.h"
#include "qpid/Exception.h"
#include "qpid/log/Statement.h"
#include "qpid/messaging/Address.h"
#include "qpid/messaging/Connection.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/MessageImpl.h"
#include "qpid/messaging/Sender.h"
#include "qpid/messaging/Receiver.h"
#include "qpid/messaging/Session.h"
#include <boost/format.hpp>
#include <boost/function.hpp>
#include <boost/intrusive_ptr.hpp>

using qpid::messaging::KeyError;
using qpid::messaging::NoMessageAvailable;
using qpid::messaging::MessagingException;
using qpid::messaging::TransactionAborted;
using qpid::messaging::SessionError;
using qpid::messaging::MessageImplAccess;
using qpid::messaging::Sender;
using qpid::messaging::Receiver;

namespace qpid {
namespace client {
namespace amqp0_10 {

typedef qpid::sys::Mutex::ScopedLock ScopedLock;
typedef qpid::sys::Mutex::ScopedUnlock ScopedUnlock;

SessionImpl::SessionImpl(ConnectionImpl& c, bool t) : connection(&c), transactional(t), aborted(false) {}

bool SessionImpl::isTransactional() const
{
    return transactional;
}

void SessionImpl::abortTransaction()
{
    ScopedLock l(lock);
    aborted = true;
}

void SessionImpl::checkAborted()
{
    ScopedLock l(lock);
    checkAbortedLH(l);
}

void SessionImpl::checkAbortedLH(const qpid::sys::Mutex::ScopedLock&)
{
    if (aborted) {
        throw TransactionAborted("Transaction implicitly aborted");
    }
}

void SessionImpl::checkError()
{
    ScopedLock l(lock);
    checkAbortedLH(l);
    qpid::client::SessionBase_0_10Access s(session);
    try {
        s.get()->assertOpen();
    } catch (const qpid::TransportFailure&) {
        throw qpid::messaging::TransportFailure(std::string());
    } catch (const qpid::framing::ResourceLimitExceededException& e) {
        throw qpid::messaging::TargetCapacityExceeded(e.what());
    } catch (const qpid::framing::UnauthorizedAccessException& e) {
        throw qpid::messaging::UnauthorizedAccess(e.what());
    } catch (const qpid::SessionException& e) {
        throw qpid::messaging::SessionError(e.what());
    } catch (const qpid::ConnectionException& e) {
        throw qpid::messaging::ConnectionError(e.what());
    } catch (const qpid::Exception& e) {
        throw qpid::messaging::MessagingException(e.what());
    }
}

bool SessionImpl::hasError()
{
    ScopedLock l(lock);
    qpid::client::SessionBase_0_10Access s(session);
    return s.get()->hasError();
}

void SessionImpl::sync(bool block)
{
    if (block) retry<Sync>();
    else execute<NonBlockingSync>();
}

void SessionImpl::commit()
{
    if (!execute<Commit>()) {
        throw TransactionAborted("Transaction aborted due to transport failure");
    }
}

void SessionImpl::rollback()
{
    //If the session fails during this operation, the transaction will
    //be rolled back anyway.
    execute<Rollback>();
}

void SessionImpl::acknowledge(bool sync_)
{
    //Should probably throw an exception on failure here, or indicate
    //it through a return type at least. Failure means that the
    //message may be redelivered; i.e. the application cannot delete
    //any state necessary for preventing reprocessing of the message
    execute<Acknowledge>();
    sync(sync_);
}

void SessionImpl::reject(qpid::messaging::Message& m)
{
    //Possibly want to somehow indicate failure here as well. Less
    //clear need as compared to acknowledge however.
    execute1<Reject>(m);
}

void SessionImpl::release(qpid::messaging::Message& m)
{
    execute1<Release>(m);
}

void SessionImpl::acknowledge(qpid::messaging::Message& m, bool cumulative)
{
    //Should probably throw an exception on failure here, or indicate
    //it through a return type at least. Failure means that the
    //message may be redelivered; i.e. the application cannot delete
    //any state necessary for preventing reprocessing of the message
    Acknowledge2 ack(*this, m, cumulative);
    execute(ack);
}

void SessionImpl::close()
{
    if (hasError()) {
        ScopedLock l(lock);
        senders.clear();
        receivers.clear();
    } else {
        Senders sCopy;
        Receivers rCopy;
        {
            ScopedLock l(lock);
            senders.swap(sCopy);
            receivers.swap(rCopy);
        }
        for (Senders::iterator i = sCopy.begin(); i != sCopy.end(); ++i)
        {
            // outside the lock, will call senderCancelled
            i->second.close();
        }
        for (Receivers::iterator i = rCopy.begin(); i != rCopy.end(); ++i)
        {
            // outside the lock, will call receiverCancelled
            i->second.close();
        }
    }
    connection->closed(*this);
    if (!hasError()) {
        ScopedLock l(lock);
        session.close();
    }
}

template <class T, class S> boost::intrusive_ptr<S> getImplPtr(T& t)
{
    return boost::dynamic_pointer_cast<S>(qpid::messaging::PrivateImplRef<T>::get(t));
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
    session = s;
    incoming.setSession(session);
    if (transactional) {
        session.txSelect();
    }
    for (Receivers::iterator i = receivers.begin(); i != receivers.end(); ++i) {
        getImplPtr<Receiver, ReceiverImpl>(i->second)->init(session, resolver);
    }
    for (Senders::iterator i = senders.begin(); i != senders.end(); ++i) {
        getImplPtr<Sender, SenderImpl>(i->second)->init(session, resolver);
    }
    session.sync();
}

struct SessionImpl::CreateReceiver : Command
{
    qpid::messaging::Receiver result;
    const qpid::messaging::Address& address;

    CreateReceiver(SessionImpl& i, const qpid::messaging::Address& a) :
        Command(i), address(a) {}
    void operator()() { result = impl.createReceiverImpl(address); }
};

Receiver SessionImpl::createReceiver(const qpid::messaging::Address& address)
{
    return get1<CreateReceiver, Receiver>(address);
}

Receiver SessionImpl::createReceiverImpl(const qpid::messaging::Address& address)
{
    ScopedLock l(lock);
    std::string name = address.getName();
    getFreeKey(name, receivers);
    Receiver receiver(new ReceiverImpl(*this, name, address, connection->getAutoDecode()));
    getImplPtr<Receiver, ReceiverImpl>(receiver)->init(session, resolver);
    receivers[name] = receiver;
    return receiver;
}

struct SessionImpl::CreateSender : Command
{
    qpid::messaging::Sender result;
    const qpid::messaging::Address& address;

    CreateSender(SessionImpl& i, const qpid::messaging::Address& a) :
        Command(i), address(a) {}
    void operator()() { result = impl.createSenderImpl(address); }
};

Sender SessionImpl::createSender(const qpid::messaging::Address& address)
{
    return get1<CreateSender, Sender>(address);
}

Sender SessionImpl::createSenderImpl(const qpid::messaging::Address& address)
{
    ScopedLock l(lock);
    std::string name = address.getName();
    getFreeKey(name, senders);
    Sender sender(new SenderImpl(*this, name, address, connection->getAutoReconnect()));
    getImplPtr<Sender, SenderImpl>(sender)->init(session, resolver);
    senders[name] = sender;
    return sender;
}

Sender SessionImpl::getSender(const std::string& name) const
{
    qpid::sys::Mutex::ScopedLock l(lock);
    Senders::const_iterator i = senders.find(name);
    if (i == senders.end()) {
        throw KeyError(name);
    } else {
        return i->second;
    }
}

Receiver SessionImpl::getReceiver(const std::string& name) const
{
    qpid::sys::Mutex::ScopedLock l(lock);
    Receivers::const_iterator i = receivers.find(name);
    if (i == receivers.end()) {
        throw KeyError(name);
    } else {
        return i->second;
    }
}

SessionImpl& SessionImpl::convert(qpid::messaging::Session& s)
{
    boost::intrusive_ptr<SessionImpl> impl = getImplPtr<qpid::messaging::Session, SessionImpl>(s);
    if (!impl) {
        throw SessionError(QPID_MSG("Configuration error; require qpid::client::amqp0_10::SessionImpl"));
    }
    return *impl;
}

namespace {

struct IncomingMessageHandler : IncomingMessages::Handler
{
    typedef boost::function1<bool, IncomingMessages::MessageTransfer&> Callback;
    Callback callback;
    ReceiverImpl* receiver;

    IncomingMessageHandler(Callback c) : callback(c), receiver(0) {}

    bool accept(IncomingMessages::MessageTransfer& transfer)
    {
        return callback(transfer);
    }

    bool isClosed()
    {
        return receiver && receiver->isClosed();
    }
};

}


bool SessionImpl::getNextReceiver(Receiver* receiver, IncomingMessages::MessageTransfer& transfer)
{
    ScopedLock l(lock);
    Receivers::const_iterator i = receivers.find(transfer.getDestination());
    if (i == receivers.end()) {
        QPID_LOG(error, "Received message for unknown destination " << transfer.getDestination());
        return false;
    } else {
        *receiver = i->second;
        return true;
    }
}

bool SessionImpl::accept(ReceiverImpl* receiver,
                         qpid::messaging::Message* message,
                         IncomingMessages::MessageTransfer& transfer)
{
    if (receiver->getName() == transfer.getDestination()) {
        transfer.retrieve(message);
        receiver->received(*message);
        return true;
    } else {
        return false;
    }
}

qpid::sys::Duration adjust(qpid::messaging::Duration timeout)
{
    uint64_t ms = timeout.getMilliseconds();
    if (ms < (uint64_t) (qpid::sys::TIME_INFINITE/qpid::sys::TIME_MSEC)) {
        return ms * qpid::sys::TIME_MSEC;
    } else {
        return qpid::sys::TIME_INFINITE;
    }
}

bool SessionImpl::getIncoming(IncomingMessages::Handler& handler, qpid::messaging::Duration timeout)
{
    return incoming.get(handler, adjust(timeout));
}

bool SessionImpl::get(ReceiverImpl& receiver, qpid::messaging::Message& message, qpid::messaging::Duration timeout)
{
    IncomingMessageHandler handler(boost::bind(&SessionImpl::accept, this, &receiver, &message, _1));
    handler.receiver = &receiver;
    return getIncoming(handler, timeout);
}

bool SessionImpl::nextReceiver(qpid::messaging::Receiver& receiver, qpid::messaging::Duration timeout)
{
    while (true) {
        checkAborted();
        try {
            std::string destination;
            if (incoming.getNextDestination(destination, adjust(timeout))) {
                qpid::sys::Mutex::ScopedLock l(lock);
                Receivers::const_iterator i = receivers.find(destination);
                if (i == receivers.end()) {
                    throw qpid::messaging::ReceiverError(QPID_MSG("Received message for unknown destination " << destination));
                } else {
                    receiver = i->second;
                }
                return true;
            } else {
                return false;
            }
        } catch (TransportFailure&) {
            reconnect();
        } catch (const qpid::framing::ResourceLimitExceededException& e) {
            if (backoff()) return false;
            else throw qpid::messaging::TargetCapacityExceeded(e.what());
        } catch (const qpid::framing::UnauthorizedAccessException& e) {
            throw qpid::messaging::UnauthorizedAccess(e.what());
        } catch (const qpid::SessionException& e) {
            throw qpid::messaging::SessionError(e.what());
        } catch (const qpid::ClosedException&) {
            throw qpid::messaging::SessionClosed();
        } catch (const qpid::ConnectionException& e) {
            throw qpid::messaging::ConnectionError(e.what());
        } catch (const qpid::ChannelException& e) {
            throw qpid::messaging::MessagingException(e.what());
        }
    }
}

qpid::messaging::Receiver SessionImpl::nextReceiver(qpid::messaging::Duration timeout)
{
    qpid::messaging::Receiver receiver;
    if (!nextReceiver(receiver, timeout)) throw NoMessageAvailable();
    if (!receiver) throw SessionError("Bad receiver returned!");
    return receiver;
}

uint32_t SessionImpl::getReceivable()
{
    return get1<Receivable, uint32_t>((const std::string*) 0);
}
uint32_t SessionImpl::getReceivable(const std::string& destination)
{
    return get1<Receivable, uint32_t>(&destination);
}

struct SessionImpl::Receivable : Command
{
    const std::string* destination;
    uint32_t result;

    Receivable(SessionImpl& i, const std::string* d) : Command(i), destination(d), result(0) {}
    void operator()() { result = impl.getReceivableImpl(destination); }
};

uint32_t SessionImpl::getReceivableImpl(const std::string* destination)
{
    ScopedLock l(lock);
    if (destination) {
        return incoming.available(*destination);
    } else {
        return incoming.available();
    }
}

uint32_t SessionImpl::getUnsettledAcks()
{
    return get1<UnsettledAcks, uint32_t>((const std::string*) 0);
}

uint32_t SessionImpl::getUnsettledAcks(const std::string& destination)
{
    return get1<UnsettledAcks, uint32_t>(&destination);
}

struct SessionImpl::UnsettledAcks : Command
{
    const std::string* destination;
    uint32_t result;

    UnsettledAcks(SessionImpl& i, const std::string* d) : Command(i), destination(d), result(0) {}
    void operator()() { result = impl.getUnsettledAcksImpl(destination); }
};

uint32_t SessionImpl::getUnsettledAcksImpl(const std::string* destination)
{
    ScopedLock l(lock);
    if (destination) {
        return incoming.pendingAccept(*destination);
    } else {
        return incoming.pendingAccept();
    }
}

void SessionImpl::syncImpl(bool block)
{
    {
        ScopedLock l(lock);
        if (block) session.sync();
        else session.flush();
    }
    //cleanup unconfirmed accept records:
    incoming.pendingAccept();
}

void SessionImpl::commitImpl()
{
    ScopedLock l(lock);
    incoming.accept();
    session.txCommit();
}

void SessionImpl::rollbackImpl()
{
    ScopedLock l(lock);
    for (Receivers::iterator i = receivers.begin(); i != receivers.end(); ++i) {
        getImplPtr<Receiver, ReceiverImpl>(i->second)->stop();
    }
    //ensure that stop has been processed and all previously sent
    //messages are available for release:
    session.sync();
    incoming.releaseAll();
    session.txRollback();

    for (Receivers::iterator i = receivers.begin(); i != receivers.end(); ++i) {
        getImplPtr<Receiver, ReceiverImpl>(i->second)->start();
    }
}

void SessionImpl::acknowledgeImpl()
{
    if (!transactional) incoming.accept();
}

void SessionImpl::acknowledgeImpl(qpid::messaging::Message& m, bool cumulative)
{
    if (!transactional) incoming.accept(MessageImplAccess::get(m).getInternalId(), cumulative);
}

void SessionImpl::rejectImpl(qpid::messaging::Message& m)
{
    SequenceSet set;
    set.add(MessageImplAccess::get(m).getInternalId());
    session.messageReject(set);
}

void SessionImpl::releaseImpl(qpid::messaging::Message& m)
{
    SequenceSet set;
    set.add(MessageImplAccess::get(m).getInternalId());
    session.messageRelease(set, true);
}

void SessionImpl::receiverCancelled(const std::string& name)
{
    {
        ScopedLock l(lock);
        receivers.erase(name);
        session.sync();
        incoming.releasePending(name);
    }
    incoming.wakeup();
}

void SessionImpl::releasePending(const std::string& name)
{
    ScopedLock l(lock);
    incoming.releasePending(name);
}

void SessionImpl::senderCancelled(const std::string& name)
{
    ScopedLock l(lock);
    senders.erase(name);
}

void SessionImpl::reconnect()
{
    if (transactional) abortTransaction();
    connection->reopen();
}

bool SessionImpl::backoff()
{
    return connection->backoff();
}

qpid::messaging::Connection SessionImpl::getConnection() const
{
    return qpid::messaging::Connection(connection.get());
}

}}} // namespace qpid::client::amqp0_10
