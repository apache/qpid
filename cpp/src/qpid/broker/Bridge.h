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
#ifndef _Bridge_
#define _Bridge_

#include "PersistableConfig.h"
#include "qpid/framing/AMQP_ServerProxy.h"
#include "qpid/framing/ChannelHandler.h"
#include "qpid/framing/Buffer.h"
#include "qpid/management/Manageable.h"
#include "qpid/management/ArgsLinkBridge.h"
#include "qpid/management/Bridge.h"

#include <boost/function.hpp>
#include <memory>

namespace qpid {
namespace broker {

class ConnectionState;
class Link;
class LinkRegistry;

class Bridge : public PersistableConfig, public management::Manageable
{
public:
    typedef boost::shared_ptr<Bridge> shared_ptr;
    typedef boost::function<void(Bridge*)> CancellationListener;

    Bridge(Link* link, framing::ChannelId id, CancellationListener l, const management::ArgsLinkBridge& args);
    ~Bridge();

    void create(ConnectionState& c);
    void cancel();
    void destroy();
    bool isDurable() { return args.i_durable; }

    management::ManagementObject::shared_ptr GetManagementObject() const;
    management::Manageable::status_t ManagementMethod(uint32_t methodId, management::Args& args);

    // PersistableConfig:
    void     setPersistenceId(uint64_t id) const;
    uint64_t getPersistenceId() const { return persistenceId; }
    uint32_t encodedSize() const;
    void     encode(framing::Buffer& buffer) const; 
    const std::string& getName() const;
    static Bridge::shared_ptr decode(LinkRegistry& links, framing::Buffer& buffer);

private:
    std::auto_ptr<framing::ChannelHandler>            channelHandler;
    std::auto_ptr<framing::AMQP_ServerProxy::Session> session;
    std::auto_ptr<framing::AMQP_ServerProxy>          peer;

    Link* link;
    framing::ChannelId                  id;
    management::ArgsLinkBridge          args;
    management::Bridge::shared_ptr      mgmtObject;
    CancellationListener listener;
    std::string name;
    mutable uint64_t  persistenceId;
};


}}

#endif
