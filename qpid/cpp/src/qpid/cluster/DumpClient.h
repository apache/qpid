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

#include "qpid/client/Connection.h"
#include "qpid/client/AsyncSession.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/ExchangeRegistry.h"
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
} // namespace broker

namespace cluster {

/**
 * A client that dumps the contents of a local broker to a remote one using AMQP.
 */
class DumpClient : public sys::Runnable {
  public:
    DumpClient(const Url& url, broker::Broker& donor,
               const boost::function<void()>& done,
               const boost::function<void(const std::exception&)>& fail);

    ~DumpClient();
    void dump();
    void run();                 // Will delete this when finished.

  private:
    void dumpQueue(const boost::shared_ptr<broker::Queue>&);
    void dumpExchange(const boost::shared_ptr<broker::Exchange>&);
    void dumpMessage(const broker::QueuedMessage&);
    void dumpBinding(const std::string& queue, const broker::QueueBinding& binding);

  private:
    client::Connection connection;
    client::AsyncSession session;
    broker::Broker& donor;
    boost::function<void()> done;
    boost::function<void(const std::exception& e)> failed;
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_DUMPCLIENT_H*/
