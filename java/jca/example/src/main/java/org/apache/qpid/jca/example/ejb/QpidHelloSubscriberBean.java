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

import java.util.Date;

import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@MessageDriven(activationConfig = {
   @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge"),
   @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
   @ActivationConfigProperty(propertyName = "destination", propertyValue = "@jndi.prefix@@qpid.hello.topic.jndi.name@"),
   @ActivationConfigProperty(propertyName = "connectionURL", propertyValue = "@broker.url@"),
   @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "NotDurable"),
   @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "10")
})
public class QpidHelloSubscriberBean implements MessageListener
{
    private static final Logger _log = LoggerFactory.getLogger(QpidHelloSubscriberBean.class);

    @Resource(@jndi.scheme@="@qpid.xacf.jndi.name@")
    private ConnectionFactory _connectionFactory;

    @Resource(@jndi.scheme@="@qpid.goodbye.topic.jndi.name@")
    private Destination _topic;

    @Override
    public void onMessage(Message message)
    {
        Connection connection = null;
        Session session = null;
        MessageProducer messageProducer = null;
        TextMessage response = null;

        try
        {
            if(message instanceof TextMessage)
            {
                String content = ((TextMessage)message).getText();

                _log.info("Received text message with contents: [" + content + "] at " + new Date());

                StringBuffer temp = new StringBuffer();
                temp.append("QpidHelloSubscriberBean received message with content: [" + content);
                temp.append("] at " + new Date());

                if(message.propertyExists("say.goodbye") && message.getBooleanProperty("say.goodbye"))
                {
                    connection = _connectionFactory.createConnection();
                    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                    messageProducer = session.createProducer(_topic);
                    response = session.createTextMessage(temp.toString());
                    messageProducer.send(response);
                }
            }
        }
        catch(Exception e)
        {
            _log.error(e.getMessage(), e);
        }
        finally
        {
            QpidUtil.closeResources(session, connection);
        }
    }
}
