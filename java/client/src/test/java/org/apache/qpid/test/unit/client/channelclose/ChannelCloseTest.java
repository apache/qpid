/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */
package org.apache.qpid.test.unit.client.channelclose;

import junit.framework.TestCase;

import org.apache.qpid.AMQException;
import org.apache.qpid.AMQTimeoutException;
import org.apache.qpid.testutil.QpidTestCase;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.failover.FailoverException;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ChannelCloseOkBody;
import org.apache.qpid.framing.ChannelOpenBody;
import org.apache.qpid.framing.ChannelOpenOkBody;
import org.apache.qpid.framing.ExchangeDeclareBody;
import org.apache.qpid.framing.ExchangeDeclareOkBody;
import org.apache.qpid.jms.ConnectionListener;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.url.URLSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

public class ChannelCloseTest extends QpidTestCase implements ExceptionListener, ConnectionListener
{
    private static final Logger _logger = LoggerFactory.getLogger(ChannelCloseTest.class);

    Connection _connection;
    private String _brokerlist = "vm://:1";
    private Session _session;
    private static final long SYNC_TIMEOUT = 500;
    private int TEST = 0;

    protected void setUp() throws Exception
    {
        super.setUp();
        TransportConnection.createVMBroker(1);
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
        TransportConnection.killAllVMBrokers();
    }

    /*
          close channel, use chanel with same id ensure error.
     */
    public void testReusingChannelAfterFullClosure() throws Exception
    {
        // this is testing an 0.8 conneciton 
        if(isBroker08())
        {
            _connection=newConnection();

            // Create Producer
            try
            {
                _connection.start();

                createChannelAndTest(1);

                // Cause it to close
                try
                {
                    _logger.info("Testing invalid exchange");
                    declareExchange(1, "", "name_that_will_lookup_to_null", false);
                    fail("Exchange name is empty so this should fail ");
                }
                catch (AMQException e)
                {
                    assertEquals("Exchange should not be found", AMQConstant.NOT_FOUND, e.getErrorCode());
                }

                // Check that
                try
                {
                    _logger.info("Testing valid exchange should fail");
                    declareExchange(1, "topic", "amq.topic", false);
                    fail("This should not succeed as the channel should be closed ");
                }
                catch (AMQException e)
                {
                    if (_logger.isInfoEnabled())
                    {
                        _logger.info("Exception occured was:" + e.getErrorCode());
                    }

                    assertEquals("Connection should be closed", AMQConstant.CHANNEL_ERROR, e.getErrorCode());

                    _connection=newConnection();
                }

                checkSendingMessage();

                _session.close();
                _connection.close();

            }
            catch (JMSException e)
            {
                e.printStackTrace();
                fail(e.getMessage());
            }
        }
    }

    /*
    close channel and send guff then send ok no errors
     */
    public void testSendingMethodsAfterClose() throws Exception
    {
        // this is testing an 0.8 connection
        if(isBroker08())
        {
            try
            {
                _connection=new AMQConnection("amqp://guest:guest@CCTTest/test?brokerlist='" + _brokerlist + "'");

                ((AMQConnection) _connection).setConnectionListener(this);

                _connection.setExceptionListener(this);

                // Change the StateManager for one that doesn't respond with Close-OKs
                AMQStateManager oldStateManager=((AMQConnection) _connection).getProtocolHandler().getStateManager();

                _session=_connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

                _connection.start();

                // Test connection
                checkSendingMessage();

                // Set StateManager to manager that ignores Close-oks
                AMQProtocolSession protocolSession=
                        ((AMQConnection) _connection).getProtocolHandler().getProtocolSession();
                AMQStateManager newStateManager=new NoCloseOKStateManager(protocolSession);
                newStateManager.changeState(oldStateManager.getCurrentState());

                ((AMQConnection) _connection).getProtocolHandler().setStateManager(newStateManager);

                final int TEST_CHANNEL=1;
                _logger.info("Testing Channel(" + TEST_CHANNEL + ") Creation");

                createChannelAndTest(TEST_CHANNEL);

                // Cause it to close
                try
                {
                    _logger.info("Closing Channel - invalid exchange");
                    declareExchange(TEST_CHANNEL, "", "name_that_will_lookup_to_null", false);
                    fail("Exchange name is empty so this should fail ");
                }
                catch (AMQException e)
                {
                    assertEquals("Exchange should not be found", AMQConstant.NOT_FOUND, e.getErrorCode());
                }

                try
                {
                    // Send other methods that should be ignored
                    // send them no wait as server will ignore them
                    _logger.info("Tested known exchange - should ignore");
                    declareExchange(TEST_CHANNEL, "topic", "amq.topic", true);

                    _logger.info("Tested known invalid exchange - should ignore");
                    declareExchange(TEST_CHANNEL, "", "name_that_will_lookup_to_null", true);

                    _logger.info("Tested known invalid exchange - should ignore");
                    declareExchange(TEST_CHANNEL, "", "name_that_will_lookup_to_null", true);

                    // Send sync .. server will igore and timy oue
                    _logger.info("Tested known invalid exchange - should ignore");
                    declareExchange(TEST_CHANNEL, "", "name_that_will_lookup_to_null", false);
                }
                catch (AMQTimeoutException te)
                {
                    assertEquals("Request should timeout", AMQConstant.REQUEST_TIMEOUT, te.getErrorCode());
                }
                catch (AMQException e)
                {
                    fail("This should not fail as all requests should be ignored");
                }

                _logger.info("Sending Close");
                // Send Close-ok
                sendClose(TEST_CHANNEL);

                _logger.info("Re-opening channel");

                createChannelAndTest(TEST_CHANNEL);

                // Test connection is still ok

                checkSendingMessage();

            }
            catch (JMSException e)
            {
                e.printStackTrace();
                fail(e.getMessage());
            }
            catch (AMQException e)
            {
                fail(e.getMessage());

            }
            catch (URLSyntaxException e)
            {
                fail(e.getMessage());
            }
            finally
            {
                try
                {
                    _session.close();
                    _connection.close();
                }
                catch (JMSException e)
                {
                    e.printStackTrace();
                    fail(e.getMessage());
                }
            }
        }
    }

