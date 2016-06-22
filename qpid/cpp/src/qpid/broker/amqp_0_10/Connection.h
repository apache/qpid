#ifndef QPID_BROKER_AMQP_0_10_CONNECTION_H
#define QPID_BROKER_AMQP_0_10_CONNECTION_H

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

#include <memory>
#include <sstream>
#include <vector>
#include <queue>

#include "qpid/broker/BrokerImportExport.h"

#include "qpid/broker/ConnectionHandler.h"
#include "qpid/broker/Connection.h"
#include "qpid/broker/OwnershipToken.h"
#include "qpid/management/Manageable.h"
#include "qpid/sys/AggregateOutput.h"
#include "qpid/sys/ConnectionInputHandler.h"
#include "qpid/sys/SecuritySettings.h"
#include "qpid/sys/Mutex.h"
#include "qpid/types/Variant.h"
#include "qpid/RefCounted.h"
#include "qpid/Url.h"
#include "qpid/ptr_map.h"

#include "qmf/org/apache/qpid/broker/Connection.h"

#include <boost/scoped_ptr.hpp>
#include <boost/bind.hpp>

#include <algorithm>

namespace qpid {
namespace sys {
class ConnectionOutputHandler;
class Timer;
class TimerTask;
}
namespace broker {

class Broker;
class LinkRegistry;
class Queue;
class SecureConnection;
class SessionHandler;

namespace amqp_0_10 {
struct ConnectionTimeoutTask;

class Connection : public sys::ConnectionInputHandler, public qpid::broker::Connection,
                   public management::Manageable,
                   public RefCounted
{
  public:
    uint32_t getFrameMax() const { return framemax; }
    uint16_t getHeartbeat() const { return heartbeat; }
    uint16_t getHeartbeatMax() const { return heartbeatmax; }

    void setFrameMax(uint32_t fm) { framemax = std::max(fm, (uint32_t) 4096); }
    void setHeartbeat(uint16_t hb) { heartbeat = hb; }
    void setHeartbeatMax(uint16_t hbm) { heartbeatmax = hbm; }


    const management::ObjectId getObjectId() const { return GetManagementObject()->getObjectId(); };
    const std::string& getUserId() const { return userId; }

    bool isFederationLink() const { return federationPeerTag.size() > 0; }
    void setFederationPeerTag(const std::string& tag) { federationPeerTag = std::string(tag); }
    const std::string& getFederationPeerTag() const { return federationPeerTag; }
    std::vector<Url>& getKnownHosts() { return knownHosts; }

    /**@return true if user is the authenticated user on this connection.
     * If id has the default realm will also compare plain username.
     */
    bool isAuthenticatedUser(const std::string& id) const {
        return (id == userId || (isDefaultRealm && id == userName));
    }

    Broker& getBroker() { return broker; }

    sys::ConnectionOutputHandler& getOutput() { return *out; }
    void activateOutput();
    void addOutputTask(OutputTask*);
    void removeOutputTask(OutputTask*);
    framing::ProtocolVersion getVersion() const { return version; }

    Connection(sys::ConnectionOutputHandler* out,
               Broker& broker,
               const std::string& mgmtId,
               const qpid::sys::SecuritySettings&,
               bool isLink = false,
               uint64_t objectId = 0);

    ~Connection ();

    /** Get the SessionHandler for channel. Create if it does not already exist */
    SessionHandler& getChannel(framing::ChannelId channel);

    /** Close the connection. Waits for the client to respond with close-ok
     * before actually destroying the connection.
     */
    QPID_BROKER_EXTERN void close(
        framing::connection::CloseCode code, const std::string& text);

    /** Abort the connection. Close abruptly and immediately. */
    QPID_BROKER_EXTERN void abort();

    // ConnectionInputHandler methods
    void received(framing::AMQFrame& frame);
    bool doOutput();
    void closed();

    void closeChannel(framing::ChannelId channel);

    // Manageable entry points
    management::ManagementObject::shared_ptr GetManagementObject(void) const;
    management::Manageable::status_t
        ManagementMethod (uint32_t methodId, management::Args& args, std::string&);

    void requestIOProcessing (boost::function0<void>);
    void recordFromServer (const framing::AMQFrame& frame);
    void recordFromClient (const framing::AMQFrame& frame);

    // gets for configured federation links
    std::string getAuthMechanism();
    std::string getAuthCredentials();
    std::string getUsername();
    std::string getPassword();
    std::string getHost();
    uint16_t    getPort();

    void notifyConnectionForced(const std::string& text);
    void setUserId(const std::string& uid);

    // credentials for connected client
    const std::string& getMgmtId() const { return mgmtId; }
    management::ManagementAgent* getAgent() const { return agent; }

    void setHeartbeatInterval(uint16_t heartbeat);
    void sendHeartbeat();
    void restartTimeout();

    void setSecureConnection(SecureConnection* secured);

    const qpid::sys::SecuritySettings& getExternalSecuritySettings() const
    {
        return securitySettings;
    }

    /** @return true if the initial connection negotiation is complete. */
    bool isOpen();

    bool isLink() const { return link; }
    void startLinkHeartbeatTimeoutTask();

    void setClientProperties(const types::Variant::Map& cp) { clientProperties = cp; }
    const types::Variant::Map& getClientProperties() const { return clientProperties; }

  private:
    // Management object is used in the constructor so must be early
    qmf::org::apache::qpid::broker::Connection::shared_ptr mgmtObject;

    //contained output tasks
    sys::AggregateOutput outputTasks;

    boost::scoped_ptr<framing::FrameHandler> outboundTracker;
    boost::scoped_ptr<sys::ConnectionOutputHandler> out;

    Broker& broker;

    framing::ProtocolVersion version;
    uint32_t framemax;
    uint16_t heartbeat;
    uint16_t heartbeatmax;
    std::string userId;
    std::string federationPeerTag;
    std::vector<Url> knownHosts;
    std::string userName;
    bool isDefaultRealm;

    typedef boost::ptr_map<framing::ChannelId, SessionHandler> ChannelMap;

    ChannelMap channels;
    qpid::sys::SecuritySettings securitySettings;
    const bool link;
    ConnectionHandler adapter;
    bool mgmtClosing;
    const std::string mgmtId;
    sys::Mutex ioCallbackLock;
    std::queue<boost::function0<void> > ioCallbacks;
    LinkRegistry& links;
    management::ManagementAgent* agent;
    sys::Timer& timer;
    boost::intrusive_ptr<sys::TimerTask> heartbeatTimer, linkHeartbeatTimer;
    boost::intrusive_ptr<ConnectionTimeoutTask> timeoutTimer;
    uint64_t objectId;
    types::Variant::Map clientProperties;

    void raiseConnectEvent();

friend class OutboundFrameTracker;

    void sent(const framing::AMQFrame& f);
    void doIoCallbacks();

  public:

    qmf::org::apache::qpid::broker::Connection::shared_ptr getMgmtObject() { return mgmtObject; }
};
}

// See weakCallback below.
template <class T> void callIfValid(boost::function1<void, T*> f, boost::weak_ptr<T> wp) {
    boost::shared_ptr<T> sp = wp.lock();
    if (sp) f(sp.get());
}

// Memory safety helper for requestIOProcessing with boost::shared_ptr.
//
// Makes a function that calls f(p) only if p is still valid. The returned
// function is bound to a weak_ptr, and only calls f(p) if the weak pointer is
// still valid. Note this does not prevent the object being deleted before the
// IO callback, instead it skips the callback if the object is already deleted.
template <class T>
boost::function0<void> weakCallback(boost::function1<void, T*> f, T* p) {
    return boost::bind(&callIfValid<T>, f, p->shared_from_this());
}

}}

#endif  /*!QPID_BROKER_AMQP_0_10_CONNECTION_H*/
