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
package org.apache.qpid.server.queue;

import org.apache.qpid.server.RequiredDeliveryException;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.protocol.AMQConstant;

import java.util.List;

/**
 * Signals that no consumers exist for a message at a given point in time.
 * Used if a message has immediate=true and there are no consumers registered
 * with the queue.
 */
public class NoConsumersException extends RequiredDeliveryException
{
    public NoConsumersException(String queue,
                                BasicPublishBody publishBody,
                                ContentHeaderBody contentHeaderBody,
                                List<ContentBody> contentBodies)
    {
        super("Immediate delivery to " + queue + " is not possible.", publishBody, contentHeaderBody, contentBodies);
    }

    public int getReplyCode()
    {
        return AMQConstant.NO_CONSUMERS.getCode();
    }
}
