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
#include "qpid/sys/AtomicValue.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/OutputControl.h"
#include "qpid/broker/amqp/Authorise.h"
#include "qpid/broker/amqp/ManagedSession.h"
#include "qpid/broker/amqp/NodeProperties.h"
#include <deque>
#include <map>
#include <set>
#include <boost/intrusive_ptr.hpp>
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
class TxBuffer;

namespace amqp {

class Connection;
class Incoming;
class Outgoing;
class Relay;
class Topic;
/**
 *
 */
class Session : public ManagedSession, public boost::enable_shared_from_this<Session>
{
  public:
    Session(pn_session_t*, Connection&, qpid::sys::OutputControl&);
    /**
     * called for links initiated by the peer
     */
    void attach(pn_link_t*);
    void detach(pn_link_t*, bool closed);
    void readable(pn_link_t*, pn_delivery_t*);
    void writable(pn_link_t*, pn_delivery_t*);
    bool dispatch();
    bool endedByManagement() const;
    void close();

    /**
     * called for links initiated by the broker
     */
    void attach(pn_link_t* link, const std::string& src, const std::string& tgt, boost::shared_ptr<Relay>);

    //called when a transfer is completely processed (e.g.including stored on disk)
    void accepted(pn_delivery_t*, bool sync);
    //called to indicate that the delivery will be accepted asynchronously
    void pending_accept(pn_delivery_t*);
    //called when async transaction completes
    void committed(bool sync);

    void wakeup();

    Authorise& getAuthorise();

    TxBuffer* getTransaction(const std::string&);
    TxBuffer* getTransaction(pn_delivery_t*);
    std::pair<TxBuffer*,uint64_t> getTransactionalState(pn_delivery_t*);
    //transaction coordination:
    std::string declare();
    void discharge(const std::string& id, bool failed, pn_delivery_t*);
    void abort();
  protected:
    void detachedByManagement();
  private:
    typedef std::map<pn_link_t*, boost::shared_ptr<Outgoing> > OutgoingLinks;
    typedef std::map<pn_link_t*, boost::shared_ptr<Incoming> > IncomingLinks;
    pn_session_t* session;
    Connection& connection;
    qpid::sys::OutputControl& out;
    IncomingLinks incoming;
    OutgoingLinks outgoing;
    std::deque<pn_delivery_t*> completed;
    std::set<pn_delivery_t*> pending;
    bool deleted;
    qpid::sys::Mutex lock;
    std::set< boost::shared_ptr<Queue> > exclusiveQueues;
    Authorise authorise;
    bool detachRequested;

    struct Transaction {
        Transaction(Session&);
        void dischargeComplete();

        Session& session;
        boost::intrusive_ptr<TxBuffer> buffer;
        std::string id;
        qpid::sys::AtomicValue<bool> commitPending;
        pn_delivery_t* discharge;
    };
    Transaction tx;

    struct ResolvedNode
    {
        boost::shared_ptr<qpid::broker::Exchange> exchange;
        boost::shared_ptr<qpid::broker::Queue> queue;
        boost::shared_ptr<qpid::broker::amqp::Topic> topic;
        boost::shared_ptr<Relay> relay;
        NodeProperties properties;
        bool created;
        ResolvedNode(bool isDynamic) : properties(isDynamic), created(false) {}
        bool trackControllingLink() const;
    };

    ResolvedNode resolve(const std::string name, pn_terminus_t* terminus, bool incoming);
    void setupOutgoing(pn_link_t* link, pn_terminus_t* source, const std::string& name);
    void setupIncoming(pn_link_t* link, pn_terminus_t* target, const std::string& name);
    std::string generateName(pn_link_t*);
    std::string qualifyName(const std::string&);
    bool clear_pending(pn_delivery_t*);//tests and clears pending status for delivery
    void abort_pending(pn_link_t*);//removes pending status for all deliveries associated with link
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP1_SESSION_H*/
