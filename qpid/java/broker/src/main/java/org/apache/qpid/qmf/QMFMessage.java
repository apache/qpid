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

package org.apache.qpid.qmf;

import org.apache.qpid.server.configuration.SessionConfig;
import org.apache.qpid.server.message.*;
import org.apache.qpid.transport.codec.BBEncoder;

import java.nio.ByteBuffer;
import java.util.Set;

public class QMFMessage implements ServerMessage, InboundMessage, AMQMessageHeader
{

    private ByteBuffer _content;
    private String _routingKey;

    public QMFMessage(String routingKey, QMFCommand command)
    {
        this(routingKey, new QMFCommand[] { command });
    }


    public QMFMessage(String routingKey, QMFCommand[] commands)
    {
        _routingKey = routingKey;
        BBEncoder encoder = new BBEncoder(256);

        for(QMFCommand cmd : commands)
        {
            cmd.encode(encoder);
        }


        _content = encoder.buffer();
    }

    public String getRoutingKey()
    {
        return _routingKey;
    }

    public AMQMessageHeader getMessageHeader()
    {
        return this;
    }

    public boolean isPersistent()
    {
        return false;
    }

    public boolean isRedelivered()
    {
        return false;
    }

    public long getSize()
    {
        return _content.limit();
    }

    public boolean isImmediate()
    {
        return false;
    }

    public String getCorrelationId()
    {
        return null;
    }

    public long getExpiration()
    {
        return 0;
    }

    public String getMessageId()
    {
        return null;
    }

    public String getMimeType()
    {
        return null;
    }

    public String getEncoding()
    {
        return null;
    }

    public byte getPriority()
    {
        return 4;
    }

    public long getTimestamp()
    {
        return 0;
    }

    public String getType()
    {
        return null;
    }

    public String getReplyTo()
    {
        return null;
    }

    public String getReplyToExchange()
    {
        return null;
    }

    public String getReplyToRoutingKey()
    {
        return null;
    }

    public Object getHeader(String name)
    {
        return null;
    }

    public boolean containsHeaders(Set<String> names)
    {
        return false;
    }

    public boolean containsHeader(String name)
    {
        return false;
    }

    public MessageReference newReference()
    {
        return new QMFMessageReference(this);
    }

    public Long getMessageNumber()
    {
        return null;
    }

    public long getArrivalTime()
    {
        return 0;
    }

    public int getContent(ByteBuffer buf, int offset)
    {
        ByteBuffer src = _content.duplicate();
        _content.position(offset);
        _content = _content.slice();
        int len = _content.remaining();
        if(len > buf.remaining())
        {
            len = buf.remaining();
        }

        buf.put(src);

        return len;
    }

    private static class QMFMessageReference extends MessageReference<QMFMessage>
    {
        public QMFMessageReference(QMFMessage message)
        {
            super(message);
        }

        protected void onReference(QMFMessage message)
        {

        }

        protected void onRelease(QMFMessage message)
        {

        }
    }

    public SessionConfig getSessionConfig()
    {
        return null;
    }

}
