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
    qpid::log::Options o;
    o.trace = debug;
    qpid::log::Logger::instance().configure(o, "qpid-c++-client");
}

Connection::~Connection(){}

void Connection::setConnector(Connector& con)
{
    connector = &con;
    connector->setInputHandler(this);
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
    connector->connect(host, port);
    channels[0] = &channel0;
    channel0.open(0, *this);
    channel0.protocolInit(uid, pwd, vhost);
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
    ReplyCode code, const string& msg, ClassId classId, MethodId methodId
)
{
    if(markClosed()) {
        try {
            channel0.sendAndReceive<ConnectionCloseOkBody>(
                make_shared_ptr(new ConnectionCloseBody(
                                    getVersion(), code, msg, classId, methodId)));
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
    if (channel == 0)
        THROW_QPID_ERROR(
            PROTOCOL_ERROR+504,
            (boost::format("Invalid channel number %g") % id).str());
    try{
        channel->getHandlers().in->handle(frame);
    }catch(const qpid::QpidError& e){
        std::cout << "Caught error while handling " << frame << ": " << e.what() <<std::endl;
        channelException(
            *channel, dynamic_cast<AMQMethodBody*>(frame.getBody().get()), e);
    }
}

void Connection::send(AMQFrame& frame) {
    out->send(frame);
}

void Connection::channelException(
    Channel& channel, AMQMethodBody* method, const QpidError& e)
{
    int code = (e.code >= PROTOCOL_ERROR) ? e.code - PROTOCOL_ERROR : 500;
    string msg = e.msg;
    if(method == 0)
        channel.close(code, msg);
    else
        channel.close(
            code, msg, method->amqpClassId(), method->amqpMethodId());
}

void Connection::idleIn(){
    connector->close();
}

void Connection::idleOut(){
    AMQFrame frame(version, 0, new AMQHeartbeatBody());
    out->send(frame);
}

}} // namespace qpid::client
