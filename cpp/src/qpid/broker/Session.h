#ifndef QPID_BROKER_SESSION_H
#define QPID_BROKER_SESSION_H

/*
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

#include "qpid/framing/FrameHandler.h"

#include <boost/ptr_container/ptr_vector.hpp>

namespace qpid {
namespace broker {

class SessionAdapter;

/**
 * Session holds the state of an open session, whether attached to a
 * channel or suspended. It also holds the handler chains associated
 * with the session. 
 */
class Session : public framing::FrameHandler::Chains,
                private boost::noncopyable
{
  public:
    Session(SessionAdapter&, uint32_t timeout);

    /** Returns 0 if this session is not currently attached */
    SessionAdapter* getAdapter() { return adapter; }
    const SessionAdapter* getAdapter() const { return adapter; }

    uint32_t getTimeout() const { return timeout; }
    
  private:
    SessionAdapter* adapter;
    uint32_t timeout;
    boost::ptr_vector<framing::FrameHandler>  handlers;
};

}} // namespace qpid::broker



#endif  /*!QPID_BROKER_SESSION_H*/
