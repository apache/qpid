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
#ifndef _Connection_
#define _Connection_

#include <sstream>
#include <vector>

#include <boost/ptr_container/ptr_map.hpp>

#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/AMQP_ServerOperations.h"
#include "qpid/framing/AMQP_ClientProxy.h"
#include "qpid/sys/AggregateOutput.h"
#include "qpid/sys/ConnectionOutputHandler.h"
#include "qpid/sys/ConnectionInputHandler.h"
#include "qpid/sys/TimeoutHandler.h"
#include "qpid/framing/ProtocolVersion.h"
#include "Broker.h"
#include "qpid/sys/Socket.h"
#include "qpid/Exception.h"
#include "ConnectionHandler.h"
#include "SessionHandler.h"
#include "qpid/management/Manageable.h"
#include "qpid/management/Client.h"

#include <boost/ptr_container/ptr_map.hpp>

namespace qpid {
namespace broker {

class Connection : public sys::ConnectionInputHandler, 
                   public ConnectionToken,
                   public management::Manageable
{
  public:
    Connection(sys::ConnectionOutputHandler* out, Broker& broker, const std::string& mgmtId);
    ~Connection ();

    /** Get the SessionHandler for channel. Create if it does not already exist */
    SessionHandler& getChannel(framing::ChannelId channel);

    /** Close the connection */
    void close(framing::ReplyCode code, const string& text, framing::ClassId classId, framing::MethodId methodId);

    sys::ConnectionOutputHandler& getOutput() const { return *out; }
    framing::ProtocolVersion getVersion() const { return version; }

    uint32_t getFrameMax() const { return framemax; }
    uint16_t getHeartbeat() const { return heartbeat; }
    uint64_t getStagingThreshold() const { return stagingThreshold; }

    void setFrameMax(uint32_t fm) { framemax = fm; }
    void setHeartbeat(uint16_t hb) { heartbeat = hb; }
    void setStagingThreshold(uint64_t st) { stagingThreshold = st; }
    
    Broker& getBroker() { return broker; }

    Broker& broker;
    std::vector<Queue::shared_ptr> exclusiveQueues;
    
    //contained output tasks
    sys::AggregateOutput outputTasks;

    // ConnectionInputHandler methods
    void received(framing::AMQFrame& frame);
    void initiated(const framing::ProtocolInitiation& header);
    void idleOut();
    void idleIn();
    void closed();
    bool doOutput();

    void closeChannel(framing::ChannelId channel);

    // Manageable entry points
    management::ManagementObject::shared_ptr GetManagementObject (void) const;
    management::Manageable::status_t
        ManagementMethod (uint32_t methodId, management::Args& args);

    void setUserId(const string& uid);
    const string& getUserId() const;

  private:
    typedef boost::ptr_map<framing::ChannelId, SessionHandler> ChannelMap;
    typedef std::vector<Queue::shared_ptr>::iterator queue_iterator;

    framing::ProtocolVersion version;
    ChannelMap channels;
    sys::ConnectionOutputHandler* out;
    uint32_t framemax;
    uint16_t heartbeat;
    framing::AMQP_ClientProxy::Connection* client;
    uint64_t stagingThreshold;
    ConnectionHandler adapter;
    management::Client::shared_ptr mgmtObject;
    bool mgmtClosing;
    string userId;
};

}}

#endif