    private void createChannelAndTest(int channel) throws FailoverException
    {
        // Create A channel
        try
        {
            createChannel(channel);
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }

        // Test it is ok
        try
        {
            declareExchange(channel, "topic", "amq.topic", false);
            _logger.info("Tested known exchange");
        }
        catch (AMQException e)
        {
            fail("This should not fail as this is the default exchange details");
        }
    }

    private void sendClose(int channel)
    {
        AMQFrame frame =
            ChannelCloseOkBody.createAMQFrame(channel,
                ((AMQConnection) _connection).getProtocolHandler().getProtocolMajorVersion(),
                ((AMQConnection) _connection).getProtocolHandler().getProtocolMinorVersion());

        ((AMQConnection) _connection).getProtocolHandler().writeFrame(frame);
    }

    private void checkSendingMessage() throws JMSException
    {
        TEST++;
        _logger.info("Test creating producer which will use channel id 1");

        Queue queue = _session.createQueue("CCT_test_validation_queue" + TEST);

        MessageConsumer consumer = _session.createConsumer(queue);

        MessageProducer producer = _session.createProducer(queue);

        final String MESSAGE = "CCT_Test_Message";
        producer.send(_session.createTextMessage(MESSAGE));

        Message msg = consumer.receive(2000);

        assertNotNull("Received messages should not be null.", msg);
        assertEquals("Message received not what we sent", MESSAGE, ((TextMessage) msg).getText());
    }

    private Connection newConnection()
    {
        AMQConnection connection = null;
        try
        {
            connection = new AMQConnection("amqp://guest:guest@CCTTest/test?brokerlist='" + _brokerlist + "'");

            connection.setConnectionListener(this);

            _session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            connection.start();

        }
        catch (JMSException e)
        {
            fail("Creating new connection when:" + e.getMessage());
        }
        catch (AMQException e)
        {
            fail("Creating new connection when:" + e.getMessage());
        }
        catch (URLSyntaxException e)
        {
            fail("Creating new connection when:" + e.getMessage());
        }

        return connection;
    }

    private void declareExchange(int channelId, String _type, String _name, boolean nowait)
        throws AMQException, FailoverException
    {
        AMQFrame exchangeDeclare =
            ExchangeDeclareBody.createAMQFrame(channelId,
                ((AMQConnection) _connection).getProtocolHandler().getProtocolMajorVersion(),
                ((AMQConnection) _connection).getProtocolHandler().getProtocolMinorVersion(), null, // arguments
                false, // autoDelete
                false, // durable
                new AMQShortString(_name), // exchange
                false, // internal
                nowait, // nowait
                true, // passive
                0, // ticket
                new AMQShortString(_type)); // type

        if (nowait)
        {
            ((AMQConnection) _connection).getProtocolHandler().writeFrame(exchangeDeclare);
        }
        else
        {
            ((AMQConnection) _connection).getProtocolHandler().syncWrite(exchangeDeclare, ExchangeDeclareOkBody.class,
                SYNC_TIMEOUT);
        }
    }

    private void createChannel(int channelId) throws AMQException, FailoverException
    {
        ((AMQConnection) _connection).getProtocolHandler().syncWrite(ChannelOpenBody.createAMQFrame(channelId,
                ((AMQConnection) _connection).getProtocolHandler().getProtocolMajorVersion(),
                ((AMQConnection) _connection).getProtocolHandler().getProtocolMinorVersion(), null), // outOfBand
            ChannelOpenOkBody.class);

    }

    public void onException(JMSException jmsException)
    {
        // _logger.info("CCT" + jmsException);
        fail(jmsException.getMessage());
    }

    public void bytesSent(long count)
    { }

    public void bytesReceived(long count)
    { }

    public boolean preFailover(boolean redirect)
    {
        return false;
    }

    public boolean preResubscribe()
    {
        return false;
    }

    public void failoverComplete()
    { }
}
