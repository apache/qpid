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

#ifndef _ConnectionImpl_
#define _ConnectionImpl_

#include "Bounds.h"
#include "ConnectionHandler.h"
#include "Connector.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/ShutdownHandler.h"
#include "qpid/sys/TimeoutHandler.h"

#include <map>
#include <boost/shared_ptr.hpp>
#include <boost/weak_ptr.hpp>
#include <boost/enable_shared_from_this.hpp>

namespace qpid {
namespace client {

class ConnectionSettings;
class SessionImpl;

class ConnectionImpl : public Bounds,
                       public framing::FrameHandler,
                       public sys::TimeoutHandler, 
                       public sys::ShutdownHandler,
                       public boost::enable_shared_from_this<ConnectionImpl>

{
    typedef std::map<uint16_t, boost::weak_ptr<SessionImpl> > SessionMap;

    SessionMap sessions; 
    ConnectionHandler handler;
    Connector connector;
    framing::ProtocolVersion version;
    sys::Mutex lock;
    bool isClosed;
    bool isClosing;

    template <class F> void closeInternal(const F&);

    void incoming(framing::AMQFrame& frame);    
    void closed(uint16_t, const std::string&);
    void idleOut();
    void idleIn();
    void shutdown();
    bool setClosing();

  public:
    ConnectionImpl(framing::ProtocolVersion version, const ConnectionSettings& settings);
    ~ConnectionImpl();
    
    void open(const std::string& host, int port);
    bool isOpen() const { return !isClosed && !isClosing; }

    void addSession(const boost::shared_ptr<SessionImpl>&);
        
    void close();
    void handle(framing::AMQFrame& frame);
    void erase(uint16_t channel);

    const ConnectionSettings& getNegotiatedSettings();
};

}}


#endif
