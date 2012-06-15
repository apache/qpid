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
package org.apache.qpid.messaging.cpp;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import org.apache.qpid.messaging.ListMessage;
import org.apache.qpid.messaging.MapMessage;
import org.apache.qpid.messaging.Message;
import org.apache.qpid.messaging.MessageEncodingException;
import org.apache.qpid.messaging.MessageFactory;
import org.apache.qpid.messaging.MessagingException;
import org.apache.qpid.messaging.StringMessage;
import org.apache.qpid.messaging.cpp.jni.Address;
import org.apache.qpid.messaging.cpp.jni.Duration;
import org.apache.qpid.messaging.cpp.jni.NativeMessage;
import org.apache.qpid.messaging.util.MessageFactory_AMQP_0_10;

/**
 * For the time being 0-10 is assumed.
 */
public class CppMessageFactory extends MessageFactory_AMQP_0_10
{
    @Override
    protected Class<? extends MessageFactory> getFactoryClass()
    {
        return CppMessageFactory.class;
    }

    @Override
    protected Message createFactorySpecificMessageDelegate()
    {
        return new CppMessageDelegate();
    }

    public Message createMessage(NativeMessage m) throws MessagingException
    {
        return createMessage(new CppMessageDelegate(m), m.getContentAsByteBuffer());
    }

    class CppMessageDelegate implements Message
    {
        private NativeMessage _cppMessage;

        public CppMessageDelegate()
        {
            this(new NativeMessage());
        }

        public CppMessageDelegate(NativeMessage msg)
        {
            _cppMessage = msg;
        }

        @Override
        public String getMessageId()
        {
            return _cppMessage.getMessageId();
        }

        @Override
        public void setMessageId(String messageId)
        {
            _cppMessage.setMessageId(messageId);

        }

        @Override
        public String getSubject()
        {
            return _cppMessage.getSubject();
        }

        @Override
        public void setSubject(String subject)
        {
            _cppMessage.setMessageId(subject);
        }

        @Override
        public String getContentType()
        {
            return _cppMessage.getContentType();
        }

        @Override
        public void setContentType(String contentType)
        {
            _cppMessage.setContentType(contentType);
        }

        @Override
        public String getCorrelationId()
        {
            return _cppMessage.getCorrelationId();
        }

        @Override
        public void setCorrelationId(String correlationId)
        {
            _cppMessage.setCorrelationId(correlationId);
        }

        @Override
        public String getReplyTo()
        {
            return _cppMessage.getReplyTo().toString();
        }

        @Override
        public void setReplyTo(String replyTo)
        {
            _cppMessage.setReplyTo(new Address(replyTo));
        }

        @Override
        public String getUserId()
        {
            return _cppMessage.getUserId();
        }

        @Override
        public void setUserId(String userId)
        {
            _cppMessage.setUserId(userId);
        }

        @Override
        public boolean isDurable()
        {
            return _cppMessage.getDurable();
        }

        @Override
        public void setDurable(boolean durable)
        {
            _cppMessage.setDurable(durable);
        }

        @Override
        public boolean isRedelivered()
        {
            return _cppMessage.getRedelivered();
        }

        @Override
        public void setRedelivered(boolean redelivered)
        {
            _cppMessage.setRedelivered(redelivered);
        }

        @Override
        public int getPriority()
        {
            return _cppMessage.getPriority();
        }

        @Override
        public void setPriority(int priority)
        {
            _cppMessage.setPriority((byte)priority);
        }

        @Override
        public long getTtl()
        {
            return _cppMessage.getTtl().getMilliseconds();
        }

        @Override
        public void setTtl(long ttl)
        {
            _cppMessage.setTtl(new Duration(ttl));
        }

        @Override
        public long getTimestamp()
        {
            return 0;
        }

        @Override
        public void setTimestamp(long timestamp)
        {
            //ignore the c++ client will set it when sending
        }

        @SuppressWarnings("unchecked")
        @Override
        public Map<String, Object> getProperties()
        {
            return _cppMessage.getProperties();
        }

        @Override
        public void setProperty(String key, Object value)
        {
            _cppMessage.setProperty(key, value);
        }

        protected NativeMessage getCppMessage()
        {
            return _cppMessage;
        }

        @Override
        public String toString()
        {
            return _cppMessage.toString();
        }

        @Override
        public ByteBuffer getContent() throws MessagingException
        {
            return null; // The delegate is only for the headers
        }

        public NativeMessage getNativeMessage()
        {
            return _cppMessage;
        }
    }

}
