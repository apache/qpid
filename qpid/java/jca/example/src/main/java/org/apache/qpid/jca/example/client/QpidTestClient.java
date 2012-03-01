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
package org.apache.qpid.jca.example.client;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;

import org.apache.qpid.jca.example.ejb.QpidTest;
import org.apache.qpid.jca.example.ejb.QpidUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QpidTestClient
{
    private static final Logger _log = LoggerFactory.getLogger(QpidTestClient.class);

    private static final String DEFAULT_EJB_JNDI = "QpidTestBean/remote";
    private static final String DEFAULT_CF_JNDI = "QpidConnectionFactory";
    private static final String DEFAULT_MESSAGE = "Hello,World!";
    private static final int DEFAULT_MESSAGE_COUNT = 1;
    private static final boolean DEFAULT_USE_TOPIC = false;
    private static final boolean DEFAULT_USE_EJB = true;
    private static final String DEFAULT_DESTINATION_JNDI = "HelloQueue";
    private static final boolean DEFAULT_SAY_GOODBYE = false;

    public static void main(String[] args) throws Exception
    {
        String content = (System.getProperty("qpid.message") == null) ? DEFAULT_MESSAGE : System.getProperty("qpid.message");
        boolean useEJB = (System.getProperty("use.ejb") == null) ? DEFAULT_USE_EJB : Boolean.valueOf(System.getProperty("use.ejb"));
        int total = (System.getProperty("message.count") == null) ? DEFAULT_MESSAGE_COUNT : Integer.valueOf(System.getProperty("message.count"));
        boolean useTopic =  (System.getProperty("use.topic") == null) ? DEFAULT_USE_TOPIC : Boolean.valueOf(System.getProperty("use.topic"));
        String destType = (useTopic) ? "Topic" : "Queue";
        boolean goodbye =  (System.getProperty("say.goodbye") == null) ? DEFAULT_SAY_GOODBYE : Boolean.valueOf(System.getProperty("say.goodbye"));

        _log.debug("Environment: ");
        _log.debug("JNDI IntialContectFactory: " + System.getProperty("java.naming.factory.initial"));
        _log.debug("JNDI Provider: " + System.getProperty("java.naming.provider.url"));
        _log.debug("Message content: " + content);
        _log.debug("Message count:" + total);
        _log.debug("Protocol: " + ((useEJB) ? "EJB" : "JMS"));
        _log.debug("Destination Type: " + destType);
        _log.debug("Say GoodBye : " + goodbye);

        Context context = new InitialContext();

        if(useEJB)
        {

            String ejbName = (System.getProperty("qpid.ejb.name") == null) ? DEFAULT_EJB_JNDI : System.getProperty("qpid.ejb.name");

            QpidTest ejb = (QpidTest)QpidTestUtil.getFromJNDI(context, ejbName);

            _log.debug("Found SLSB " + ejbName + "in JNDI");
            ejb.testQpidAdapter(content, total, useTopic, false, goodbye);

        }
        else
        {
            ConnectionFactory connectionFactory = null;
            Connection connection = null;
            Session session = null;
            MessageProducer messageProducer = null;
            Destination destination = null;
            int count = 0;

            String cfName = (System.getProperty("qpid.cf.name") == null) ? DEFAULT_CF_JNDI : System.getProperty("qpid.cf.name");
            String destName = (System.getProperty("qpid.dest.name") == null) ? DEFAULT_DESTINATION_JNDI : System.getProperty("qpid.dest.name");

            _log.debug("Using JMS with CF name " + cfName + " and Destination name " + destName + " to send " + total + " message(s) with content " + content);

            try
            {
                _log.debug("Using JNDI at " + System.getProperty("java.naming.provider.url"));

                connectionFactory = (ConnectionFactory)QpidTestUtil.getFromJNDI(context, cfName);
                destination = (Destination)QpidTestUtil.getFromJNDI(context, destName);

                _log.debug("Using CF: " + connectionFactory);
                _log.debug("Destination " + destination);

                connection = connectionFactory.createConnection();
                session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                messageProducer = session.createProducer(destination);

                _log.debug("Sending " + total + " message(s) with content: " + content + " to destination  " + destName);

                for(int i = 0; i < total; i++)
                {
                    TextMessage message = session.createTextMessage(content);
                    message.setBooleanProperty("say.goodbye", goodbye);
                    messageProducer.send(message);
                    count++;
                }


            }
            catch(Exception e)
            {
                e.printStackTrace();
                _log.error(e.getMessage());
            }
            finally
            {
                QpidUtil.closeResources(session, connection, context);
            }

            _log.debug(count  + " message(s) sent successfully");
        }

    }
}
