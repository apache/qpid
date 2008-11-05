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
 * \details 
 * 
 * <h2>Getting and setting message contents</h2>
 *
 * <ul>
 * <li> 
 * <p>getData()</p>
 * <pre>std::cout &lt;&lt; "Response: " &lt;&lt; message.getData() &lt;&lt; std::endl;</pre>
 * </li>
 * <li>
 * <p>setData()</p>
 * <pre>message.setData("That's all, folks!");</pre></li>
 * <li>
 * <p>appendData()</p>
 * <pre>message.appendData(" ... let's add a bit more ...");</pre></li>
 * </ul>
 * 
 * <h2>Getting and Setting Delivery Properties</h2>
 * 
 * <ul>
 * <li>
 * <p>getDeliveryProperties()</p>
 * <pre>message.getDeliveryProperties().setRoutingKey("control");</pre>
 * <pre>message.getDeliveryProperties().setDeliveryMode(PERSISTENT);</pre>
 * <pre>message.getDeliveryProperties().setPriority(9);</pre>
 * <pre>message.getDeliveryProperties().setTtl(100);</pre></li>
 * 
 * <li>
 * <p>hasDeliveryProperties()</p>
 * <pre>if (! message.hasDeliveryProperties()) {
 *  ...
 *}</pre></li>
 * </ul>
 * 
 * <h2>Getting and Setting Message Properties</h2>
 * 
 * <ul>
 * <li>
 * <p>getMessageProperties()</p>
 * <pre>
 *request.getMessageProperties().setReplyTo(ReplyTo("amq.direct", response_queue.str()));
 * </pre>
 * <pre>
 *routingKey = request.getMessageProperties().getReplyTo().getRoutingKey();
 *exchange = request.getMessageProperties().getReplyTo().getExchange();
 * </pre>
 * <pre>message.getMessageProperties().setContentType("text/plain");</pre>
 * <pre>message.getMessageProperties().setContentEncoding("text/plain");</pre>
 * </li>
 * <li>
 * <p>hasMessageProperties()</p>
 * <pre>request.getMessageProperties().hasReplyTo();</pre>
 * </li>
 * </ul>
 * 
 * <h2>Getting and Setting Application Headers</h2>
 * 
 * <ul>
 * <li>
 * <p>getHeaders()</p>
 * <pre>
 *message.getHeaders().getString("control");
 * </pre>
 * <pre>
 *message.getHeaders().setString("control","continue");
 * </pre></li>
 * </ul>
 * 
 * 
 */

class Message : public framing::TransferContent 
{
public:
    /** Create a Message.
     *@param data Data for the message body.
     *@param routingKey Passed to the exchange that routes the message.
     */
    Message(const std::string& data=std::string(),
            const std::string& routingKey=std::string());

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
