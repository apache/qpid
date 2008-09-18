#ifndef QPID_CLUSTER_CONNECTION_H
#define QPID_CLUSTER_CONNECTION_H

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

#include "types.h"
#include "WriteEstimate.h"
#include "OutputInterceptor.h"
#include "NoOpConnectionOutputHandler.h"

#include "qpid/broker/Connection.h"
#include "qpid/amqp_0_10/Connection.h"
#include "qpid/sys/ConnectionInputHandler.h"
#include "qpid/sys/ConnectionOutputHandler.h"
#include "qpid/framing/FrameDecoder.h"
#include "qpid/framing/SequenceNumber.h"

namespace qpid {

namespace framing { class AMQFrame; }

namespace cluster {
class Cluster;

/** Intercept broker::Connection calls for shadow and local cluster connections. */
class Connection :
        public RefCounted,
        public sys::ConnectionInputHandler,
        public sys::ConnectionOutputHandler,
        public framing::AMQP_AllOperations::ClusterConnectionHandler
        
{
  public:
    /** Local connection, use this in ConnectionId */
    Connection(Cluster&, sys::ConnectionOutputHandler& out, const std::string& id, MemberId, bool catchUp);
    /** Shadow connection */
    Connection(Cluster&, sys::ConnectionOutputHandler& out, const std::string& id, ConnectionId);
    ~Connection();
    
    ConnectionId getId() const { return self; }
    broker::Connection& getBrokerConnection() { return connection; }
    bool isLocal() const;

    /** True if the connection is in "catch-up" mode: building initial state */
    bool isCatchUp() const { return catchUp; }
    bool isExCatchUp() const { return exCatchUp; }


    Cluster& getCluster() { return cluster; }

    // ConnectionOutputHandler methods
    void close() {}
    void send(framing::AMQFrame&) {}
    void activateOutput() {}
    virtual size_t getBuffered() const { assert(0); return 0; }

    // ConnectionInputHandler methods
    void received(framing::AMQFrame&);
    void closed();
    bool doOutput();
    bool hasOutput() { return connection.hasOutput(); }
    void idleOut() { connection.idleOut(); }
    void idleIn() { connection.idleIn(); }

    // ConnectionCodec methods
    size_t decode(const char* buffer, size_t size);

    // Called by cluster to deliver a buffer from CPG.
    void deliverBuffer(framing::Buffer&);


    // ==== Used in catch-up mode to build initial state.
    // 
    // State dump methods.
    void sessionState(const SequenceNumber& replayStart,
                              const SequenceSet& sentIncomplete,
                              const SequenceNumber& expected,
                              const SequenceNumber& received,
                              const SequenceSet& unknownCompleted, const SequenceSet& receivedIncomplete);
    
    void shadowReady(uint64_t memberId, uint64_t connectionId);

    void dumpComplete();

  private:

    void deliverClose();
    void deliverDoOutput(uint32_t requested);
    void sendDoOutput();

    Cluster& cluster;
    ConnectionId self;
    NoOpConnectionOutputHandler discardHandler;
    WriteEstimate writeEstimate;
    OutputInterceptor output;
    framing::FrameDecoder decoder;
    broker::Connection connection;
    framing::SequenceNumber mcastSeq;
    framing::SequenceNumber deliverSeq;
    bool catchUp;
    bool exCatchUp;
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_CONNECTION_H*/
