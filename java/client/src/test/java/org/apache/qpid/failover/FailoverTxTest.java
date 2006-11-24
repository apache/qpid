/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.failover;

import org.apache.log4j.Logger;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.jms.ConnectionListener;
import org.junit.Assert;

import javax.jms.*;
import javax.jms.IllegalStateException;

public class FailoverTxTest implements ConnectionListener
{
    private static Logger _log = Logger.getLogger(FailoverTxTest.class);

    AMQConnection _connection;

    FailoverTxTest(String connectionUrl) throws Exception
    {
        _connection = new AMQConnection(connectionUrl);
        _connection.setConnectionListener(this);
        System.out.println("connection.url = " + _connection.toURL());
        Session session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination queue = session.createTemporaryQueue();

        session.createConsumer(queue).setMessageListener(new MessageListener()
        {
            public void onMessage(Message message)
            {
                try
                {
                    _log.info("Received: " + ((TextMessage) message).getText());
                }
                catch (JMSException e)
                {
                    error(e);
                }
            }
        });

        _connection.start();

        sendInTx(queue);

        _connection.close();
        _log.info("FailoverTxText complete");
    }

    private void sendInTx(Destination queue) throws JMSException
    {
        Session session = _connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
        MessageProducer producer = session.createProducer(queue);
        for (int i = 1; i <= 10; ++i)
        {
            for (int j = 1; j <= 10; ++j)
            {
                TextMessage msg = session.createTextMessage("Tx=" + i + " msg=" + j);
                _log.info("sending message = " + msg.getText());
                producer.send(msg);
                try
                {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException("Someone interrupted me!", e);
                }
            }
            session.commit();
        }
    }

    private void error(Exception e)
    {
        _log.fatal("Exception received. About to stop.", e);
        stop();
    }

    private void stop()
    {
        System.out.println("Stopping...");
        try
        {                                                            
            _connection.close();
        }
        catch (JMSException e)
        {
            System.out.println("Failed to shutdown: " + e);
            e.printStackTrace();
        }
    }

    public void bytesSent(long count)
    {
    }

    public void bytesReceived(long count)
    {
    }

    public boolean preFailover(boolean redirect)
    {
        System.out.println("preFailover(" + redirect + ") called");
        return true;
    }

    public boolean preResubscribe()
    {
        System.out.println("preResubscribe() called");
        return true;
    }

    public void failoverComplete()
    {
        System.out.println("failoverComplete() called");
    }

    public static void main(final String[] argv) throws Exception
    {
        int[] creationOrder = {1, 2, 3};
        int[] killingOrder = {1, 2, 3};
        long delayBeforeKillingStart = 2000;
        long delayBetweenCullings = 2000;
        boolean recreateBrokers = true;
        long delayBeforeReCreationStart = 4000;
        long delayBetweenRecreates = 3000;

        FailoverBrokerTester tester = new FailoverBrokerTester(creationOrder, killingOrder, delayBeforeKillingStart, delayBetweenCullings,
                                                               recreateBrokers, delayBeforeReCreationStart, delayBetweenRecreates);

        try
        {
            final String clientId = "failover" + System.currentTimeMillis();
            final String defaultUrl = "amqp://guest:guest@" + clientId + "/test" +
                                      "?brokerlist='vm://:1;vm://:2;vm://:3'&failover='roundrobin?cyclecount='2''";

            System.out.println("url = [" + defaultUrl + "]");

            System.out.println("connection url = [" + new AMQConnectionURL(defaultUrl) + "]");

            final String url = argv.length == 0 ? defaultUrl : argv[0];
            new FailoverTxTest(url);

        }
        catch (Throwable t)
        {

            if (t instanceof IllegalStateException)
            {
                t.getMessage().endsWith("has been closed");
            }
            else
            {
                Assert.fail("Unexpected Exception occured:" + t.getMessage());
            }
        }
        finally
        {
            tester.stopTesting();
        }
    }
}
