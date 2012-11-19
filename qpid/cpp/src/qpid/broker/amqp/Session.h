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
#include "qpid/broker/amqp/ManagedSession.h"
#include <deque>
#include <map>
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

class ManagedConnection;
class Outgoing;
class Target;
/**
 *
 */
class Session : public ManagedSession, public boost::enable_shared_from_this<Session>
{
  public:
    Session(pn_session_t*, qpid::broker::Broker&, ManagedConnection&, qpid::sys::OutputControl&);
    void attach(pn_link_t*);
    void detach(pn_link_t*);
    void incoming(pn_link_t*, pn_delivery_t*);
    void outgoing(pn_link_t*, pn_delivery_t*);
    bool dispatch();
    void close();

    //called when a transfer is completly processed (e.g.including stored on disk)
    void accepted(pn_delivery_t*, bool sync);
  private:
    typedef std::map<pn_link_t*, boost::shared_ptr<Outgoing> > Senders;
    typedef std::map<pn_link_t*, boost::shared_ptr<Target> > Targets;
    pn_session_t* session;
    qpid::broker::Broker& broker;
    ManagedConnection& connection;
    qpid::sys::OutputControl& out;
    Targets targets;
    Senders senders;
    std::deque<pn_delivery_t*> completed;
    bool deleted;
    qpid::sys::Mutex lock;
    struct ResolvedNode
    {
        boost::shared_ptr<qpid::broker::Exchange> exchange;
        boost::shared_ptr<qpid::broker::Queue> queue;
    };

    ResolvedNode resolve(const std::string name, pn_terminus_t* terminus);
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP1_SESSION_H*/
