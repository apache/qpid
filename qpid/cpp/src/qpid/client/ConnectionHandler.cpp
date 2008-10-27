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

#include "ConnectionHandler.h"

#include "qpid/log/Statement.h"
#include "qpid/framing/amqp_framing.h"
#include "qpid/framing/all_method_bodies.h"
#include "qpid/framing/ClientInvoker.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/log/Helpers.h"

using namespace qpid::client;
using namespace qpid::framing;

namespace {
const std::string OK("OK");
const std::string PLAIN("PLAIN");
const std::string en_US("en_US");

const std::string INVALID_STATE_START("start received in invalid state");
const std::string INVALID_STATE_TUNE("tune received in invalid state");
const std::string INVALID_STATE_OPEN_OK("open-ok received in invalid state");
const std::string INVALID_STATE_CLOSE_OK("close-ok received in invalid state");
}

ConnectionHandler::ConnectionHandler(const ConnectionSettings& s, ProtocolVersion& v) 
    : StateManager(NOT_STARTED), ConnectionSettings(s), outHandler(*this), proxy(outHandler), errorCode(200), version(v)
{    
    insist = true;

    ESTABLISHED.insert(FAILED);
    ESTABLISHED.insert(CLOSED);
    ESTABLISHED.insert(OPEN);

    FINISHED.insert(FAILED);
    FINISHED.insert(CLOSED);
} 

void ConnectionHandler::incoming(AMQFrame& frame)
{
    if (getState() == CLOSED) {
        throw Exception("Received frame on closed connection");        
    }


    AMQBody* body = frame.getBody();
    try {
        if (frame.getChannel() != 0 || !invoke(static_cast<ConnectionOperations&>(*this), *body)) {
            switch(getState()) {
            case OPEN:
                in(frame);
                break;
            case CLOSING:
                QPID_LOG(warning, "Ignoring frame while closing connection: " << frame);        
                break;
            default:
                throw Exception("Cannot receive frames on non-zero channel until connection is established.");
            }
        }
    }catch(std::exception& e){
        QPID_LOG(warning, "Closing connection due to " << e.what());        
        setState(CLOSING);
        proxy.close(501, e.what());    
        if (onError) onError(501, e.what());
    }
}

void ConnectionHandler::outgoing(AMQFrame& frame)
{
    if (getState() == OPEN) 
        out(frame);
    else
        throw Exception(errorText.empty() ? "Connection is not open." : errorText);
}

void ConnectionHandler::waitForOpen()
{
    waitFor(ESTABLISHED);
    if (getState() == FAILED || getState() == CLOSED) {
        throw ConnectionException(errorCode, errorText);
    }
}

void ConnectionHandler::close()
{
    switch (getState()) {
      case NEGOTIATING:
      case OPENING:
        fail("Connection closed before it was established");
        break;
      case OPEN:
        setState(CLOSING);
        proxy.close(200, OK);
        waitFor(FINISHED);
        break;
        // Nothing to do for CLOSING, CLOSED, FAILED or NOT_STARTED
    }
}

void ConnectionHandler::checkState(STATES s, const std::string& msg)
{
    if (getState() != s) {
        throw CommandInvalidException(msg);
    }
}

void ConnectionHandler::fail(const std::string& message)
{
    errorCode = 502;
    errorText = message;
    QPID_LOG(warning, message);
    setState(FAILED);
}

void ConnectionHandler::start(const FieldTable& /*serverProps*/, const Array& /*mechanisms*/, const Array& /*locales*/)
{
    checkState(NOT_STARTED, INVALID_STATE_START);
    setState(NEGOTIATING);
    //TODO: verify that desired mechanism and locale are supported
    string response = ((char)0) + username + ((char)0) + password;
    proxy.startOk(properties, mechanism, response, locale);
}

void ConnectionHandler::secure(const std::string& /*challenge*/)
{
    throw NotImplementedException("Challenge-response cycle not yet implemented in client");
}

void ConnectionHandler::tune(uint16_t maxChannelsProposed, uint16_t maxFrameSizeProposed, 
                             uint16_t /*heartbeatMin*/, uint16_t /*heartbeatMax*/)
{
    checkState(NEGOTIATING, INVALID_STATE_TUNE);
    maxChannels = std::min(maxChannels, maxChannelsProposed);
    maxFrameSize = std::min(maxFrameSize, maxFrameSizeProposed);
    //TODO: implement heartbeats and check desired value is in valid range
    proxy.tuneOk(maxChannels, maxFrameSize, heartbeat);
    setState(OPENING);
    proxy.open(virtualhost, capabilities, insist);
}

void ConnectionHandler::openOk ( const Array& knownBrokers )
{
    checkState(OPENING, INVALID_STATE_OPEN_OK);
    knownBrokersUrls.clear();
    framing::Array::ValueVector::const_iterator i;
    for ( i = knownBrokers.begin(); i != knownBrokers.end(); ++i )
        knownBrokersUrls.push_back(Url((*i)->get<std::string>()));
    setState(OPEN);
    QPID_LOG(debug, "Known-brokers for connection: " << log::formatList(knownBrokersUrls));
}


void ConnectionHandler::redirect(const std::string& /*host*/, const Array& /*knownHosts*/)
{
    throw NotImplementedException("Redirection received from broker; not yet implemented in client");
}

void ConnectionHandler::close(uint16_t replyCode, const std::string& replyText)
{
    proxy.closeOk();
    errorCode = replyCode;
    errorText = replyText;
    setState(CLOSED);
    QPID_LOG(warning, "Broker closed connection: " << replyCode << ", " << replyText);
    if (onError) {
        onError(replyCode, replyText);
    }
}

void ConnectionHandler::closeOk()
{
    checkState(CLOSING, INVALID_STATE_CLOSE_OK);
    if (onClose) {
        onClose();
    }
    setState(CLOSED);
}

bool ConnectionHandler::isOpen() const
{
    return getState() == OPEN;
}

bool ConnectionHandler::isClosed() const
{
    int s = getState();
    return s == CLOSED || s == FAILED;
}

bool ConnectionHandler::isClosing() const { return getState() == CLOSING; }
