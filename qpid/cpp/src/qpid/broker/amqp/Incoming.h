#ifndef QPID_BROKER_AMQP_INCOMING_H
#define QPID_BROKER_AMQP_INCOMING_H

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
#include "Message.h"
#include "ManagedIncomingLink.h"
extern "C" {
#include <proton/engine.h>
}
#include <boost/intrusive_ptr.hpp>
namespace qpid {
namespace broker {
class Broker;
class Message;
class TxBuffer;
namespace amqp {
class Session;

class Incoming : public ManagedIncomingLink
{
  public:
    Incoming(pn_link_t*, Broker& broker, Session& parent, const std::string& source, const std::string& target, const std::string& name);
    virtual ~Incoming();
    virtual bool doWork();//do anything that requires output
    virtual bool haveWork();//called when handling input to see whether any output work is needed
    virtual void detached(bool closed);
    virtual void readable(pn_delivery_t* delivery) = 0;
    void verify(const std::string& userid, const std::string& defaultRealm);
    void wakeup();
  protected:
    class UserId
    {
      public:
        UserId();
        void init(const std::string& userid, const std::string& defaultRealm);
        void verify(const std::string& claimed);
      private:
        std::string userid;
        bool inDefaultRealm;
        std::string unqualified;
    };

    const uint32_t credit;
    uint32_t window;


    pn_link_t* link;
    Session& session;
    UserId userid;
    virtual uint32_t getCredit();
};

class DecodingIncoming : public Incoming
{
  public:
    DecodingIncoming(pn_link_t*, Broker& broker, Session& parent, const std::string& source, const std::string& target, const std::string& name);
    virtual ~DecodingIncoming();
    void readable(pn_delivery_t* delivery);
    virtual void deliver(boost::intrusive_ptr<qpid::broker::amqp::Message> received, pn_delivery_t* delivery);
    virtual void handle(qpid::broker::Message&, qpid::broker::TxBuffer*) = 0;
  private:
    boost::shared_ptr<Session> sessionPtr;
    boost::intrusive_ptr<Message> partial;
};

}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_INCOMING_H*/
