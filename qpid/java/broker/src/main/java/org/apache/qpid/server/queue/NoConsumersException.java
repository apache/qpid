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
package org.apache.qpid.server.queue;

import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.RequiredDeliveryException;

/**
 * Signals that no consumers exist for a message at a given point in time.
 * Used if a message has immediate=true and there are no consumers registered
 * with the queue.
 */
public class NoConsumersException extends RequiredDeliveryException
{
    public NoConsumersException(AMQMessage message)
    {
        super("Immediate delivery is not possible.", message);
    }

    public AMQConstant getReplyCode()
    {
        return AMQConstant.NO_CONSUMERS;
    }
}
