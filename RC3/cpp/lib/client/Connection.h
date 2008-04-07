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

#ifndef _Connection_
#define _Connection_

#include <QpidError.h>
#include <Connector.h>
#include <sys/ShutdownHandler.h>
#include <sys/TimeoutHandler.h>

#include <framing/amqp_framing.h>
#include <ClientExchange.h>
#include <IncomingMessage.h>
#include <ClientMessage.h>
#include <MessageListener.h>
#include <ClientQueue.h>
#include <ResponseHandler.h>
#include <AMQP_HighestVersion.h>

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
     * \defgroup clientapi Application API for an AMQP client
     */

    /**
     * Represents a connection to an AMQP broker. All communication is
     * initiated by establishing a connection, then opening one or
     * more Channels over that connection.
     * 
     * \ingroup clientapi
     */
    class Connection : public virtual qpid::framing::InputHandler, 
        public virtual qpid::sys::TimeoutHandler, 
        public virtual qpid::sys::ShutdownHandler, 
        private virtual qpid::framing::BodyHandler{

        typedef std::map<int, Channel*>::iterator iterator;

        const bool debug;
	u_int16_t channelIdCounter;

	std::string host;
	int port;
	const u_int32_t max_frame_size;
	std::map<int, Channel*> channels; 
	Connector* connector;
	qpid::framing::OutputHandler* out;
	ResponseHandler responses;
        volatile bool closed;
        qpid::framing::ProtocolVersion version;
        bool tcpNoDelay;

        void channelException(Channel* channel, qpid::framing::AMQMethodBody* body, QpidError& e);
        void error(int code, const std::string& msg, int classid = 0, int methodid = 0);
        void closeChannel(Channel* channel, u_int16_t code, std::string& text, u_int16_t classId = 0, u_int16_t methodId = 0);
	void sendAndReceive(qpid::framing::AMQFrame* frame, const qpid::framing::AMQMethodBody& body);

	virtual void handleMethod(qpid::framing::AMQMethodBody::shared_ptr body);
	virtual void handleHeader(qpid::framing::AMQHeaderBody::shared_ptr body);
	virtual void handleContent(qpid::framing::AMQContentBody::shared_ptr body);
	virtual void handleHeartbeat(qpid::framing::AMQHeartbeatBody::shared_ptr body);

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
	Connection( bool debug = false, u_int32_t max_frame_size = 65536, 
			qpid::framing::ProtocolVersion* _version = &(qpid::framing::highestProtocolVersion));
	~Connection();

        void setTcpNoDelay(bool on);

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
                  const std::string& uid = "guest", const std::string& pwd = "guest", 
                  const std::string& virtualhost = "");
        /**
         * Closes the connection. Any further use of this connection
         * (without reopening it) will not succeed.
         */
        void close();
        /**
         * Opens a Channel. In AMQP channels are like multi-plexed
         * 'sessions' of work over a connection. Almost all the
         * interaction with AMQP is done over a channel.
         * 
         * @param channel a pointer to a channel instance that will be
         * used to represent the new channel.
         */
	void openChannel(Channel* channel);
	/*
         * Requests that the server close this channel, then removes
         * the association to the channel from this connection
         *
         * @param channel a pointer to the channel instance to close
         */
	void closeChannel(Channel* channel);
	/*
         * Removes the channel from association with this connection,
	 * without sending a close request to the server.
         *
         * @param channel a pointer to the channel instance to
         * disassociate
         */
	void removeChannel(Channel* channel);

	virtual void received(qpid::framing::AMQFrame* frame);

	virtual void idleOut();
	virtual void idleIn();

	virtual void shutdown();

        /**
         * @return the maximum frame size in use on this connection
         */
	inline u_int32_t getMaxFrameSize(){ return max_frame_size; }
    };

}
}


#endif
