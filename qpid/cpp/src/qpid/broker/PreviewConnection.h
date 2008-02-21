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
#ifndef _PreviewConnection_
#define _PreviewConnection_

#include <memory>
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
#include "PreviewConnectionHandler.h"
#include "ConnectionState.h"
#include "PreviewSessionHandler.h"
#include "qpid/management/Manageable.h"
#include "qpid/management/Client.h"
#include "qpid/management/Link.h"

#include <boost/ptr_container/ptr_map.hpp>

namespace qpid {
namespace broker {

class PreviewConnection : public sys::ConnectionInputHandler, 
                   public ConnectionState
{
  public:
    PreviewConnection(sys::ConnectionOutputHandler* out, Broker& broker, const std::string& mgmtId);
    ~PreviewConnection ();

    /** Get the PreviewSessionHandler for channel. Create if it does not already exist */
    PreviewSessionHandler& getChannel(framing::ChannelId channel);

    /** Close the connection */
    void close(framing::ReplyCode code, const string& text, framing::ClassId classId, framing::MethodId methodId);

    // ConnectionInputHandler methods
    void received(framing::AMQFrame& frame);
    void initiated(const framing::ProtocolInitiation& header);
    void idleOut();
    void idleIn();
    void closed();
    bool doOutput();
    framing::ProtocolInitiation getInitiation() { return framing::ProtocolInitiation(version); }

    void closeChannel(framing::ChannelId channel);

    // Manageable entry points
    management::ManagementObject::shared_ptr GetManagementObject (void) const;
    management::Manageable::status_t
        ManagementMethod (uint32_t methodId, management::Args& args);

    void initMgmt(bool asLink = false);

  private:
    typedef boost::ptr_map<framing::ChannelId, PreviewSessionHandler> ChannelMap;
    typedef std::vector<Queue::shared_ptr>::iterator queue_iterator;

    /**
     * Connection may appear, for the purposes of management, as a
     * normal client initiated connection or as an agent initiated
     * inter-broker link. This wrapper abstracts the common interface
     * for both.
     */
    class MgmtWrapper
    {
    public:
        virtual ~MgmtWrapper(){}
        virtual void received(framing::AMQFrame& frame) = 0;
        virtual management::ManagementObject::shared_ptr getManagementObject() const = 0;
        virtual void closing() = 0;
        virtual void processPending(){}
        virtual void process(PreviewConnection&, const management::Args&){}
    };
    class MgmtClient;
    class MgmtLink;

    ChannelMap channels;
    framing::AMQP_ClientProxy::Connection* client;
    uint64_t stagingThreshold;
    PreviewConnectionHandler adapter;
    std::auto_ptr<MgmtWrapper> mgmtWrapper;
    bool mgmtClosing;
    const std::string mgmtId;
};

}}

#endif
