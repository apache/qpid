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

import java.util.Collections;
import java.util.Map;

import org.apache.qpid.messaging.Message;
import org.apache.qpid.messaging.MessageNotWritableException;
import org.apache.qpid.messaging.MessagingException;

/**
 *  Ensures the message is read only by blocking the delegates
 *  setter methods.
 */
public class ReadOnlyMessageAdapter implements Message
{
    private Message _delegate;

    ReadOnlyMessageAdapter(Message delegate)
    {
        _delegate = delegate;
    }

    @Override
    public Object getContent() throws MessagingException
    {
        return _delegate.getContent();
    }

    @Override
    public String getMessageId() throws MessagingException
    {
        return _delegate.getMessageId();
    }

    @Override
    public void setMessageId(String messageId) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public String getSubject() throws MessagingException
    {
        return _delegate.getSubject();
    }

    @Override
    public void setSubject(String subject) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public String getContentType() throws MessagingException
    {
        return _delegate.getContentType();
    }

    @Override
    public void setContentType(String contentType) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public String getCorrelationId() throws MessagingException
    {
        return _delegate.getCorrelationId();
    }

    @Override
    public void setCorrelationId(String correlationId) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public String getReplyTo() throws MessagingException
    {
        return _delegate.getReplyTo();
    }

    @Override
    public void setReplyTo(String replyTo) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public String getUserId() throws MessagingException
    {
        return _delegate.getUserId();
    }

    @Override
    public void setUserId(String userId) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public boolean isDurable() throws MessagingException
    {
        return _delegate.isDurable();
    }

    @Override
    public void setDurable(boolean durable) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public boolean isRedelivered() throws MessagingException
    {
        return _delegate.isRedelivered();
    }

    @Override
    public void setRedelivered(boolean redelivered) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public int getPriority() throws MessagingException
    {
        return _delegate.getPriority();
    }

    @Override
    public void setPriority(int priority) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public long getTtl() throws MessagingException
    {
        return _delegate.getTtl();
    }

    @Override
    public void setTtl(long ttl) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public long getTimestamp() throws MessagingException
    {
        return _delegate.getTimestamp();
    }

    @Override
    public void setTimestamp(long timestamp) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    @Override
    public Map<String, Object> getProperties() throws MessagingException
    {
        return Collections.unmodifiableMap(_delegate.getProperties());
    }

    @Override
    public void setProperty(String key, Object value) throws MessagingException
    {
        throwMessageNotWritableException();
    }

    private void throwMessageNotWritableException() throws MessageNotWritableException
    {
        throw new MessageNotWritableException("Message is read-only");
    }

}
