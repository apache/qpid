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
#include "MultiVersionConnectionInputHandler.h"
#include "Connection.h"
#include "PreviewConnection.h"
#include "qpid/framing/reply_exceptions.h"

namespace qpid {
namespace broker {

MultiVersionConnectionInputHandler::MultiVersionConnectionInputHandler(
    qpid::sys::ConnectionOutputHandler* _out, 
    Broker& _broker, 
    const std::string& _id) : linkVersion(99,0), out(_out), broker(_broker), id(_id) {}

    
void MultiVersionConnectionInputHandler::initiated(const qpid::framing::ProtocolInitiation& i)
{
    if (i.getMajor() == 99 && i.getMinor() == 0) {
        handler = std::auto_ptr<ConnectionInputHandler>(new PreviewConnection(out, broker, id));
    } else if (i.getMajor() == 0 && i.getMinor() == 10) {
        handler = std::auto_ptr<ConnectionInputHandler>(new Connection(out, broker, id));
    } else {
        throw qpid::framing::InternalErrorException("Unsupported version: " + i.getVersion().toString());        
    }
    handler->initiated(i);
}

void MultiVersionConnectionInputHandler::received(qpid::framing::AMQFrame& f)
{
    check();
    handler->received(f);
}

void MultiVersionConnectionInputHandler::idleOut()
{
    check();
    handler->idleOut();
}

void MultiVersionConnectionInputHandler::idleIn()
{
    check();
    handler->idleIn();
}

bool MultiVersionConnectionInputHandler::doOutput()
{
    return check(false) &&  handler->doOutput();
}
    
qpid::framing::ProtocolInitiation MultiVersionConnectionInputHandler::getInitiation()
{
    return qpid::framing::ProtocolInitiation(linkVersion);
}

void MultiVersionConnectionInputHandler::closed()
{
    check();
    handler->closed();
}

bool MultiVersionConnectionInputHandler::check(bool fail)
{
    if (!handler.get()) { 
        if (fail) throw qpid::framing::InternalErrorException("Handler not initialised!");
        else return false;
    } else {
        return true;
    }
}

}
}
