/* Licensed to the Apache Software Foundation (ASF) under one
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
 */
package org.apache.qpid.messaging.util;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;

import org.apache.qpid.messaging.Message;
import org.apache.qpid.messaging.MessageNotWritableException;
import org.apache.qpid.messaging.MessagingException;

/**
 *  Ensures the message properties and content are read only.
 */
public class ReadOnlyMessageAdapter extends GenericMessageAdapter
{
    ReadOnlyMessageAdapter(Message delegate)
    {
        super(delegate);
    }

    @Override
    public void setMessageId(String messageId) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public void setSubject(String subject) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public void setContentType(String contentType) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public void setCorrelationId(String correlationId) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public void setReplyTo(String replyTo) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public void setUserId(String userId) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public void setDurable(boolean durable) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public void setRedelivered(boolean redelivered) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public void setPriority(int priority) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public void setTtl(long ttl) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public void setTimestamp(long timestamp) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public Map<String, Object> getProperties() throws MessagingException
    {
        return Collections.unmodifiableMap(super.getProperties());
    }

    @Override
    public void setProperty(String key, Object value) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public ByteBuffer getContent() throws MessagingException
    {
        return super.getContent().asReadOnlyBuffer();
    }

    private void throwMessageNotWritableException() throws MessageNotWritableException
    {
        throw new MessageNotWritableException("Message is read-only");
    }
}
