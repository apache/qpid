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
package org.apache.qpid.test.unit.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.TopicSession;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionDelegate_0_10;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AMQConnectionTest extends QpidBrokerTestCase
{
    private static AMQConnection _connection;
    private static AMQTopic _topic;
    private static AMQQueue _queue;
    private static QueueSession _queueSession;
    private static TopicSession _topicSession;
    protected static final Logger _logger = LoggerFactory.getLogger(AMQConnectionTest.class);

    protected void setUp() throws Exception
    {
        super.setUp();
        _connection = (AMQConnection) getConnection("guest", "guest");
        _topic = new AMQTopic(_connection.getDefaultTopicExchangeName(), new AMQShortString("mytopic"));
        _queue = new AMQQueue(_connection.getDefaultQueueExchangeName(), new AMQShortString("myqueue"));
    }

    protected void tearDown() throws Exception
    {
        _connection.close();
        super.tearDown();
    }

    /**
     * Simple tests to check we can create TopicSession and QueueSession ok
     * And that they throw exceptions where appropriate as per JMS spec
     */

    public void testCreateQueueSession() throws JMSException
    {
        _queueSession = _connection.createQueueSession(false, AMQSession.NO_ACKNOWLEDGE);
    }

    public void testCreateTopicSession() throws JMSException
    {
        _topicSession = _connection.createTopicSession(false, AMQSession.NO_ACKNOWLEDGE);
    }

    public void testTopicSessionCreateBrowser() throws JMSException
    {
        try
        {
            _topicSession.createBrowser(_queue);
            fail("expected exception did not occur");
        }
        catch (javax.jms.IllegalStateException s)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected javax.jms.IllegalStateException, got " + e);
        }
    }

    public void testTopicSessionCreateQueue() throws JMSException
    {
        try
        {
            _topicSession.createQueue("abc");
            fail("expected exception did not occur");
        }
        catch (javax.jms.IllegalStateException s)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected javax.jms.IllegalStateException, got " + e);
        }
    }

    public void testTopicSessionCreateTemporaryQueue() throws JMSException
    {
        try
        {
            _topicSession.createTemporaryQueue();
            fail("expected exception did not occur");
        }
        catch (javax.jms.IllegalStateException s)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected javax.jms.IllegalStateException, got " + e);
        }
    }

    public void testQueueSessionCreateTemporaryTopic() throws JMSException
    {
        try
        {
            _queueSession.createTemporaryTopic();
            fail("expected exception did not occur");
        }
        catch (javax.jms.IllegalStateException s)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected javax.jms.IllegalStateException, got " + e);
        }
    }

    public void testQueueSessionCreateTopic() throws JMSException
    {
        try
        {
            _queueSession.createTopic("abc");
            fail("expected exception did not occur");
        }
        catch (javax.jms.IllegalStateException s)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected javax.jms.IllegalStateException, got " + e);
        }
    }

    public void testQueueSessionDurableSubscriber() throws JMSException
    {
        try
        {
            _queueSession.createDurableSubscriber(_topic, "abc");
            fail("expected exception did not occur");
        }
        catch (javax.jms.IllegalStateException s)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected javax.jms.IllegalStateException, got " + e);
        }
    }

    public void testQueueSessionUnsubscribe() throws JMSException
    {
        try
        {
            _queueSession.unsubscribe("abc");
            fail("expected exception did not occur");
        }
        catch (javax.jms.IllegalStateException s)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected javax.jms.IllegalStateException, got " + e);
        }
    }

    public void testPrefetchSystemProperty() throws Exception
    {
        String oldPrefetch = System.getProperty(ClientProperties.MAX_PREFETCH_PROP_NAME);
        try
        {
            _connection.close();
            System.setProperty(ClientProperties.MAX_PREFETCH_PROP_NAME, new Integer(2).toString());
            _connection = (AMQConnection) getConnection();
            _connection.start();
            // Create two consumers on different sessions
            Session consSessA = _connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer consumerA = consSessA.createConsumer(_queue);

            Session producerSession = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = producerSession.createProducer(_queue);

            // Send 3 messages
            for (int i = 0; i < 3; i++)
            {
                producer.send(producerSession.createTextMessage("test"));
            }
            
            MessageConsumer consumerB = null;
            if (isBroker08())
            {
                Session consSessB = _connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
                consumerB = consSessB.createConsumer(_queue);
            }
            else
            {
                consumerB = consSessA.createConsumer(_queue);
            }

            Message msg;
            // Check that consumer A has 2 messages
            for (int i = 0; i < 2; i++)
            {
                msg = consumerA.receive(1500);
                assertNotNull("Consumer A should receive 2 messages",msg);                
            }
            
            msg = consumerA.receive(1500);
            assertNull("Consumer A should not have received a 3rd message",msg);
            
            // Check that consumer B has the last message
            msg = consumerB.receive(1500);
            assertNotNull("Consumer B should have received the message",msg);
        }
        finally
        {
            if (oldPrefetch == null)
            {
                oldPrefetch = ClientProperties.MAX_PREFETCH_DEFAULT;
            }
            System.setProperty(ClientProperties.MAX_PREFETCH_PROP_NAME, oldPrefetch);
        }
    }
    
    public void testGetChannelID() throws Exception
    {
        long maxChannelID = _connection.getMaximumChannelCount();
        if (isBroker010())
        {
            //Usable numbers are 0 to N-1 when using 0-10
            //and 1 to N for 0-8/0-9
            maxChannelID = maxChannelID-1;
        }
        for (int j = 0; j < 3; j++)
        {
            int i = isBroker010() ? 0 : 1;
            for ( ; i <= maxChannelID; i++)
            {
                int id = _connection.getNextChannelID();
                assertEquals("Unexpected number on iteration "+j, i, id);
                _connection.deregisterSession(id);
            }
        }
    }
    
    /**
     * Test Strategy : Kill -STOP the broker and see
     * if the client terminates the connection with a
     * read timeout.
     * The broker process is cleaned up in the test itself
     * and avoids using process.waitFor() as it hangs. 
     */
    public void testHeartBeat() throws Exception
    {
       boolean windows = 
            ((String) System.getProperties().get("os.name")).matches("(?i).*windows.*");
 
       if (!isCppBroker() || windows)
       {
           return;
       }
       
       Process process = null;
       int port = getPort(0);
       String pid = null;
       try
       {
           // close the connection and shutdown the broker started by QpidTest
           _connection.close();
           stopBroker(port);
           
           System.setProperty("qpid.heartbeat", "1");
           
           // in case this broker gets stuck, atleast the rest of the tests will not fail.
           port = port + 200;
           String startCmd = getBrokerCommand(port);
           
           // start a broker using a script
           ProcessBuilder pb = new ProcessBuilder(System.getProperty("broker.start"));
           pb.redirectErrorStream(true);

           Map<String, String> env = pb.environment();
           env.put("BROKER_CMD",startCmd);
           env.put("BROKER_READY",System.getProperty(BROKER_READY));
           
           Process startScript = pb.start();
           startScript.waitFor();
           startScript.destroy();
           
           Connection con = 
               new AMQConnection("amqp://guest:guest@clientid/testpath?brokerlist='tcp://localhost:" + port + "'");
           final AtomicBoolean lock = new AtomicBoolean(false);
           
           String cmd = "/usr/bin/pgrep -f " + port;
           process = Runtime.getRuntime().exec("/bin/bash");
           LineNumberReader reader = new LineNumberReader(new InputStreamReader(process.getInputStream()));
           PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(process.getOutputStream())), true); 
           out.println(cmd); 
           pid = reader.readLine();
           try
           {
               Integer.parseInt(pid);
           }
           catch (NumberFormatException e) 
           {
               // Error! try to read further to gather the error msg.
               String line;
               _logger.debug(pid);
               while ((line = reader.readLine()) != null )
               {
                   _logger.debug(line);
               }
               throw new Exception( "Unable to get the brokers pid " + pid);
           }
           _logger.debug("pid : " + pid);
           
           con.setExceptionListener(new ExceptionListener(){
               
               public void onException(JMSException e)
               {
                   synchronized(lock) {
                       lock.set(true);
                       lock.notifyAll();
                  }
               }           
           });   

           out.println("kill -STOP " + pid);           
           
           synchronized(lock){
               lock.wait(2500);
           }
           out.close();
           reader.close();
           assertTrue("Client did not terminate the connection, check log for details",lock.get());
       }
       catch(Exception e)
       {
           throw e;
       }
       finally
       {
           System.setProperty("qpid.heartbeat", "");
           
           if (process != null)
           {
               process.destroy();
           }
           
           Process killScript = Runtime.getRuntime().exec(System.getProperty("broker.kill") + " " + pid);
           killScript.waitFor();
           killScript.destroy();
           cleanBroker();
       }
    }
    
    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(AMQConnectionTest.class);
    }
}
