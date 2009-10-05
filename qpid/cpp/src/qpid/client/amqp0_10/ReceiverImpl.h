#ifndef QPID_CLIENT_AMQP0_10_RECEIVERIMPL_H
#define QPID_CLIENT_AMQP0_10_RECEIVERIMPL_H

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
#include "qpid/messaging/Message.h"
#include "qpid/messaging/ReceiverImpl.h"
#include "qpid/client/AsyncSession.h"
#include "qpid/sys/Time.h"
#include <memory>

namespace qpid {
namespace client {
namespace amqp0_10 {

class MessageSource;
class SessionImpl;

/**
 * A receiver implementation based on an AMQP 0-10 subscription.
 */
class ReceiverImpl : public qpid::messaging::ReceiverImpl
{
  public:

    ReceiverImpl(SessionImpl& parent, const std::string& name, std::auto_ptr<MessageSource> source);

    bool get(qpid::messaging::Message& message, qpid::sys::Duration timeout);
    qpid::messaging::Message get(qpid::sys::Duration timeout);
    bool fetch(qpid::messaging::Message& message, qpid::sys::Duration timeout);
    qpid::messaging::Message fetch(qpid::sys::Duration timeout);
    void cancel();
    void start();
    void stop();
    void subscribe();
    void setSession(qpid::client::AsyncSession s);
    const std::string& getName() const;
    void setCapacity(uint32_t);
    void setListener(qpid::messaging::MessageListener* listener);
    qpid::messaging::MessageListener* getListener();
    void received(qpid::messaging::Message& message);
  private:
    SessionImpl& parent;
    const std::auto_ptr<MessageSource> source;
    const std::string destination;
    const uint32_t byteCredit;
    
    uint32_t capacity;
    qpid::client::AsyncSession session;
    bool started;
    bool cancelled;
    qpid::messaging::MessageListener* listener;
    uint32_t window;
};

}}} // namespace qpid::client::amqp0_10

#endif  /*!QPID_CLIENT_AMQP0_10_RECEIVERIMPL_H*/
