#ifndef QPID_BROKER_AMQP_RELAY_H
#define QPID_BROKER_AMQP_RELAY_H

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
#include "Incoming.h"
#include "Outgoing.h"
#include "qpid/sys/Mutex.h"
extern "C" {
#include <proton/engine.h>
}
#include <deque>

namespace qpid {
namespace broker {
namespace amqp {

struct Delivery
{
    bool settled;
    pn_delivery_t* handle;

    Delivery();
    Delivery(pn_delivery_t* d);
};

class BufferedTransfer
{
  public:
    BufferedTransfer();
    void initIn(pn_link_t* link, pn_delivery_t* d);
    bool settle();
    void initOut(pn_link_t* link);
    uint64_t updated();
    bool write(pn_link_t*);
  private:
    std::vector<char> data;
    Delivery in;
    Delivery out;
    pn_delivery_tag_t dt;
    std::vector<char> tag;
    uint64_t disposition;
};

/**
 *
 */
class Relay
{
  public:
    Relay(size_t max);
    void check();
    size_t size() const;
    BufferedTransfer& front();
    void pop();
    bool send(pn_link_t*);
    void received(pn_link_t* link, pn_delivery_t* delivery);
    int getCredit() const;
    void setCredit(int);
    void attached(Outgoing*);
    void attached(Incoming*);
    void detached(Outgoing*);
    void detached(Incoming*);
  private:
    std::deque<BufferedTransfer> buffer;//TODO: optimise by replacing with simple circular array
    int credit;//issued by outgoing peer, decremented everytime we send a message on outgoing link
    size_t max;
    size_t head;
    size_t tail;
    bool isDetached;
    Outgoing* out;
    Incoming* in;
    mutable qpid::sys::Mutex lock;

    BufferedTransfer& push();
};

class OutgoingFromRelay : public Outgoing
{
  public:
    OutgoingFromRelay(pn_link_t*, Broker&, Session&, const std::string& source,
                      const std::string& target, const std::string& name, boost::shared_ptr<Relay>);
    bool doWork();
    void handle(pn_delivery_t* delivery);
    void detached(bool closed);
    void init();
    void setSubjectFilter(const std::string&);
    void setSelectorFilter(const std::string&);
  private:
    const std::string name;
    pn_link_t* link;
    boost::shared_ptr<Relay> relay;
};

class IncomingToRelay : public Incoming
{
  public:
    IncomingToRelay(pn_link_t*, Broker&, Session&, const std::string& source,
                    const std::string& target, const std::string& name, boost::shared_ptr<Relay> r);
    bool settle();
    bool doWork();
    bool haveWork();
    void detached(bool closed);
    void readable(pn_delivery_t* delivery);
    uint32_t getCredit();
  private:
    boost::shared_ptr<Relay> relay;
};

}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_RELAY_H*/
