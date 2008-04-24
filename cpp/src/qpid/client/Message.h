#ifndef _client_Message_h
#define _client_Message_h

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
#include <string>
#include "qpid/client/Session.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/framing/TransferContent.h"

namespace qpid {
namespace client {

/**
 * A representation of messages sent or received through the
 * client api.
 *
 * \ingroup clientapi
 */
class Message : public framing::TransferContent 
{
public:
    Message(const std::string& data=std::string(),
            const std::string& routingKey=std::string(),
            const std::string& exchange=std::string());
    std::string getDestination() const;
    bool isRedelivered() const;
    void setRedelivered(bool redelivered);
    framing::FieldTable& getHeaders();
    const framing::MessageTransferBody& getMethod() const;
    const framing::SequenceNumber& getId() const;

    /**@internal for incoming messages */
    Message(const framing::FrameSet& frameset);
private:
    //method and id are only set for received messages:
    framing::MessageTransferBody method;
    framing::SequenceNumber id;
};

}}

#endif  /*!_client_Message_h*/
