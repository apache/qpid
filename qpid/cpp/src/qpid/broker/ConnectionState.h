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
#include "qpid/sys/ConnectionOutputHandler.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/management/Manageable.h"
#include "Broker.h"

namespace qpid {
namespace broker {

class ConnectionState : public ConnectionToken, public management::Manageable
{
  public:
    ConnectionState(qpid::sys::ConnectionOutputHandler* o, Broker& b) : 
        broker(b), 
        outputTasks(*o),
        out(o), 
        framemax(65535), 
        heartbeat(0),
        stagingThreshold(broker.getStagingThreshold())
        {}



    virtual ~ConnectionState () {}

    uint32_t getFrameMax() const { return framemax; }
    uint16_t getHeartbeat() const { return heartbeat; }
    uint64_t getStagingThreshold() const { return stagingThreshold; }

    void setFrameMax(uint32_t fm) { framemax = fm; }
    void setHeartbeat(uint16_t hb) { heartbeat = hb; }
    void setStagingThreshold(uint64_t st) { stagingThreshold = st; }

    virtual void setUserId(const string& uid) {  userId = uid; }
    const string& getUserId() const { return userId; }
    
    Broker& getBroker() { return broker; }

    Broker& broker;
    std::vector<Queue::shared_ptr> exclusiveQueues;
    
    //contained output tasks
    sys::AggregateOutput outputTasks;

    sys::ConnectionOutputHandler& getOutput() const { return *out; }
    framing::ProtocolVersion getVersion() const { return version; }

  protected:
    framing::ProtocolVersion version;
    sys::ConnectionOutputHandler* out;
    uint32_t framemax;
    uint16_t heartbeat;
    uint64_t stagingThreshold;
    string userId;
};

}}

#endif
