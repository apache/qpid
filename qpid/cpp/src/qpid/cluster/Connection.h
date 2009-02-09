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
#include "EventFrame.h"

#include "qpid/broker/Connection.h"
#include "qpid/amqp_0_10/Connection.h"
#include "qpid/sys/AtomicValue.h"
#include "qpid/sys/ConnectionInputHandler.h"
#include "qpid/sys/ConnectionOutputHandler.h"
#include "qpid/framing/FrameDecoder.h"
#include "qpid/framing/SequenceNumber.h"

#include <iosfwd>

namespace qpid {

namespace framing { class AMQFrame; }

namespace broker {
class SemanticState;
class QueuedMessage;
class TxBuffer;
class TxAccept;
}

namespace cluster {
class Cluster;
class Event;

/** Intercept broker::Connection calls for shadow and local cluster connections. */
class Connection :
        public RefCounted,
        public sys::ConnectionInputHandler,
        public framing::AMQP_AllOperations::ClusterConnectionHandler
        
{
  public:
    typedef sys::PollableQueue<EventFrame> PollableFrameQueue;

    /** Local connection, use this in ConnectionId */
    Connection(Cluster&, sys::ConnectionOutputHandler& out, const std::string& id, MemberId, bool catchUp, bool isLink);
    /** Shadow connection */
    Connection(Cluster&, sys::ConnectionOutputHandler& out, const std::string& id, ConnectionId);
    ~Connection();
    
    ConnectionId getId() const { return self; }
    broker::Connection& getBrokerConnection() { return connection; }

    /** Local connections may be clients or catch-up connections */
    bool isLocal() const;

    bool isLocalClient() const { return isLocal() && !isCatchUp(); }

    /** True for connections that are shadowing remote broker connections */
    bool isShadow() const;

    /** True if the connection is in "catch-up" mode: building initial broker state. */
    bool isCatchUp() const { return catchUp; }

    /** True if the connection is a completed shared update connection */
    bool isUpdated() const;

    Cluster& getCluster() { return cluster; }

    // ConnectionInputHandler methods
    void received(framing::AMQFrame&);
    void closed();
    bool doOutput();
    bool hasOutput() { return connection.hasOutput(); }
    void idleOut() { connection.idleOut(); }
    void idleIn() { connection.idleIn(); }

    /** Called if the connectors member has left the cluster */
    void left();
    
    // ConnectionCodec methods
    size_t decode(const char* buffer, size_t size);

    // Called for data delivered from the cluster.
    void deliveredFrame(const EventFrame&);

    void consumerState(const std::string& name, bool blocked, bool notifyEnabled);
    
    // ==== Used in catch-up mode to build initial state.
    // 
    // State update methods.
    void sessionState(const framing::SequenceNumber& replayStart,
                      const framing::SequenceNumber& sendCommandPoint,
                      const framing::SequenceSet& sentIncomplete,
                      const framing::SequenceNumber& expected,
                      const framing::SequenceNumber& received,
                      const framing::SequenceSet& unknownCompleted, const SequenceSet& receivedIncomplete);
    
    void shadowReady(uint64_t memberId, uint64_t connectionId);

    void membership(const framing::FieldTable&, const framing::FieldTable&, uint64_t frameId);

    void deliveryRecord(const std::string& queue,
                        const framing::SequenceNumber& position,
                        const std::string& tag,
                        const framing::SequenceNumber& id,
                        bool acquired,
                        bool accepted,
                        bool cancelled,
                        bool completed,
                        bool ended,
                        bool windowing,
                        uint32_t credit);

    void queuePosition(const std::string&, const framing::SequenceNumber&);

    void txStart();
    void txAccept(const framing::SequenceSet&);
    void txDequeue(const std::string&);
    void txEnqueue(const std::string&);
    void txPublish(const qpid::framing::Array&, bool);
    void txEnd();
    void accumulatedAck(const qpid::framing::SequenceSet&);

    // Encoded queue/exchange replication.
    void queue(const std::string& encoded);
    void exchange(const std::string& encoded);
    
  private:
    void init();
    bool checkUnsupported(const framing::AMQBody& body);
    void deliverClose();
    void deliverDoOutput(uint32_t requested);
    void sendDoOutput();

    boost::shared_ptr<broker::Queue> findQueue(const std::string& qname);
    broker::SessionState& sessionState();
    broker::SemanticState& semanticState();
    broker::QueuedMessage getUpdateMessage();

    static NoOpConnectionOutputHandler discardHandler;

    Cluster& cluster;
    ConnectionId self;
    bool catchUp;
    WriteEstimate writeEstimate;
    OutputInterceptor output;
    framing::FrameDecoder localDecoder;
    broker::Connection connection;
    framing::SequenceNumber deliverSeq;
    framing::ChannelId currentChannel;
    boost::shared_ptr<broker::TxBuffer> txBuffer;
    int readCredit;
    bool expectProtocolHeader;

    static qpid::sys::AtomicValue<uint64_t> catchUpId;
    
  friend std::ostream& operator<<(std::ostream&, const Connection&);
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_CONNECTION_H*/
