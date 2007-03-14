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
#include <boost/shared_ptr.hpp>

#include "amqp_types.h"
#include <QpidError.h>
#include <Connector.h>
#include <sys/ShutdownHandler.h>
#include <sys/TimeoutHandler.h>


#include "framing/amqp_types.h"
#include <framing/amqp_framing.h>
#include <ClientExchange.h>
#include <IncomingMessage.h>
#include <ClientMessage.h>
#include <MessageListener.h>
#include <ClientQueue.h>
#include <ResponseHandler.h>
#include <AMQP_HighestVersion.h>
#include "ClientChannel.h"

namespace qpid {

/**
 * The client namespace contains all classes that make up a client
 * implementation of the AMQP protocol. The key classes that form
 * the basis of the client API to be used by applications are
 * Connection and Channel.
 */
namespace client {

class Channel;

/**
 * \internal provide access to selected private channel functions
 * for the Connection without making it a friend of the entire channel.
 */
class ConnectionForChannel :
        public framing::InputHandler,
        public framing::OutputHandler,
        public sys::TimeoutHandler, 
        public sys::ShutdownHandler
        
{
  private:
  friend class Channel;
    virtual void erase(framing::ChannelId) = 0;
};


/**
 * \defgroup clientapi Application API for an AMQP client
 */

/**
 * Represents a connection to an AMQP broker. All communication is
 * initiated by establishing a connection, then opening one or
 * more Channels over that connection.
 * 
 * \ingroup clientapi
 */
class Connection : public ConnectionForChannel
{
    typedef std::map<framing::ChannelId, Channel*> ChannelMap;

    framing::ChannelId channelIdCounter;
    static const std::string OK;

    framing::ProtocolVersion version;
    const u_int32_t max_frame_size;
    ChannelMap channels;
    Connector defaultConnector;
    Connector* connector;
    framing::OutputHandler* out;
    volatile bool isOpen;
    Channel channel0;
    bool debug;
        
    void erase(framing::ChannelId);
    void channelException(
        Channel&, framing::AMQMethodBody*, const QpidError&);

    // TODO aconway 2007-01-26: too many friendships, untagle these classes.
  friend class Channel;
    
  public:
    /**
     * Creates a connection object, but does not open the
     * connection.  
     * 
     * @param _version the version of the protocol to connect with
     *
     * @param debug turns on tracing for the connection
     * (i.e. prints details of the frames sent and received to std
     * out). Optional and defaults to false.
     * 
     * @param max_frame_size the maximum frame size that the
     * client will accept. Optional and defaults to 65536.
     */
    Connection(bool debug = false, u_int32_t max_frame_size = 65536,
               framing::ProtocolVersion=framing::highestProtocolVersion);
    ~Connection();

    /**
     * Opens a connection to a broker.
     * 
     * @param host the host on which the broker is running
     * 
     * @param port the port on the which the broker is listening
     * 
     * @param uid the userid to connect with
     * 
     * @param pwd the password to connect with (currently SASL
     * PLAIN is the only authentication method supported so this
     * is sent in clear text)
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
     * Close the connection with optional error information for the peer.
     *
     * Any further use of this connection (without reopening it) will
     * not succeed.
     */
    void close(framing::ReplyCode=200, const std::string& msg=OK,
               framing::ClassId = 0, framing::MethodId = 0);

    /**
     * Associate a Channel with this connection and open it for use.
     *
     * In AMQP channels are like multi-plexed 'sessions' of work over
     * a connection. Almost all the interaction with AMQP is done over
     * a channel.
     * 
     * @param connection the connection object to be associated with
     * the channel. Call Channel::close() to close the channel.
     */
    void openChannel(Channel&);


    // TODO aconway 2007-01-26: can these be private?
    void send(framing::AMQFrame*);
    void received(framing::AMQFrame*);
    void idleOut();
    void idleIn();
    void shutdown();
    
    /**\internal used for testing */
    void setConnector(Connector& connector);
    
    /**
     * @return the maximum frame size in use on this connection
     */
    inline u_int32_t getMaxFrameSize(){ return max_frame_size; }

    /** @return protocol version in use on this connection. */ 
    framing::ProtocolVersion getVersion() const { return version; }
};

}} // namespace qpid::client


#endif
