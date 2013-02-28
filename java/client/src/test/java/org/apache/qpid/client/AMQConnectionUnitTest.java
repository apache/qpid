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
package org.apache.qpid.client;

import java.util.concurrent.atomic.AtomicReference;

import javax.jms.ExceptionListener;
import javax.jms.JMSException;

import org.apache.qpid.AMQInvalidArgumentException;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.test.utils.QpidTestCase;

public class AMQConnectionUnitTest extends QpidTestCase
{
    String _url = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'";

    public void testVerifyQueueOnSendDefault() throws Exception
    {
        MockAMQConnection connection = new MockAMQConnection(_url);
        assertFalse(connection.validateQueueOnSend());
    }

    public void testVerifyQueueOnSendViaSystemProperty() throws Exception
    {
        setTestSystemProperty(ClientProperties.VERIFY_QUEUE_ON_SEND, "true");
        MockAMQConnection connection = new MockAMQConnection(_url);
        assertTrue(connection.validateQueueOnSend());

        setTestSystemProperty(ClientProperties.VERIFY_QUEUE_ON_SEND, "false");
        connection = new MockAMQConnection(_url);
        assertFalse(connection.validateQueueOnSend());
    }

    public void testVerifyQueueOnSendViaURL() throws Exception
    {
        MockAMQConnection connection = new MockAMQConnection(_url + "&" +  ConnectionURL.OPTIONS_VERIFY_QUEUE_ON_SEND + "='true'");
        assertTrue(connection.validateQueueOnSend());

        connection = new MockAMQConnection(_url + "&" +  ConnectionURL.OPTIONS_VERIFY_QUEUE_ON_SEND + "='false'");
        assertFalse(connection.validateQueueOnSend());
    }

    public void testVerifyQueueOnSendViaURLoverridesSystemProperty() throws Exception
    {
        setTestSystemProperty(ClientProperties.VERIFY_QUEUE_ON_SEND, "false");
        MockAMQConnection connection = new MockAMQConnection(_url + "&" +  ConnectionURL.OPTIONS_VERIFY_QUEUE_ON_SEND + "='true'");
        assertTrue(connection.validateQueueOnSend());
    }

    public void testExceptionReceived()
    {
        AMQInvalidArgumentException expectedException = new AMQInvalidArgumentException("Test", null);
        final AtomicReference<JMSException> receivedException = new AtomicReference<JMSException>();
        try
        {
            MockAMQConnection connection = new MockAMQConnection(_url);
            connection.setExceptionListener(new ExceptionListener()
            {

                @Override
                public void onException(JMSException jmsException)
                {
                    receivedException.set(jmsException);
                }
            });
            connection.exceptionReceived(expectedException);
        }
        catch (Exception e)
        {
            fail("Failure to test exceptionRecived:" + e.getMessage());
        }
        JMSException exception = receivedException.get();
        assertNotNull("Expected JMSException but got null", exception);
        assertEquals("JMSException error code is incorrect", Integer.toString(expectedException.getErrorCode().getCode()), exception.getErrorCode());
        assertNotNull("Expected not null message for JMSException", exception.getMessage());
        assertTrue("JMSException error message is incorrect",  exception.getMessage().contains(expectedException.getMessage()));
        assertEquals("JMSException linked exception is incorrect", expectedException, exception.getLinkedException());
    }

    /**
     * This should expand to test all the defaults.
     */
    public void testDefaultStreamMessageEncoding() throws Exception
    {
        MockAMQConnection connection = new MockAMQConnection(_url);
        assertTrue("Legacy Stream message encoding should be the default",connection.isUseLegacyStreamMessageFormat());
    }

    /**
     * This should expand to test all the connection properties.
     */
    public void testStreamMessageEncodingProperty() throws Exception
    {
        MockAMQConnection connection = new MockAMQConnection(_url + "&use_legacy_stream_msg_format='false'");
        assertFalse("Stream message encoding should be amqp/list",connection.isUseLegacyStreamMessageFormat());
    }

}
