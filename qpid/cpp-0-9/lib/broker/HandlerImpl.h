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

#include "BrokerChannel.h"
#include "AMQP_ClientProxy.h"

namespace qpid {

namespace framing {
class AMQP_ClientProxy;
}

namespace broker {

class Broker;
class Channel;
class Connection;

/**
 * A collection of references to the core objects required by an adapter,
 * and a client proxy.
 */
struct CoreRefs
{
    CoreRefs(Channel& ch, Connection& c, Broker& b)
        : channel(ch), connection(c), broker(b), proxy(ch) {}

    Channel& channel;
    Connection& connection;
    Broker& broker;
    framing::AMQP_ClientProxy proxy;
};


/**
 * Base template for protocol handler implementations.
 * Provides the core references and appropriate AMQP class proxy.
 */
template <class ProxyType>
struct HandlerImpl : public CoreRefs {
    typedef HandlerImpl<ProxyType> HandlerImplType;
    HandlerImpl(CoreRefs& parent)
        : CoreRefs(parent), client(ProxyType::get(proxy)) {}
    ProxyType client;
};



}} // namespace qpid::broker



#endif  /*!_broker_HandlerImpl_h*/
