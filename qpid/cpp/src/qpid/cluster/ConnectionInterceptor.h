#ifndef QPID_CLUSTER_CONNECTIONPLUGIN_H
#define QPID_CLUSTER_CONNECTIONPLUGIN_H

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

#include "Cluster.h"
#include "qpid/broker/Connection.h"
#include "qpid/sys/ConnectionOutputHandler.h"

namespace qpid {
namespace framing { class AMQFrame; }
namespace cluster {

/**
 * Plug-in associated with broker::Connections, both local and shadow.
 */
class ConnectionInterceptor {
  public:
    ConnectionInterceptor(broker::Connection&, Cluster&,
                          Cluster::ShadowConnectionId shadowId=Cluster::ShadowConnectionId(0,0));
    ~ConnectionInterceptor();
    
    Cluster::ShadowConnectionId getShadowId() const { return shadowId; }

    bool isLocal() const { return shadowId == Cluster::ShadowConnectionId(0,0); }

    // self-delivery of intercepted extension points.
    void deliver(framing::AMQFrame& f);
    void deliverClosed();
    void deliverDoOutput();

  private:
    struct NullConnectionHandler : public qpid::sys::ConnectionOutputHandler {
        void close() {}
        void send(framing::AMQFrame&) {}
        void doOutput() {}
        void activateOutput() {}
    };
    
    // Functions to intercept to Connection extension points.
    void received(framing::AMQFrame&);
    void closed();
    bool doOutput();

    boost::function<void (framing::AMQFrame&)> receivedNext;
    boost::function<void ()> closedNext;
    boost::function<bool ()> doOutputNext;

    boost::intrusive_ptr<broker::Connection> connection;
    Cluster& cluster;
    NullConnectionHandler discardHandler;
    bool isClosed;
    Cluster::ShadowConnectionId shadowId;
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_CONNECTIONPLUGIN_H*/

