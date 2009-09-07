#ifndef QPID_CLIENT_AMQP0_10_SENDERIMPL_H
#define QPID_CLIENT_AMQP0_10_SENDERIMPL_H

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
#include "qpid/messaging/Address.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/SenderImpl.h"
#include "qpid/messaging/Variant.h"
#include "qpid/client/AsyncSession.h"
#include "qpid/client/amqp0_10/SessionImpl.h"
#include <memory>
#include <boost/ptr_container/ptr_deque.hpp>

namespace qpid {
namespace client {
namespace amqp0_10 {

class AddressResolution;
class MessageSink;
class OutgoingMessage;

/**
 *
 */
class SenderImpl : public qpid::messaging::SenderImpl
{
  public:
    enum State {UNRESOLVED, ACTIVE, CANCELLED};

    SenderImpl(SessionImpl& parent, const std::string& name, 
               const qpid::messaging::Address& address, 
               const qpid::messaging::Variant::Map& options);
    void send(const qpid::messaging::Message&);
    void cancel();
    void init(qpid::client::AsyncSession, AddressResolution&);

  private:
    SessionImpl& parent;
    const std::string name;
    const qpid::messaging::Address address;
    const qpid::messaging::Variant::Map options;
    State state;
    std::auto_ptr<MessageSink> sink;

    qpid::client::AsyncSession session;
    std::string destination;
    std::string routingKey;

    typedef boost::ptr_deque<OutgoingMessage> OutgoingMessages;
    OutgoingMessages outgoing;
    uint32_t capacity;
    uint32_t window;

    void checkPendingSends();
    void replay();

    //logic for application visible methods:
    void sendImpl(const qpid::messaging::Message&);
    void cancelImpl();

    //functors for application visible methods (allowing locking and
    //retry to be centralised):
    struct Command
    {
        SenderImpl& impl;

        Command(SenderImpl& i) : impl(i) {}
    };

    struct Send : Command
    {
        const qpid::messaging::Message* message;

        Send(SenderImpl& i, const qpid::messaging::Message* m) : Command(i), message(m) {}
        void operator()() { impl.sendImpl(*message); }
    };

    struct Cancel : Command
    {
        Cancel(SenderImpl& i) : Command(i) {}
        void operator()() { impl.cancelImpl(); }
    };

    //helper templates for some common patterns
    template <class F> void execute()
    {
        F f(*this);
        parent.execute(f);
    }
    
    template <class F, class P> void execute1(P p)
    {
        F f(*this, p);
        parent.execute(f);
    }    
};
}}} // namespace qpid::client::amqp0_10

#endif  /*!QPID_CLIENT_AMQP0_10_SENDERIMPL_H*/
