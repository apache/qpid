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

package org.apache.qpid.client.topic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Context;
import javax.naming.InitialContext;

import javax.jms.*;
import java.util.Properties;


public class Server
{
    /**
     * This class logger
     */
    private static final Logger _logger =LoggerFactory.getLogger(Server.class);


    public static void main(String[] args)
    {
        (new Server()).runServer();
    }

    void runServer()
    {
        try
        {
            // Load JNDI properties
            Properties properties=new Properties();
            properties.load(this.getClass().getResourceAsStream("topic.properties"));

            //Create the initial context
            Context ctx=new InitialContext(properties);

            // Lookup the connection factory
            ConnectionFactory conFac=(ConnectionFactory) ctx.lookup("qpidConnectionfactory");
            // create the connection
            Connection connection=conFac.createConnection();

            connection.setExceptionListener(new ExceptionListener()
            {
                public void onException(JMSException jmse)
                {
                    // The connection may have broken invoke reconnect code if available.
                    // The connection may have broken invoke reconnect code if available.
                    _logger.warn("Received an exception through the ExceptionListener");
                    System.exit(0);
                }
            });

            // Create a session on the connection
            // This session is a default choice of non-transacted and uses the auto acknowledge feature of a session.
            Session session=connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            for (int i=0; i < 50; i++)
            {
                Topic topic=session.createTopic("topic-" + i);
                TopicSubscriber dursub=session.createDurableSubscriber(topic, "durable-" + i);
                dursub.setMessageListener(new MyListener());
            }

            // Now the messageConsumer is set up we can start the connection
            connection.start();
            synchronized (connection)
            {
                connection.wait();
            }

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private class MyListener implements MessageListener
    {
        public void onMessage(Message message)
        {
            _logger.debug("Received a message");
        }
    }
}
