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
#ifndef _ConnectionState_
#define _ConnectionState_

#include <vector>

#include "qpid/sys/AggregateOutput.h"
#include "qpid/sys/ConnectionOutputHandlerPtr.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/management/Manageable.h"
#include "qpid/Url.h"
#include "qpid/broker/Broker.h"

namespace qpid {
namespace broker {

class ConnectionState : public ConnectionToken, public management::Manageable
{
  protected:
    sys::ConnectionOutputHandlerPtr out;

  public:
    ConnectionState(qpid::sys::ConnectionOutputHandler* o, Broker& b) :
        out(o),
        broker(b),
        outputTasks(out),
        framemax(65535),
        heartbeat(0),
        heartbeatmax(120),
        userProxyAuth(false), // Can proxy msgs with non-matching auth ids when true (used by federation links)
        federationLink(true),
        isDefaultRealm(false)
    {}

    virtual ~ConnectionState () {}

    uint32_t getFrameMax() const { return framemax; }
    uint16_t getHeartbeat() const { return heartbeat; }
    uint16_t getHeartbeatMax() const { return heartbeatmax; }

    void setFrameMax(uint32_t fm) { framemax = std::max(fm, (uint32_t) 4096); }
    void setHeartbeat(uint16_t hb) { heartbeat = hb; }
    void setHeartbeatMax(uint16_t hbm) { heartbeatmax = hbm; }

    virtual void setUserId(const std::string& uid) {
        userId = uid;
        size_t at = userId.find('@');
        userName = userId.substr(0, at);
        isDefaultRealm = (
            at!= std::string::npos &&
            getBroker().getOptions().realm == userId.substr(at+1,userId.size()));
    }

    const std::string& getUserId() const { return userId; }

    void setUrl(const std::string& _url) { url = _url; }
    const std::string& getUrl() const { return url; }

    void setUserProxyAuth(const bool b) { userProxyAuth = b; }
    bool isUserProxyAuth() const { return userProxyAuth || federationPeerTag.size() > 0; } // links can proxy msgs with non-matching auth ids
    void setFederationLink(bool b) { federationLink = b; } // deprecated - use setFederationPeerTag() instead
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

    Broker& broker;

    //contained output tasks
    sys::AggregateOutput outputTasks;

    sys::ConnectionOutputHandler& getOutput() { return out; }
    framing::ProtocolVersion getVersion() const { return version; }
    void setOutputHandler(qpid::sys::ConnectionOutputHandler* o) { out.set(o); }

    virtual void requestIOProcessing (boost::function0<void>) = 0;

  protected:
    framing::ProtocolVersion version;
    uint32_t framemax;
    uint16_t heartbeat;
    uint16_t heartbeatmax;
    std::string userId;
    std::string url;
    bool userProxyAuth;
    bool federationLink;
    std::string federationPeerTag;
    std::vector<Url> knownHosts;
    std::string userName;
    bool isDefaultRealm;
};

}}

#endif
