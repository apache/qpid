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
    public Object getContent();

    public String getMessageId();

    public void setMessageId(String messageId);

    public String getSubject();

    public void setSubject(String subject);

    public String getContentType();

    public void setContentType(String contentType);

    public String getCorrelationId();

    public void setCorrelationId(String correlationId);

    public String getReplyTo();

    public void setReplyTo(String replyTo);

    public String getUserId();

    public void setUserId(String userId);

    public boolean isDurable();

    public void setDurable(boolean durable);

    public boolean isRedelivered();

    public void setRedelivered(boolean redelivered);

    public int getPriority();

    public void setPriority(int priority);

    public long getTtl();

    public void setTtl(long ttl);

    public long getTimestamp();

    public void setTimestamp(long timestamp);

    public Map<String, Object> getProperties();

    public void setProperty(String key, Object value);
}
