/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.exchange;

import org.apache.qpid.server.RequiredDeliveryException;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.protocol.AMQConstant;

/**
 * Thrown by an exchange if there is no way to route a message with the
 * mandatory flag set.
 */
public class NoRouteException extends RequiredDeliveryException
{
    public NoRouteException(String msg, AMQMessage message)
    {
        super(msg, message);
    }

    public int getReplyCode()
    {
        return AMQConstant.NO_ROUTE.getCode();
    }
}
