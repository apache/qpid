#ifndef _client_ClientAdapter_h
#define _client_ClientAdapter_h

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

#include "qpid/framing/ChannelAdapter.h"
#include "ClientChannel.h"

namespace qpid {
namespace client {

class AMQMethodBody;
class Connection;

/**
 * Per-channel protocol adapter.
 *
 * Translates protocol bodies into calls on the core Channel,
 * Connection and Client objects.
 *
 * Owns a channel, has references to Connection and Client.
 */
class ClientAdapter : public framing::ChannelAdapter
{
  public:
    ClientAdapter(std::auto_ptr<Channel> ch, Connection&, Client&);
    Channel& getChannel() { return *channel; }

    void handleHeader(boost::shared_ptr<qpid::framing::AMQHeaderBody>);
    void handleContent(boost::shared_ptr<qpid::framing::AMQContentBody>);
    void handleHeartbeat(boost::shared_ptr<qpid::framing::AMQHeartbeatBody>);

  private:
    void handleMethodInContext(
        boost::shared_ptr<qpid::framing::AMQMethodBody> method,
        const framing::MethodContext& context);
    
    class ClientOps;

    std::auto_ptr<Channel> channel;
    Connection& connection;
    Client& client;
    boost::shared_ptr<ClientOps> clientOps;
};

}} // namespace qpid::client



#endif  /*!_client_ClientAdapter_h*/
