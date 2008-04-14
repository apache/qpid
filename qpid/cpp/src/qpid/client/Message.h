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
 * A representation of messages for sent or recived through the
 * client api.
 *
 * \ingroup clientapi
 */
class Message : public framing::TransferContent 
{
public:
    Message(const std::string& data_=std::string(),
            const std::string& routingKey=std::string(),
            const std::string& exchange=std::string()
    ) : TransferContent(data_, routingKey, exchange) {}

    std::string getDestination() const 
    { 
        return method.getDestination(); 
    }

    bool isRedelivered() const 
    { 
        return hasDeliveryProperties() && getDeliveryProperties().getRedelivered(); 
    }

    void setRedelivered(bool redelivered) 
    { 
        getDeliveryProperties().setRedelivered(redelivered); 
    }

    framing::FieldTable& getHeaders() 
    { 
        return getMessageProperties().getApplicationHeaders(); 
    }

    void acknowledge(Session& session, bool cumulative = true, bool send = true) const
    {
        session.getExecution().completed(id, cumulative, send);
    }

    void acknowledge(bool cumulative = true, bool send = true) const
    {
        const_cast<Session&>(session).getExecution().completed(id, cumulative, send);
    }

    /**@internal for incoming messages */
    Message(const framing::FrameSet& frameset, Session s) :
        method(*frameset.as<framing::MessageTransferBody>()), id(frameset.getId()), session(s)
    {
        populate(frameset);
    }

    const framing::MessageTransferBody& getMethod() const
    {
        return method;
    }

    const framing::SequenceNumber& getId() const
    {
        return id;
    }

    /**@internal use for incoming messages. */
    void setSession(Session s) { session=s; }
private:
    //method and id are only set for received messages:
    framing::MessageTransferBody method;
    framing::SequenceNumber id;
    Session session;
};

}}

#endif  /*!_client_Message_h*/
