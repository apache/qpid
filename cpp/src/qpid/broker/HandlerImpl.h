#ifndef _broker_HandlerImpl_h
#define _broker_HandlerImpl_h

/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "Session.h"
#include "SessionHandler.h"
#include "Connection.h"

namespace qpid {
namespace broker {

class Broker;

/**
 * Base template for protocol handler implementations.
 * Provides convenience methods for getting common session objects.
 */
class HandlerImpl {
  protected:
    HandlerImpl(Session& s) : session(s) {}

    Session& getSession() { return session; }
    const Session& getSession() const { return session; }
    
    SessionHandler* getSessionHandler() { return session.getHandler(); }
    const SessionHandler* getSessionHandler() const { return session.getHandler(); }

    // Remaining functions may only be called if getSessionHandler() != 0
    framing::AMQP_ClientProxy& getProxy() { return getSessionHandler()->getProxy(); }
    const framing::AMQP_ClientProxy& getProxy() const { return getSessionHandler()->getProxy(); }

    Connection& getConnection() { return getSessionHandler()->getConnection(); }
    const Connection& getConnection() const { return getSessionHandler()->getConnection(); }
    
    Broker& getBroker() { return getConnection().broker; }
    const Broker& getBroker() const { return getConnection().broker; }

  private:
    Session& session;
};

}} // namespace qpid::broker



#endif  /*!_broker_HandlerImpl_h*/


