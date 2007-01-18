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

#include <AMQFrame.h>
#include <AMQP_ClientProxy.h>
#include <AMQP_ServerOperations.h>
#include <sys/SessionContext.h>
#include <sys/ConnectionInputHandler.h>
#include <sys/TimeoutHandler.h>
#include "Broker.h"
#include "Exception.h"
#include "BrokerAdapter.h"

namespace qpid {
namespace broker {

class Settings {
  public:
    const u_int32_t timeout;//timeout for auto-deleted queues (in ms)
    const u_int64_t stagingThreshold;

    Settings(u_int32_t _timeout, u_int64_t _stagingThreshold) : timeout(_timeout), stagingThreshold(_stagingThreshold) {}
};

class Connection : public qpid::sys::ConnectionInputHandler, 
                   public ConnectionToken
{
    typedef boost::ptr_map<u_int16_t, BrokerAdapter> AdapterMap;

    // FIXME aconway 2007-01-16: on broker.
    typedef std::vector<Queue::shared_ptr>::iterator queue_iterator;
    Exchange::shared_ptr findExchange(const string& name);

    BrokerAdapter& getAdapter(u_int16_t id);
    
    AdapterMap adapters;
    
  public:
    Connection(qpid::sys::SessionContext* context, Broker& broker);
    // ConnectionInputHandler methods
    void received(qpid::framing::AMQFrame* frame);
    void initiated(qpid::framing::ProtocolInitiation* header);
    void idleOut();
    void idleIn();
    void closed();

    // FIXME aconway 2007-01-16: encapsulate.
    qpid::sys::SessionContext* context;
    u_int32_t framemax;
    u_int16_t heartbeat;
    Broker& broker;
    std::auto_ptr<qpid::framing::AMQP_ClientProxy> client;
    Settings settings;
    // FIXME aconway 2007-01-16: Belongs on broker?
    std::vector<Queue::shared_ptr> exclusiveQueues;

    // FIXME aconway 2007-01-16: move to broker.
    /**
     * Get named queue, never returns 0.
     * @return: named queue or default queue for channel if name=""
     * @exception: ChannelException if no queue of that name is found.
     * @exception: ConnectionException if no queue specified and channel has not declared one.
     */
    Queue::shared_ptr getQueue(const string& name, u_int16_t channel);

    Channel& newChannel(u_int16_t channel);
    Channel& getChannel(u_int16_t channel);
    void closeChannel(u_int16_t channel);
};

}}

#endif
