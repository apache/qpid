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

#include "qpid/framing/AMQP_ServerProxy.h"
#include "qpid/framing/ChannelHandler.h"
#include "qpid/management/Manageable.h"
#include "qpid/management/ArgsLinkBridge.h"
#include "qpid/management/Bridge.h"

#include <boost/function.hpp>

namespace qpid {
namespace broker {

class ConnectionState;

class Bridge : public management::Manageable
{
public:
    typedef boost::function<void(Bridge*)> CancellationListener;

    Bridge(framing::ChannelId id, ConnectionState& c, CancellationListener l,
           const management::ArgsLinkBridge& args);
    ~Bridge();

    void create();
    void cancel();

    management::ManagementObject::shared_ptr GetManagementObject() const;
    management::Manageable::status_t ManagementMethod(uint32_t methodId, management::Args& args);

private:
    management::ArgsLinkBridge args;
    framing::ChannelHandler channel;
    framing::AMQP_ServerProxy peer;
    management::Bridge::shared_ptr mgmtObject;
    ConnectionState& connection;
    CancellationListener listener;
};


}}

#endif
