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
#include <algorithm>
#include <boost/format.hpp>
#include <boost/bind.hpp>

#include "Connection.h"
#include "ClientChannel.h"
#include "ClientMessage.h"
#include "qpid/log/Logger.h"
#include "qpid/log/Options.h"
#include "qpid/log/Statement.h"
#include "qpid/QpidError.h"
#include <iostream>
#include <sstream>
#include "MethodBodyInstances.h"
#include <functional>

using namespace qpid::framing;
using namespace qpid::sys;


namespace qpid {
namespace client {

const std::string Connection::OK("OK");

Connection::Connection(
    bool _debug, uint32_t _max_frame_size,
    framing::ProtocolVersion _version
    ) : channelIdCounter(0), version(_version), max_frame_size(_max_frame_size),
    defaultConnector(version, _debug, _max_frame_size),
    isOpen(false), debug(_debug)
{
    setConnector(defaultConnector);

    handler.maxFrameSize = _max_frame_size;
}

Connection::~Connection(){}

void Connection::setConnector(Connector& con)
{
    connector = &con;
    connector->setInputHandler(&handler);
    connector->setTimeoutHandler(this);
    connector->setShutdownHandler(this);
    out = connector->getOutputHandler();
}

void Connection::open(
    const std::string& host, int port,
    const std::string& uid, const std::string& pwd, const std::string& vhost)
{
    if (isOpen)
        THROW_QPID_ERROR(INTERNAL_ERROR, "Channel object is already open");

    //wire up the handler:
    handler.in = boost::bind(&Connection::received, this, _1);
    handler.out = boost::bind(&Connector::send, connector, _1);
    handler.onClose = boost::bind(&Connection::closeChannels, this);

    handler.uid = uid;
    handler.pwd = pwd;
    handler.vhost = vhost;

    connector->connect(host, port);
    connector->init();
    handler.waitForOpen();
    isOpen = true;
}

void Connection::shutdown() {
    //this indicates that the socket to the server has closed we do
    //not want to send a close request (or any other requests)
    if(markClosed()) {
        QPID_LOG(info, "Connection to peer closed!");
        closeChannels();
    }
}
        
void Connection::close(
    ReplyCode /*code*/, const string& /*msg*/, ClassId /*classId*/, MethodId /*methodId*/
)
{
    if(markClosed()) {
        try {
            handler.close();
        } catch (const std::exception& e) {
            QPID_LOG(error, "Exception closing channel: " << e.what());
        }
        closeChannels();
        connector->close();
    }
}

bool Connection::markClosed()
{
    Mutex::ScopedLock locker(shutdownLock);
    if (isOpen) {
        isOpen = false;
        return true;
    } else {
        return false; 
    }
}

void Connection::closeChannels()
{
    using boost::bind;
    for_each(channels.begin(), channels.end(),
             bind(&Channel::closeInternal,
                  bind(&ChannelMap::value_type::second, _1)));
    channels.clear();
}

void Connection::openChannel(Channel& channel) {
    ChannelId id = ++channelIdCounter;
    assert (channels.find(id) == channels.end());
    assert(out);
    channels[id] = &channel;
    channel.open(id, *this);
}

void Connection::erase(ChannelId id) {
    channels.erase(id);
}

void Connection::received(AMQFrame& frame){
    ChannelId id = frame.getChannel();
    Channel* channel = channels[id];
    if (channel == 0) {
        throw ConnectionException(504, (boost::format("Invalid channel number %g") % id).str());
    }
    channel->channelHandler.incoming(frame);
}

void Connection::send(AMQFrame& frame) {
    out->send(frame);
}

void Connection::idleIn(){
    connector->close();
}

void Connection::idleOut(){
    AMQFrame frame(version, 0, new AMQHeartbeatBody());
    out->send(frame);
}

}} // namespace qpid::client
