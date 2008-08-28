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
 * A message sent to or received from the broker.
 *
 * \ingroup clientapi
 */
class Message : public framing::TransferContent 
{
public:
    /** Create a Message.
     *@param data Data for the message body.
     *@param routingKey Passed to the exchange that routes the message.
     *@param exchange Name of the exchange that should route the message.
     */
    Message(const std::string& data=std::string(),
            const std::string& routingKey=std::string(),
            const std::string& exchange=std::string());

    /** The destination of messages sent to the broker is the exchange
     * name.  The destination of messages received from the broker is
     * the delivery tag identifyig the local subscription (often this
     * is the name of the subscribed queue.)
     */
    std::string getDestination() const;

    /** Check the redelivered flag. */
    bool isRedelivered() const;
    /** Set the redelivered flag. */
    void setRedelivered(bool redelivered);

    /** Get a modifyable reference to the message headers. */
    framing::FieldTable& getHeaders();

    /** Get a non-modifyable reference to the message headers. */
    const framing::FieldTable& getHeaders() const;

    ///@internal
    const framing::MessageTransferBody& getMethod() const;
    ///@internal
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
