#ifndef QPID_BROKER_CONNECTION_H
#define QPID_BROKER_CONNECTION_H

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

#include <boost/ptr_container/ptr_map.hpp>

#include "Broker.h"
#include "ConnectionHandler.h"
#include "ConnectionState.h"
#include "SessionHandler.h"
#include "qmf/org/apache/qpid/broker/Connection.h"
#include "qpid/Exception.h"
#include "qpid/RefCounted.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/AMQP_ClientProxy.h"
#include "qpid/framing/AMQP_ServerOperations.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/agent/ManagementAgent.h"
#include "qpid/management/Manageable.h"
#include "qpid/ptr_map.h"
#include "qpid/sys/AggregateOutput.h"
#include "qpid/sys/ConnectionInputHandler.h"
#include "qpid/sys/ConnectionOutputHandler.h"
#include "qpid/sys/Socket.h"
#include "qpid/sys/TimeoutHandler.h"

#include <boost/ptr_container/ptr_map.hpp>
#include <boost/bind.hpp>

#include <algorithm>

namespace qpid {
namespace broker {

class LinkRegistry;
class SecureConnection;

class Connection : public sys::ConnectionInputHandler, 
                   public ConnectionState,
                   public RefCounted
{
  public:
    Connection(sys::ConnectionOutputHandler* out, Broker& broker, const std::string& mgmtId, bool isLink = false);
    ~Connection ();

    /** Get the SessionHandler for channel. Create if it does not already exist */
    SessionHandler& getChannel(framing::ChannelId channel);

    /** Close the connection */
    void close(framing::connection::CloseCode code, const string& text);

    // ConnectionInputHandler methods
    void received(framing::AMQFrame& frame);
    void idleOut();
    void idleIn();
    bool hasOutput();
    bool doOutput();
    void closed();

    void closeChannel(framing::ChannelId channel);

    // Manageable entry points
    management::ManagementObject* GetManagementObject (void) const;
    management::Manageable::status_t
        ManagementMethod (uint32_t methodId, management::Args& args, std::string&);

    void requestIOProcessing (boost::function0<void>);
    void recordFromServer (framing::AMQFrame& frame);
    void recordFromClient (framing::AMQFrame& frame);
    std::string getAuthMechanism();
    std::string getAuthCredentials();
    void notifyConnectionForced(const std::string& text);
    void setUserId(const string& uid);
    const std::string& getUserId() const { return ConnectionState::getUserId(); }
    const std::string& getMgmtId() const { return mgmtId; }
    management::ManagementAgent* getAgent() const { return agent; }
    void setFederationLink(bool b);
    
    void setHeartbeatInterval(uint16_t heartbeat);
    void sendHeartbeat();

    template <class F> void eachSessionHandler(F f) {
        for (ChannelMap::iterator i = channels.begin(); i != channels.end(); ++i)
            f(*ptr_map_ptr(i));
    }

    void sendClose();
    void setSecureConnection(SecureConnection* secured);
  private:
    typedef boost::ptr_map<framing::ChannelId, SessionHandler> ChannelMap;
    typedef std::vector<Queue::shared_ptr>::iterator queue_iterator;

    ChannelMap channels;
    //framing::AMQP_ClientProxy::Connection* client;
    ConnectionHandler adapter;
    const bool isLink;
    bool mgmtClosing;
    const std::string mgmtId;
    boost::function0<void> ioCallback;
    qmf::org::apache::qpid::broker::Connection* mgmtObject;
    LinkRegistry& links;
    management::ManagementAgent* agent;
    Timer& timer;
    boost::intrusive_ptr<TimerTask> heartbeatTimer;
};

}}

#endif  /*!QPID_BROKER_CONNECTION_H*/
