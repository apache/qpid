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
package org.apache.qpid.client.message;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jms.JMSException;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.Content;

public class MessageFactoryRegistry
{
    private final Map _mimeToFactoryMap = new HashMap();

    public void registerFactory(String mimeType, MessageFactory mf)
    {
        if (mf == null)
        {
            throw new IllegalArgumentException("Message factory must not be null");
        }
        _mimeToFactoryMap.put(mimeType, mf);
    }

    public MessageFactory deregisterFactory(String mimeType)
    {
        return (MessageFactory) _mimeToFactoryMap.remove(mimeType);
    }

    /**
     * Create a message. This looks up the MIME type from the content header and instantiates the appropriate
     * concrete message type.
     * @param deliveryTag the AMQ message id
     * @param redelivered true if redelivered
     * @param contentHeader the content header that was received
     * @param bodies a list of ContentBody instances
     * @return the message.
     * @throws AMQException
     * @throws JMSException
     */
    public AbstractJMSMessage createMessage(long deliveryTag, boolean redelivered,
                                            MessageHeaders contentHeader,
                                            List contents) throws AMQException, JMSException
    {
        MessageFactory mf = (MessageFactory) _mimeToFactoryMap.get(contentHeader.getContentType());
        if (mf == null)
        {
            throw new AMQException("Unsupport MIME type of " + contentHeader.getContentType());
        }
        else
        {
            return mf.createMessage(deliveryTag, redelivered, contentHeader, contents);
        }
    }

    public AbstractJMSMessage createMessage(String mimeType) throws AMQException, JMSException
    {
        if (mimeType == null)
        {
            throw new IllegalArgumentException("Mime type must not be null");
        }
        MessageFactory mf = (MessageFactory) _mimeToFactoryMap.get(mimeType);
        if (mf == null)
        {
            throw new AMQException("Unsupport MIME type of " + mimeType);
        }
        else
        {
            return mf.createMessage();
        }
    }

    /**
     * Construct a new registry with the default message factories registered
     * @return a message factory registry
     */
    public static MessageFactoryRegistry newDefaultRegistry()
    {
        MessageFactoryRegistry mf = new MessageFactoryRegistry();
        mf.registerFactory(JMSMapMessage.MIME_TYPE, new JMSMapMessageFactory());
        mf.registerFactory("text/plain", new JMSTextMessageFactory());
        mf.registerFactory("text/xml", new JMSTextMessageFactory());
        mf.registerFactory("application/octet-stream", new JMSBytesMessageFactory());
        mf.registerFactory(JMSObjectMessage.MIME_TYPE, new JMSObjectMessageFactory());
        mf.registerFactory(JMSStreamMessage.MIME_TYPE, new JMSStreamMessageFactory());
        mf.registerFactory(null, new JMSBytesMessageFactory());
        return mf;
    }
}
