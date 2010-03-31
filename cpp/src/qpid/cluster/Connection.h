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
#include "OutputInterceptor.h"
#include "EventFrame.h"
#include "McastFrameHandler.h"
#include "UpdateReceiver.h"

#include "qpid/broker/Connection.h"
#include "qpid/broker/SemanticState.h"
#include "qpid/amqp_0_10/Connection.h"
#include "qpid/sys/AtomicValue.h"
#include "qpid/sys/ConnectionInputHandler.h"
#include "qpid/sys/ConnectionOutputHandler.h"
#include "qpid/sys/SecuritySettings.h"
#include "qpid/framing/SequenceNumber.h"
#include "qpid/framing/FrameDecoder.h"

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
        public framing::AMQP_AllOperations::ClusterConnectionHandler,
        private broker::Connection::ErrorListener
        
{
  public:
    
    /** Local connection. */
    Connection(Cluster&, sys::ConnectionOutputHandler& out, const std::string& mgmtId, MemberId, bool catchUp, bool isLink,
               const qpid::sys::SecuritySettings& external);
    /** Shadow connection. */
    Connection(Cluster&, sys::ConnectionOutputHandler& out, const std::string& mgmtId, const ConnectionId& id,
               const qpid::sys::SecuritySettings& external);
    ~Connection();
    
    ConnectionId getId() const { return self; }
    broker::Connection& getBrokerConnection() { return *connection; }

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
    void idleOut() { if (connection.get()) connection->idleOut(); }
    void idleIn() { if (connection.get()) connection->idleIn(); }

    // ConnectionCodec methods - called by IO layer with a read buffer.
    size_t decode(const char* buffer, size_t size);

    // Called for data delivered from the cluster.
    void deliveredFrame(const EventFrame&);

    void consumerState(const std::string& name, bool blocked, bool notifyEnabled, const qpid::framing::SequenceNumber& position);
    
    // ==== Used in catch-up mode to build initial state.
    // 
    // State update methods.
    void shadowPrepare(const std::string&);

    void sessionState(const framing::SequenceNumber& replayStart,
                      const framing::SequenceNumber& sendCommandPoint,
                      const framing::SequenceSet& sentIncomplete,
                      const framing::SequenceNumber& expected,
                      const framing::SequenceNumber& received,
                      const framing::SequenceSet& unknownCompleted,
                      const SequenceSet& receivedIncomplete);
    
    void outputTask(uint16_t channel, const std::string& name);
    
    void shadowReady(uint64_t memberId,
                     uint64_t connectionId,
                     const std::string& managementId,
                     const std::string& username,
                     const std::string& fragment,
                     uint32_t sendMax);

    void membership(const framing::FieldTable&, const framing::FieldTable&,
                    const framing::SequenceNumber& frameSeq);

    void retractOffer();

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
                        bool enqueued,
                        uint32_t credit);

    void queuePosition(const std::string&, const framing::SequenceNumber&);
    void expiryId(uint64_t);

    void txStart();
    void txAccept(const framing::SequenceSet&);
    void txDequeue(const std::string&);
    void txEnqueue(const std::string&);
    void txPublish(const framing::Array&, bool);
    void txEnd();
    void accumulatedAck(const framing::SequenceSet&);

    // Encoded queue/exchange replication.
    void queue(const std::string& encoded);
    void exchange(const std::string& encoded);

    void giveReadCredit(int credit);
    void announce(const std::string& mgmtId, uint32_t ssf, const std::string& authid, bool nodict);
    void abort();
    void deliverClose();

    OutputInterceptor& getOutput() { return output; }

    void addQueueListener(const std::string& queue, uint32_t listener);
    void managementSchema(const std::string& data);
    void managementAgents(const std::string& data);
    void managementSetupState(uint64_t objectNum, uint16_t bootSequence);

    //uint32_t getSsf() const { return connectionCtor.external.ssf; }

  private:
    struct NullFrameHandler : public framing::FrameHandler {
        void handle(framing::AMQFrame&) {}
    };

    // Arguments to construct a broker::Connection
    struct ConnectionCtor {
        sys::ConnectionOutputHandler* out;
        broker::Broker& broker;
        std::string mgmtId;
        qpid::sys::SecuritySettings external;
        bool isLink;
        uint64_t objectId;
        bool shadow;

        ConnectionCtor(
            sys::ConnectionOutputHandler* out_,
            broker::Broker& broker_,
            const std::string& mgmtId_,
            const qpid::sys::SecuritySettings& external_,
            bool isLink_=false,
            uint64_t objectId_=0,
            bool shadow_=false
        ) : out(out_), broker(broker_), mgmtId(mgmtId_), external(external_),
            isLink(isLink_), objectId(objectId_), shadow(shadow_)
        {}

        std::auto_ptr<broker::Connection> construct() {
            return std::auto_ptr<broker::Connection>(
                new broker::Connection(out, broker, mgmtId, external, isLink, objectId, shadow));
        }
    };

    static NullFrameHandler nullFrameHandler;

    // Error listener functions
    void connectionError(const std::string&);
    void sessionError(uint16_t channel, const std::string&);
    
    void init();
    bool checkUnsupported(const framing::AMQBody& body);
    void deliverDoOutput(uint32_t limit);

    boost::shared_ptr<broker::Queue> findQueue(const std::string& qname);
    broker::SessionState& sessionState();
    broker::SemanticState& semanticState();
    broker::QueuedMessage getUpdateMessage();

    Cluster& cluster;
    ConnectionId self;
    bool catchUp;
    OutputInterceptor output;
    framing::FrameDecoder localDecoder;
    ConnectionCtor connectionCtor;
    std::auto_ptr<broker::Connection> connection;
    framing::SequenceNumber deliverSeq;
    framing::ChannelId currentChannel;
    boost::shared_ptr<broker::TxBuffer> txBuffer;
    bool expectProtocolHeader;
    McastFrameHandler mcastFrameHandler;
    UpdateReceiver& updateIn;

    static qpid::sys::AtomicValue<uint64_t> catchUpId;
    
  friend std::ostream& operator<<(std::ostream&, const Connection&);
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_CONNECTION_H*/
