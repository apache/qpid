#ifndef QPID_BROKER_SESSIONADAPTER_H
#define QPID_BROKER_SESSIONADAPTER_H

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

#include "qpid/framing/FrameDefaultVisitor.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/broker/SuspendedSessions.h"

namespace qpid {
namespace broker {

/**
 * Session Handler: Handles frames arriving for a session.
 * Implements AMQP session class commands, forwards other traffic
 * to the next handler in the chain.
 */
class SessionAdapter : public framing::FrameVisitorHandler
{
  public:
    SessionAdapter(framing::FrameHandler& out, SuspendedSessions&);
    ~SessionAdapter();

  protected:
    void visit(const framing::SessionAckBody&);
    void visit(const framing::SessionAttachedBody&);
    void visit(const framing::SessionCloseBody&);
    void visit(const framing::SessionClosedBody&);
    void visit(const framing::SessionDetachedBody&);
    void visit(const framing::SessionFlowBody&);
    void visit(const framing::SessionFlowOkBody&);
    void visit(const framing::SessionHighWaterMarkBody&);
    void visit(const framing::SessionOpenBody&);
    void visit(const framing::SessionResumeBody&);
    void visit(const framing::SessionSolicitAckBody&);
    void visit(const framing::SessionSuspendBody&);

    using FrameDefaultVisitor::visit;
    
  private:
    SessionState state;
    SuspendedSessions& suspended;
    Chain next;
    framing::FrameHandler& out;
};

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_SESSIONADAPTER_H*/
