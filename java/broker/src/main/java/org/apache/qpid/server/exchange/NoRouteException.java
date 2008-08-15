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
package org.apache.qpid.server.exchange;

import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.RequiredDeliveryException;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.IncomingMessage;

/**
 * NoRouteException is a {@link RequiredDeliveryException} that represents the failure case where a manadatory message
 * cannot be delivered because there is no route for the message. The AMQP status code, 312, is always used to report
 * this condition.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Represent failure to deliver a message that must be delivered.
 * </table>
 */
public class NoRouteException extends RequiredDeliveryException
{
    public NoRouteException(String msg, AMQMessage amqMessage)
    {
        super(msg, amqMessage);
    }

    public AMQConstant getReplyCode()
    {
        return AMQConstant.NO_ROUTE;
    }
}
