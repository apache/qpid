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
#include "qpid/messaging/Variant.h"
#include "qpid/client/Session.h"
#include "qpid/client/SubscriptionManager.h"
#include "qpid/client/amqp0_10/AddressResolution.h"
#include "qpid/client/amqp0_10/IncomingMessages.h"
#include "qpid/sys/Mutex.h"

namespace qpid {

namespace messaging {
struct Address;
struct Filter;
class Message;
class Receiver;
class Sender;
class Session;
}

namespace client {
namespace amqp0_10 {

class ReceiverImpl;
class SenderImpl;

/**
 * Implementation of the protocol independent Session interface using
 * AMQP 0-10.
 */
class SessionImpl : public qpid::messaging::SessionImpl
{
  public:
    SessionImpl(qpid::client::Session);
    void commit();
    void rollback();
    void acknowledge();
    void reject(qpid::messaging::Message&);
    void close();
    void sync();
    void flush();
    qpid::messaging::Address createTempQueue(const std::string& baseName);
    qpid::messaging::Sender createSender(const qpid::messaging::Address& address,
                                         const qpid::messaging::VariantMap& options);
    qpid::messaging::Receiver createReceiver(const qpid::messaging::Address& address,
                                             const qpid::messaging::VariantMap& options);
    qpid::messaging::Receiver createReceiver(const qpid::messaging::Address& address, 
                                             const qpid::messaging::Filter& filter,
                                             const qpid::messaging::VariantMap& options);

    void* getLastConfirmedSent();
    void* getLastConfirmedAcknowledged();

    bool fetch(qpid::messaging::Message& message, qpid::sys::Duration timeout);
    qpid::messaging::Message fetch(qpid::sys::Duration timeout);
    bool dispatch(qpid::sys::Duration timeout);

    bool get(ReceiverImpl& receiver, qpid::messaging::Message& message, qpid::sys::Duration timeout);    

    void receiverCancelled(const std::string& name);
    void senderCancelled(const std::string& name);
    
    static SessionImpl& convert(qpid::messaging::Session&);

    qpid::client::Session session;
  private:
    typedef std::map<std::string, qpid::messaging::Receiver> Receivers;
    typedef std::map<std::string, qpid::messaging::Sender> Senders;

    qpid::sys::Mutex lock;
    AddressResolution resolver;
    IncomingMessages incoming;
    Receivers receivers;
    Senders senders;

    qpid::messaging::Receiver addReceiver(const qpid::messaging::Address& address, 
                                          const qpid::messaging::Filter* filter, 
                                          const qpid::messaging::VariantMap& options);
    bool acceptAny(qpid::messaging::Message*, bool, IncomingMessages::MessageTransfer&);
    bool accept(ReceiverImpl*, qpid::messaging::Message*, bool, IncomingMessages::MessageTransfer&);
    bool getIncoming(IncomingMessages::Handler& handler, qpid::sys::Duration timeout);
};
}}} // namespace qpid::client::amqp0_10

#endif  /*!QPID_CLIENT_AMQP0_10_SESSIONIMPL_H*/
