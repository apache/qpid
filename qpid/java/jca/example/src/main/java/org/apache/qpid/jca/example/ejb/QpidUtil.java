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
package org.apache.qpid.jca.example.ejb;

import java.lang.reflect.Method;
import java.util.Enumeration;

import javax.jms.Message;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QpidUtil
{
    private static final Logger _log = LoggerFactory.getLogger(QpidTestBean.class);

    public static void handleMessage(String beanName, final Message message) throws Exception
    {
        if(message instanceof TextMessage)
        {
            String content = ((TextMessage)message).getText();
            _log.debug(beanName + ": Received text message with contents " + content);

            if(content.contains("PrintEnv"))
            {
                printJMSHeaders(message);
                printProperties(message);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    public static void printProperties(final Message message) throws Exception
    {
        _log.debug("Priting Message Properties:");

        Enumeration e = message.getPropertyNames();

        while(e.hasMoreElements())
        {
           _log.debug(e + ":" + message.getObjectProperty(e.toString()));
        }
    }

    public static void printJMSHeaders(final Message message) throws Exception
    {
        _log.debug("JMSCorrelationID:" + message.getJMSCorrelationID());
        _log.debug("JMSDeliveryMode:" + message.getJMSDeliveryMode());
        _log.debug("JMSExpires:" + message.getJMSExpiration());
        _log.debug("JMSMessageID:" + message.getJMSMessageID());
        _log.debug("JMSPriority:" + message.getJMSPriority());
        _log.debug("JMSTimestamp:" + message.getJMSTimestamp());
        _log.debug("JMSType:" + message.getJMSType());
        _log.debug("JMSReplyTo:" + message.getJMSReplyTo());
    }

    public static void closeResources(Object...objects)
    {
        try
        {
            for(Object object: objects)
            {
                Method close = object.getClass().getMethod("close", new Class[]{});
                close.invoke(object, new Object[]{});
            }
        }
        catch(Exception ignore)
        {
        }
    }
}
