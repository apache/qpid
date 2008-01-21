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

import org.apache.qpid.client.message.TestMessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.jms.*;
import java.util.Properties;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;

public class Client
{
    /**
     * This class logger
     */
    private static final Logger _logger=LoggerFactory.getLogger(Client.class);

    private long _messagesProduced=0;
    private final Object _lock=new Object();
    private Message _message;
    private List<Runner> _runners=new ArrayList<Runner>();


    /**
     * Run the message consumer example.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args)
    {
        Client syncConsumer=new Client();
        int firstArg=120;
        if (args.length > 0)
        {
            try
            {
                firstArg=Integer.parseInt(args[0]);
            }
            catch (NumberFormatException e)
            {
                _logger.warn("Argument must be an integer, running for 2 minutes");
            }
        }
        syncConsumer.runClient(firstArg);
    }


    void runClient(long duration)
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
                    System.err.println("Received an exception through the ExceptionListener");
                    System.exit(0);
                }
            });

            // Now the messageConsumer is set up we can start the connection
            connection.start();

            // Create a session on the connection
            // This session is a default choice of non-transacted and uses the auto acknowledge feature of a session.
            Session session=connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            _message=TestMessageFactory.newBytesMessage(session, 1024);

            Random random=new Random();
            long testDuration=0;
            long totalMessagesProduced;
            long messagesProducedLastInterval=0;
            long intervalThroughput;
            long totalThroughput;
            long numProducers=1;
            startNewProducer(session, random);
            while (testDuration < duration)
            {
                // every 5 second creates a thread an print the throughput
                synchronized (_lock)
                {
                    _lock.wait(5000);
                    totalMessagesProduced=_messagesProduced;
                }
                testDuration=testDuration + 5;
                intervalThroughput=(totalMessagesProduced - messagesProducedLastInterval) / 5;
                totalThroughput=totalMessagesProduced / testDuration;
                messagesProducedLastInterval=totalMessagesProduced;
                _logger.info("Number of producers " + numProducers + " | This interval throughput = " +
                        intervalThroughput + " | Total throughput = " + totalThroughput);
                startNewProducer(session, random);
                numProducers++;
            }
            // stop all the producers
            for (Runner runner : _runners)
            {
                runner.stop();
            }

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void startNewProducer(Session session, Random random)
            throws JMSException
    {
        // select a random topic
        int topicNumber=random.nextInt(50);
        Topic topic=session.createTopic("topic-" + topicNumber);
        MessageProducer prod=session.createProducer(topic);
        Runner runner=new Runner(prod);
        _runners.add(runner);
        Thread thread=new Thread(runner);
        thread.start();
    }

    private class Runner implements Runnable
    {
        MessageProducer _prod;
        boolean _produce=true;

        private Runner(MessageProducer prod)
        {
            _prod=prod;
        }

        public void run()
        {
            while (_produce)
            {
                try
                {
                    _prod.send(_message, DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY,
                            Message.DEFAULT_TIME_TO_LIVE);
                    synchronized (_lock)
                    {
                        _messagesProduced++;
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    _produce=false;
                }
            }
        }

        public void stop()
        {
            _produce=false;
        }
    }

}
