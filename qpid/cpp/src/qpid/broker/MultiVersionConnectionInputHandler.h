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
#ifndef _MultiVersionConnectionInputHandler_
#define _MultiVersionConnectionInputHandler_

#include <memory>
#include <string>
#include "qpid/sys/ConnectionInputHandler.h"
#include "qpid/sys/ConnectionOutputHandler.h"
#include "qpid/broker/Broker.h"

namespace qpid {
namespace broker {

class MultiVersionConnectionInputHandler : public qpid::sys::ConnectionInputHandler
{
    qpid::framing::ProtocolVersion linkVersion;//version used for inter-broker links
    std::auto_ptr<qpid::sys::ConnectionInputHandler> handler;
    qpid::sys::ConnectionOutputHandler* out;
    Broker& broker; 
    const std::string id;

    void check();

public:
    MultiVersionConnectionInputHandler(qpid::sys::ConnectionOutputHandler* out, Broker& broker, const std::string& id);
    virtual ~MultiVersionConnectionInputHandler() {}

    void received(qpid::framing::AMQFrame&);
    void idleOut();
    void idleIn();
    bool doOutput();    
    void closed();
};

}
}


#endif
