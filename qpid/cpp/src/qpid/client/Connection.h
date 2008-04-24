#ifndef _client_Connection_
#define _client_Connection_

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
#include <map>
#include <string>
#include "Channel.h"
#include "ConnectionImpl.h"
#include "qpid/client/Session.h"
#include "qpid/framing/AMQP_HighestVersion.h"
#include "qpid/framing/Uuid.h"

namespace qpid {

/**
 * The client namespace contains all classes that make up a client
 * implementation of the AMQP protocol. The key classes that form
 * the basis of the client API to be used by applications are
 * Connection and Channel.
 */
namespace client {

/**
 * \defgroup clientapi Application API for an AMQP client.
 */

/**
 * Represents a connection to an AMQP broker. All communication is
 * initiated by establishing a connection, then opening one or
 * more Channels over that connection.
 * 
 * \ingroup clientapi
 */
class Connection
{
    framing::ChannelId channelIdCounter;
    framing::ProtocolVersion version;
    const uint32_t max_frame_size;
    bool isOpen;
    bool debug;

  protected:
    boost::shared_ptr<ConnectionImpl> impl;

  public:
    /**
     * Creates a connection object, but does not open the
     * connection.  
     * 
     * @param _version the version of the protocol to connect with.
     *
     * @param debug turns on tracing for the connection
     * (i.e. prints details of the frames sent and received to std
     * out). Optional. Defaults to false.
     * 
     * @param max_frame_size the maximum frame size that the
     * client will accept. Optional. Defaults to 65535.
     */
    Connection(bool debug = false, uint32_t max_frame_size = 65535,
               framing::ProtocolVersion=framing::highestProtocolVersion);
    Connection(boost::shared_ptr<Connector>);
    ~Connection();

    /**
     * Opens a connection to a broker.
     * 
     * @param host the host on which the broker is running.
     * 
     * @param port the port on the which the broker is listening.
     * 
     * @param uid the userid to connect with.
     * 
     * @param pwd the password to connect with (currently SASL
     * PLAIN is the only authentication method supported so this
     * is sent in clear text).
     * 
     * @param virtualhost the AMQP virtual host to use (virtual
     * hosts, where implemented(!), provide namespace partitioning
     * within a single broker).
     */
    void open(const std::string& host, int port = 5672, 
              const std::string& uid = "guest",
              const std::string& pwd = "guest", 
              const std::string& virtualhost = "/");

    /**
     * Close the connection.
     *
     * Any further use of this connection (without reopening it) will
     * not succeed.
     */
    void close();

    /**
     * Associate a Channel with this connection and open it for use.
     *
     * In AMQP, channels are like multiplexed 'sessions' of work over
     * a connection. Almost all the interaction with AMQP is done over
     * a channel.
     * 
     * @param connection the connection object to be associated with
     * the channel. Call Channel::close() to close the channel.
     */
    void openChannel(Channel&);

    /**
     * Create a new session on this connection.  Sessions allow
     * multiple streams of work to be multiplexed over the same
     * connection.
     *
     *@param detachedLifetime: A session may be detached from its
     * channel, either by calling Session::suspend() or because of a
     * network failure. The session state is preserved for
     * detachedLifetime seconds to allow a call to resume(). After
     * that the broker may discard the session state. Default is 0,
     * meaning the session cannot be resumed.
     */
    Session newSession(SynchronousMode sync, uint32_t detachedLifetime=0);

    /**
     * Resume a suspended session. A session may be resumed
     * on a different connection to the one that created it.
     */
    void resume(Session& session);
};

}} // namespace qpid::client


#endif
