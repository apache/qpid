#ifndef QPID_BROKER_AMQP1_SESSION_H
#define QPID_BROKER_AMQP1_SESSION_H

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
#include "qpid/sys/Mutex.h"
#include "qpid/sys/OutputControl.h"
#include "qpid/broker/amqp/Authorise.h"
#include "qpid/broker/amqp/ManagedSession.h"
#include "qpid/broker/amqp/NodeProperties.h"
#include <deque>
#include <map>
#include <set>
#include <boost/shared_ptr.hpp>
#include <boost/enable_shared_from_this.hpp>

struct pn_delivery_t;
struct pn_link_t;
struct pn_session_t;
struct pn_terminus_t;

namespace qpid {
namespace broker {

class Broker;
class Exchange;
class Queue;

namespace amqp {

class Connection;
class Incoming;
class Outgoing;
class Relay;
/**
 *
 */
class Session : public ManagedSession, public boost::enable_shared_from_this<Session>
{
  public:
    Session(pn_session_t*, qpid::broker::Broker&, Connection&, qpid::sys::OutputControl&);
    /**
     * called for links initiated by the peer
     */
    void attach(pn_link_t*);
    void detach(pn_link_t*);
    void readable(pn_link_t*, pn_delivery_t*);
    void writable(pn_link_t*, pn_delivery_t*);
    bool dispatch();
    void close();

    /**
     * called for links initiated by the broker
     */
    void attach(pn_link_t* link, const std::string& src, const std::string& tgt, boost::shared_ptr<Relay>);

    //called when a transfer is completly processed (e.g.including stored on disk)
    void accepted(pn_delivery_t*, bool sync);

    void wakeup();

    Authorise& getAuthorise();
  private:
    typedef std::map<pn_link_t*, boost::shared_ptr<Outgoing> > OutgoingLinks;
    typedef std::map<pn_link_t*, boost::shared_ptr<Incoming> > IncomingLinks;
    pn_session_t* session;
    qpid::broker::Broker& broker;
    Connection& connection;
    qpid::sys::OutputControl& out;
    IncomingLinks incoming;
    OutgoingLinks outgoing;
    std::deque<pn_delivery_t*> completed;
    bool deleted;
    qpid::sys::Mutex lock;
    std::set< boost::shared_ptr<Queue> > exclusiveQueues;
    Authorise authorise;

    struct ResolvedNode
    {
        boost::shared_ptr<qpid::broker::Exchange> exchange;
        boost::shared_ptr<qpid::broker::Queue> queue;
        boost::shared_ptr<Relay> relay;
        NodeProperties properties;
    };

    ResolvedNode resolve(const std::string name, pn_terminus_t* terminus, bool incoming);
    void setupOutgoing(pn_link_t* link, pn_terminus_t* source, const std::string& name);
    void setupIncoming(pn_link_t* link, pn_terminus_t* target, const std::string& name);
    std::string generateName(pn_link_t*);
    std::string qualifyName(const std::string&);
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP1_SESSION_H*/
