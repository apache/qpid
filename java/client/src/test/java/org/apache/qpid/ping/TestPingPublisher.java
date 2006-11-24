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
package org.apache.qpid.ping;

import org.apache.log4j.Logger;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.AMQException;
import org.apache.qpid.url.URLSyntaxException;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.client.BasicMessageProducer;
import org.apache.qpid.jms.MessageProducer;
import org.apache.qpid.jms.Session;

import javax.jms.*;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * A client that behaves as follows:
 * <ul><li>Connects to a queue, whose name is specified as a cmd-line argument</li>
 * <li>Creates a temporary queue</li>
 * <li>Creates messages containing a property that is the name of the temporary queue</li>
 * <li>Fires off a message on the original queue and waits for a response on the temporary queue</li>
 * </ul>
 */
public class TestPingPublisher implements ExceptionListener
{
    private static final Logger _log = Logger.getLogger(TestPingPublisher.class);

    private AMQConnection _connection;

    private Session _session;

    private boolean _publish;

    private long SLEEP_TIME = 0L;

    private class CallbackHandler implements MessageListener
    {

        private int _actualMessageCount;


        public void onMessage(Message m)
        {
            if (_log.isDebugEnabled())
            {
                _log.debug("Message received: " + m);
            }
            _actualMessageCount++;
            if (_actualMessageCount % 1000 == 0)
            {
                _log.info("Received message count: " + _actualMessageCount);
            }
        }
    }

    public TestPingPublisher(String brokerDetails, String clientID, String virtualpath) throws AMQException, URLSyntaxException
    {
        try
        {
            createConnection(brokerDetails, clientID, virtualpath);

            _session = (Session) _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            //AMQQueue destination = new AMQQueue("ping");
            AMQTopic destination = new AMQTopic("ping");
            MessageProducer producer = (MessageProducer) _session.createProducer(destination);

            _connection.setExceptionListener(this);

            _connection.start();

            int messageNumber = 0;

            while (_publish)
            {
/*
                TextMessage msg = _session.createTextMessage(
                        "Presented to in conjunction with Mahnah Mahnah and the Snowths: " + ++messageNumber);
*/
                ObjectMessage msg = _session.createObjectMessage();

                Long time = System.nanoTime();
                msg.setStringProperty("timestampString", Long.toString(time));
                msg.setLongProperty("timestamp", time);

                ((BasicMessageProducer) producer).send(msg, DeliveryMode.PERSISTENT, true);

                _log.info("Message Sent:\n" + msg);

                if (SLEEP_TIME > 0)
                {
                    try
                    {
                        Thread.sleep(SLEEP_TIME);
                    }
                    catch (InterruptedException ie)
                    {
                        //do nothing
                    }
                }


            }

        }
        catch (JMSException e)
        {
            e.printStackTrace();
        }
    }

    private void createConnection(String brokerDetails, String clientID, String virtualpath) throws AMQException, URLSyntaxException
    {
        _publish = true;
        _connection = new AMQConnection(brokerDetails, "guest", "guest",
                clientID, virtualpath);

    }

    /**
     * @param args argument 1 if present specifies the name of the temporary queue to create. Leaving it blank
     *             means the server will allocate a name.
     */
    public static void main(String[] args) throws URLSyntaxException
    {
        if (args.length == 0)
        {
            System.err.println("Usage: TestPingPublisher <brokerDetails> <virtual path>");
            System.exit(0);
        }
        try
        {
            InetAddress address = InetAddress.getLocalHost();
            String clientID = address.getHostName() + System.currentTimeMillis();
            new TestPingPublisher(args[0], clientID, args[1]);
        }
        catch (UnknownHostException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        catch (AMQException e)
        {
            System.err.println("Error in client: " + e);
            e.printStackTrace();
        }

        //System.exit(0);
    }

    /**
     * @see javax.jms.ExceptionListener#onException(javax.jms.JMSException)
     */
    public void onException(JMSException e)
    {
        System.err.println(e.getMessage());

        _publish = false;
        e.printStackTrace(System.err);
    }
}
