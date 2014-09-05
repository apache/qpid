#ifndef QPID_CLIENT_AMQP0_10_SESSIONIMPL_H
#define QPID_CLIENT_AMQP0_10_SESSIONIMPL_H

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
#include "qpid/messaging/SessionImpl.h"
#include "qpid/messaging/Duration.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/client/Session.h"
#include "qpid/client/SubscriptionManager.h"
#include "qpid/client/amqp0_10/AddressResolution.h"
#include "qpid/client/amqp0_10/IncomingMessages.h"
#include "qpid/sys/Mutex.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/sys/ExceptionHolder.h"
#include <boost/intrusive_ptr.hpp>

namespace qpid {

namespace messaging {
class Address;
class Connection;
class Message;
class Receiver;
class Sender;
class Session;
}

namespace client {
namespace amqp0_10 {

class ConnectionImpl;
class ReceiverImpl;
class SenderImpl;

/**
 * Implementation of the protocol independent Session interface using
 * AMQP 0-10.
 */
class SessionImpl : public qpid::messaging::SessionImpl
{
  public:
    SessionImpl(ConnectionImpl&, bool transactional);
    void commit();
    void rollback();
    void acknowledge(bool sync);
    void reject(qpid::messaging::Message&);
    void release(qpid::messaging::Message&);
    void acknowledge(qpid::messaging::Message& msg, bool cumulative);
    void close();
    void sync(bool block);
    qpid::messaging::Sender createSender(const qpid::messaging::Address& address);
    qpid::messaging::Receiver createReceiver(const qpid::messaging::Address& address);

    qpid::messaging::Sender getSender(const std::string& name) const;
    qpid::messaging::Receiver getReceiver(const std::string& name) const;

    bool nextReceiver(qpid::messaging::Receiver& receiver, qpid::messaging::Duration timeout);
    qpid::messaging::Receiver nextReceiver(qpid::messaging::Duration timeout);

    qpid::messaging::Connection getConnection() const;
    void checkError();
    bool hasError();
    bool isTransactional() const;

    bool get(ReceiverImpl& receiver, qpid::messaging::Message& message, qpid::messaging::Duration timeout);

    void releasePending(const std::string& destination);
    void receiverCancelled(const std::string& name);
    void senderCancelled(const std::string& name);

    uint32_t getReceivable();
    uint32_t getReceivable(const std::string& destination);

    uint32_t getUnsettledAcks();
    uint32_t getUnsettledAcks(const std::string& destination);

    void setSession(qpid::client::Session);

    template <class T> bool execute(T& f)
    {
        try {
            txError.raise();
            f();
            return true;
        } catch (const qpid::TransportFailure&) {
            reconnect();
            return false;
        } catch (const qpid::framing::ResourceLimitExceededException& e) {
            if (backoff()) return false;
            else throw qpid::messaging::TargetCapacityExceeded(e.what());
        } catch (const qpid::framing::UnauthorizedAccessException& e) {
            throw qpid::messaging::UnauthorizedAccess(e.what());
        } catch (const qpid::framing::NotFoundException& e) {
            throw qpid::messaging::NotFound(e.what());
        } catch (const qpid::framing::ResourceDeletedException& e) {
            throw qpid::messaging::NotFound(e.what());
        } catch (const qpid::SessionException& e) {
            rethrow(e);
            return false;       // Keep the compiler happy
        } catch (const qpid::ConnectionException& e) {
            throw qpid::messaging::ConnectionError(e.what());
        } catch (const qpid::ChannelException& e) {
            throw qpid::messaging::MessagingException(e.what());
        }
    }

    static SessionImpl& convert(qpid::messaging::Session&);
    static void rethrow(const qpid::SessionException&);

  private:
    typedef std::map<std::string, qpid::messaging::Receiver> Receivers;
    typedef std::map<std::string, qpid::messaging::Sender> Senders;

    mutable qpid::sys::Mutex lock;
    boost::intrusive_ptr<ConnectionImpl> connection;
    qpid::client::Session session;
    AddressResolution resolver;
    IncomingMessages incoming;
    Receivers receivers;
    Senders senders;
    const bool transactional;
    bool committing;
    sys::ExceptionHolder txError;

    bool accept(ReceiverImpl*, qpid::messaging::Message*, IncomingMessages::MessageTransfer&);
    bool getIncoming(IncomingMessages::Handler& handler, qpid::messaging::Duration timeout);
    bool getNextReceiver(qpid::messaging::Receiver* receiver, IncomingMessages::MessageTransfer& transfer);
    void reconnect();
    bool backoff();

    void commitImpl();
    void rollbackImpl();
    void acknowledgeImpl();
    void acknowledgeImpl(qpid::messaging::Message&, bool cumulative);
    void rejectImpl(qpid::messaging::Message&);
    void releaseImpl(qpid::messaging::Message&);
    void closeImpl();
    void syncImpl(bool block);
    qpid::messaging::Sender createSenderImpl(const qpid::messaging::Address& address);
    qpid::messaging::Receiver createReceiverImpl(const qpid::messaging::Address& address);
    uint32_t getReceivableImpl(const std::string* destination);
    uint32_t getUnsettledAcksImpl(const std::string* destination);

    //functors for public facing methods (allows locking and retry
    //logic to be centralised)
    struct Command
    {
        SessionImpl& impl;

        Command(SessionImpl& i) : impl(i) {}
    };

    struct Commit : Command
    {
        Commit(SessionImpl& i) : Command(i) {}
        void operator()() { impl.commitImpl(); }
    };

    struct Rollback : Command
    {
        Rollback(SessionImpl& i) : Command(i) {}
        void operator()() { impl.rollbackImpl(); }
    };

    struct Acknowledge : Command
    {
        Acknowledge(SessionImpl& i) : Command(i) {}
        void operator()() { impl.acknowledgeImpl(); }
    };

    struct Sync : Command
    {
        Sync(SessionImpl& i) : Command(i) {}
        void operator()() { impl.syncImpl(true); }
    };

    struct NonBlockingSync : Command
    {
        NonBlockingSync(SessionImpl& i) : Command(i) {}
        void operator()() { impl.syncImpl(false); }
    };

    struct Reject : Command
    {
        qpid::messaging::Message& message;

        Reject(SessionImpl& i, qpid::messaging::Message& m) : Command(i), message(m) {}
        void operator()() { impl.rejectImpl(message); }
    };

    struct Release : Command
    {
        qpid::messaging::Message& message;

        Release(SessionImpl& i, qpid::messaging::Message& m) : Command(i), message(m) {}
        void operator()() { impl.releaseImpl(message); }
    };

    struct Acknowledge2 : Command
    {
        qpid::messaging::Message& message;
        bool cumulative;

        Acknowledge2(SessionImpl& i, qpid::messaging::Message& m, bool c) : Command(i), message(m), cumulative(c) {}
        void operator()() { impl.acknowledgeImpl(message, cumulative); }
    };

    struct CreateSender;
    struct CreateReceiver;
    struct UnsettledAcks;
    struct Receivable;

    //helper templates for some common patterns
    template <class F> bool execute()
    {
        F f(*this);
        return execute(f);
    }

    template <class F> void retry()
    {
        while (!execute<F>()) {}
    }

    template <class F, class P> bool execute1(P p)
    {
        F f(*this, p);
        return execute(f);
    }

    template <class F, class R, class P> R get1(P p)
    {
        F f(*this, p);
        while (!execute(f)) {}
        return f.result;
    }
};
}}} // namespace qpid::client::amqp0_10

#endif  /*!QPID_CLIENT_AMQP0_10_SESSIONIMPL_H*/
