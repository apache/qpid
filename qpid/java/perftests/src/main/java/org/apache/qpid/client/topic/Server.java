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
import java.io.FileWriter;


public class Server
{
    /**
     * This class logger
     */
    private static final Logger _logger=LoggerFactory.getLogger(Server.class);


    private final Object _lock=new Object();
    private long _numMessages=0;
    public FileWriter _file;
    public boolean _running=true;

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

            String logFilePath=System.getProperty("logFilePath", "./");
            _file=new FileWriter(logFilePath + "server-" + System.currentTimeMillis() + ".cvs", true);

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
            // Session session=connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            for (int i=0; i < 50; i++)
            {
                Session session=connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Topic topic=session.createTopic("topic-" + i);
                TopicSubscriber dursub=session.createDurableSubscriber(topic, "durable-" + i);
                dursub.setMessageListener(new MyListener());
            }

            // Now the messageConsumer is set up we can start the connection
            connection.start();
            _logger.info("Ready to consume messages");
            // listen for the termination message
            Session session=connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queueCompleted=session.createQueue("completed");
            Queue queueStarted=session.createQueue("started");
            MessageProducer prod=session.createProducer(queueStarted);

            Thread logger=new Thread(new MyLogger());
            logger.setDaemon(true);
            logger.start();

            prod.send(session.createTextMessage("start"));
            long startTime=System.currentTimeMillis();
            MessageConsumer cons=session.createConsumer(queueCompleted);
            cons.receive();

            _running=false;

            long endTime=System.currentTimeMillis();
            session.close();
            _logger.info("Received " + _numMessages);
            _file.write("Received " + _numMessages + "\n");
            _logger.info("Throughput " + _numMessages / (endTime - startTime) * 1000 + "msg/s");
            _file.write("Throughput " + _numMessages / (endTime - startTime) * 1000 + "msg/s");
            _file.close();
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
            synchronized (_lock)
            {
                _numMessages++;
                /*if(_numMessages % 1000 == 0)
                {
                    _logger.info("received: " + _numMessages);
                } */
            }
        }
    }

    private class MyLogger implements Runnable
    {
        public void run()
        {
            long endTime=0;
            while (_running)
            {
                synchronized (_lock)
                {
                    try
                    {
                        _lock.wait(5000);
                        if (_running)
                        {
                            endTime=endTime + 5;
                            String s="Throughput " + _numMessages / endTime + " msg/s";
                            _logger.info(s);
                            _file.write(s + "\n");
                        }

                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }

                }
            }
        }
    }
}
