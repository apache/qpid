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
package org.apache.qpid.server;

import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.queue.AMQMessage;

/**
 * Signals that a required delivery could not be made. This could be bacuse of the immediate flag being set and the
 * queue having no consumers, or the mandatory flag being set and the exchange having no valid bindings.
 *
 * <p/>The failed message is associated with this error condition, by taking a reference to it. This enables the
 * correct compensating action to be taken against the message, for example, bouncing it back to the sender.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Represent failure to deliver a message that must be delivered.
 * <tr><td> Associate the failed message with the error condition. <td> {@link AMQMessage}
 * </table>
 */
public abstract class RequiredDeliveryException extends AMQException
{
    private AMQMessage _amqMessage;

    public RequiredDeliveryException(String message, AMQMessage payload)
    {
        super(message);

        setMessage(payload);
    }


    public RequiredDeliveryException(String message)
    {
        super(message);
    }

    public void setMessage(final AMQMessage payload)
    {

        // Increment the reference as this message is in the routing phase
        // and so will have the ref decremented as routing fails.
        // we need to keep this message around so we can return it in the
        // handler. So increment here.
        _amqMessage = payload.takeReference();

    }

    public AMQMessage getAMQMessage()
    {
        return _amqMessage;
    }

    public AMQConstant getErrorCode()
    {
        return getReplyCode();
    }

    public abstract AMQConstant getReplyCode();
}
