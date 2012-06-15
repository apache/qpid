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

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Representation of a message.
 * The getters/setters are for the message headers.
 *
 * To get the content of a message you need to either,
 * (1) use the generic method @see Message#getContent() which returns a ByteBuffer,
 *
 * (2) cast a generic Message, to a specific sub interface type and invoke the specific method.
 * @see StringMessage#getString()
 * @see MapMessage#getMap()
 * @see ListMessage#getList()
 *
 * (2) Or use one of the convenience methods provided by the MessageFactory.
 * @see MessageFactory#getContentAsString(Message)
 * @see MessageFactory#getContentAsMap(Message)
 * @see MessageFactory#getContentAsList(Message)
 *
 * To create a specific a concrete Message with a specific content type
 * you need to use one of the following methods from the @see MessageFactory
 * @see MessageFactory#createMessage(String)
 * @see MessageFactory#createMessage(byte[])
 * @see MessageFactory#createMessage(java.nio.ByteBuffer)
 * @see MessageFactory#createMessage(java.util.Map)
 * @see MessageFactory#createMessage(java.util.List)
 */
public interface Message
{
    public final static String QPID_SUBJECT = "qpid.subject";

    public ByteBuffer getContent();

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
