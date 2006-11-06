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
#include <map>
#include <string>

#ifndef _Connection_
#define _Connection_

#include "qpid/QpidError.h"
#include "qpid/sys/Connector.h"
#include "qpid/sys/ShutdownHandler.h"
#include "qpid/sys/TimeoutHandler.h"

#include "qpid/framing/amqp_framing.h"
#include "qpid/client/Exchange.h"
#include "qpid/client/IncomingMessage.h"
#include "qpid/client/Message.h"
#include "qpid/client/MessageListener.h"
#include "qpid/client/Queue.h"
#include "qpid/client/ResponseHandler.h"

namespace qpid {
namespace client {

    class Channel;

class Connection : public virtual qpid::framing::InputHandler, 
        public virtual qpid::sys::TimeoutHandler, 
        public virtual qpid::sys::ShutdownHandler, 
        private virtual qpid::framing::BodyHandler{

        typedef std::map<int, Channel*>::iterator iterator;

	static u_int16_t channelIdCounter;

	std::string host;
	int port;
	const u_int32_t max_frame_size;
	std::map<int, Channel*> channels; 
	qpid::sys::Connector* connector;
	qpid::framing::OutputHandler* out;
	ResponseHandler responses;
        volatile bool closed;

        void channelException(Channel* channel, qpid::framing::AMQMethodBody* body, QpidError& e);
        void error(int code, const string& msg, int classid = 0, int methodid = 0);
        void closeChannel(Channel* channel, u_int16_t code, string& text, u_int16_t classId = 0, u_int16_t methodId = 0);
	void sendAndReceive(qpid::framing::AMQFrame* frame, const qpid::framing::AMQMethodBody& body);

	virtual void handleMethod(qpid::framing::AMQMethodBody::shared_ptr body);
	virtual void handleHeader(qpid::framing::AMQHeaderBody::shared_ptr body);
	virtual void handleContent(qpid::framing::AMQContentBody::shared_ptr body);
	virtual void handleHeartbeat(qpid::framing::AMQHeartbeatBody::shared_ptr body);

    public:

	Connection(bool debug = false, u_int32_t max_frame_size = 65536);
	~Connection();
        void open(const std::string& host, int port = 5672, 
                  const std::string& uid = "guest", const std::string& pwd = "guest", 
                  const std::string& virtualhost = "/");
        void close();
	void openChannel(Channel* channel);
	/*
         * Requests that the server close this channel, then removes
         * the association to the channel from this connection
         */
	void closeChannel(Channel* channel);
	/*
         * Removes the channel from association with this connection,
	 * without sending a close request to the server.
         */
	void removeChannel(Channel* channel);

	virtual void received(qpid::framing::AMQFrame* frame);

	virtual void idleOut();
	virtual void idleIn();

	virtual void shutdown();

	inline u_int32_t getMaxFrameSize(){ return max_frame_size; }
    };


}
}


#endif
