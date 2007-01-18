#ifndef _broker_BrokerAdapter_h
#define _broker_BrokerAdapter_h

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

#include "AMQP_ServerOperations.h"
#include "BodyHandler.h"
#include "BrokerChannel.h"

namespace qpid {
namespace broker {

class AMQMethodBody;
class Connection;
class Broker;

// FIXME aconway 2007-01-17: Rename to ChannelAdapter.

/**
 * Per-channel protocol adapter.
 *
 * Translates protocol bodies into calls on the core Channel,
 * Connection and Broker objects.
 *
 * Owns a channel, has references to Connection and Broker.
 */
class BrokerAdapter : public qpid::framing::BodyHandler
{
  public:
    // FIXME aconway 2007-01-18: takes ownership, should pass auto_ptr<Channel>
    BrokerAdapter(Channel* ch, Connection&, Broker&);
    Channel& getChannel() { return *channel; }

    void handleMethod(boost::shared_ptr<qpid::framing::AMQMethodBody>);
    void handleHeader(boost::shared_ptr<qpid::framing::AMQHeaderBody>);
    void handleContent(boost::shared_ptr<qpid::framing::AMQContentBody>);
    void handleHeartbeat(boost::shared_ptr<qpid::framing::AMQHeartbeatBody>);

  private:
    class ServerOps;

    std::auto_ptr<Channel> channel;
    Connection& connection;
    Broker& broker;
    boost::shared_ptr<ServerOps> serverOps;
};
  

}} // namespace qpid::broker



#endif  /*!_broker_BrokerAdapter_h*/
