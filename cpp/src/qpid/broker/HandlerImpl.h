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

#include "Broker.h"
#include "BrokerChannel.h"
#include "qpid/framing/AMQP_ClientProxy.h"

namespace qpid {

namespace framing {
class AMQP_ClientProxy;
}

namespace broker {

    //class Channel;
class Connection;

/**
 * A collection of references to the core objects required by an adapter,
 * and a client proxy.
 */
struct CoreRefs
{
    CoreRefs(Channel& ch, Connection& c, Broker& b, framing::ChannelAdapter& a)
        : channel(ch), connection(c), broker(b), adapter(a), proxy(a) {}

    Channel& channel;
    Connection& connection;
    Broker& broker;
    framing::ChannelAdapter& adapter;
    framing::AMQP_ClientProxy proxy;

    /**
     * Get named queue, never returns 0.
     * @return: named queue or default queue for channel if name=""
     * @exception: ChannelException if no queue of that name is found.
     * @exception: ConnectionException if name="" and channel has no default.
     */
    Queue::shared_ptr getQueue(const string& name) {
        //Note: this can be removed soon as the default queue for channels is scrapped in 0-10
        Queue::shared_ptr queue;
        if (name.empty()) {
            queue = channel.getDefaultQueue();
            if (!queue) throw ConnectionException( 530, "Queue must be specified or previously declared" );
        } else {
            queue = broker.getQueues().find(name);
            if (queue == 0) {
                throw ChannelException( 404, "Queue not found: " + name);
            }
        }
        return queue;
    }

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
