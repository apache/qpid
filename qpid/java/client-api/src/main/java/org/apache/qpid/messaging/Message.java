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
package org.apache.qpid.messaging;

import java.util.Map;

/**
 * Representation of a message.
 */
public interface Message
{
    public Object getContent() throws MessagingException;

    public String getMessageId() throws MessagingException;

    public void setMessageId(String messageId)throws MessagingException;

    public String getSubject()throws MessagingException;

    public void setSubject(String subject)throws MessagingException;

    public String getContentType()throws MessagingException;

    public void setContentType(String contentType)throws MessagingException;

    public String getCorrelationId()throws MessagingException;

    public void setCorrelationId(String correlationId)throws MessagingException;

    public String getReplyTo()throws MessagingException;

    public void setReplyTo(String replyTo)throws MessagingException;

    public String getUserId()throws MessagingException;

    public void setUserId(String userId)throws MessagingException;

    public boolean isDurable()throws MessagingException;

    public void setDurable(boolean durable)throws MessagingException;

    public boolean isRedelivered()throws MessagingException;

    public void setRedelivered(boolean redelivered)throws MessagingException;

    public int getPriority()throws MessagingException;

    public void setPriority(int priority)throws MessagingException;

    public long getTtl()throws MessagingException;

    public void setTtl(long ttl)throws MessagingException;

    public long getTimestamp()throws MessagingException;

    public void setTimestamp(long timestamp)throws MessagingException;

    public Map<String, Object> getProperties()throws MessagingException;

    public void setProperty(String key, Object value)throws MessagingException;
}
