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
#include "qpid/framing/AMQP_HighestVersion.h"
#include "qpid/framing/all_method_bodies.h"

using namespace qpid::client;
using namespace qpid::framing;
using namespace boost;

namespace {
const std::string OK("OK");
}

ConnectionHandler::ConnectionHandler() 
    : StateManager(NOT_STARTED) 
{

    mechanism = "PLAIN";
    locale = "en_US";
    heartbeat = 0; 
    maxChannels = 32767; 
    maxFrameSize = 65536; 
    insist = true;
    version = framing::highestProtocolVersion;

    ESTABLISHED.insert(FAILED);
    ESTABLISHED.insert(OPEN);
} 

void ConnectionHandler::incoming(AMQFrame& frame)
{
    if (getState() == CLOSED) {
        throw Exception("Connection is closed.");        
    }

    AMQBody* body = frame.getBody();
    if (frame.getChannel() == 0) {
        if (body->getMethod()) {
            handle(body->getMethod());
        } else {
            error(503, "Cannot send content on channel zero.");
        }
    } else {
        switch(getState()) {
          case OPEN:
            try {
                in(frame);
            }catch(ConnectionException& e){
                error(e.code, e.what(), body);
            }catch(std::exception& e){
                error(541/*internal error*/, e.what(), body);
            }
            break;
          case CLOSING:
            QPID_LOG(warning, "Received frame on non-zero channel while closing connection; frame ignored.");        
            break;
          default:
            //must be in connection initialisation:
            fail("Cannot receive frames on non-zero channel until connection is established.");
        }
    }
}

void ConnectionHandler::outgoing(AMQFrame& frame)
{
    if (getState() == OPEN) {
        out(frame);
    } else {
        throw Exception("Connection is not open.");
    }
}

void ConnectionHandler::waitForOpen()
{
    waitFor(ESTABLISHED);
    if (getState() == FAILED) {
        throw Exception("Failed to establish connection.");
    }
}

void ConnectionHandler::close()
{
    switch (getState()) {
      case NEGOTIATING:
      case OPENING:
        setState(FAILED);
        break;
      case OPEN:
        setState(CLOSING);
        send(ConnectionCloseBody(version, 200, OK, 0, 0));
        waitFor(CLOSED);
        break;
        // Nothing to do for CLOSING, CLOSED, FAILED or NOT_STARTED
    }
}

void ConnectionHandler::send(const framing::AMQBody& body)
{
    AMQFrame f(body);
    out(f);
}

void ConnectionHandler::error(uint16_t code, const std::string& message, uint16_t classId, uint16_t methodId)
{
    setState(CLOSING);
    send(ConnectionCloseBody(version, code, message, classId, methodId));    
}

void ConnectionHandler::error(uint16_t code, const std::string& message, AMQBody* body)
{
    if (onError)
        onError(code, message);
    AMQMethodBody* method = body->getMethod();
    if (method)
        error(code, message, method->amqpClassId(), method->amqpMethodId());
    else
        error(code, message);
}


void ConnectionHandler::fail(const std::string& message)
{
    QPID_LOG(error, message);
    setState(FAILED);
}

void ConnectionHandler::handle(AMQMethodBody* method)
{
    switch (getState()) {
      case NOT_STARTED:
        if (method->isA<ConnectionStartBody>()) {
            setState(NEGOTIATING);
            string response = ((char)0) + uid + ((char)0) + pwd;
            send(ConnectionStartOkBody(version, properties, mechanism, response, locale));
        } else {
            fail("Bad method sequence, expected connection-start.");
        }
        break;
      case NEGOTIATING:
        if (method->isA<ConnectionTuneBody>()) {
            ConnectionTuneBody* proposal=polymorphic_downcast<ConnectionTuneBody*>(method);
            heartbeat = proposal->getHeartbeat();
            maxChannels = proposal->getChannelMax();    
            send(ConnectionTuneOkBody(version, maxChannels, maxFrameSize, heartbeat));
            setState(OPENING);
            send(ConnectionOpenBody(version, vhost, capabilities, insist));
            //TODO: support for further security challenges
            //} else if (method->isA<ConnectionSecureBody>()) {
        } else {
            fail("Unexpected method sequence, expected connection-tune.");
        }
        break;
      case OPENING:
        if (method->isA<ConnectionOpenOkBody>()) {
            setState(OPEN);
            //TODO: support for redirection    
            //} else if (method->isA<ConnectionRedirectBody>()) {
        } else {
            fail("Unexpected method sequence, expected connection-open-ok.");
        }
        break;
      case OPEN:
        if (method->isA<ConnectionCloseBody>()) {
            send(ConnectionCloseOkBody(version));
            setState(CLOSED);
            ConnectionCloseBody* c=polymorphic_downcast<ConnectionCloseBody*>(method);
            QPID_LOG(warning, "Broker closed connection: " << c->getReplyCode() 
                     << ", " << c->getReplyText());
            if (onError) {
                onError(c->getReplyCode(), c->getReplyText());
            }
        } else {
            error(503, "Unexpected method on channel zero.", method->amqpClassId(), method->amqpMethodId());
        }
        break;
      case CLOSING:
        if (method->isA<ConnectionCloseOkBody>()) {
            if (onClose) {
                onClose();
            }
            setState(CLOSED);
        } else {
            QPID_LOG(warning, "Received frame on channel zero while closing connection; frame ignored.");        
        }
        break;
    }
}
