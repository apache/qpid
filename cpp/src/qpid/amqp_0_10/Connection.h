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
#include "qpid/sys/ConnectionCodec.h"
#include "qpid/sys/ConnectionOutputHandler.h"
#include "qpid/sys/Mutex.h"
#include "Connection.h"
#include "qpid/broker/Connection.h"
#include <queue>
#include <memory>

namespace qpid {
namespace broker { class Broker; }
namespace amqp_0_10 {

// FIXME aconway 2008-03-18: Update to 0-10.
class Connection  : public sys::ConnectionCodec,
                    public sys::ConnectionOutputHandler
{
    std::queue<framing::AMQFrame> frameQueue;
    bool frameQueueClosed;
    mutable sys::Mutex frameQueueLock;
    sys::OutputControl& output;
    std::auto_ptr<broker::Connection> connection; // FIXME aconway 2008-03-18: 
    std::string identifier;
    bool initialized;
    bool isClient;
    
  public:
    Connection(sys::OutputControl&, broker::Broker&, const std::string& id, bool isClient = false);
    size_t decode(const char* buffer, size_t size);
    size_t encode(const char* buffer, size_t size);
    bool isClosed() const;
    bool canEncode();
    void activateOutput();
    void closed();              // connection closed by peer.
    void close();               // closing from this end.
    void send(framing::AMQFrame&);
    framing::ProtocolVersion getVersion() const;
};

}} // namespace qpid::amqp_0_10

#endif  /*!QPID_BROKER_CONNECTION_H*/
