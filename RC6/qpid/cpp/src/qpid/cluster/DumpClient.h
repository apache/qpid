#ifndef QPID_CLUSTER_DUMPCLIENT_H
#define QPID_CLUSTER_DUMPCLIENT_H

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

#include "ClusterMap.h"
#include "qpid/client/Connection.h"
#include "qpid/client/AsyncSession.h"
#include "qpid/broker/SemanticState.h"
#include "qpid/sys/Runnable.h"
#include <boost/shared_ptr.hpp>


namespace qpid {

class Url;

namespace broker {

class Broker;
class Queue;
class Exchange;
class QueueBindings;
class QueueBinding;
class QueuedMessage;
class SessionHandler;
class DeliveryRecord;
class SessionState;
class SemanticState;

} // namespace broker

namespace cluster {

class Cluster;
class Connection;
class ClusterMap;

/**
 * A client that dumps the contents of a local broker to a remote one using AMQP.
 */
class DumpClient : public sys::Runnable {
  public:
    static const std::string DUMP; // Name for special dump queue and exchange.
    
    DumpClient(const MemberId& dumper, const MemberId& dumpee, const Url&,
               broker::Broker& donor, const ClusterMap& map, const std::vector<boost::intrusive_ptr<Connection> >& ,
               const boost::function<void()>& done,
               const boost::function<void(const std::exception&)>& fail);

    ~DumpClient();
    void dump();
    void run();                 // Will delete this when finished.

    void dumpUnacked(const broker::DeliveryRecord&);

  private:
    void dumpQueue(const boost::shared_ptr<broker::Queue>&);
    void dumpExchange(const boost::shared_ptr<broker::Exchange>&);
    void dumpMessage(const broker::QueuedMessage&);
    void dumpMessageTo(const broker::QueuedMessage&, const std::string& queue, client::Session s);
    void dumpBinding(const std::string& queue, const broker::QueueBinding& binding);
    void dumpConnection(const boost::intrusive_ptr<Connection>& connection);
    void dumpSession(broker::SessionHandler& s);
    void dumpTxState(broker::SemanticState& s);
    void dumpConsumer(const broker::SemanticState::ConsumerImpl*);

    MemberId dumperId;
    MemberId dumpeeId;
    Url dumpeeUrl;
    broker::Broker& dumperBroker;
    ClusterMap map;
    std::vector<boost::intrusive_ptr<Connection> > connections;
    client::Connection connection, shadowConnection;
    client::AsyncSession session, shadowSession;
    boost::function<void()> done;
    boost::function<void(const std::exception& e)> failed;
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_DUMPCLIENT_H*/
